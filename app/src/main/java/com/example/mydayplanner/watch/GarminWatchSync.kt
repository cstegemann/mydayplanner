package com.example.mydayplanner.watch

import android.content.Context
import android.util.Log
import com.example.mydayplanner.config.Project
import com.example.mydayplanner.data.TodoRepository
import com.example.mydayplanner.ui.home.evaluateRules
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

enum class SnapshotSendOrigin(val label: String) {
    INITIAL_LOAD("Initial data load"),
    PLANNER_UPDATE("Planner data update"),
    SDK_READY("Connect IQ initialized"),
    APP_RESUME("App resumed"),
    DEVICE_RECONNECTED("Device connected"),
    FORCED("Manual send")
}

data class SentWatchSnapshot(
    val sentAtMillis: Long,
    val deviceName: String,
    val origin: SnapshotSendOrigin,
    val payload: Map<String, Any>
)

data class ForcedSendLog(
    val startedAtMillis: Long,
    val entries: List<String>
)

/** Sends the latest planner snapshot to every connected Garmin device with the watch face installed. */
class GarminWatchSync(
    context: Context,
    repo: TodoRepository
) {
    private val appContext = context.applicationContext
    private val connectIq = ConnectIQ.getInstance(appContext, ConnectIQ.IQConnectType.WIRELESS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registeredDevices = mutableSetOf<Long>()
    private val pendingForcedSends = ConcurrentHashMap<Long, Long>()
    private val forcedSendIds = AtomicLong()
    private val _sentSnapshots = MutableStateFlow<List<SentWatchSnapshot>>(emptyList())
    val sentSnapshots: StateFlow<List<SentWatchSnapshot>> = _sentSnapshots.asStateFlow()
    private val _lastForcedSend = MutableStateFlow<ForcedSendLog?>(null)
    val lastForcedSend: StateFlow<ForcedSendLog?> = _lastForcedSend.asStateFlow()

    @Volatile
    private var sdkReady = false

    @Volatile
    private var latestPayload: Map<String, Any>? = null

    private var hasBuiltPayload = false

    init {
        scope.launch {
            combine(repo.todayTodos, repo.tracking, repo.config, repo.routineProgress, repo.isLoaded) {
                    todos, tracking, config, routineProgress, isLoaded ->
                if (!isLoaded) return@combine null
                val freeDay = tracking.current == Project.FREE_DAY
                val notices = config?.let {
                    evaluateRules(it, todos, routineProgress, freeDay)
                }.orEmpty()
                buildWatchSnapshot(config, routineProgress, todos, notices, freeDay)
            }.filterNotNull().collect { payload ->
                latestPayload = payload
                val origin = if (hasBuiltPayload) SnapshotSendOrigin.PLANNER_UPDATE else SnapshotSendOrigin.INITIAL_LOAD
                hasBuiltPayload = true
                sendLatest(origin)
            }
        }

        connectIq.initialize(appContext, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                sdkReady = true
                registerDevicesAndSend(SnapshotSendOrigin.SDK_READY)
            }

            override fun onInitializeError(errorStatus: ConnectIQ.IQSdkErrorStatus) {
                Log.w(TAG, "Connect IQ initialization failed: $errorStatus")
            }

            override fun onSdkShutDown() {
                sdkReady = false
                synchronized(registeredDevices) { registeredDevices.clear() }
            }
        })
    }

    /** Re-checks connections and sends current data, for example when the app returns to the foreground. */
    fun onAppResumed() {
        if (sdkReady) registerDevicesAndSend(SnapshotSendOrigin.APP_RESUME)
    }

    /** Immediately retries the current snapshot on all connected devices. */
    fun sendSnapshot() {
        val id = forcedSendIds.incrementAndGet()
        _lastForcedSend.value = ForcedSendLog(System.currentTimeMillis(), listOf("Send snapshot requested"))
        if (!sdkReady) {
            appendForcedLog(id, "Stopped: Connect IQ SDK is not ready")
            return
        }
        if (latestPayload == null) {
            appendForcedLog(id, "Stopped: planner data has not finished loading")
            return
        }
        appendForcedLog(id, "Connect IQ SDK ready; checking known devices")
        registerDevicesAndSend(SnapshotSendOrigin.FORCED, id)
    }

    private fun registerDevicesAndSend(origin: SnapshotSendOrigin, forcedSendId: Long? = null) {
        val devices = connectIq.knownDevices.orEmpty()
        forcedSendId?.let { appendForcedLog(it, "Found ${devices.size} known device(s)") }
        devices.forEach { device ->
            val shouldRegister = synchronized(registeredDevices) {
                registeredDevices.add(device.deviceIdentifier)
            }
            if (shouldRegister) {
                connectIq.registerForDeviceEvents(device) { changedDevice, status ->
                    val pendingForcedSend = pendingForcedSends[changedDevice.deviceIdentifier]
                    pendingForcedSend?.let { appendForcedLog(it, "${changedDevice.friendlyName}: device event $status") }
                    if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                        pendingForcedSends.remove(changedDevice.deviceIdentifier)
                        sendTo(
                            changedDevice,
                            if (pendingForcedSend != null) SnapshotSendOrigin.FORCED else SnapshotSendOrigin.DEVICE_RECONNECTED,
                            pendingForcedSend
                        )
                    }
                }
            }
            val status = runCatching { connectIq.getDeviceStatus(device) }
                .getOrElse { error ->
                    forcedSendId?.let { appendForcedLog(it, "${device.friendlyName}: status lookup failed (${error.message})") }
                    IQDevice.IQDeviceStatus.UNKNOWN
                }
            device.status = status
            if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                forcedSendId?.let { appendForcedLog(it, "${device.friendlyName}: connected; requesting watch app info") }
                sendTo(device, origin, forcedSendId)
            } else {
                forcedSendId?.let {
                    appendForcedLog(it, "${device.friendlyName}: current status $status; waiting for a device event")
                    pendingForcedSends[device.deviceIdentifier] = it
                }
            }
        }
    }

    private fun sendLatest(origin: SnapshotSendOrigin) {
        if (!sdkReady) return
        connectIq.knownDevices.orEmpty()
            .filter { runCatching { connectIq.getDeviceStatus(it) }.getOrNull() == IQDevice.IQDeviceStatus.CONNECTED }
            .forEach { sendTo(it, origin) }
    }

    private fun sendTo(
        device: IQDevice,
        origin: SnapshotSendOrigin,
        forcedSendId: Long? = null
    ) {
        if (latestPayload == null) return
        connectIq.getApplicationInfo(WATCH_FACE_UUID, device,
            object : ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    val payload = latestPayload ?: return
                    forcedSendId?.let { appendForcedLog(it, "${device.friendlyName}: watch app found; sending payload") }
                    _sentSnapshots.update { snapshots ->
                        listOf(SentWatchSnapshot(System.currentTimeMillis(), device.friendlyName, origin, payload.toMap())) +
                            snapshots.take(MAX_CACHED_SNAPSHOTS - 1)
                    }
                    connectIq.sendMessage(device, app, payload) { _, _, status ->
                        forcedSendId?.let { appendForcedLog(it, "${device.friendlyName}: send result $status") }
                        if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                            Log.w(TAG, "Watch snapshot send failed for ${device.friendlyName}: $status")
                        }
                    }
                }

                override fun onApplicationNotInstalled(applicationId: String) {
                    forcedSendId?.let { appendForcedLog(it, "${device.friendlyName}: watch face is not installed") }
                    Log.d(TAG, "Watch face $applicationId is not installed on ${device.friendlyName}")
                }
            }
        )
    }

    private fun appendForcedLog(id: Long, message: String) {
        if (forcedSendIds.get() != id) return
        _lastForcedSend.update { current ->
            current?.copy(entries = current.entries + message)
        }
    }

    private companion object {
        const val TAG = "GarminWatchSync"
        const val WATCH_FACE_UUID = "36629bca-a6fa-4be8-a2b2-4a8a870c7415"
        const val MAX_CACHED_SNAPSHOTS = 100
    }
}
