package com.ke.hud_dj_demo

import android.app.Application
import com.amap.api.navi.AmapNaviPage
import com.ke.hud_dj.HudService
import com.ke.hud_dj.MessageHandler
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy

class App : Application() {



    override fun onCreate() {
        super.onCreate()
        val formatStrategy = PrettyFormatStrategy.newBuilder()
            .showThreadInfo(true)
            .methodCount(5)
            .tag("logger")
            .build()
        Logger.addLogAdapter(AndroidLogAdapter(formatStrategy))



        HudService.hudService.messageHandler = object :MessageHandler{
            override fun log(message: String) {
                message.log()

            }

            override fun error(throwable: Throwable) {
                throwable.printStackTrace()
            }

        }
    }
}

fun String.log() {
    Logger.d(this)
}