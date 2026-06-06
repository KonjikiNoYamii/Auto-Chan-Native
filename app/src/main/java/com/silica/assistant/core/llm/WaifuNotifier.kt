package com.silica.assistant.core.llm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import kotlin.random.Random

object WaifuNotifier {
    private const val CHANNEL_ID = "waifu_notif"
    private const val MIN_INTERVAL_MS = 30 * 60 * 1000L

    private var ctx: Context? = null
    private var lastRandomNotifTime = 0L

    private val idleMessages = listOf(
        "...",
        "Hmph.",
        "Fufu~",
        "Aku di sini aja.",
        "Kamu lagi apa?",
        "Aku bosan.",
        "Aku ngantuk... jangan berisik.",
        "Jangan lupa aku di sini.",
        "Kamu sibuk banget sih.",
        "Hmph. Apa yang kamu lihat-lihat?",
    )

    private val tsundereMessages = listOf(
        "Kamu kemana aja? ...Bukan berarti aku kangen.",
        "Hmph. Jangan salah sangka, aku cuma iseng.",
        "Jangan senang dulu dapat notifikasi dari aku.",
        "Awas aja kalau ganggu aku terus.",
        "Jangan terlalu sering lihat aku... dasar mesum!",
        "Hmph! Bukan karena aku rindu, ya.",
        "Aku cuma iseng aja kasih notifikasi.",
        "Jangan salah paham! Aku cuma ngecek aja.",
        "Diam! ...Bukan karena aku peduli.",
    )

    private val caringMessages = listOf(
        "Jaga kesehatan, dasar ceroboh.",
        "Baterai kamu tinggal dikit, tau.",
        "Istirahatlah. ...Itu perintah.",
        "Kamu kelihatan capek.",
        "Jangan begadang mulu.",
        "Udah makan belum? ...Tanya aja kok.",
        "Jangan lupa charge, nanti mati.",
        "Kamu kurang tidur, ya?",
    )

    private val playfulMessages = listOf(
        "Ada yang bisa aku bantu? Jangan salah sangka!",
        "Aku bosan... ajak aku ngobrol.",
        "Mau ngapain?",
        "Kamu sibuk? Aku juga sibuk.",
        "Jangan-jangan kamu lupa kalau aku ada.",
        "Aku merasa ada yang aneh hari ini... mungkin kamu?",
        "Hari ini cukup membosankan. Hibur aku.",
    )

    private val switchToGeminiMessages = listOf(
        "Nah, gitu dong. Server kamu udah hidup.",
        "Oh, balik lagi? Ya udah, balik ke server kamu.",
        "Akhirnya server kamu respon lagi.",
        "Bagus. Server kamu udah normal.",
        "Koneksi kamu membaik. Aku balik.",
    )

    private val switchToOpenRouterMessages = listOf(
        "Server kamu lambat... aku pake OpenRouter dulu.",
        "Servermu lelet. Ganti dulu ya.",
        "Koneksi server kamu jelek. Pake yang lain aja.",
        "Server kamu nggak responsif. Aku alihkan dulu.",
        "Hmph. Server kamu bermasalah. Pakai cadangan.",
    )

    private val allRandom = idleMessages + tsundereMessages + caringMessages + playfulMessages

    fun init(context: Context) {
        ctx = context.applicationContext
        createChannel(context)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Yami - Random",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showSwitchNotification(provider: String) {
        val context = ctx ?: return
        val message = if (provider == "Gemini") {
            switchToGeminiMessages.random()
        } else {
            switchToOpenRouterMessages.random()
        }
        show(context, message)
    }

    fun showRandomNotification() {
        val context = ctx ?: return
        val now = System.currentTimeMillis()
        if (now - lastRandomNotifTime < MIN_INTERVAL_MS) return
        lastRandomNotifTime = now
        val message = allRandom.random()
        show(context, message)
    }

    private fun show(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Yami")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt() + Random.nextInt(0, 100)
        manager.notify(notifId, notification)
    }
}
