package com.xuan.gridironmanager.domain.model

import kotlin.math.sqrt

data class Vector3D(val x: Float, val y: Float, val z: Float) {
    fun distance2DTo(other: Vector3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }
}
