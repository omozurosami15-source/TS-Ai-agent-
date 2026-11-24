package com.tsagent.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings

/** Things the agent can do on the phone: check permissions, flashlight, open settings pages. */
object Device {
    /** True when the permission is already allowed, so no popup is needed. */
    fun granted(ctx: Context, which: String): Boolean = when (which.lowercase()) {
        "accessibility" -> AgentService.instance != null
        "overlay" -> Settings.canDrawOverlays(ctx)
        "microphone" -> ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        "media" -> ctx.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        "notifications" -> android.os.Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else -> false
    }

    /** Turns the flashlight on or off. */
    fun torch(ctx: Context, on: Boolean): String {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "this phone has no flashlight"
            cm.setTorchMode(id, on)
            if (on) "flashlight is on" else "flashlight is off"
        } catch (e: Exception) {
            "could not change the flashlight: " + e.message
        }
    }

    /** Opens a settings page. Android does not let an app switch Wi-Fi or Bluetooth itself, so the page is opened. */
    fun openSettings(ctx: Context, which: String): String {
        val action = when (which.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "opened " + which + " settings"
        } catch (e: Exception) {
            "could not open " + which + " settings"
        }
    }

    /** Opens the quick settings panel through the accessibility service. */
    fun quickSettings(): String {
        val svc = AgentService.instance ?: return "accessibility is off, cannot open quick settings"
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        return "opened quick settings"
    }
}
