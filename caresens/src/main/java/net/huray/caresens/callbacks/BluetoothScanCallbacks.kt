package net.huray.caresens.callbacks

import net.huray.caresens.ble.ExtendedDevice
import net.huray.caresens.enums.ScanState

interface BluetoothScanCallbacks {
    /**
     *  STATE : ScanState_FAIL
     *          ScanState_SCANNING
     *          ScanState_STOPPED
     */
    fun onScan(state: ScanState, errorMsg: String?, devices: ArrayList<ExtendedDevice>?)
}