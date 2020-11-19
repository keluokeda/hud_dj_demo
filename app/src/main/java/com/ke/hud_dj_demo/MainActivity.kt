package com.ke.hud_dj_demo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.amap.api.navi.AmapNaviPage
import com.amap.api.navi.AmapNaviParams
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.orhanobut.logger.Logger
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {


    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    private val adapter =
        object : BaseQuickAdapter<BluetoothDevice, BaseViewHolder>(R.layout.item_bluetooth) {
            override fun convert(helper: BaseViewHolder, item: BluetoothDevice) {
                helper.setText(R.id.name, item.name)
                    .setText(R.id.address, item.address)
            }

        }


    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)


                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0)


                    val deviceName = device.name ?: ""

                    Logger.d("发现设备 name = $deviceName rssi = $rssi")

                    if (!deviceName.startsWith("Hud_")) {
                        return
                    }

                    if (adapter.data.map { it.address }.contains(device.address)) {
                        return
                    }
                    adapter.addData(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {

                    adapter.setNewData(null)
                    search.visibility = View.INVISIBLE
                    progress_bar.visibility = View.VISIBLE

                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    search.visibility = View.VISIBLE
                    progress_bar.visibility = View.INVISIBLE
                }
            }
        }

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intentFilter = IntentFilter()
            .apply {
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_FOUND)
            }

        registerReceiver(bluetoothReceiver, intentFilter)

        recycler_view.adapter = adapter

        adapter.setOnItemClickListener { _, _, position ->

            bluetoothAdapter.cancelDiscovery()

            val device = adapter.getItem(position) ?: return@setOnItemClickListener

            val newIntent = Intent(this, ConnectActivity::class.java)
            newIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, device)
            startActivity(newIntent)
            finish()

        }


        search.setOnClickListener {
            bluetoothAdapter.startDiscovery()
        }


    }

    override fun onPause() {
        super.onPause()

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(bluetoothReceiver)
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 101, 0, "导航")
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (101 == item?.itemId) {
            toNavigationView()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun toNavigationView() {
        AmapNaviPage.getInstance()
            .showRouteActivity(
                applicationContext,
                AmapNaviParams(null).apply {
                    isNeedDestroyDriveManagerInstanceWhenNaviExit = false
                },
                null
            )
    }
}
