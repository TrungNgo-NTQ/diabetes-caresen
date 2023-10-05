package net.huray.caresens.callbacks

interface BluetoothInitializeCallbacks {
    fun onSuccess()
    fun onError(errorMsg: String?)
}