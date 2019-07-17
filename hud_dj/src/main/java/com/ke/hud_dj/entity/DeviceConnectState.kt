package com.ke.hud_dj.entity

enum class DeviceConnectState(type: Int) {

    /**
     * 已连接
     */
    Connected(3),
    /**
     * 连接中
     */
    Connecting(2),
    /**
     *未连接
     */
    Disconnected(0)


}

fun Int.toDeviceConnectState() = when (this) {
    3 -> DeviceConnectState.Connected
    2 -> DeviceConnectState.Connecting
    else -> DeviceConnectState.Disconnected
}