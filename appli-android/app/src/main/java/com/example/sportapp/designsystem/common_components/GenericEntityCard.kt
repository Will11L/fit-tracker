package com.example.sportapp.designsystem.common_components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.darkGray

/**
 * Carte d'entité dépliable canonique. Header (icône + titre + slot trailing +
 * chevron expand) + détails dépliables (slot [detailsContent]) + actions
 * (slot [actions]). Canonique R16 — remplace l'ancienne version par réflexion
 * et sert de base aux wrappers `ExerciseCard` / `MuscleCard`.
 * [headerTrailing] permet d'insérer un élément secondaire dans le header
 * (ex. étoile favori). [cardBackground] surcharge la couleur de fond du Card.
 */
@Composable
fun GenericEntityCard(
    title: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    isPendingDeletion: Boolean = false,
    cardBackground: Color = appColors.bgRecessed,
    headerTrailing: @Composable (RowScope.() -> Unit)? = null,
    detailsContent: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val cardColor = if (isPendingDeletion) darkGray else cardBackground
    val headerColor = when {
        isPendingDeletion -> appColors.textTertiary
        expanded -> appColors.primaryAction
        else -> appColors.textPrimary.copy(alpha = 0.8f)
    }
    val dividerColor = if (isPendingDeletion) appColors.textTertiary else appColors.divider

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // 🔹 HEADER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp, horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = headerColor,
                        modifier = Modifier.size(36.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        headerTrailing?.invoke(this)
                        Icon(
                            painter = painterResource(
                                id = if (expanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down
                            ),
                            contentDescription = null,
                            tint = headerColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = headerColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = dividerColor,
                    thickness = 1.dp
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    // 🔹 DETAILS (slot)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = detailsContent,
                    )

                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = dividerColor,
                        thickness = 1.dp
                    )

                    // 🔹 ACTIONS (slot)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        content = actions,
                    )
                }
            }
        }
    }
}
