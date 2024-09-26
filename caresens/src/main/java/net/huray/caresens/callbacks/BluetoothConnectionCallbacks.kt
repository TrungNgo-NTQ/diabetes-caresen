package net.huray.caresens.callbacks

import net.huray.caresens.data.DeviceInfo
import net.huray.caresens.enums.ConnectState

interface BluetoothConnectionCallbacks {
    /**
     *  STATE: ConnectState_CONNECTING
     *         ConnectState_CONNECTED
     *         ConnectState_DISCONNECTED
     *         ConnectState_ERROR
     */
    fun onStateChanged(
        state: ConnectState,
        errorMsg: String?,
        deviceInfo: DeviceInfo?
    )
}
