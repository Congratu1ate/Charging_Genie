package com.chargetone.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneHistoryLogicTest {

    private fun entry(
        name: String,
        at: Long,
        slot: String = "charge",
        token: String = "$name.mp3",
    ) = ToneHistoryEntry(token = token, displayName = name, usedAt = at, slot = slot)

    @Test
    fun `新记录排在最前`() {
        val merged = ToneHistoryLogic.merge(
            current = listOf(entry("old", 100)),
            entry = entry("new", 200),
        )

        assertEquals(2, merged.entries.size)
        assertEquals("new", merged.entries[0].displayName)
        assertEquals("old", merged.entries[1].displayName)
        assertTrue(merged.evicted.isEmpty())
    }

    @Test
    fun `同名音效只保留最新一条`() {
        val first = entry("chime", 100, token = "chime_a.mp3")
        val later = entry("chime", 200, slot = "unplug", token = "chime_b.mp3")

        val merged = ToneHistoryLogic.merge(listOf(first), later)

        assertEquals(1, merged.entries.size)
        assertEquals("chime_b.mp3", merged.entries[0].token)
        assertEquals("unplug", merged.entries[0].slot)
    }

    @Test
    fun `文件名大小写不同也视为同一音效`() {
        val merged = ToneHistoryLogic.merge(
            current = listOf(entry("Chime", 100)),
            entry = entry("chime", 200),
        )

        assertEquals(1, merged.entries.size)
        assertEquals("chime", merged.entries[0].displayName)
    }

    @Test
    fun `超出上限时最旧的被挤出`() {
        val current = (1..3).map { entry("tone$it", it.toLong() * 100) }

        val merged = ToneHistoryLogic.merge(current, entry("newest", 1000), max = 3)

        assertEquals(3, merged.entries.size)
        assertEquals("newest", merged.entries[0].displayName)
        assertEquals(1, merged.evicted.size)
        assertEquals("tone1", merged.evicted[0].displayName)
    }

    @Test
    fun `remove 只移除匹配的 token`() {
        val list = listOf(entry("a", 100), entry("b", 200))

        val remaining = ToneHistoryLogic.remove(list, "a.mp3")

        assertEquals(1, remaining.size)
        assertEquals("b", remaining[0].displayName)
    }
}
