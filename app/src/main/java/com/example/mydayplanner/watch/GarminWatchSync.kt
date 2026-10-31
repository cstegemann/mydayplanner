package com.example.mydayplanner.watch

import android.content.Context
import android.util.Log
import com.example.mydayplanner.config.Project
import com.example.mydayplanner.data.TodoRepository
import com.example.mydayplanner.weather.WeatherRepository
import com.example.mydayplanner.weather.WeatherStatus
import com.example.mydayplanner.ui.home.evaluateRules
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class SnapshotSendOrigin(val label: String) {
    WEATHER_UPDATE("Weather update"),
    INITIAL_LOAD("Initial data load"),
    PLANNER_UPDATE("Planner data update"),
    SDK_READY("Connect IQ initialized"),
    SDK_STATUS("Connect IQ lifecycle"),
    APP_RESUME("App resumed"),
    DEVICE_RECONNECTED("Device connected"),
    FORCED("Manual send")
}

/** Sends the latest planner snapshot to every connected Garmin device with the watch face installed. */
class GarminWatchSync(
    context: Context,
    repo: TodoRepository,
    weatherRepository: WeatherRepository
) {
    private val appContext = context.applicationContext
    private val connectIq = ConnectIQ.getInstance(appContext, ConnectIQ.IQConnectType.WIRELESS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registeredDevices = mutableSetOf<Long>()
    private val pendingForcedSends = ConcurrentHashMap<Long, Long>()
    private val history = WatchSyncHistory()
    internal val syncHistory = history.attempts

    @Volatile
    private var sdkReady = false

    @Volatile
    private var latestPayload: Map<String, Any>? = null

    private var hasBuiltPayload = false

    init {
        scope.launch {
            val planner = combine(repo.todayTodos, repo.tracking, repo.config, repo.routineProgress, repo.isLoaded) {
                    todos, tracking, config, routineProgress, isLoaded ->
                if (!isLoaded) null else {
                    val freeDay = tracking.current == Project.FREE_DAY
                    val notices = config?.let { evaluateRules(it, todos, routineProgress, freeDay) }.orEmpty()
                    buildWatchSnapshot(config, routineProgress, todos, notices, freeDay)
                }
            }
            combine(planner, weatherRepository.status) { plannerPayload, weatherStatus ->
                if (plannerPayload == null) return@combine null
                plannerPayload.toMutableMap().apply {
                    weatherStatus.values?.let { values ->
                        values.temperatureC?.let { put("tc", it) }
                        values.delta3h?.let { put("td", it) }
                        values.rain?.let { put("rn", it) }
                        values.wind?.let { put("wi", it) }
                        values.thunder?.let { put("th", it) }
                    }
                    put("wd", 0) // Only advertise fine forecast when radar nowcasting really exists.
                }
            }.filterNotNull().collect { payload ->
                latestPayload = payload
                val origin = if (hasBuiltPayload) SnapshotSendOrigin.WEATHER_UPDATE else SnapshotSendOrigin.INITIAL_LOAD
                hasBuiltPayload = true
                sendLatest(origin)
            }
        }

        runCatching { connectIq.initialize(appContext, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                sdkReady = true
                sendLatest(SnapshotSendOrigin.SDK_READY)
            }

            override fun onInitializeError(errorStatus: ConnectIQ.IQSdkErrorStatus) {
                sdkReady = false
                val attempt = history.start(SnapshotSendOrigin.SDK_STATUS, latestPayload)
                history.append(attempt.id, "Connect IQ initialization failed: $errorStatus")
                Log.w(TAG, "Connect IQ initialization failed: $errorStatus")
            }

            override fun onSdkShutDown() {
                sdkReady = false
                synchronized(registeredDevices) { registeredDevices.clear() }
                pendingForcedSends.values.toSet().forEach { history.append(it, "Stopped waiting: Connect IQ SDK shut down") }
                pendingForcedSends.clear()
                val attempt = history.start(SnapshotSendOrigin.SDK_STATUS, latestPayload)
                history.append(attempt.id, "Connect IQ SDK shut down")
            }
        }) }.onFailure { error ->
            val attempt = history.start(SnapshotSendOrigin.SDK_STATUS, latestPayload)
            history.append(attempt.id, "Connect IQ initialization threw: ${error.message}")
            Log.w(TAG, "Connect IQ initialization failed", error)
        }
    }

    /** Re-checks connections and sends current data, for example when the app returns to the foreground. */
    fun onAppResumed() = sendLatest(SnapshotSendOrigin.APP_RESUME)

    /** Manual and automatic attempts share the same detailed, timestamped history. */
    fun sendSnapshot() = sendLatest(SnapshotSendOrigin.FORCED)

    private fun sendLatest(origin: SnapshotSendOrigin) {
        val attempt = history.start(origin, latestPayload)
        history.append(attempt.id, "Snapshot requested: ${origin.label}")
        if (!sdkReady) {
            history.append(attempt.id, "Stopped: Connect IQ SDK is not ready")
            return
        }
        if (attempt.payload == null) {
            history.append(attempt.id, "Stopped: planner data has not finished loading")
            return
        }
        val devices = runCatching { connectIq.knownDevices.orEmpty() }.getOrElse { error ->
            history.append(attempt.id, "Known device lookup failed: ${error.message}")
            return
        }
        history.append(attempt.id, "Connect IQ SDK ready; found ${devices.size} known device(s)")
        devices.forEach { device ->
            val shouldRegister = synchronized(registeredDevices) {
                registeredDevices.add(device.deviceIdentifier)
            }
            if (shouldRegister) {
                runCatching {
                    connectIq.registerForDeviceEvents(device) { changedDevice, status ->
                        val pendingId = pendingForcedSends[changedDevice.deviceIdentifier]
                        pendingId?.let { history.append(it, "${changedDevice.friendlyName}: device event $status") }
                        if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                            val pending = pendingForcedSends.remove(changedDevice.deviceIdentifier)
                            val waitingAttempt = history.attempts.value.firstOrNull { it.id == pending }
                            val reconnect = waitingAttempt ?: history.start(SnapshotSendOrigin.DEVICE_RECONNECTED, latestPayload)
                            history.append(reconnect.id, "${changedDevice.friendlyName}: connected; requesting watch app info")
                            sendTo(changedDevice, reconnect)
                        } else if (pendingId == null) {
                            val event = history.start(SnapshotSendOrigin.DEVICE_RECONNECTED, null)
                            history.append(event.id, "${changedDevice.friendlyName}: device event $status")
                        }
                    }
                    history.append(attempt.id, "${device.friendlyName}: registered device events")
                }.onFailure { error ->
                    synchronized(registeredDevices) { registeredDevices.remove(device.deviceIdentifier) }
                    history.append(attempt.id, "${device.friendlyName}: event registration failed (${error.message})")
                }
            }
            val status = runCatching { connectIq.getDeviceStatus(device) }.getOrElse { error ->
                history.append(attempt.id, "${device.friendlyName}: status lookup failed (${error.message})")
                IQDevice.IQDeviceStatus.UNKNOWN
            }
            history.append(attempt.id, "${device.friendlyName}: current status $status")
            if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                pendingForcedSends.remove(device.deviceIdentifier)?.let {
                    history.append(it, "${device.friendlyName}: waiting send superseded by attempt ${attempt.id}")
                }
                history.append(attempt.id, "${device.friendlyName}: requesting watch app info")
                sendTo(device, attempt)
            } else if (origin == SnapshotSendOrigin.FORCED) {
                pendingForcedSends.put(device.deviceIdentifier, attempt.id)?.let {
                    history.append(it, "${device.friendlyName}: superseded by manual attempt ${attempt.id}")
                }
                history.append(attempt.id, "${device.friendlyName}: waiting for a connected device event")
            } else {
                history.append(attempt.id, "${device.friendlyName}: skipped; device is not connected")
            }
        }
    }

    private fun sendTo(device: IQDevice, attempt: WatchSyncAttempt) {
        val payload = attempt.payload
        if (payload == null) {
            history.append(attempt.id, "${device.friendlyName}: stopped; no planner payload")
            return
        }
        runCatching {
            connectIq.getApplicationInfo(WATCH_FACE_UUID, device,
                object : ConnectIQ.IQApplicationInfoListener {
                    override fun onApplicationInfoReceived(app: IQApp) {
                        history.append(attempt.id, "${device.friendlyName}: watch app found; sending payload")
                        runCatching {
                            connectIq.sendMessage(device, app, payload) { _, _, status ->
                                history.append(attempt.id, "${device.friendlyName}: send result $status")
                                if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                                    Log.w(TAG, "Watch snapshot send failed for ${device.friendlyName}: $status")
                                }
                            }
                        }.onFailure { error ->
                            history.append(attempt.id, "${device.friendlyName}: send threw (${error.message})")
                        }
                    }

                    override fun onApplicationNotInstalled(applicationId: String) {
                        history.append(attempt.id, "${device.friendlyName}: watch face $applicationId is not installed")
                    }
                }
            )
        }.onFailure { error ->
            history.append(attempt.id, "${device.friendlyName}: watch app lookup failed (${error.message})")
        }
    }

    private companion object {
        const val TAG = "GarminWatchSync"
        const val WATCH_FACE_UUID = "36629bca-a6fa-4be8-a2b2-4a8a870c7415"
    }
}
