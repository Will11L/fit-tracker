@file:OptIn(ExperimentalFoundationApi::class)

package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import com.example.sportapp.core.data.model.RoutinePeriod
import com.example.sportapp.designsystem.common_components.TitledDivider

@Composable
fun RoutinePeriodHeaderDropItem(
    period: RoutinePeriod,
    title: String,
    onClickHeader: () -> Unit,
    onDropTaskUUID: (String) -> Unit,
    onHover: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
        TitledDivider(
            title = title,
            onClick = onClickHeader
        )
    }
}
