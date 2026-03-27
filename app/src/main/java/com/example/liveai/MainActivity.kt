package com.example.liveai

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.liveai.catalog.CatalogActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LiveAI"
    }

    private var btnOverlayPreview: Button? = null
    private var btnToggleOverlay: Button? = null

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

        btnOverlayPreview = findViewById<Button>(R.id.btnOverlayPreview)
        btnOverlayPreview?.setOnClickListener {
            Log.d(TAG, "Overlay preview clicked")
            disableOverlayButtons()
            startActivity(Intent(this, WallpaperSetupActivity::class.java).apply {
                putExtra(WallpaperSetupActivity.EXTRA_MODE, WallpaperSetupActivity.MODE_OVERLAY)
            })
        }

        btnToggleOverlay = findViewById<Button>(R.id.btnToggleOverlay)
        btnToggleOverlay?.setOnClickListener {
            Log.d(TAG, "Start overlay clicked")
            disableOverlayButtons()
            launchOverlayService()
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

        findViewById<Button>(R.id.btnRemoveWallpaper).setOnClickListener {
            removeWallpaper()
        }

        findViewById<Button>(R.id.btnCatalog).setOnClickListener {
            startActivity(Intent(this, CatalogActivity::class.java))
        }

        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        enableOverlayButtons()
        updateRemoveButtonState()
    }

    private fun disableOverlayButtons() {
        btnOverlayPreview?.isEnabled = false
        btnToggleOverlay?.isEnabled = false
    }

    private fun enableOverlayButtons() {
        btnOverlayPreview?.isEnabled = true
        btnToggleOverlay?.isEnabled = true
    }

    private fun isOurWallpaperActive(): Boolean {
        val wm = WallpaperManager.getInstance(this)
        val info = wm.wallpaperInfo ?: return false
        val ourComponent = ComponentName(this, Live2DWallpaperService::class.java)
        return info.component == ourComponent
    }

    private fun updateRemoveButtonState() {
        val btnRemove = findViewById<Button>(R.id.btnRemoveWallpaper)
        btnRemove.isEnabled = isOurWallpaperActive()
    }

    private fun removeWallpaper() {
        if (!isOurWallpaperActive()) {
            Toast.makeText(this, "Live wallpaper is not active", Toast.LENGTH_SHORT).show()
            return
        }

        val wm = WallpaperManager.getInstance(this)
        if (!wm.isSetWallpaperAllowed) {
            Toast.makeText(this, "Wallpaper changes not allowed by device policy", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.clear(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            } else {
                wm.clear()
            }
            Toast.makeText(this, "Live wallpaper removed", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Live wallpaper cleared successfully")
            updateRemoveButtonState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove wallpaper", e)
            Toast.makeText(this, "Failed to remove wallpaper: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchOverlayService() {
        Log.d(TAG, "Launching overlay service")
        try {
            OverlayService.requestRestart(this) {
                val intent = Intent(this, OverlayService::class.java)
                startForegroundService(intent)
                Toast.makeText(this, "Overlay started!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
