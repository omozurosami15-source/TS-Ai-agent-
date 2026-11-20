package com.tsagent.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Security alerts: suspicious messages and newly installed apps. Nothing is removed; the user is told, with a sound. */
object Security {
    private const val CHANNEL = "security_alert"
    /** Groups of warning words. A message matching a group gets a reason. Add words here as needed. */
    private val groups: Map<String, List<String>> = mapOf(
        "asks for a code or PIN" to listOf("otp", "one time password", "one-time password", "pin code", "pin number", "pin দিন", "পিন দিন", "কোড দিন", "ওটিপি", "পাসওয়ার্ড দিন", "password dao", "password পাঠাও", "verification code", "ভেরিফিকেশন কোড", "security code"),
        "threatens your account" to listOf("account will be blocked", "account blocked", "account suspended", "will be suspended", "verify your account", "অ্যাকাউন্ট বন্ধ", "অ্যাকাউন্ট ব্লক", "অ্যাকাউন্ট স্থগিত", "অ্যাকাউন্ট যাচাই"),
        "asks to send money" to listOf("send money", "send me money", "transfer money", "send payment", "টাকা পাঠাও", "টাকা পাঠান", "টাকা দাও", "টাকা লাগবে", "রিচার্জ করো", "ফি দিতে হবে", "fee to receive", "pay the fee", "bkash", "nagad", "rocket", "upay", "বিকাশ", "নগদ", "রকেট", "উপায়"),
        "prize or lottery" to listOf("prize", "lottery", "winner", "you have won", "congratulations you", "free gift", "reward claim", "পুরস্কার", "লটারি", "জিতেছেন", "বিনামূল্যে উপহার"),
        "urgent pressure" to listOf("urgent", "act now", "last chance", "expires today", "limited time", "জরুরি", "আজকের মধ্যে", "এখনই করুন"),
        "job or easy money scam" to listOf("part time job", "work from home earn", "daily income guaranteed", "double your money", "invest and earn", "ঘরে বসে আয়", "দৈনিক আয় নিশ্চিত", "দ্বিগুণ টাকা")
    )

    private val linkRx = Regex("(https?://|bit\\.ly/|tinyurl\\.com/|\\.apk\\b)", RegexOption.IGNORE_CASE)
    private val riskyPerms = listOf("android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS",
        "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.BIND_DEVICE_ADMIN", "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.BIND_ACCESSIBILITY_SERVICE", "android.permission.READ_CALL_LOG")

    /** Reasons this text looks like a scam. Empty list means nothing suspicious found. */
    fun checkText(text: String): List<String> {
        val l = text.lowercase()
        val reasons = mutableListOf<String>()
        for ((name, words) in groups) {
            val hits = words.filter { l.contains(it) }
            if (hits.isNotEmpty()) reasons.add(name + " (" + hits.take(2).joinToString(", ") + ")")
        }
        if (linkRx.containsMatchIn(text)) reasons.add("contains a link or APK file")
        if (Regex("(?i)(৳|tk|bdt|taka|টাকা)\\s*[0-9]{2,}|[0-9]{2,}\\s*(৳|tk|bdt|taka|টাকা)").containsMatchIn(text)) reasons.add("mentions an amount of money")
        return reasons
    }

    fun checkApp(ctx: Context, pkg: String): List<String> {
        return try {
            val info = ctx.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            val asked = info.requestedPermissions?.toList().orEmpty()
            asked.filter { it in riskyPerms }.map { "asks for: " + it.substringAfterLast('.') }
        } catch (e: Exception) { emptyList() }
    }

    fun alert(ctx: Context, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL, "Security alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            }
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
        b.setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true)
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(body.hashCode(), b.build())
        // also keep it for the app screen, so it shows the next time the app opens
        ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("security_pending", title + "\n" + body).apply()
    }

    fun showPending(ctx: Context): String? {
        val p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val text = p.getString("security_pending", null) ?: return null
        p.edit().remove("security_pending").apply()
        return text
    }
}

/** Reads new notifications and warns about scam-like messages. The user turns this on in Android's notification access settings. */
class ScamListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val text = listOfNotNull(extras.getCharSequence("android.title"), extras.getCharSequence("android.text"))
            .joinToString(" ") { it.toString() }
        if (text.isBlank()) return
        val reasons = Security.checkText(text)
        if (reasons.isNotEmpty()) {
            Security.alert(this, "Possible scam from " + sbn.packageName, reasons.joinToString("; ") + "\n\"" + text.take(120) + "\"")
        }
    }
}

/** Looks at every newly installed app and warns when it asks for risky permissions. */
class PackageWatch : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        val reasons = Security.checkApp(ctx, pkg)
        if (reasons.isNotEmpty()) {
            Security.alert(ctx, "Check new app: " + pkg, reasons.joinToString("; ") + ". Remove it if you did not install it.")
        }
    }
}
