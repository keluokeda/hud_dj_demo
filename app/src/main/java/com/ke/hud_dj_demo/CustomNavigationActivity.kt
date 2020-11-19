package com.ke.hud_dj_demo

import android.R
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.AmapRouteActivity
import com.amap.api.navi.model.*
import com.autonavi.tbt.TrafficFacilityInfo
import com.ke.hud_dj.HudService
import com.ke.hud_dj.entity.CameraInfo
import com.ke.hud_dj.entity.NavigationInfo
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo

class CustomNavigationActivity : AmapRouteActivity(), AMapNaviListener {


    private val compositeDisposable = CompositeDisposable()

    private val hudService = HudService.hudService


    override fun onNaviInfoUpdate(info: NaviInfo) {


        hudService.sendNavigationInformationWithDirection(
            NavigationInfo(
                info.iconType,
                info.curStepRetainDistance,
                info.currentRoadName,
                info.nextRoadName,
                info.pathRetainTime,
                info.pathRetainDistance,
                info.currentSpeed
            )
        ).observeOn(AndroidSchedulers.mainThread())
            .subscribe {
//                loggerMessage("导航信息发送结果 $it")
            }
            .addTo(compositeDisposable)

    }

    override fun onCalculateRouteSuccess(p0: IntArray?) {
    }

    override fun onCalculateRouteSuccess(p0: AMapCalcRouteResult?) {
    }

    override fun onCalculateRouteFailure(p0: Int) {
    }

    override fun onCalculateRouteFailure(p0: AMapCalcRouteResult?) {
    }

    override fun onServiceAreaUpdate(p0: Array<out AMapServiceAreaInfo>?) {
    }

    override fun onEndEmulatorNavi() {
    }

    override fun onArrivedWayPoint(p0: Int) {
    }

    override fun onArriveDestination() {
    }

    override fun onPlayRing(p0: Int) {
    }

    override fun onTrafficStatusUpdate() {
    }

    override fun onGpsOpenStatus(p0: Boolean) {
    }

    override fun updateAimlessModeCongestionInfo(p0: AimLessModeCongestionInfo?) {
    }

    override fun showCross(p0: AMapNaviCross) {

        hudService.sendImage(p0.bitmap)
            .subscribe {
                loggerMessage("发送图片结果 $it")
            }.addTo(compositeDisposable)
    }

    override fun onGetNavigationText(p0: Int, p1: String?) {
    }

    override fun onGetNavigationText(p0: String?) {
    }

    override fun updateAimlessModeStatistics(p0: AimLessModeStat?) {
    }

    override fun hideCross() {
        hudService.clearImage()
            .subscribe {
                loggerMessage("清除图片结果 $it")
            }.addTo(compositeDisposable)
    }

    override fun onInitNaviFailure() {
    }

    override fun onInitNaviSuccess() {
    }

    override fun onReCalculateRouteForTrafficJam() {
    }

    override fun updateIntervalCameraInfo(
        p0: AMapNaviCameraInfo?,
        p1: AMapNaviCameraInfo?,
        p2: Int
    ) {
    }

    override fun hideLaneInfo() {
    }

    override fun onNaviInfoUpdated(p0: AMapNaviInfo?) {
    }

    override fun showModeCross(p0: AMapModelCross?) {
    }

    override fun updateCameraInfo(array: Array<out AMapNaviCameraInfo>) {

        hudService.sendCameraInfoList(array.map {
            CameraInfo(
                type = it.cameraType,
                speed = it.cameraSpeed,
                distance = it.cameraDistance,
                x = it.x,
                y = it.y
            )
        })
    }

    override fun hideModeCross() {
    }

    override fun onLocationChange(p0: AMapNaviLocation?) {
    }

    override fun onReCalculateRouteForYaw() {
    }

    override fun onStartNavi(p0: Int) {
    }

    override fun notifyParallelRoad(p0: Int) {
    }

    override fun OnUpdateTrafficFacility(p0: AMapNaviTrafficFacilityInfo?) {
    }

    override fun OnUpdateTrafficFacility(p0: Array<out AMapNaviTrafficFacilityInfo>?) {
    }

    override fun OnUpdateTrafficFacility(p0: TrafficFacilityInfo?) {
    }

    override fun onNaviRouteNotify(p0: AMapNaviRouteNotifyData?) {
    }

    override fun onGpsSignalWeak(p0: Boolean) {

    }

    override fun showLaneInfo(p0: Array<out AMapLaneInfo>?, p1: ByteArray?, p2: ByteArray?) {
    }

    override fun showLaneInfo(p0: AMapLaneInfo?) {
    }

    private lateinit var aMapNavi: AMapNavi

    private lateinit var button: Button

    private val listener: ViewTreeObserver.OnGlobalLayoutListener =
        ViewTreeObserver.OnGlobalLayoutListener {

            if (button.parent != null) {
                (button.parent as? ViewGroup)?.removeView(button)
                val frameLayout = findViewById<View>(R.id.content) as FrameLayout
                frameLayout.addView(
                    button, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL
                    )
                )
            }
        }


    override fun onCreate(p0: Bundle?) {
        super.onCreate(p0)

        loggerMessage("CustomNavigationActivity onCreate")

        aMapNavi = AMapNavi.getInstance(application)

        aMapNavi.setEmulatorNaviSpeed(180)
        aMapNavi.setUseInnerVoice(true)
        aMapNavi.addAMapNaviListener(this)


        val frameLayout = findViewById<View>(R.id.content) as FrameLayout

        button = Button(this)
        button.setOnClickListener {
            val intent = Intent(this, ConnectActivity::class.java)
            startActivity(intent)
        }
        button.text = "返回首页"
        frameLayout.addView(
            button, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL
            )
        )

        frameLayout.viewTreeObserver.addOnGlobalLayoutListener(listener)

    }

    override fun onResume() {
        super.onResume()
        loggerMessage("CustomNavigationActivity onResume")

    }

    override fun onPause() {
        super.onPause()
        loggerMessage("CustomNavigationActivity onPause")

    }


    override fun onDestroy() {
        super.onDestroy()
        loggerMessage("CustomNavigationActivity onDestroy")

        aMapNavi.stopNavi()
        aMapNavi.destroy()
        val frameLayout = findViewById<View>(R.id.content) as FrameLayout

        frameLayout.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }


    private fun loggerMessage(message: String) {
        message.log()
    }
}