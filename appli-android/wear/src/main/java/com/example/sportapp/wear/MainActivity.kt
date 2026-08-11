package com.example.sportapp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sportapp.wear.ui.WearHealthApp

/** Écran unique de l'app montre : FC + pas en direct, poussés au téléphone. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: WearHealthViewModel = viewModel()
            WearHealthApp(viewModel)
        }
    }
}
