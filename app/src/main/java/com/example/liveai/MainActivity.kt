package com.example.liveai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LiveAI"
        private const val OVERLAY_PERMISSION_REQUEST = 1001
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d(TAG, "RECORD_AUDIO permission granted")
            startOverlayService()
        } else {
            Toast.makeText(this, "Microphone permission denied — noise monitoring disabled", Toast.LENGTH_SHORT).show()
            startOverlayService()
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return@registerForActivityResult
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            val file = File(filesDir, "saved_wallpaper.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            Log.d(TAG, "Background saved: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
            Toast.makeText(this, "Background saved! Now set wallpaper.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save background", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "MainActivity created")

        val btnOverlayPreview = findViewById<Button>(R.id.btnOverlayPreview)
        btnOverlayPreview.setOnClickListener {
            Log.d(TAG, "Overlay preview clicked")
            startActivity(Intent(this, WallpaperSetupActivity::class.java).apply {
                putExtra(WallpaperSetupActivity.EXTRA_MODE, WallpaperSetupActivity.MODE_OVERLAY)
            })
        }

        val btnToggleOverlay = findViewById<Button>(R.id.btnToggleOverlay)
        btnToggleOverlay.setOnClickListener {
            Log.d(TAG, "Button clicked. canDrawOverlays=${Settings.canDrawOverlays(this)}")
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(this, "Requesting overlay permission...", Toast.LENGTH_SHORT).show()
                requestOverlayPermission()
            }
        }

        val btnPickBackground = findViewById<Button>(R.id.btnPickBackground)
        btnPickBackground.setOnClickListener {
            pickImage.launch("image/*")
        }

        val btnSetWallpaper = findViewById<Button>(R.id.btnSetWallpaper)
        btnSetWallpaper.setOnClickListener {
            Log.d(TAG, "Set wallpaper clicked — opening setup")
            startActivity(Intent(this, WallpaperSetupActivity::class.java))
        }
    }

    private fun requestOverlayPermission() {
        Log.d(TAG, "Requesting overlay permission")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
    }

    @Deprecated("Use ActivityResultLauncher")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult requestCode=$requestCode canDrawOverlays=${Settings.canDrawOverlays(this)}")
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayWithAudioPermission()
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startOverlayWithAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        Log.d(TAG, "Starting overlay service")
        try {
            val intent = Intent(this, OverlayService::class.java)
            startForegroundService(intent)
            Toast.makeText(this, "Overlay started!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
