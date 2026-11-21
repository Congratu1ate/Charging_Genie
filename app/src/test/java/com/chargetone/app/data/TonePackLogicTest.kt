package com.chargetone.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TonePackLogicTest {

    private fun pack(
        id: String,
        name: String,
        at: Long,
        charge: String? = "${name}_charge.wav",
        unplug: String? = "${name}_unplug.wav",
        full: String? = "${name}_full.wav",
    ) = TonePack(
        id = id,
        name = name,
        chargeToken = charge,
        unplugToken = unplug,
        fullToken = full,
        usedAt = at,
    )

    @Test
    fun `新套装排在最前`() {
        val merged = TonePackLogic.upsert(
            current = listOf(pack("1", "旧套装", 100)),
            pack = pack("2", "新套装", 200),
        )

        assertEquals(2, merged.packs.size)
        assertEquals("新套装", merged.packs[0].name)
        assertTrue(merged.evicted.isEmpty())
    }

    @Test
    fun `同名套装覆盖并保留原 id`() {
        val old = pack("same-id", "马里奥", 100, charge = "a.wav")
        val updated = pack("new-id", "马里奥", 200, charge = "b.wav")

        val merged = TonePackLogic.upsert(listOf(old), updated)

        assertEquals(1, merged.packs.size)
        assertEquals("b.wav", merged.packs[0].chargeToken)
        assertEquals("new-id", merged.packs[0].id)
    }

    @Test
    fun `名字大小写不同也视为同一套装`() {
        val merged = TonePackLogic.upsert(
            current = listOf(pack("1", "Mario", 100)),
            pack = pack("2", "mario", 200),
        )

        assertEquals(1, merged.packs.size)
    }

    @Test
    fun `超出上限时最旧的被挤出`() {
        val current = (1..3).map { pack("id$it", "p$it", it.toLong() * 100) }

        val merged = TonePackLogic.upsert(current, pack("id4", "p4", 1000), max = 3)

        assertEquals(3, merged.packs.size)
        assertEquals(1, merged.evicted.size)
        assertEquals("p1", merged.evicted[0].name)
    }

    @Test
    fun `remove 按 id 移除`() {
        val list = listOf(pack("1", "a", 100), pack("2", "b", 200))

        val remaining = TonePackLogic.remove(list, "1")

        assertEquals(1, remaining.size)
        assertEquals("b", remaining[0].name)
    }

    @Test
    fun `touch 会把套装顶到最前`() {
        val list = listOf(pack("1", "a", 200), pack("2", "b", 100))

        val touched = TonePackLogic.touch(list, "2", at = 300)

        assertEquals("b", touched[0].name)
        assertEquals(300, touched[0].usedAt)
    }

    @Test
    fun `references 覆盖三个槽位`() {
        val item = pack("1", "x", 100, charge = "c.wav", unplug = "u.wav", full = "f.wav")

        assertTrue(TonePackLogic.references(item, "c.wav"))
        assertTrue(TonePackLogic.references(item, "u.wav"))
        assertTrue(TonePackLogic.references(item, "f.wav"))
        assertFalse(TonePackLogic.references(item, "other.wav"))
    }
}
