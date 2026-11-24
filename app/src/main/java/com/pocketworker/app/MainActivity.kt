package com.pocketworker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pocketworker.app.ui.navigation.AppNavHost
import com.pocketworker.app.ui.theme.PocketWorkerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PocketWorkerApplication).container
        setContent {
            PocketWorkerTheme {
                AppNavHost(container = container)
            }
        }
    }
}
