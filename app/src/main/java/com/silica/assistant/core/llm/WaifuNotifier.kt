package com.silica.assistant.core.llm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.silica.assistant.MainActivity
import com.silica.assistant.core.llm.db.ChatDao
import com.silica.assistant.core.llm.model.ChatMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

object WaifuNotifier {
    private const val CHANNEL_ID = "waifu_notif"
    private const val MIN_INTERVAL_MS = 30 * 60 * 1000L

    private var ctx: Context? = null
    private var chatDao: ChatDao? = null
    private var lastRandomNotifTime = 0L

    fun init(context: Context, dao: ChatDao) {
        ctx = context.applicationContext
        chatDao = dao
        createChannel(context)
    }

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

    private val switchToLocalMessages = listOf(
        "Nah, gitu dong. Server kamu udah hidup.",
        "Oh, balik lagi? Ya udah, balik ke server kamu.",
        "Akhirnya server kamu respon lagi.",
        "Bagus. Server kamu udah normal.",
        "Koneksi kamu membaik. Aku balik.",
    )

    private val switchToGeminiMessages = listOf(
        "Server kamu lambat... aku pake Gemini dulu.",
        "Servermu lelet. Ganti dulu ya.",
        "Koneksi server kamu jelek. Pake yang lain aja.",
        "Server kamu nggak responsif. Aku alihkan dulu.",
        "Hmph. Server kamu bermasalah. Pakai cadangan.",
    )

    private val morningMessages = listOf(
        "Selamat pagi. Bangunlah, dunia tidak akan menunggumu.",
        "Matahari sudah tinggi. Jangan malas-malasan terus.",
        "Pagi... Jangan lupa sarapan, dasar ceroboh.",
        "Oi, sudah pagi. Ayo mulai harimu."
    )

    private val nightMessages = listOf(
        "Sudah larut. Kenapa kamu belum tidur?",
        "Istirahatlah, tubuhmu bukan mesin.",
        "Selamat malam. Semoga mimpimu tidak seaneh biasanya.",
        "Matikan layarmu, sudah waktunya tidur."
    )

    private val allRandom = idleMessages + tsundereMessages + caringMessages + playfulMessages

    fun cancelAllNotifications() {
        val context = ctx ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }

    fun showTimeBasedGreeting() {
        val context = ctx ?: return
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val message = when (hour) {
            in 5..10 -> morningMessages.random()
            in 21..23, in 0..3 -> nightMessages.random()
            else -> allRandom.random() // Fallback to random idle/tsundere/caring message
        }
        
        // Add message to chat database
        chatDao?.let { dao ->
            CoroutineScope(Dispatchers.IO).launch {
                dao.insertMessage(ChatMessageEntity(role = "assistant", content = message))
            }
        }
        
        show(context, message, isHighPriority = true)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Yami - Assistant",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifikasi dari Yami"
            enableLights(true)
            lightColor = android.graphics.Color.MAGENTA
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showSwitchNotification(provider: String) {
        val context = ctx ?: return
        val message = if (provider == "Gemini") {
            switchToGeminiMessages.random()
        } else {
            switchToLocalMessages.random()
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

    private fun show(context: Context, message: String, isHighPriority: Boolean = false) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_SCREEN", "CHAT")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Yami")
            .setContentText(message)
            .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = if (isHighPriority) 1001 else Random.nextInt(1002, 9999)
        manager.notify(notifId, builder.build())
    }
}
