package com.chargetone.app.power

import android.os.BatteryManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FullChargeDetectorTest {

    private val ac = BatteryManager.BATTERY_PLUGGED_AC
    private val charging = BatteryManager.BATTERY_STATUS_CHARGING
    private val full = BatteryManager.BATTERY_STATUS_FULL

    @Before
    fun setUp() {
        FullChargeDetector.reset()
    }

    @Test
    fun `充电中未满不通知`() {
        assertFalse(FullChargeDetector.shouldNotify(99, charging, ac))
    }

    @Test
    fun `电量达到满值时通知一次`() {
        assertTrue(FullChargeDetector.shouldNotify(100, charging, ac))
        assertFalse("同一充电周期内不应重复通知", FullChargeDetector.shouldNotify(100, charging, ac))
    }

    @Test
    fun `系统状态为FULL时即使未显示满值也通知`() {
        assertTrue(FullChargeDetector.shouldNotify(99, full, ac))
    }

    @Test
    fun `未接电源时满电不通知`() {
        assertFalse(FullChargeDetector.shouldNotify(100, full, 0))
    }

    @Test
    fun `拔出电源后重新充到满电会再次通知`() {
        assertTrue(FullChargeDetector.shouldNotify(100, charging, ac))

        // 拔出：重置本次充电周期
        assertFalse(FullChargeDetector.shouldNotify(95, charging, 0))

        assertTrue("新的一次充电周期应再次通知", FullChargeDetector.shouldNotify(100, charging, ac))
    }

    @Test
    fun `服务启动时已满电不会立刻响`() {
        FullChargeDetector.prime(levelPercent = 100, status = full, plugged = ac)

        assertFalse(FullChargeDetector.shouldNotify(100, full, ac))
    }

    @Test
    fun `服务启动时未满电，后续充满仍会通知`() {
        FullChargeDetector.prime(levelPercent = 80, status = charging, plugged = ac)

        assertTrue(FullChargeDetector.shouldNotify(100, charging, ac))
    }
}
