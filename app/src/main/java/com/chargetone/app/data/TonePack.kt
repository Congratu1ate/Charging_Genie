package com.chargetone.app.data

/**
 * 一个音效套装：把三个槽位（充电 / 断开 / 充满）打包成一个命名组合，
 * 例如「超级马里奥」。
 *
 * 音效以文件名（token）引用，可能为 null（套装只保存了一部分槽位）。
 */
data class TonePack(
    val id: String,
    val name: String,
    val chargeToken: String?,
    val unplugToken: String?,
    val fullToken: String?,
    val usedAt: Long,
)

data class TonePackMerge(
    val packs: List<TonePack>,
    val evicted: List<TonePack>,
)

/**
 * 套装列表的纯逻辑，便于单元测试。
 *
 * - 同名套装视为同一个，保存时覆盖旧内容（保留最新 id 与时间）
 * - 按 usedAt 倒序，最多 [MAX_PACKS] 个
 */
object TonePackLogic {

    const val MAX_PACKS = 10

    fun upsert(
        current: List<TonePack>,
        pack: TonePack,
        max: Int = MAX_PACKS,
    ): TonePackMerge {
        val deduped = current.filterNot { it.name.equals(pack.name, ignoreCase = true) }
        val merged = (listOf(pack) + deduped).sortedByDescending { it.usedAt }
        return TonePackMerge(
            packs = merged.take(max),
            evicted = merged.drop(max),
        )
    }

    fun remove(current: List<TonePack>, id: String): List<TonePack> =
        current.filterNot { it.id == id }

    fun touch(current: List<TonePack>, id: String, at: Long): List<TonePack> =
        current.map { if (it.id == id) it.copy(usedAt = at) else it }
            .sortedByDescending { it.usedAt }

    /** 套装是否引用了某个音效文件 */
    fun references(pack: TonePack, token: String): Boolean =
        pack.chargeToken == token || pack.unplugToken == token || pack.fullToken == token
}
