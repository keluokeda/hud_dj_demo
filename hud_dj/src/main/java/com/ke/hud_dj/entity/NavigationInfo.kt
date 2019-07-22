package com.ke.hud_dj.entity

data class NavigationInfo(
    /**
     * 图标类型
     */
    val iconType: Int,
    /**
     * 导航距离
     */
    val currentStepRetainDistance: Int,
    /**
     * 当前道路名称
     */
    val currentRoadName: String,
    /**
     * 下一个道路名称
     */
    val nextRoadName: String,
    /**
     * 本次导航剩余时间
     */
    val pathRetainTime: Int,
    /**
     * 剩余导航距离
     */
    val pathRetainDistance: Int,
    /**
     * 当前车速
     */
    val currentSpeed: Int
)