@file:OptIn(ExperimentalFoundationApi::class)

package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.RoutinePeriod
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun RoutineEmptyPeriodDropItem(
    period: RoutinePeriod,
    isHovering: Boolean,
    onHover: () -> Unit,
    onExit: () -> Unit,
    onDropTaskUUID: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isHovering) Modifier.border(1.5.dp, appColors.primaryAction) else Modifier)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                },
                target = remember(period.uuid) {
                    object : DragAndDropTarget {
                        override fun onEntered(event: DragAndDropEvent) = onHover()
                        override fun onExited(event: DragAndDropEvent) = onExit()
                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            val uuid = extractDraggedUUID(event) ?: return false
                            onDropTaskUUID(uuid)
                            return true
                        }
                    }
                }
            )
    ) {
        EmptyListRow(
            text = stringResource(R.string.routine_empty_period),
            iconRes = R.drawable.ic_rounded_check_circle,
            fontSize = 13.sp,
            verticalPadding = 5.dp,
        )
    }
}
