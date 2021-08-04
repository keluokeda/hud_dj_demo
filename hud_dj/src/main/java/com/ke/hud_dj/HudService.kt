package com.ke.hud_dj

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.annotation.CheckResult
import android.text.TextUtils
import androidx.annotation.IntRange
import com.example.bletohud.DJBTManager
import com.example.bletohud.bleDevice.CamerasInfo
import com.example.bletohud.bleDevice.NaviTrafficStatus

import com.example.bletohud.bleDevice.Update
import com.example.bletohud.bleDevice.bean.HudState
import com.example.bletohud.bleDevice.listener.OnAbsConnectListener
import com.example.bletohud.bleDevice.listener.OnAbsGetDataListener
import com.example.bletohud.bleDevice.recevie.FirmwareInfo
import com.example.bletohud.bleDevice.utils.ToolUtil
import com.ke.hud_dj.entity.*
import com.ke.hud_dj.exception.NeedRetryException
import com.ke.hud_dj.exception.RetryTimesOutExcrption
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit


class HudService private constructor() {

    init {
        val key = byteArrayOf(
            0x9A.toByte(),
            0x5E.toByte(),
            0x81.toByte(),
            0x8A.toByte(),
            0x85.toByte(),
            0xAF.toByte(),
            0xD1.toByte(),
            0x65.toByte(),
            0xCA.toByte(),
            0xE7.toByte(),
            0x7D.toByte(),
            0x7C.toByte(),
            0xCC.toByte(),
            0x87.toByte(),
            0xB7.toByte(),
            0xF1.toByte()
        )
        ToolUtil.setUserKey(key)
    }

    val connectStateSubject: Subject<DeviceConnectState> = BehaviorSubject.create()


    private var reconnectDisposable: Disposable? = null

    @Suppress("MemberVisibilityCanBePrivate")
    val reconnectResultSubject = PublishSubject.create<Boolean>()

    private var lastNavigationInfo: NavigationInfo? = null

    /**
     * 是否自动重连
     */
    private var autoReconnect = true


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


            messageHandler?.log("action = ${intent.action} extra = ${intent.extras}")


            val bluetoothDevice: BluetoothDevice? =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)


            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED && intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    -1
                ) == BluetoothAdapter.STATE_OFF
            ) {
                messageHandler?.log("发现蓝牙断开")
                connectStateSubject.onNext(DeviceConnectState.Disconnected)
                return
            }


            messageHandler?.log(
                "HudService 找到hud 设备 name = ${bluetoothDevice?.name},address =  ${bluetoothDevice?.address},bondState ${bluetoothDevice?.bondState},type ${bluetoothDevice?.type},当前蓝牙地址 $bluetoothDeviceAddress"
            )

            if (bluetoothDeviceAddress != null && TextUtils.equals(
                    bluetoothDeviceAddress,
                    bluetoothDevice?.address
                )
            ) {

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
    }


    private fun onDeviceDisconnected() {

//        chatService.sender.upDateOta()

        if (autoReconnect && !isUserQuit) {
            startReconnect()
        }

        if (isUserQuit) {
            isUserQuit = false
        }

    }


    /**
     * 升级软件
     */

    fun otaUpdate(file: File, application: Application): Observable<Int> {

        return Observable.create { emitter ->

            Update.getInstance(application)
                .UpdateOtaDataByLocal(file.absolutePath, object : OnAbsGetDataListener() {

                    override fun onProgress(p0: Double) {

                        val progress = (p0 * 100).toInt()

                        messageHandler?.log("更新进度变更 $p0 $progress")

                        emitter.onNext(progress)

                        if (progress == 100) {
                            emitter.onComplete()
                        }

                    }
                })
        }


//        return Observable.just(false)
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

                reconnectResultSubject.onNext(it)


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
//                    emitter.onComplete()
                }

                override fun onConnectSuccess(p0: String) {


                    messageHandler?.log("连接蓝牙设备成功 $p0")

                    if (emitter.isDisposed) {
                        return
                    }
                    emitter.onNext(true)
//                    emitter.onComplete()

                }

            })

            if (result) {
                connectStateSubject.onNext(DeviceConnectState.Connecting)
            }
        }

    }


    /**
     * 发送时间给hud
     */
    fun sendTime() = chatService.sender.sendTime()

    /**
     * 断开连接
     */
    fun disconnect(needReconnect: Boolean = false) {
        messageHandler?.log("用户点击断开连接")
        chatService.stop()
        connectStateSubject.onNext(DeviceConnectState.Disconnected)

        if (!needReconnect) {

            isUserQuit = true

            reconnectDisposable?.dispose()
        }


//        Observable.timer(5, TimeUnit.SECONDS)
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe {
//                isUserQuit = false
//            }
    }


    /**
     * 发送路况光柱图
     */
    fun sendTrafficStatus(
        pointerBitmap: Bitmap,
        list: List<NavigationTrafficStatus>,
        progress: Float
    ) {
        val target = list.map {
            NaviTrafficStatus()
                .apply {
                    length = it.length
                    status = it.status
                }
        }
        val bitmap = chatService.getTrafficBitmap(target)
        val result =
            chatService.combineBitmap(bitmap, scaleBitmap(pointerBitmap, 8), progress)
        chatService.sender.sendTrafficStatus(target, result)
    }

    /**
     * 发送心跳包
     */
    fun sendHeart(): Boolean = chatService.sender.sentHeart()

    /**
     * 发送导航信息
     */
    fun sendNavigationInformationWithDirection(
        navigationInfo: NavigationInfo
    ): Boolean {
        lastNavigationInfo = navigationInfo
        return chatService.sender.sendNavigationInformationWithDirection(
            if (navigationInfo.currentStepRetainDistance >= 2000) 9 else navigationInfo.iconType,
            navigationInfo.currentStepRetainDistance,
            navigationInfo.currentRoadName,
            navigationInfo.nextRoadName,
            navigationInfo.pathRetainTime,
            navigationInfo.pathRetainDistance,
            navigationInfo.currentSpeed
        )

    }

    /**
     * 导航关闭时调用这个方法
     */
    fun onNavigationDestroy(): Boolean {
        return chatService.sender.sendNavigationInformationWithDirection(
            255,
            0,
            "",
            "", 0, 0, 0
        )
    }

    /**
     * 发送图片
     */
    fun sendImage(bitmap: Bitmap): Boolean {
        lastNavigationInfo?.apply {
            sendNavigationInformationWithDirection(this)
        }
        val newBitmap = scaleBitmap(bitmap, 160)
        val compressedBitmap = compressBitmap(newBitmap, 8) ?: return false
        messageHandler?.log("压缩后的图片大小 ${compressedBitmap.byteCount} 图片宽度 = ${compressedBitmap.width} 图片高度${compressedBitmap.height}")
        return chatService.sender.sendImg(compressedBitmap)
    }

    /**
     * 取消图片
     */
    fun clearImage(): Boolean =
        chatService.sender.clearImg()


    /**
     * 发送电话号码
     */
    fun sendPhoneWithName(phone: String, name: String?) =
        chatService.sender.sendPhoneWithName(1, phone, name)

    /**
     * 取消显示来电
     */
    fun cancelSendPhone() = chatService.sender.sendPhoneWithName(0, "", "")

    /**
     * 发送摄像头信息
     */
    fun sendCameraInfoList(cameraInfoList: List<CameraInfo>) =
        chatService.sender.sendListCameraInformation(cameraInfoList.map { toCameraInfo(it) }
            .filter { it.cameraDistance < 500 }
            .toTypedArray())


    /**
     * 发送道路图片
     */
    fun sendRoadImage(bitmap: Bitmap, height: Int = 20): Boolean {
        lastNavigationInfo?.apply {
            sendNavigationInformationWithDirection(this)
        }

        val newBitmap = scaleBitmap(bitmap, height)
        val compressedBitmap = compressBitmap(newBitmap, 2) ?: return false

        messageHandler?.log("新的图片的宽度 = ${compressedBitmap.width} ， 高度 = ${compressedBitmap.height} ，图片大小${compressedBitmap.byteCount}")


        return chatService.sender.sendRoadImageWithPositionX(0, 0, compressedBitmap, true)
    }


    fun scaleBitmap(bitmap: Bitmap, height: Int): Bitmap {


        val scale = height * 1.0f / bitmap.height
        val matrix = Matrix()
        matrix.postScale(scale, scale)

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    }

    fun compressBitmap(bitmap: Bitmap, sizeLimit: Long): Bitmap? {
        val baos = ByteArrayOutputStream()
        var quality = 100
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)

        // 循环判断压缩后图片是否超过限制大小
        while (baos.toByteArray().size / 1024 > sizeLimit && quality > 0) {
            // 清空baos
            baos.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            quality -= 10
        }
        return BitmapFactory.decodeStream(ByteArrayInputStream(baos.toByteArray()), null, null)
    }


    /**
     * 取消道路图片显示
     */
    fun clearRoadImage() = chatService.sender.sendCancleRoadImageWithPositionX()

    /**
     * 获取hud信息
     */
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
                        version = p0.vision ?: "",
                        protocol = p0.protocol ?: "",
                        fileUrl = p0.fileUrl ?: ""

                    )
                )
            }

            override fun onGetFirmwareInfoFailed() {
                super.onGetFirmwareInfoFailed()

                messageHandler?.log("获取版本信息失败")

                if (emitter.isDisposed) {
                    return
                }

                emitter.onError(RuntimeException("获取版本信息失败"))
            }


        })
    }


    private fun toCameraInfo(cameraInfo: CameraInfo) =
        CamerasInfo().apply {
            cameraType = cameraInfo.type
            cameraSpeed = cameraInfo.speed
            cameraDistance = cameraInfo.distance
        }

    /**
     * 设置引擎类型
     */
    fun setEngineType(engineType: EngineType): Boolean {
        return chatService.sender.setEngineType(engineType.type)
    }

    /**
     * 获取引擎类型
     */
    fun getEngineType(): Observable<EngineType> {
        return Observable.create { emitter ->
            val result = chatService.geter.getEngineType(object : OnAbsGetDataListener() {
                override fun onGetEngineTypeInfo(type: Int) {

                    fromType(type)?.apply {
                        emitter.onNext(this)
                        emitter.onComplete()
                    }
                }

                override fun dataError(str: String) {
                    super.dataError(str)
                    emitter.onError(RuntimeException(str))
                }
            })

            if (!result) {
                emitter.onError(java.lang.RuntimeException("获取失败"))
            }
        }
    }

    /**
     * 获取蜂鸣器开关
     */
    fun getBuzzerSwitch(): Observable<Boolean> {
        return Observable.create {
            val result = chatService.geter.getBuzzerSwitch(object : OnAbsGetDataListener() {
                override fun onGetBuzzerSwitch(on: Boolean) {
                    if (it.isDisposed) {
                        return
                    }
                    it.onNext(on)
                    it.onComplete()
                }
            })
            if (!result) {
                it.onNext(false)
                it.onComplete()
            }
        }
    }

    /**
     * 设置蜂鸣器开关
     */
    fun setBuzzerSwitch(boolean: Boolean): Boolean {
        return chatService.sender.setHudSwitch(HudState.HUD_BUZZER, boolean)
    }

    companion object {
        val hudService = HudService()

        val intentFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
    }
}