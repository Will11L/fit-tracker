package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.darkGray

/**
 * Row canonique R17 — squelette 44dp partagé par `PlannedExerciseRow`,
 * `SessionExerciseRow` et `RoutineTaskRow`. Compose : (Box vertical padding) →
 * Row 44dp avec fond pendingDeletion-aware → 3 slots : [leadingContent?] + nom
 * cliquable (Box centré avec text color pendingDeletion-aware) +
 * [trailingContent].
 *
 * Le name box est centralisé (clickable, height 44dp, padding horizontal 12dp,
 * nameBoxColor masqué si pendingDeletion, text color textTertiary si
 * pendingDeletion sinon textPrimary). Les autres zones sont libres dans
 * `trailingContent` (weights, espaces, icônes — propres à chaque caller).
 *
 * `verticalPadding` et `contentEndPadding` exposés en params pour absorber les
 * micro-différences de chaque caller (Planned=4dp, Routine/Session=5dp ; Routine
 * a un padding end 8dp pour gap symétrique).
 */
@Composable
fun EntityListRow(
    modifier: Modifier = Modifier,
    isPendingDeletion: Boolean = false,
    backgroundColor: Color,
    nameBoxColor: Color = Color.Transparent,
    name: String,
    nameWeight: Float = 1f,
    nameMaxLines: Int = Int.MAX_VALUE,
    onNameClick: () -> Unit,
    verticalPadding: Dp = 5.dp,
    contentEndPadding: Dp = 0.dp,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    if (isPendingDeletion) darkGray else backgroundColor,
                    shape = MaterialTheme.shapes.small
                )
                .padding(end = contentEndPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent?.invoke(this)

            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(
                        enabled = !isPendingDeletion,
                        onClick = onNameClick
                    )
                    .background(
                        if (isPendingDeletion) Color.Transparent else nameBoxColor,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 12.dp)
                    .weight(nameWeight),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = name,
                    color = if (isPendingDeletion) appColors.textTertiary else appColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = nameMaxLines,
                    overflow = if (nameMaxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip,
                )
            }

            trailingContent()
        }
    }
}
