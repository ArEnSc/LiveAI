package com.example.liveai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Single permissions page shown on first launch (or when permissions are missing).
 * Checks all required permissions and lets the user grant them before proceeding.
 */
class PermissionsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LiveAI-Permissions"
        private const val OVERLAY_REQUEST = 2001
    }

    private var overlayStatus: TextView? = null
    private var micStatus: TextView? = null
    private var notifStatus: TextView? = null
    private var btnContinue: Button? = null

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> refreshStatus() }

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If all permissions granted, skip straight to MainActivity
        if (allPermissionsGranted()) {
            launchMain()
            return
        }

        val dp = resources.displayMetrics.density
        val pad = (24 * dp).toInt()

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Title
        val title = TextView(this).apply {
            text = "LiveAI Permissions"
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (16 * dp).toInt())
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "This app needs the following permissions to work properly:"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, (24 * dp).toInt())
        }
        root.addView(subtitle)

        // --- Overlay Permission ---
        buildPermissionRow(
            "Draw Over Other Apps",
            "Required to show the Live2D overlay on screen",
            { Settings.canDrawOverlays(this) },
            {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_REQUEST)
            }
        ).let { (view, status) ->
            overlayStatus = status
            root.addView(view)
        }

        // --- Microphone Permission ---
        buildPermissionRow(
            "Microphone",
            "Used for audio-driven model animation",
            {
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            },
            { requestMicPermission.launch(Manifest.permission.RECORD_AUDIO) }
        ).let { (view, status) ->
            micStatus = status
            root.addView(view)
        }

        // --- Notification Permission (API 33+) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            buildPermissionRow(
                "Notifications",
                "Required for the foreground service notification",
                {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                },
                { requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            ).let { (view, status) ->
                notifStatus = status
                root.addView(view)
            }
        }

        // Spacer
        root.addView(TextView(this).apply {
            setPadding(0, (24 * dp).toInt(), 0, 0)
        })

        // Continue button
        btnContinue = Button(this).apply {
            text = "Continue"
            isEnabled = false
            setOnClickListener { launchMain() }
        }
        root.addView(btnContinue)

        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    @Deprecated("Use ActivityResultLauncher")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST) {
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val micOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        overlayStatus?.text = if (overlayOk) "Granted" else "Not granted"
        overlayStatus?.setTextColor(if (overlayOk) Color.rgb(0, 150, 0) else Color.RED)

        micStatus?.text = if (micOk) "Granted" else "Not granted"
        micStatus?.setTextColor(if (micOk) Color.rgb(0, 150, 0) else Color.RED)

        notifStatus?.text = if (notifOk) "Granted" else "Not granted"
        notifStatus?.setTextColor(if (notifOk) Color.rgb(0, 150, 0) else Color.RED)

        btnContinue?.isEnabled = overlayOk && micOk && notifOk

        Log.d(TAG, "Permissions: overlay=$overlayOk mic=$micOk notif=$notifOk")
    }

    private fun allPermissionsGranted(): Boolean {
        if (!Settings.canDrawOverlays(this)) return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return false
        return true
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Returns a pair of (row view, status TextView) for a permission row.
     */
    private fun buildPermissionRow(
        name: String,
        description: String,
        isGranted: () -> Boolean,
        onGrant: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val dp = resources.displayMetrics.density

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val nameView = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(Color.BLACK)
        }
        infoCol.addView(nameView)

        val descView = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.GRAY)
        }
        infoCol.addView(descView)

        row.addView(infoCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val statusView = TextView(this).apply {
            text = if (isGranted()) "Granted" else "Not granted"
            setTextColor(if (isGranted()) Color.rgb(0, 150, 0) else Color.RED)
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        }
        row.addView(statusView)

        val grantBtn = Button(this).apply {
            text = "Grant"
            setOnClickListener { onGrant() }
        }
        row.addView(grantBtn)

        return Pair(row, statusView)
    }
}
