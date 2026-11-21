package com.chargetone.app.data

/**
 * 一条「使用过的音效」记录。
 *
 * @param token 私有目录中的文件名
 * @param displayName 用户可读的名字（用于去重展示）
 * @param usedAt 最近一次使用的时间戳
 * @param slot 最近一次用于哪个槽位：charge / unplug / full
 */
data class ToneHistoryEntry(
    val token: String,
    val displayName: String,
    val usedAt: Long,
    val slot: String,
)

/** 合并结果：新列表 + 因超出上限被挤出的条目（调用方负责清理对应文件） */
data class ToneHistoryMerge(
    val entries: List<ToneHistoryEntry>,
    val evicted: List<ToneHistoryEntry>,
)

/**
 * 纯逻辑部分，便于单元测试。
 *
 * 规则：
 * - 同一个名字（displayName）视为同一音效，重复使用时把旧记录替换掉并置顶
 * - 按 usedAt 倒序，最多保留 [MAX_ENTRIES] 条
 * - 超出上限的条目通过 [ToneHistoryMerge.evicted] 返回，由调用方删除文件
 */
object ToneHistoryLogic {

    const val MAX_ENTRIES = 20

    fun merge(
        current: List<ToneHistoryEntry>,
        entry: ToneHistoryEntry,
        max: Int = MAX_ENTRIES,
    ): ToneHistoryMerge {
        val deduped = current.filterNot { it.displayName.equals(entry.displayName, ignoreCase = true) }
        val merged = (listOf(entry) + deduped).sortedByDescending { it.usedAt }
        val kept = merged.take(max)
        val evicted = merged.drop(max)
        return ToneHistoryMerge(entries = kept, evicted = evicted)
    }

    fun remove(current: List<ToneHistoryEntry>, token: String): List<ToneHistoryEntry> =
        current.filterNot { it.token == token }
}
