package net.huray.caresens.callbacks

import android.util.SparseArray
import net.huray.caresens.ble.GlucoseRecord
import net.huray.caresens.data.DeviceInfo
import net.huray.caresens.enums.DataReadState

interface BluetoothDataCallbacks {
    /*
        STATE: DataReadState.DEVICE_INFO_READ_COMPLETE,
               DataReadState.GlUCOSE_RECORD_READ_COMPLETE,
               DataReadState.BLE_DEVICE_NOT_SUPPORTED,
               DataReadState.BLE_OPERATE_FAILED,
               DataReadState.BLE_OPERATE_NOT_SUPPORTED,
               DataReadState.UNKNOWN_ERROR,
     */
    fun onRead(
        dataReadState: DataReadState, errorMsg: String?, deviceInfo: DeviceInfo?,
        glucoseRecords: SparseArray<GlucoseRecord>?
    )
}
