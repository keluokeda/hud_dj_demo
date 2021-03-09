package com.ke.hud_dj_demo

import android.graphics.BitmapFactory
import android.os.Bundle
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.AmapRouteActivity
import com.amap.api.navi.model.*
import com.ke.hud_dj.HudService
import com.ke.hud_dj.entity.CameraInfo
import com.ke.hud_dj.entity.NavigationInfo
import com.ke.hud_dj.entity.NavigationTrafficStatus
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class CustomNavigationActivity : AmapRouteActivity(), AMapNaviListener {


    private val compositeDisposable = CompositeDisposable()

    private val hudService = HudService.hudService


    private val updateTrafficStatusSubject = PublishSubject.create<Float>()

    private var totalDistance: Int? = null

    private var currentProgress = 0f
    override fun onNaviInfoUpdate(info: NaviInfo) {
        if (totalDistance == null) {
            totalDistance = info.pathRetainDistance
        } else {
            currentProgress =
                (totalDistance!! - info.pathRetainDistance) / (totalDistance!! * 1f)
//            loggerMessage("更新进度 $currentProgress 剩余距离 ${info.pathRetainDistance} 总距离 ${totalDistance!!}")
            updateTrafficStatusSubject.onNext(currentProgress)
        }


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
        )

    }

//    override fun onNaviInfoUpdated(p0: AMapNaviInfo?) {
//    }

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

    var trafficStatusList = emptyList<NavigationTrafficStatus>()

    override fun onTrafficStatusUpdate() {
        val list = aMapNavi.naviPath.trafficStatuses

        trafficStatusList = list.map {
            NavigationTrafficStatus(
                length = it.length,
                status = it.status
            )
        }

        "开始更新光柱图 ".log()


    }

    override fun onGpsOpenStatus(p0: Boolean) {
    }

    override fun updateAimlessModeCongestionInfo(p0: AimLessModeCongestionInfo?) {
    }

    override fun showCross(p0: AMapNaviCross) {

        hudService.sendImage(p0.bitmap)

    }

    override fun onGetNavigationText(p0: Int, p1: String?) {
    }

    override fun onGetNavigationText(p0: String?) {
    }

    override fun updateAimlessModeStatistics(p0: AimLessModeStat?) {
    }

    override fun hideCross() {
        hudService.clearImage()

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

    override fun OnUpdateTrafficFacility(p0: Array<out AMapNaviTrafficFacilityInfo>?) {

    }

    override fun OnUpdateTrafficFacility(p0: AMapNaviTrafficFacilityInfo?) {
    }

//    override fun OnUpdateTrafficFacility(p0: Array<out AMapNaviTrafficFacilityInfo>?) {
//
//    }
//
//    override fun OnUpdateTrafficFacility(p0: AMapNaviTrafficFacilityInfo?) {
//
//    }
//
//    override fun OnUpdateTrafficFacility(p0: TrafficFacilityInfo?) {
//
//    }


    override fun onNaviRouteNotify(p0: AMapNaviRouteNotifyData?) {
    }

    override fun onGpsSignalWeak(p0: Boolean) {

    }

    override fun showLaneInfo(p0: Array<out AMapLaneInfo>?, p1: ByteArray?, p2: ByteArray?) {
    }

    override fun showLaneInfo(p0: AMapLaneInfo?) {
    }

    private lateinit var aMapNavi: AMapNavi


    override fun onCreate(p0: Bundle?) {
        super.onCreate(p0)

        loggerMessage("CustomNavigationActivity onCreate")

        aMapNavi = AMapNavi.getInstance(application)

        aMapNavi.setEmulatorNaviSpeed(180)
        aMapNavi.setUseInnerVoice(true)
        aMapNavi.addAMapNaviListener(this)

        updateTrafficStatusSubject.throttleFirst(1, TimeUnit.SECONDS)
            .subscribe {
                "更新光柱图 $currentProgress".log()
                hudService.sendTrafficStatus(
                    BitmapFactory.decodeResource(
                        resources,
                        com.ke.hud_dj_demo.R.drawable.progress_pointer
                    ),
                    trafficStatusList,
                    currentProgress
                )
            }.addTo(compositeDisposable)

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
        compositeDisposable.dispose()

    }


    private fun loggerMessage(message: String) {
        message.log()
    }
}