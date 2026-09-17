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
import kotlinx.coroutines.launch

/** Sends the latest planner snapshot to every connected Garmin device with the watch face installed. */
class GarminWatchSync(
    context: Context,
    repo: TodoRepository
) {
    private val appContext = context.applicationContext
    private val connectIq = ConnectIQ.getInstance(appContext, ConnectIQ.IQConnectType.WIRELESS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registeredDevices = mutableSetOf<Long>()

    @Volatile
    private var sdkReady = false

    @Volatile
    private var latestPayload: Map<String, Any>? = null

    init {
        scope.launch {
            combine(repo.todayTodos, repo.tracking, repo.config, repo.routineProgress) {
                    todos, tracking, config, routineProgress ->
                val freeDay = tracking.current == Project.FREE_DAY
                val notices = config?.let {
                    evaluateRules(it, todos, routineProgress, freeDay)
                }.orEmpty()
                buildWatchSnapshot(config, routineProgress, todos, notices, freeDay)
            }.collect { payload ->
                latestPayload = payload
                sendLatest()
            }
        }

        connectIq.initialize(appContext, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                sdkReady = true
                registerDevicesAndSend()
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
        if (sdkReady) registerDevicesAndSend()
    }

    private fun registerDevicesAndSend() {
        connectIq.knownDevices.orEmpty().forEach { device ->
            val shouldRegister = synchronized(registeredDevices) {
                registeredDevices.add(device.deviceIdentifier)
            }
            if (shouldRegister) {
                connectIq.registerForDeviceEvents(device) { changedDevice, status ->
                    if (status == IQDevice.IQDeviceStatus.CONNECTED) sendTo(changedDevice)
                }
            }
            if (device.status == IQDevice.IQDeviceStatus.CONNECTED) sendTo(device)
        }
    }

    private fun sendLatest() {
        if (!sdkReady) return
        connectIq.knownDevices.orEmpty()
            .filter { it.status == IQDevice.IQDeviceStatus.CONNECTED }
            .forEach(::sendTo)
    }

    private fun sendTo(device: IQDevice) {
        if (latestPayload == null) return
        connectIq.getApplicationInfo(WATCH_FACE_UUID, device,
            object : ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    val payload = latestPayload ?: return
                    connectIq.sendMessage(device, app, payload) { _, _, status ->
                        if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                            Log.w(TAG, "Watch snapshot send failed for ${device.friendlyName}: $status")
                        }
                    }
                }

                override fun onApplicationNotInstalled(applicationId: String) {
                    Log.d(TAG, "Watch face $applicationId is not installed on ${device.friendlyName}")
                }
            }
        )
    }

    private companion object {
        const val TAG = "GarminWatchSync"
        const val WATCH_FACE_UUID = "36629bca-a6fa-4be8-a2b2-4a8a870c7415"
    }
}
