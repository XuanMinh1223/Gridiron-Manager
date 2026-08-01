package com.xuan.gridironmanager.domain.sim.match

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriveEngineTest {
    private val engine = DriveEngine()

    @Test
    fun testFirstDownReset() {
        val initialState = GameState(down = 1, distance = 10, yardLine = 25)
        val result = PlayResult(yardsGained = 12, description = "Long gain", isTouchdown = false, isTurnover = false)
        
        val nextState = engine.resolvePlay(initialState, result)
        
        assertEquals(1, nextState.down)
        assertEquals(10, nextState.distance)
        assertEquals(37, nextState.yardLine)
    }

    @Test
    fun testSecondDown() {
        val initialState = GameState(down = 1, distance = 10, yardLine = 25)
        val result = PlayResult(yardsGained = 4, description = "Short gain", isTouchdown = false, isTurnover = false)
        
        val nextState = engine.resolvePlay(initialState, result)
        
        assertEquals(2, nextState.down)
        assertEquals(6, nextState.distance)
        assertEquals(29, nextState.yardLine)
    }

    @Test
    fun testTurnoverOnDowns() {
        val initialState = GameState(down = 4, distance = 5, yardLine = 50)
        val result = PlayResult(yardsGained = 2, description = "Stopped short", isTouchdown = false, isTurnover = false)
        
        val nextState = engine.resolvePlay(initialState, result)
        
        assertEquals(1, nextState.down)
        assertEquals(10, nextState.distance)
        assertEquals(48, nextState.yardLine) // Possession flips: 100 - (50 + 2) = 48
    }

    @Test
    fun testTouchdown() {
        val initialState = GameState(homeScore = 0)
        val result = PlayResult(yardsGained = 50, description = "TD!", isTouchdown = true, isTurnover = false)
        
        val nextState = engine.resolvePlay(initialState, result)
        
        assertEquals(7, nextState.homeScore)
        assertEquals(1, nextState.down)
        assertEquals(25, nextState.yardLine) // Reset after TD
    }
}
