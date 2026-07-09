package com.reminds.characters.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class SmartSchedulerTest {

    // 平日火曜の朝10時を基準に
    private val morning = LocalDateTime.of(2026, 7, 7, 10, 0)

    @Test
    fun `予約メモは営業時間の締切付きでスケジュールされる`() {
        val result = SmartScheduler.schedule("歯医者を予約", now = morning)

        // 歯医者は9-17時 → 締切は今日の17時
        assertEquals(LocalTime.of(17, 0), result.deadline?.toLocalTime())
        assertEquals(morning.toLocalDate(), result.deadline?.toLocalDate())
        // 営業中なので30分後にリマインド
        assertEquals(morning.plusMinutes(30), result.remindAt)
        assertTrue(result.bubbleMessage.contains("17時までに電話"))
    }

    @Test
    fun `閉店後の予約メモは翌日の開店後になる`() {
        val night = LocalDateTime.of(2026, 7, 7, 22, 0)
        val result = SmartScheduler.schedule("美容院を予約する", now = night)

        // 美容院は10-19時 → 明日の10:15にリマインド
        assertEquals(night.toLocalDate().plusDays(1), result.remindAt.toLocalDate())
        assertEquals(LocalTime.of(10, 15), result.remindAt.toLocalTime())
        assertTrue(result.bubbleMessage.contains("明日"))
    }

    @Test
    fun `閉店間際はすぐにリマインドする`() {
        val lateAfternoon = LocalDateTime.of(2026, 7, 7, 16, 30)
        val result = SmartScheduler.schedule("病院に電話", now = lateAfternoon)

        // 17時閉店まで30分 → 5分後に即通知
        assertEquals(lateAfternoon.plusMinutes(5), result.remindAt)
        assertTrue(result.bubbleMessage.contains("もうすぐ閉まっちゃう"))
    }

    @Test
    fun `明示時刻はその10分前にリマインドされる`() {
        val result = SmartScheduler.schedule("15時に資料を送る", now = morning)
        assertEquals(LocalTime.of(14, 50), result.remindAt.toLocalTime())
        assertEquals(LocalTime.of(15, 0), result.deadline?.toLocalTime())
    }

    @Test
    fun `時刻表記のバリエーションを解釈できる`() {
        assertEquals(LocalTime.of(15, 30), SmartScheduler.parseExplicitTime("15:30に会議", morning)?.toLocalTime())
        assertEquals(LocalTime.of(15, 30), SmartScheduler.parseExplicitTime("3時半に会議", morning)?.toLocalTime())
        assertEquals(LocalTime.of(15, 0), SmartScheduler.parseExplicitTime("午後3時に会議", morning)?.toLocalTime())
        assertEquals(LocalTime.of(15, 0), SmartScheduler.parseExplicitTime("１５時に会議", morning)?.toLocalTime())
        assertNull(SmartScheduler.parseExplicitTime("牛乳を買う", morning))
    }

    @Test
    fun `過ぎた時刻は午後として読み替える`() {
        // 朝10時に「7時に薬」→ 19時と解釈
        val target = SmartScheduler.parseExplicitTime("7時に薬を飲む", morning)
        assertEquals(LocalTime.of(19, 0), target?.toLocalTime())
    }

    @Test
    fun `緊急ワードは15分後になる`() {
        val result = SmartScheduler.schedule("至急！鍵を返す", now = morning)
        assertEquals(morning.plusMinutes(15), result.remindAt)
    }

    @Test
    fun `時間帯ワードを解釈できる`() {
        val result = SmartScheduler.schedule("夕方に洗濯物を取り込む", now = morning)
        assertEquals(LocalTime.of(17, 0), result.remindAt.toLocalTime())
    }

    @Test
    fun `既定は2時間後で今日中に収まる`() {
        val result = SmartScheduler.schedule("牛乳を買う", now = morning)
        assertEquals(morning.plusHours(2), result.remindAt)
        assertNull(result.deadline)

        // 夜20時に登録 → 21時（その日の最終ライン）に丸められる
        val evening = LocalDateTime.of(2026, 7, 7, 20, 0)
        val lateResult = SmartScheduler.schedule("牛乳を買う", now = evening)
        assertEquals(LocalTime.of(21, 0), lateResult.remindAt.toLocalTime())
        assertEquals(evening.toLocalDate(), lateResult.remindAt.toLocalDate())
    }

    @Test
    fun `深夜でも今日のうちに知らせようとする`() {
        val lateNight = LocalDateTime.of(2026, 7, 7, 22, 30)
        val result = SmartScheduler.schedule("ゴミをまとめる", now = lateNight)
        assertEquals(lateNight.plusMinutes(15), result.remindAt)
    }

    @Test
    fun `長いメモはセリフ内で省略される`() {
        val result = SmartScheduler.schedule("とてもとても長いメモの内容ですこれは省略されるはず", now = morning)
        assertTrue(result.bubbleMessage.contains("…"))
        assertNotNull(result.bubbleMessage)
    }
}
