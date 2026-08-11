package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.*

@Composable
fun DualTabMenu(
    topTabs: List<String>,
    subTabsMap: Map<String, List<String>>,
    selectedTopIndex: Int,
    selectedSubIndex: Int?,
    onTopTabSelected: (Int) -> Unit,
    onSubTabSelected: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().background(appColors.bgBottomNav)) {

        TabRowCustom(
            items = topTabs,
            selectedIndex = selectedTopIndex,
            onTabSelected = onTopTabSelected,
            height = 42.dp,
            isSubRow = false
        )

        val currentTopTab = topTabs[selectedTopIndex]
        val subTabs = subTabsMap[currentTopTab]

        if (!subTabs.isNullOrEmpty()) {
            HorizontalDivider(
                color = appColors.dividerStrong,
                thickness = 1.5.dp
            )

            TabRowCustom(
                items = subTabs,
                selectedIndex = selectedSubIndex ?: 0,
                onTabSelected = onSubTabSelected,
                height = 40.dp,
                isSubRow = true
            )
        }
    }
}
