package com.xuan.gridironmanager.domain.sim.playbook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DisguiseTest {
    private val disguised = listOf(Playbook.COVER_3_FROM_2, Playbook.COVER_2_FROM_3, Playbook.COVER_2_FROM_MAN)

    @Test
    fun testPrototypeCallsRotateAtPointFourSecondsInTheSameFormation() {
        for (call in disguised) {
            val disguise = assertNotNull(call.disguise, call.name)
            assertEquals(0.4f, disguise.rotationSec, call.name)
            assertEquals(Formations.BASE_4_3, call.formation, call.name)
            assertEquals(emptySet(), call.blitzers, call.name)
        }
    }

    @Test
    fun testShellAndTrueCoverageMatchTheNamedCalls() {
        assertEquals("43_cover_3_from_2", Playbook.COVER_3_FROM_2.id)
        assertEquals("Cover 3 (from 2)", Playbook.COVER_3_FROM_2.name)
        assertEquals(Coverage.COVER_3, Playbook.COVER_3_FROM_2.coverage)
        assertEquals(Playbook.COVER_3.zones, Playbook.COVER_3_FROM_2.zones)
        assertEquals(Coverage.COVER_2, Playbook.COVER_3_FROM_2.disguise?.shellCoverage)
        assertEquals(Playbook.COVER_2.zones, Playbook.COVER_3_FROM_2.disguise?.shellZones)

        assertEquals("43_cover_2_from_3", Playbook.COVER_2_FROM_3.id)
        assertEquals("Cover 2 (from 3)", Playbook.COVER_2_FROM_3.name)
        assertEquals(Coverage.COVER_2, Playbook.COVER_2_FROM_3.coverage)
        assertEquals(Playbook.COVER_2.zones, Playbook.COVER_2_FROM_3.zones)
        assertEquals(Coverage.COVER_3, Playbook.COVER_2_FROM_3.disguise?.shellCoverage)
        assertEquals(Playbook.COVER_3.zones, Playbook.COVER_2_FROM_3.disguise?.shellZones)

        assertEquals("43_cover_2_from_man", Playbook.COVER_2_FROM_MAN.id)
        assertEquals("Cover 2 (from man)", Playbook.COVER_2_FROM_MAN.name)
        assertEquals(Coverage.COVER_2, Playbook.COVER_2_FROM_MAN.coverage)
        assertEquals(Playbook.COVER_2.zones, Playbook.COVER_2_FROM_MAN.zones)
        assertEquals(Coverage.MAN, Playbook.COVER_2_FROM_MAN.disguise?.shellCoverage)
        assertEquals(emptyMap(), Playbook.COVER_2_FROM_MAN.disguise?.shellZones)
    }

    @Test
    fun testShellCallDropsTheDisguise() {
        val shell = Playbook.COVER_3_FROM_2.shell()

        assertNull(shell.disguise)
        assertEquals(Coverage.COVER_2, shell.coverage)
        assertEquals(Playbook.COVER_2.zones, shell.zones)
        assertEquals(Playbook.COVER_2, Playbook.COVER_2.shell())
    }

    @Test
    fun testExistingCallsAreNotDisguised() {
        assertEquals(6, Playbook.defensiveCalls.size)
        for (call in Playbook.defensiveCalls + listOf(Playbook.KICK_RETURN, Playbook.PUNT_RETURN, Playbook.FIELD_GOAL_BLOCK)) {
            assertNull(call.disguise, call.name)
        }
        assertEquals(Playbook.defensiveCalls + disguised, Playbook.defenseOptions)
    }

    @Test
    fun testRejectsShellZoneForASlotNotInTheFormation() {
        assertFailsWith<IllegalArgumentException> {
            Playbook.COVER_3.copy(disguise = CoverageDisguise(Coverage.COVER_2, mapOf("NOT_A_SLOT" to ZoneSpot(0f, 10f))))
        }
    }

    @Test
    fun testRejectsShellZoneOnABlitzer() {
        assertFailsWith<IllegalArgumentException> {
            Playbook.NICKEL_BLITZ.copy(disguise = CoverageDisguise(Coverage.COVER_2, mapOf("WLB" to ZoneSpot(0f, 9f))))
        }
    }

    @Test
    fun testRejectsRotationTimeOutsideTheAllowedRange() {
        for (sec in listOf(0.1f, 0.9f)) {
            assertFailsWith<IllegalArgumentException>("$sec") {
                Playbook.COVER_3.copy(disguise = CoverageDisguise(Coverage.COVER_2, Playbook.COVER_2.zones, rotationSec = sec))
            }
        }
        Playbook.COVER_3.copy(disguise = CoverageDisguise(Coverage.COVER_2, Playbook.COVER_2.zones, rotationSec = 0.2f))
        Playbook.COVER_3.copy(disguise = CoverageDisguise(Coverage.COVER_2, Playbook.COVER_2.zones, rotationSec = 0.8f))
    }

    @Test
    fun testRejectsADisguiseIdenticalToTheTrueCall() {
        assertFailsWith<IllegalArgumentException> {
            Playbook.COVER_3.copy(disguise = CoverageDisguise(Coverage.COVER_3, Playbook.COVER_3.zones))
        }
    }
}
