package com.xuan.gridironmanager.domain.model

data class Waypoint(val x: Float, val y: Float)

data class Route(val name: String, val waypoints: List<Waypoint>)
