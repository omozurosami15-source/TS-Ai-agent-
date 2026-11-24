package com.tsagent.app

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Private owner mode, all on the phone. No account, no password in the code.
 * The first time you enter a passphrase it is set (stored only as a salted hash). After that it must match.
 * Until you unlock, the personal memory, companion persona and personal reminders stay off,
 * so someone borrowing your phone cannot see them. Unlock lasts 7 days unless you log out.
 */
object OwnerAuth {
    private const val ITER = 120_000
    private const val WEEK = 7L * 24 * 3600 * 1000
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    private fun hash(pass: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass.toCharArray(), salt, ITER, 256)).encoded

    private fun enc(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun dec(s: String) = Base64.decode(s, Base64.NO_WRAP)

    fun isSet(ctx: Context) = prefs(ctx).contains("owner_hash")

<<<<<<< HEAD
    /** Locked by default. Unlocked only when a passphrase is set AND the owner logged in within the last 7 days. */
    fun unlocked(ctx: Context): Boolean = isSet(ctx) && System.currentTimeMillis() < prefs(ctx).getLong("owner_until", 0L)
=======
<<<<<<< HEAD
    /** Locked by default. Unlocked only when a passphrase is set AND the owner logged in within the last 7 days. */
    fun unlocked(ctx: Context): Boolean = isSet(ctx) && System.currentTimeMillis() < prefs(ctx).getLong("owner_until", 0L)
=======
    /** True when no passphrase was ever set (old behaviour), or the owner is currently unlocked. */
    fun unlocked(ctx: Context): Boolean = !isSet(ctx) || System.currentTimeMillis() < prefs(ctx).getLong("owner_until", 0L)
>>>>>>> 70ef1c274ce2bdca019764a62eb3127290033e2b
>>>>>>> d31f216a46405461342b68f7278a48ac4b80d297

    fun logout(ctx: Context) { prefs(ctx).edit().putLong("owner_until", 0L).apply() }

    /** First call sets the passphrase, later calls verify it. Returns a message to show. */
    fun login(ctx: Context, pass: String): String {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        if (!isSet(ctx)) {
            if (pass.length < 8) return "Choose a passphrase of at least 8 characters"
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            p.edit().putString("owner_salt", enc(salt)).putString("owner_hash", enc(hash(pass, salt)))
                .putLong("owner_until", now + WEEK).putInt("owner_fail", 0).apply()
            return "Owner passphrase set. Owner mode is unlocked."
        }
        val wait = p.getLong("owner_lock", 0L) - now
        if (wait > 0) return "Too many wrong tries. Wait ${wait / 1000} seconds."
        val ok = MessageDigest.isEqual(hash(pass, dec(p.getString("owner_salt", "") ?: "")), dec(p.getString("owner_hash", "") ?: ""))
        if (ok) {
            p.edit().putLong("owner_until", now + WEEK).putInt("owner_fail", 0).apply()
            return "Owner mode unlocked"
        }
        val fails = p.getInt("owner_fail", 0) + 1
        p.edit().putInt("owner_fail", if (fails >= 5) 0 else fails).putLong("owner_lock", if (fails >= 5) now + 60_000 else 0L).apply()
        return "Wrong passphrase"
    }
}
