package com.reminds.characters.ai

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * メモ文をAIが解析した結果。
 *
 * @param remindAt      リマインド（吹き出し通知）を出す日時
 * @param deadline      締切（営業時間の終わりなど）。無い場合は「今日中」が暗黙の締切
 * @param bubbleMessage キャラが通知で話しかけるセリフ
 */
data class ScheduleResult(
    val remindAt: LocalDateTime,
    val deadline: LocalDateTime?,
    val bubbleMessage: String,
)

/**
 * メモの内容を解析してリマインド時刻を自動決定するルールエンジン。
 *
 * 方針は「その日のうちに終わらせよう。」
 *  1. 「今すぐ」「至急」→ 15分後
 *  2. 「15時」「3時半」「15:30」など明示された時刻 → その時刻の少し前
 *  3. 「予約」「電話」など → 相手の営業時間を推定し、閉まる前に知らせる
 *  4. 「朝」「昼」「夕方」「夜」「寝る前」→ その時間帯
 *  5. それ以外 → 2時間後（ただし必ず今日中に収まるように調整）
 *
 * 外部APIに依存しない完全オンデバイス動作。将来ここをLLM呼び出しに
 * 差し替えられるよう、入出力は ScheduleResult に閉じている。
 */
object SmartScheduler {

    /** 営業時間の推定テーブル。実店舗検索APIを繋ぐまでの既定値。 */
    private data class Venue(
        val keywords: List<String>,
        val open: LocalTime,
        val close: LocalTime,
    )

    private val venues = listOf(
        Venue(listOf("病院", "歯医者", "歯科", "クリニック", "医院", "皮膚科", "眼科", "内科", "整形外科"),
            LocalTime.of(9, 0), LocalTime.of(17, 0)),
        Venue(listOf("美容院", "美容室", "床屋", "サロン", "ネイル", "マッサージ", "整体"),
            LocalTime.of(10, 0), LocalTime.of(19, 0)),
        Venue(listOf("銀行"), LocalTime.of(9, 0), LocalTime.of(15, 0)),
        Venue(listOf("役所", "市役所", "区役所", "郵便局"), LocalTime.of(9, 0), LocalTime.of(17, 0)),
        Venue(listOf("レストラン", "居酒屋", "焼肉", "寿司", "ホテル", "旅館"),
            LocalTime.of(11, 0), LocalTime.of(21, 0)),
    )

    /** 業種が分からない「電話・予約」の既定営業時間 */
    private val defaultVenue = Venue(emptyList(), LocalTime.of(10, 0), LocalTime.of(18, 0))

    private val callWords = listOf("予約", "電話", "問い合わせ", "問合せ", "申し込み", "申込", "キャンセル")
    private val urgentWords = listOf("今すぐ", "至急", "大至急", "急ぎ")

    /** 「その日のうちに」の最終ライン。これ以降には既定リマインドを置かない */
    private val endOfDay = LocalTime.of(21, 0)

    fun schedule(memo: String, now: LocalDateTime = LocalDateTime.now()): ScheduleResult {
        val title = memo.trim().let { if (it.length > 14) it.take(14) + "…" else it }

        // 1. 緊急ワード
        if (urgentWords.any { memo.contains(it) }) {
            return ScheduleResult(
                remindAt = now.plusMinutes(15),
                deadline = null,
                bubbleMessage = "「$title」急ぎだよ！今すぐやっちゃおう！🔥",
            )
        }

        // 2. 明示された時刻（15時 / 3時半 / 15:30 / 午後3時 など）
        parseExplicitTime(memo, now)?.let { target ->
            val remindAt = if (target.minusMinutes(10).isAfter(now)) target.minusMinutes(10) else now.plusMinutes(5)
            return ScheduleResult(
                remindAt = remindAt,
                deadline = target,
                bubbleMessage = "「$title」${target.hour}時${if (target.minute > 0) "${target.minute}分" else ""}だよ！準備はいい？⏰",
            )
        }

        // 3. 予約・電話系 → 営業時間から締切を逆算
        if (callWords.any { memo.contains(it) }) {
            return scheduleCall(memo, title, now)
        }

        // 4. 時間帯ワード
        parseDaypart(memo, now)?.let { target ->
            return ScheduleResult(
                remindAt = target,
                deadline = null,
                bubbleMessage = "「$title」の時間だよ！今日中に終わらせよう💪",
            )
        }

        // 5. 既定：2時間後。ただし今日中（21時まで）に収める
        val default = now.plusHours(2)
        val eod = now.with(endOfDay)
        val remindAt = when {
            default.isBefore(eod) -> default
            now.isBefore(eod) -> eod
            else -> now.plusMinutes(15) // もう夜遅い：それでも今日中に
        }
        return ScheduleResult(
            remindAt = remindAt,
            deadline = null,
            bubbleMessage = "「$title」そろそろやろう！今日中に終わらせよう💪",
        )
    }

    /** 予約・電話タスク：営業時間内、閉店の余裕があるうちに知らせる */
    private fun scheduleCall(memo: String, title: String, now: LocalDateTime): ScheduleResult {
        val venue = venues.firstOrNull { v -> v.keywords.any { memo.contains(it) } } ?: defaultVenue
        val openToday = now.with(venue.open)
        val closeToday = now.with(venue.close)

        return when {
            // まだ開店前 → 開店15分後に知らせる
            now.isBefore(openToday) -> ScheduleResult(
                remindAt = openToday.plusMinutes(15),
                deadline = closeToday,
                bubbleMessage = "「$title」あと${venue.close.hour}時までに電話だよ！📞",
            )
            // 営業中 → 30分後（ただし閉店1時間前を過ぎないように）
            now.isBefore(closeToday) -> {
                val latest = closeToday.minusHours(1)
                val candidate = now.plusMinutes(30)
                val remindAt = when {
                    candidate.isBefore(latest) -> candidate
                    now.isBefore(latest) -> latest
                    else -> now.plusMinutes(5) // 閉店間際：すぐ知らせる
                }
                val remaining = Duration.between(now, closeToday).toMinutes()
                val urgency = if (remaining <= 90) "もうすぐ閉まっちゃう！" else ""
                ScheduleResult(
                    remindAt = remindAt,
                    deadline = closeToday,
                    bubbleMessage = "「$title」あと${venue.close.hour}時までに電話だよ！$urgency📞",
                )
            }
            // 今日はもう閉店 → 明日の開店後に（今日中は物理的に無理なケース）
            else -> ScheduleResult(
                remindAt = openToday.plusDays(1).plusMinutes(15),
                deadline = closeToday.plusDays(1),
                bubbleMessage = "「$title」今日はもう営業時間外…明日${venue.open.hour}時に開いたら電話しよう！📞",
            )
        }
    }

    /**
     * 「15時」「3時半」「15:30」「午後3時」などを今日の日時として解釈する。
     * 過去の時刻で、12時間足せば未来になる場合（例: 朝に「7時に薬」→19時）は午後とみなす。
     */
    internal fun parseExplicitTime(memo: String, now: LocalDateTime): LocalDateTime? {
        val regex = Regex("(午前|午後|朝|夜|夕方)?\\s*([0-9０-９]{1,2})\\s*[:：時]\\s*(半|[0-5]?[0-9０-９]?)分?")
        val match = regex.find(memo) ?: return null

        val modifier = match.groupValues[1]
        var hour = match.groupValues[2].toHalfWidth().toIntOrNull() ?: return null
        if (hour > 23) return null
        val minute = when (val m = match.groupValues[3]) {
            "半" -> 30
            "" -> 0
            else -> m.toHalfWidth().toIntOrNull() ?: 0
        }

        when (modifier) {
            "午後", "夜", "夕方" -> if (hour < 12) hour += 12
            "午前", "朝" -> { /* そのまま */ }
            else -> {
                // 修飾なし：過去の時刻なら午後に読み替えられるか試す
                val asIs = now.with(LocalTime.of(hour, minute))
                if (asIs.isBefore(now) && hour < 12 && now.with(LocalTime.of(hour + 12, minute)).isAfter(now)) {
                    hour += 12
                }
            }
        }
        if (hour > 23) return null
        return now.with(LocalTime.of(hour, minute))
    }

    /** 時間帯ワード → 今日のその時刻。もう過ぎていたら30分後 */
    internal fun parseDaypart(memo: String, now: LocalDateTime): LocalDateTime? {
        val dayparts = listOf(
            "寝る前" to LocalTime.of(22, 0),
            "夕方" to LocalTime.of(17, 0),
            "帰ったら" to LocalTime.of(18, 30),
            "帰宅" to LocalTime.of(18, 30),
            "朝" to LocalTime.of(8, 0),
            "昼" to LocalTime.of(12, 30),
            "夜" to LocalTime.of(19, 30),
        )
        val hit = dayparts.firstOrNull { memo.contains(it.first) } ?: return null
        val target = now.with(hit.second)
        return if (target.isAfter(now)) target else now.plusMinutes(30)
    }

    private fun String.toHalfWidth(): String =
        map { c -> if (c in '０'..'９') ('0' + (c - '０')) else c }.joinToString("")
}
