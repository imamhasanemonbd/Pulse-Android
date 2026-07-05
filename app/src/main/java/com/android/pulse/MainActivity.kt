package com.android.pulse

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.ui.screens.MainScreen
import com.android.pulse.ui.theme.PulseTheme
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var audioPlayerManager: AudioPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val app = (application as PulseApplication)
        audioPlayerManager = app.audioPlayerManager

        setContent {
            PulseTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    android.util.Log.d("PulseMain", "Permissions: $permissions")
                }

                LaunchedEffect(Unit) {
                    val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                    
                    permissionLauncher.launch(permissions.toTypedArray())

                    // Request Display Over Other Apps
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("PulseMain", "Could not request overlay permission", e)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(audioPlayerManager)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
