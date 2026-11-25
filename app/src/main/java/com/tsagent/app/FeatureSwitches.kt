package com.tsagent.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.json.JSONObject

/**
 * Three switches controlled by the owner (later from the admin panel):
 *   companion   Companion mode (persona for paid plans)
 *   sara_watch  Sara Watch (screen and camera companion)
 *   owner_mode  Owner controls (owner login, favorite song)
 *
 * When a switch is OFF, ordinary users do not see that control and it does not run for them.
 * A phone with the owner plan always sees the controls, so the owner can still use everything.
 * Values come from docs/feature_switches.json in the GitHub repo until the admin panel exists.
 * No file or no network = all switches ON (the current behaviour).
 */
object FeatureSwitches {
    const val COMPANION = "companion"
    const val SARA_WATCH = "sara_watch"
    const val OWNER_MODE = "owner_mode"
    private val keys = listOf(COMPANION, SARA_WATCH, OWNER_MODE)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /** The last value read; ON when nothing has been read yet. */
    fun isOn(ctx: Context, key: String): Boolean = prefs(ctx).getBoolean("sw_$key", true)

    /** Whether this phone sees and may run the feature. Owner plan always does. */
    fun visible(ctx: Context, key: String): Boolean =
        Entitlements.plan(ctx) == "owner" || isOn(ctx, key)

    /** Blocking: reads the switch file from GitHub and stores the values. Call from a background thread. */
    fun refresh(ctx: Context) {
        val text = Remote.switches() ?: return
        val j = try { JSONObject(text) } catch (e: Exception) { return }
        val edit = prefs(ctx).edit()
        for (k in keys) if (j.has(k)) edit.putBoolean("sw_$k", j.optBoolean(k, true))
        edit.apply()
    }

    private fun keyOf(label: String): String? = when {
        label.startsWith("Companion mode") -> COMPANION
        label.startsWith("Sara Watch") -> SARA_WATCH
        label.startsWith("OWNER") || label.startsWith("PICK FAVORITE SONG") ||
            label.startsWith("Owner passphrase") -> OWNER_MODE
        else -> null
    }

    /** Hides the controls of switched-off features for this phone. Call after the settings page is built. */
    fun apply(ctx: Context, root: View) {
        if (root is ViewGroup) for (i in 0 until root.childCount) apply(ctx, root.getChildAt(i))
        val label = when (root) {
            is Button -> root.text.toString()
            is EditText -> root.hint?.toString().orEmpty()
            is TextView -> root.text.toString()
            else -> return
        }
        val key = keyOf(label) ?: return
        root.visibility = if (visible(ctx, key)) View.VISIBLE else View.GONE
    }
}
