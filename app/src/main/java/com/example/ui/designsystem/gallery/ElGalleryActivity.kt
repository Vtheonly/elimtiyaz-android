package com.example.ui.designsystem.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ui.designsystem.theme.ElImtiyazTheme
import com.example.ui.designsystem.theme.ElTheme

/**
 * Standalone activity that launches the design system gallery.
 *
 * Useful for previewing every component without wiring it into the main app.
 * Register in your AndroidManifest:
 *
 *   <activity android:name="com.example.ui.designsystem.gallery.ElGalleryActivity"
 *             android:exported="false" />
 *
 * Then trigger it from a debug menu or adb:
 *   adb shell am start -n com.aistudio.elimtiyazstaff.bxmzlx/.ElGalleryActivity
 *
 * (Replace the applicationId prefix with your actual one.)
 */
class ElGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElImtiyazTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ElTheme.colors.background)
                            .padding(inner),
                    ) {
                        ElGalleryScreen()
                    }
                }
            }
        }
    }
}
