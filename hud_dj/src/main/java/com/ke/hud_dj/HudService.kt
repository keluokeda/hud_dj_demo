package com.ke.hud_dj

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.support.annotation.CheckResult
import com.example.bletohud.DJBTManager
import com.example.bletohud.bleDevice.CamerasInfo
import com.example.bletohud.bleDevice.OnAbsConnectListener
import com.example.bletohud.bleDevice.OnAbsGetDataListener
import com.example.bletohud.bleDevice.recevie.FirmwareInfo
import com.ke.hud_dj.entity.CameraInfo
import com.ke.hud_dj.entity.DeviceConnectState
import com.ke.hud_dj.entity.HudInfo
import com.ke.hud_dj.entity.toDeviceConnectState
import com.ke.hud_dj.exception.NeedRetryException
import com.ke.hud_dj.exception.RetryTimesOutExcrption
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.util.concurrent.TimeUnit


class HudService private constructor() {
    val connectStateSubject: Subject<DeviceConnectState> = BehaviorSubject.create()


    private var reconnectDisposable: Disposable? = null


    /**
     * 是否自动重连
     */
    var autoReconnect = true


    private var isUserQuit = false


    private var bluetoothDeviceAddress: String? = null

    /**
     * 重连尝试次数
     */
    private val maxRetryCount = 3
    /**
     * 重试间隔
     */
    private val retryInterval = 5L

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()


    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {


            messageHandler?.log("action = ${intent.action}")


            when (intent.action) {


                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    connectStateSubject.onNext(DeviceConnectState.Connected)
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    connectStateSubject.onNext(DeviceConnectState.Disconnected)
                    onDeviceDisconnected()
                }
            }
        }
    }


    private fun onDeviceDisconnected() {

        if (autoReconnect && !isUserQuit) {
            startReconnect()
        }

    }


    /**
     * 开始重新连接
     */
    private fun startReconnect() {

        messageHandler?.log("准备重新连接")

        val device = try {
            bluetoothAdapter.getRemoteDevice(bluetoothDeviceAddress)

        } catch (e: Exception) {
            messageHandler?.log("找不到设备 $bluetoothDeviceAddress")
            messageHandler?.error(e)
            null
        } ?: return


        var retryCount = 0

        reconnectDisposable?.dispose()

        reconnectDisposable = Observable.create<Boolean> { emitter ->

            retryCount += 1

            messageHandler?.log("开始尝试第" + retryCount + "次重连")

            chatService.connect(device, object : OnAbsConnectListener() {
                override fun onConnectSuccess(p0: String?) {

                    messageHandler?.log("第$retryCount 次重连成功 $p0")

                    if (emitter.isDisposed) {
                        return
                    }

                    emitter.onNext(true)
                    emitter.onComplete()
                }

                override fun onConnectFailed(p0: String) {
                    messageHandler?.log("第$retryCount 次重连失败 $p0")
                    if (emitter.isDisposed) {
                        return
                    }

                    if (retryCount <= maxRetryCount) {
                        emitter.onError(NeedRetryException())

                    } else {
                        emitter.onError(RetryTimesOutExcrption())
                    }
                }
            })

        }.retryWhen {
            it.flatMap { throwable ->


                messageHandler?.log("判断是否需要重连 ${throwable.javaClass.name}")

                if (throwable is NeedRetryException) {
                    return@flatMap Observable.just(1).delay(retryInterval, TimeUnit.SECONDS)
                } else {
                    return@flatMap Observable.error<Any>(throwable)
                }

            }
        }
            .doOnDispose {
                messageHandler?.log("hud重连取消")
            }
            .subscribe({
                messageHandler?.log("hud重连结果 $it")
            }, {
                messageHandler?.log("hud重连失败")
                messageHandler?.error(it)
            }, {
                messageHandler?.log("hud重连完成")
            })


    }


    var messageHandler: MessageHandler? = null


    private var chatService = DJBTManager.getInstance()


    /**
     * 连接状态
     */
    val connectState
        get() = chatService.state.toDeviceConnectState()


    /**
     * 连接设备
     * @param bluetoothDevice 要连接的设备
     */
    @CheckResult
    fun connectDevice(bluetoothDevice: BluetoothDevice): Observable<Boolean> {

        reconnectDisposable?.dispose()


        this.bluetoothDeviceAddress = bluetoothDevice.address
        return Observable.create { emitter ->

            //            connectHudDevice(bluetoothDevice, emitter)

            val result = chatService.connect(bluetoothDevice, object : OnAbsConnectListener() {
                override fun onConnectFailed(p0: String) {

                    messageHandler?.log("连接蓝牙设备失败 $p0")

                    if (emitter.isDisposed) {
                        return
                    }
                    emitter.onNext(false)
                    emitter.onComplete()
                }

                override fun onConnectSuccess(p0: String) {


                    messageHandler?.log("连接蓝牙设备成功 $p0")

                    if (emitter.isDisposed) {
                        return
                    }
                    emitter.onNext(true)
                    emitter.onComplete()

                }

            })

            if (result) {
                connectStateSubject.onNext(DeviceConnectState.Connecting)
            }
        }

    }


    /**
     * 断开连接
     */
    fun disconnect() {
        chatService.stop()
        connectStateSubject.onNext(DeviceConnectState.Disconnected)

        isUserQuit = true

        reconnectDisposable?.dispose()


        Observable.timer(2, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                isUserQuit = false
            }
    }


    /**
     * 发送心跳包
     */
    @CheckResult
    fun sendHeart(): Boolean = chatService.sender.sentHeart()

    /**
     * 发送导航信息
     */
    @CheckResult
    fun sendNavigationInformationWithDirection(
        iconType: Int,
        currentStepRetainDistance: Int,
        currentRoadName: String,
        nextRoadName: String,
        pathRetainTime: Int,
        pathRetainDistance: Int,
        currentSpeed: Int
    ): Observable<Boolean> {

//        chatService.state

        return Observable.just(1)
            .observeOn(Schedulers.io())
            .map {
                chatService.sender.sendNavigationInformationWithDirection(
                    if (currentStepRetainDistance >= 2000) 9 else iconType,
                    currentStepRetainDistance,
                    currentRoadName,
                    nextRoadName,
                    pathRetainTime,
                    pathRetainDistance,
                    currentSpeed
                )
            }
    }

    /**
     * 发送图片
     */
    @CheckResult
    fun sendImage(bitmap: Bitmap): Observable<Boolean> {
        return Observable.just(chatService.sender.sendImg(bitmap)).subscribeOn(Schedulers.io())
    }

    /**
     * 取消图片
     */
    @CheckResult
    fun clearImage() = Observable.just(chatService.sender.clearImg()).subscribeOn(Schedulers.io())


    /**
     * 发送电话号码
     */
    fun sendPhoneWithName(phone: String, name: String?) = chatService.sender.sendPhoneWithName(1, phone, name)


    /**
     * 发送摄像头信息
     */
    fun sendCameraInfoList(cameraInfoList: List<CameraInfo>) =
        chatService.sender.sendListCameraInformation(cameraInfoList.map { toCameraInfo(it) }.toTypedArray())


    /**
     * 获取hud信息
     */
    @Deprecated(message = "一直回调错误信息")
    fun getHudInfo(): Observable<HudInfo> = Observable.create { emitter ->
        chatService.geter.getHUDInfo(object : OnAbsGetDataListener() {
            override fun onGetFirmwareInfo(p0: FirmwareInfo) {
                super.onGetFirmwareInfo(p0)

                messageHandler?.log("获取到hud信息 $p0")

                if (emitter.isDisposed) {
                    return
                }

                emitter.onNext(
                    HudInfo(
                        hardware = p0.hardware ?: "",
                        mode = p0.mode ?: "",
                        vision = p0.vision ?: "",
                        protocol = p0.protocol ?: "",
                        fileUrl = p0.fileUrl ?: ""
                    )
                )
            }

            override fun dataError(p0: String?) {
                super.dataError(p0)

                messageHandler?.log("发生错误 $p0")

                if (emitter.isDisposed) {
                    return
                }

                emitter.onError(RuntimeException(p0))
            }

            override fun unKnowData(p0: String?) {
                super.unKnowData(p0)
                messageHandler?.log("发生错误 $p0")

                if (emitter.isDisposed) {
                    return
                }

                emitter.onError(RuntimeException(p0))

            }

        })
    }


    private fun toCameraInfo(cameraInfo: CameraInfo) =
        CamerasInfo().apply {
            cameraType = cameraInfo.type
            cameraSpeed = cameraInfo.speed
            cameraDistance = cameraInfo.distance
        }


    companion object {
        val hudService = HudService()

        val intentFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
    }
}