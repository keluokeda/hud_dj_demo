package com.ke.hud_dj

interface MessageHandler {

    /**
     * 打印消息
     */
    fun log(message: String)

    /**
     * 处理错误
     */
    fun error(throwable: Throwable)
}