package com.example.sportapp.designsystem.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors

/**
 * Section repliable du drawer (accordéon) — pendant Android de
 * `drawer-section.ts` (web). Le titre devient cliquable : tap → bascule
 * [expanded] (état piloté par le parent, géré session-only via [DrawerSectionStateManager]),
 * chevron qui pivote, contenu animé en expand/collapse.
 *
 * @param expanded section dépliée (items visibles).
 * @param onHeaderClick tap sur le titre : le parent bascule l'ouverture.
 */
@Composable
fun DrawerSection(
    title: String,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    items: List<DrawerItem>,
    modifier: Modifier = Modifier
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "drawerSectionChevron"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(appColors.bgRecessed)
            .padding(top = 12.dp, bottom = 8.dp)
    ) {
        // En-tête cliquable : titre centré + chevron pivotant à droite (le chevron
        // déborde à droite, n'altère pas le centrage visuel du titre — miroir web).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onHeaderClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = appColors.accentText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Icon(
                painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                contentDescription = stringResource(
                    if (expanded) R.string.drawer_section_collapse
                    else R.string.drawer_section_expand
                ),
                tint = appColors.accentText,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(22.dp)
                    .rotate(chevronRotation)
            )
        }

        // Contenu animé : le divider supérieur vit dans le wrapper -> il s'enroule
        // avec les items (miroir web : divider dans itemswrap).
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    if (index == 0) {
                        HorizontalDivider(
                            color = appColors.dividerStrong.copy(alpha = 0.6f),
                            thickness = 2.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = item.onClick)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = item.title,
                            tint = item.iconTint ?: appColors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(Modifier.width(10.dp))

                        Text(
                            text = item.title,
                            color = appColors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(10f)
                        )

                        Spacer(Modifier.weight(1f)) // ✅ pousse le trailing à droite

                        // ✅ contenu à droite (progress bar + % par ex)
                        item.trailingContent?.invoke()
                    }

                    if (index != items.lastIndex) {
                        HorizontalDivider(
                            color = appColors.dividerStrong.copy(alpha = 0.30f),
                            thickness = 2.dp,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                    }
                }
            }
        }
    }
}
