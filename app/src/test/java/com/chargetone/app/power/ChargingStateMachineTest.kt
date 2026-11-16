package com.chargetone.app.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ChargingStateMachineTest {

    @Before
    fun setUp() {
        ChargingStateMachine.reset()
    }

    @Test
    fun `首次接入充电器会被采纳`() {
        val transition = ChargingStateMachine.onEvent(connected = true, plugged = 1, now = 1_000L)

        assertNotNull(transition)
        assertEquals(true, transition!!.connected)
        assertEquals(1, transition.plugged)
    }

    @Test
    fun `同状态重复事件会被丢弃`() {
        ChargingStateMachine.onEvent(connected = true, plugged = 1, now = 1_000L)

        val duplicate = ChargingStateMachine.onEvent(connected = true, plugged = 1, now = 5_000L)

        assertNull(duplicate)
    }

    @Test
    fun `先接入再拔出会产生两次合法事件`() {
        assertNotNull(ChargingStateMachine.onEvent(connected = true, plugged = 2, now = 1_000L))
        assertNotNull(ChargingStateMachine.onEvent(connected = false, plugged = 0, now = 10_000L))
    }

    @Test
    fun `抖动窗口内的状态翻转为被忽略`() {
        ChargingStateMachine.onEvent(connected = true, plugged = 1, now = 1_000L)

        val flap = ChargingStateMachine.onEvent(connected = false, plugged = 0, now = 1_500L)
        assertNull("1.2 秒内的翻转应被丢弃", flap)

        val afterWindow = ChargingStateMachine.onEvent(connected = false, plugged = 0, now = 1_000L + 1_201L)
        assertNotNull("超过窗口后应重新采纳", afterWindow)
    }

    @Test
    fun `无线充电的 plugged 类型会透传`() {
        val transition = ChargingStateMachine.onEvent(connected = true, plugged = 4, now = 1_000L)

        assertEquals(4, transition?.plugged)
    }
}
