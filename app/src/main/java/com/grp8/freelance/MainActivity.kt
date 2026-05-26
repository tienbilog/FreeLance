package com.grp8.freelance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grp8.freelance.ui.theme.FreeLanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreeLanceTheme() {
                val viewModel: SchedulerViewModel = viewModel()
                SchedulerApp(viewModel = viewModel)
            }
        }
    }
}