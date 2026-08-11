package com.example.sportapp.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R

@Composable
fun ExportDatasScreen(
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("📤 " + stringResource(R.string.export_datas_title))
    }
}
