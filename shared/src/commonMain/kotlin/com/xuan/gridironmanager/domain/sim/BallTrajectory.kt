package com.xuan.gridironmanager.domain.sim

import com.xuan.gridironmanager.domain.model.Vector3D

class BallTrajectory(
    val startPos: Vector3D,
    val targetPos: Vector3D,
    val totalFlightTimeSec: Float,
    val apexHeightYards: Float
) {
    fun getPositionAt(t: Float): Vector3D {
        val p = (t / totalFlightTimeSec).coerceIn(0f, 1f)
        
        val x = startPos.x + (targetPos.x - startPos.x) * p
        val y = startPos.y + (targetPos.y - startPos.y) * p
        
        // Parabolic arc for Z: Z = 4 * apex * p * (1 - p)
        // Adding linear interpolation for start/end Z if they differ (usually they won't for a simple pass)
        // But the requirement specifically asks for the arc formula.
        val zArc = 4 * apexHeightYards * p * (1 - p)
        
        // Linear interpolation for Z base (e.g. from QB release height to WR catch height)
        val zBase = startPos.z + (targetPos.z - startPos.z) * p
        
        return Vector3D(x, y, zBase + zArc)
    }
}
