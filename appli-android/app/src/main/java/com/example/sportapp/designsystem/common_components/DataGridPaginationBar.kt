package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium

/**
 * Molecule : barre de pagination Excel-style en bas d'une data grid.
 *
 * Composition :
 *   [◀ ActionIconButton] [Page X / Y] [▶ ActionIconButton] · · · [Showing A-B of Z] [50 ▾ CustomSelect]
 *
 * - Prev/Next : `ActionIconButton` désactivés aux extrémités
 * - Page label : strings i18n
 * - "Showing A-B of Z" : range affiché, calculé en interne
 * - Page size dropdown : `CustomSelect` (compact)
 *
 * Cf. T-sync-grid (2026-05-26) + sync Figma page `3 · Molecules`.
 */
@Composable
fun DataGridPaginationBar(
    totalCount: Int,
    pageSize: Int,
    currentPage: Int,
    pageSizeOptions: List<Int>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPageSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalPages = if (totalCount == 0) 1 else (totalCount + pageSize - 1) / pageSize
    val from = if (totalCount == 0) 0 else currentPage * pageSize + 1
    val to = ((currentPage + 1) * pageSize).coerceAtMost(totalCount)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(appColors.bgRecessed)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_keyboard_arrow_left,
            onClick = onPrev,
            clickable = currentPage > 0,
            customBackgroundColor = appColors.bgButton,
            tint = if (currentPage > 0) appColors.primaryAction else appColors.textTertiary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.sync_table_page_x_of_y, currentPage + 1, totalPages),
            color = appColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        ActionIconButton(
            iconRes = R.drawable.ic_keyboard_arrow_right,
            onClick = onNext,
            clickable = currentPage < totalPages - 1,
            customBackgroundColor = appColors.bgButton,
            tint = if (currentPage < totalPages - 1) appColors.primaryAction else appColors.textTertiary,
        )

        Spacer(Modifier.weight(1f))

        // Pill X-Y / Z : bg bgSurface (= boxBlue) + text blanc (textPrimary) pour contraster
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = appColors.bgSurface,
        ) {
            Text(
                text = stringResource(R.string.sync_table_showing_range, from, to, totalCount),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = appColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))

        CustomSelect(
            selected = pageSize.toString(),
            options = pageSizeOptions.map { it.toString() },
            onSelect = { onPageSizeChange(it.toInt()) },
            modifier = Modifier.width(80.dp),
            textSize = 12,
            backgroundColor = appColors.bgButton,
            menuBackgroundColor = appColors.bgButton,
            textColor = appColors.textPrimary,
        )
    }
}
