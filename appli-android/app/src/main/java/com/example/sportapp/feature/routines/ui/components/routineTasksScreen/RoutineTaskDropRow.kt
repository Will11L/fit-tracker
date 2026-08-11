@file:OptIn(ExperimentalFoundationApi::class)

package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.feature.routines.viewmodel.TaskRowUi

@Composable
fun RoutineTaskDropRow(
    row: TaskRowUi,
    onClickOptions: (Task) -> Unit,
    onToggleChecked: (taskUUID: String, nowChecked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = null
) {
    val task = row.task

    // Source drag : uniquement le handle
    val dragHandleModifier = Modifier.dragAndDropSource(
        transferData = { _ ->
            DragAndDropTransferData(
                clipData = ClipData.newPlainText(DRAG_LABEL, task.uuid)
            )
        }
    )

    RoutineTaskRow(
        modifier = modifier,
        task = task,
        isChecked = row.isChecked,
        isCheckSynced = row.isCheckSynced,
        backgroundColor = appColors.bgRecessed,
        nameBoxColor = appColors.bgSurface,
        onClickOptions = onClickOptions,
        onToggleChecked = { t, nowChecked -> onToggleChecked(t.uuid, nowChecked) },
        dragHandle = dragHandle ?: {
            Box(
                modifier = dragHandleModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_rounded_drag_indicator),
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}
