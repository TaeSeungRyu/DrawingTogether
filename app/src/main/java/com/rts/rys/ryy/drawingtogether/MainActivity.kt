package com.rts.rys.ryy.drawingtogether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.rts.rys.ryy.drawingtogether.ui.canvas.DrawingScreen
import com.rts.rys.ryy.drawingtogether.ui.theme.DrawingTogetherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DrawingTogetherTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    DrawingScreen(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}
