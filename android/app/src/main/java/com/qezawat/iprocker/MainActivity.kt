package com.qezawat.iprocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qezawat.iprocker.ui.ScanScreen
import com.qezawat.iprocker.ui.theme.IPRockerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IPRockerTheme {
                ScanScreen()
            }
        }
    }
}
