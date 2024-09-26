package net.huray.caresens.enums


enum class ConnectState(val sourceId: Int) {
    CONNECTING(0),
    CONNECTED(1),
    DISCONNECTED(2),
    ERROR(3),
    UNKNOWN(-1);

    companion object {
        fun getConnectState(sourceId: Int) =
            when (sourceId) {
                0 -> CONNECTING
                1 -> CONNECTED
                2 -> DISCONNECTED
                3 -> ERROR
                else -> UNKNOWN
            }
    }
}