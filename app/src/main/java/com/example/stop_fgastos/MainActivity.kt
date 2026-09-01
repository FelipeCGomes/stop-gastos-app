package com.example.stop_fgastos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.stop_fgastos.ui.StopGastosApp
import com.example.stop_fgastos.ui.theme.Stop_fgastosTheme
import com.example.stop_fgastos.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            Stop_fgastosTheme(dynamicColor = false) {
                StopGastosApp(viewModel = viewModel)
            }
        }
    }
}