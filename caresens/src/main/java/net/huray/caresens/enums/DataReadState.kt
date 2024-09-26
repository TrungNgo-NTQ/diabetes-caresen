package net.huray.caresens.enums

enum class DataReadState(val value: Int) {
    DEVICE_INFO_READ_COMPLETE(1),
    GlUCOSE_RECORD_READ_COMPLETE(2),
    BLE_DEVICE_NOT_SUPPORTED(3),
    BLE_OPERATE_FAILED(4),
    BLE_OPERATE_NOT_SUPPORTED(5),
    UNKNOWN_ERROR(-1),
}