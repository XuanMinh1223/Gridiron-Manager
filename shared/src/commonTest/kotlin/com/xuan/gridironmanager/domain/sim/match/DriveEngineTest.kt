package com.xuan.gridironmanager.domain.sim.match

import kotlin.test.Test
import kotlin.test.assertEquals

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
        val initialState = GameState(homeScore = 0, isHomePossession = true)
        val result = PlayResult(yardsGained = 50, description = "TD!", isTouchdown = true, isTurnover = false)
        
        val nextState = engine.resolvePlay(initialState, result)
        
        assertEquals(7, nextState.homeScore)
        assertEquals(1, nextState.down)
        assertEquals(25, nextState.yardLine) // Reset after TD
        assertEquals(false, nextState.isHomePossession) // Possession flips
    }

    @Test
    fun testQuarterTransition() {
        val initialState = GameState(clockSeconds = 0, quarter = 1)
        val result = PlayResult(yardsGained = 5, description = "Play", isTouchdown = false, isTurnover = false)
        
        val nextState = engine.resolvePlay(initialState, result)
        
        assertEquals(2, nextState.quarter)
        assertEquals(900, nextState.clockSeconds)
    }

    @Test
    fun testKickoffTouchback() {
        val initialState = GameState(isHomePossession = true)
        val result = KickResult(endYardLine = 0, description = "Touchback", isTouchback = true, isOutOfBounds = false)
        
        val nextState = engine.resolveKickoff(initialState, result)
        
        assertEquals(25, nextState.yardLine)
        assertEquals(false, nextState.isHomePossession)
        assertEquals(1, nextState.down)
    }

    @Test
    fun testPuntTouchback() {
        val initialState = GameState(isHomePossession = true)
        val result = KickResult(endYardLine = 0, description = "Touchback", isTouchback = true, isOutOfBounds = false)
        
        val nextState = engine.resolvePunt(initialState, result)
        
        assertEquals(20, nextState.yardLine)
        assertEquals(false, nextState.isHomePossession)
        assertEquals(1, nextState.down)
    }
}
