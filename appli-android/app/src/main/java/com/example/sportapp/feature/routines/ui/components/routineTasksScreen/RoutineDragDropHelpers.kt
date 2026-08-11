@file:OptIn(ExperimentalFoundationApi::class)

package com.example.sportapp.feature.routines.ui.components.routineTasksScreen

import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent

/** Libellé du ClipData de drag d'une tâche de routine (partagé source ↔ cibles). */
internal const val DRAG_LABEL = "routine_task_uuid"

/**
 * Extrait l'UUID de tâche depuis un événement de drop. `internal` (et non plus
 * `private`) car partagé entre [dropTargetForGap], [RoutinePeriodHeaderDropItem]
 * et [RoutineEmptyPeriodDropItem] désormais dans des fichiers séparés.
 */
internal fun extractDraggedUUID(event: DragAndDropEvent): String? {
    return event.toAndroidDragEvent().clipData
        ?.getItemAt(0)
        ?.text
        ?.toString()
        ?.takeIf { it.isNotBlank() }
}

// ✅ IMPORTANT : PAS @Composable
fun Modifier.dropTargetForGap(
    key: String,
    onHover: () -> Unit,
    onExit: () -> Unit,
    onDropTaskUUID: (String) -> Unit
): Modifier = composed {
    this.dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
        },
        target = remember(key) {
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
}
