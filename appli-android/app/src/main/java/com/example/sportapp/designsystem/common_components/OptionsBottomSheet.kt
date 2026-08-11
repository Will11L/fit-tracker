package com.example.sportapp.designsystem.common_components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.appColors

data class SheetAction(
    val label: String,
    @DrawableRes val iconRes: Int,
    val color: Color,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsBottomSheet(
    title: String,
    actions: List<SheetAction>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = appColors.bgScreen,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = containerColor,
    ) {
        // ✅ Fix nav/status bar sur la Window du Dialog
        ForceSheetSystemBars(
            lightStatusBars = false,
            lightNavBars = false
        )

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            TitledDivider(title = title)
            Spacer(modifier = Modifier.height(10.dp))

            actions
                .filter { it.label.isNotBlank() }
                .forEachIndexed { index, action ->
                    OptionRow(
                        label = action.label,
                        iconRes = action.iconRes,
                        onClick = action.onClick,
                        hasBackground = true,
                        customColor = action.color,
                        // si ton OptionRow supporte enabled, sinon tu peux wrapper
                        // enabled = action.enabled
                    )

                    if (index != actions.lastIndex) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
        }
    }
}
