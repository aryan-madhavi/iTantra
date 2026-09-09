package com.astramesh.routing

import com.astramesh.core.AstraNetworkConfig

/**
 * Composite route metric calculator.
 * Computes cost based on Hop Count, Link Quality Index (LQI), Congestion, and Battery Factor.
 */
object RouteMetricCalculator {

    fun calculateLinkCost(
        hopCount: Int,
        linkQuality: Float, // 0.0 (poor) to 1.0 (perfect)
        congestionFactor: Float, // 0.0 (empty queue) to 1.0 (full queue)
        batteryLevel: Float // 0.0 (critical) to 1.0 (full)
    ): Float {
        val clampedLqi = linkQuality.coerceIn(0f, 1f)
        val clampedCongestion = congestionFactor.coerceIn(0f, 1f)
        val clampedBattery = batteryLevel.coerceIn(0f, 1f)

        val hopPart = AstraNetworkConfig.WEIGHT_HOP * (hopCount.coerceAtLeast(1).toFloat())
        val lqiPart = AstraNetworkConfig.WEIGHT_LQI * (1.0f - clampedLqi)
        val congestionPart = AstraNetworkConfig.WEIGHT_CONGESTION * clampedCongestion
        val batteryPart = AstraNetworkConfig.WEIGHT_BATTERY * (1.0f - clampedBattery)

        return hopPart + lqiPart + congestionPart + batteryPart
    }
}
