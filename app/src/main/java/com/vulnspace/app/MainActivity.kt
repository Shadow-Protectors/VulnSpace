package com.vulnspace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vulnspace.app.presentation.navigation.VulnSpaceNavGraph
import com.vulnspace.app.ui.theme.AppBackground
import com.vulnspace.app.ui.theme.VulnSpaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VulnSpaceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppBackground
                ) {
                    // Root session router — resolves role from Supabase
                    // and directs to the correct navigation graph
                    VulnSpaceNavGraph()
                }
            }
        }
    }
}
