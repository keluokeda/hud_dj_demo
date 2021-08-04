package com.ke.hud_dj_demo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.amap.api.navi.AmapNaviPage
import com.amap.api.navi.AmapNaviParams
import com.ke.addresspicker.RxAddressPicker
import com.ke.hud_dj.HudService
import com.ke.hud_dj.entity.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_connect.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.contracts.Returns

class ConnectActivity : AppCompatActivity() {
    private lateinit var device: BluetoothDevice

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()


    private var hudService = HudService.hudService


    private val compositeDisposable = CompositeDisposable()


    private var heartLoopDisposable: Disposable? = null


    private var navigationLoopDisposable: Disposable? = null


    private fun getBondStateString(bondState: Int) = when (bondState) {
        BluetoothDevice.BOND_BONDED -> "已配对"
        BluetoothDevice.BOND_BONDING -> "配对中"
        BluetoothDevice.BOND_NONE -> "未配对"
        else -> "未知"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)

        loggerMessage("ConnectActivity onCreate")


        registerReceiver(hudService.receiver, HudService.intentFilter)


        hudService.connectStateSubject.observeOn(AndroidSchedulers.mainThread())
            .subscribe { connectState ->
                "状态变化 ${connectState.name}".log()
                when (connectState) {
                    DeviceConnectState.Connected -> device_state.setImageResource(R.drawable.ic_bluetooth_connected_green_500_24dp)
                    DeviceConnectState.Connecting -> {
                        device_state.setImageResource(R.drawable.ic_settings_bluetooth_blue_500_24dp)
                    }
                    DeviceConnectState.Disconnected -> device_state.setImageResource(R.drawable.ic_bluetooth_disabled_red_500_24dp)
                }
            }.addTo(compositeDisposable)

        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            ?: throw RuntimeException("需要传入 BluetoothDevice")




        device_detail.text =
            "name = ${device.name}\naddress = ${device.address}\n配对状态 = ${getBondStateString(device.bondState)}"


        initListener()

//        loopSendHeart()

    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
//        return super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.menu_connect, menu)
        return true

    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (item.itemId == R.id.action_navigation) {

            AmapNaviPage.getInstance()
                .showRouteActivity(
                    this,
                    AmapNaviParams(null),
                    null,
                    CustomNavigationActivity::class.java
                )

            return true
        }


        return super.onOptionsItemSelected(item)
    }

    private fun initListener() {
        pickAddress.setOnClickListener {
            RxAddressPicker(this)
                .pick()
                .subscribe {

                }
        }

        connect.setOnClickListener { connectDevice() }

        disconnect.setOnClickListener { hudService.disconnect() }

        send_heart.setOnClickListener { view ->
            loopSendHeart()
        }

        cancel_send_heart.setOnClickListener {
            heartLoopDisposable?.dispose()
        }

        send_navigation.setOnClickListener {


            hudService.sendNavigationInformationWithDirection(
                NavigationInfo(
                    3,
                    100,
                    "当前道路",
                    "下一个道路",
                    100,
                    1000,
                    60
                )
            )

        }

        send_road_image.setOnClickListener {

            hudService.sendNavigationInformationWithDirection(
                NavigationInfo(
                    3,
                    100,
                    "当前道路",
                    "下一个道路",
                    100,
                    1000,
                    60
                )
            )
            hudService.sendRoadImage(BitmapFactory.decodeResource(resources, R.mipmap.road))


        }

        cancel_send_road_image.setOnClickListener {
            val result = hudService.clearRoadImage()
            "取消车道图结果 $result".log()
        }
        send_image.setOnClickListener {

            hudService.sendNavigationInformationWithDirection(
                NavigationInfo(
                    3,
                    100,
                    "当前道路",
                    "下一个道路",
                    100,
                    1000,
                    60
                )
            )
            val bitmap = BitmapFactory.decodeResource(
                resources,
                R.mipmap.navi_cross
            )
            val target = hudService.compressBitmap(hudService.scaleBitmap(bitmap, 160), 16)
                ?: return@setOnClickListener
            hudService.sendImage(
                target
            )
            saveBitmap(target, "_")

        }

        clear_image.setOnClickListener {
            hudService.clearImage()

        }

        navigation_loop.setOnClickListener {

            var start = 0

            navigationLoopDisposable?.dispose()

            navigationLoopDisposable = Observable.interval(0, 1, TimeUnit.SECONDS)
                .map {
                    start++

                    hudService.sendNavigationInformationWithDirection(
                        NavigationInfo(
                            3,
                            start,
                            "当前道路",
                            "下一个道路",
                            100,
                            1000,
                            60
                        )
                    )
                }.map { result ->
                    return@map result to start
                }
                .doOnDispose {
                    loggerMessage("取消循环发送导航信息 已发次数 $start")
                }
                .subscribe {
                    loggerMessage("发送导航信息结果 ${it.first} 发送次数 ${it.second}")
                }

        }


        cancel_loop_navigation.setOnClickListener {
            navigationLoopDisposable?.dispose()
        }

        send_phone.setOnClickListener {
            val result = hudService.sendPhoneWithName("1234567890", "汉库克")

            loggerMessage("发送电话号码结果 $result")
        }


        send_camera_info_list.setOnClickListener {

            val result =
                hudService.sendCameraInfoList(
                    listOf(
                        CameraInfo(1, 80, 200, 0.0, 0.0),
                        CameraInfo(3, 90, 200, .0, .0)
                    )
                )

            loggerMessage("发送摄像机信息结果 $result")
        }

        get_version.setOnClickListener {
            hudService.getHudInfo().observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    AlertDialog.Builder(this)
                        .setTitle("Hud版本信息")
                        .setMessage(it.toString())
                        .setPositiveButton("确定") { _, _ -> }
                        .show()

                }, {
                    it.printStackTrace()
                })
                .addTo(compositeDisposable)
        }



        refresh_state.setOnClickListener {
            refresh_state.text = "刷新状态 = ${hudService.connectState.name}"
        }

        send_traffic_status.setOnClickListener {
            hudService.sendTrafficStatus(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.progress_pointer
                ),
                listOf(NavigationTrafficStatus(100, 0), NavigationTrafficStatus(50, 1)),
                seek_bar.progress / 100f
            )
        }

        get_engine_type.setOnClickListener {
            hudService.getEngineType().subscribe({
                AlertDialog.Builder(this)
                    .setTitle(
                        "name = ${it.typeName} type = ${it.type}"
                    ).show()
            }, {
                it.printStackTrace()
            })
        }

        val engineTypeList = EngineType.values()


//        spinner.setOnItemClickListener { _, _, position, id ->
//            val selectedType = engineTypeList[position]
//            AlertDialog.Builder(this)
//                .setTitle(
//                    "name = ${selectedType.typeName} type = ${selectedType.type}"
//                ).show()
//        }


        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedType = engineTypeList[position]
//                AlertDialog.Builder(this@ConnectActivity)
//                    .setTitle(
//                        "name = ${selectedType.typeName} type = ${selectedType.type}"
//                    ).show()

                val result = hudService.setEngineType(selectedType)

                AlertDialog.Builder(this@ConnectActivity)
                    .setTitle("设置引擎类型结果 $result $selectedType")
                    .show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

        }
        spinner.adapter =
            ArrayAdapter<EngineType>(this, android.R.layout.simple_list_item_1, engineTypeList)


        get_buzzer_switch.setOnClickListener {
            hudService.getBuzzerSwitch().subscribe {
                AlertDialog.Builder(this)
                    .setTitle("蜂鸣器 $it")
                    .show()
            }
        }

        set_buzzer_switch.setOnClickListener {
            hudService.setBuzzerSwitch(buzzer_switch.isChecked)
        }

        get_buzzer_and_engine_type.setOnClickListener {
            hudService.getBuzzerSwitch().subscribe({
                loggerMessage("读取到蜂鸣器开关 $it")
            }, {
                it.printStackTrace()
            })

            hudService.getEngineType().subscribe({
                loggerMessage("读取到引擎类型 $it")

            }, {
                it.printStackTrace()
            }
            )
        }
    }

    private fun saveBitmap(bitmap: Bitmap, type: String) {
        if (BuildConfig.DEBUG) {

            val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")


            val parent = File(filesDir, "hud_navigation")

            if (!parent.exists()) {
                parent.mkdir()
            }

            val file = File(parent, "${type}_${simpleDateFormat.format(Date())}.jpg")

            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
            fileOutputStream.flush()
        }
    }

    private fun loopSendHeart() {
        heartLoopDisposable?.dispose()

        heartLoopDisposable = Observable.interval(0, 5, TimeUnit.SECONDS)
            .map { hudService.sendHeart() to it }
            .doOnDispose {
                "取消发送心跳包".log()
            }
            .subscribe {
//                "心跳包发送结果 ${it.first} ${it.second}".log()
            }
    }


    private fun loggerMessage(message: String) {

        message.log()

    }


    private fun connectDevice() {

        Observable.just(bluetoothAdapter.getRemoteDevice(device.address))
            .flatMap { bluetoothDevice ->

                return@flatMap hudService.connectDevice(bluetoothDevice)

            }
            .subscribe({

                "连接设备结果 $it".log()

            }, {
                "连接设备出错".log()

                it.printStackTrace()
            })
            .addTo(compositeDisposable)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        loggerMessage("ConnectActivity onNewIntent")
    }


    override fun onDestroy() {
        super.onDestroy()

        heartLoopDisposable?.dispose()

        compositeDisposable.dispose()
        unregisterReceiver(hudService.receiver)

        hudService.disconnect()
        loggerMessage("ConnectActivity onDestroy")
    }
}
