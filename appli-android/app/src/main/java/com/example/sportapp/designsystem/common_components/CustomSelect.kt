package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun CustomSelect(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    colorMap: Map<String, Color> = emptyMap(),
    textSize: Int = 14, // 👈 Taille du texte par défaut
    textWeight: FontWeight = FontWeight.Normal, // 👈 Style du texte par défaut
    backgroundColor: Color = appColors.bgRecessed, // 👈 Couleur de fond par défaut du bouton
    textColor: Color = appColors.primaryAction, // 👈 Couleur du texte par défaut
    menuBackgroundColor: Color = appColors.bgRecessed // 👈 Couleur de fond par défaut du menu
) {
    var expanded by remember { mutableStateOf(false) }
    val currentBackgroundColor = colorMap[selected] ?: backgroundColor

    Column(modifier = modifier) {

        // ✅ Label optionnel
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = appColors.textTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val dropdownWidth = this.maxWidth

            Box {
                Surface(
                    modifier = Modifier
                        .width(dropdownWidth)
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(currentBackgroundColor)
                        .clickable { expanded = true },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selected,
                            color = textColor,
                            fontSize = textSize.sp,
                            fontWeight = textWeight
                        )

                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = if (expanded) appColors.primaryAction else appColors.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .width(dropdownWidth)
                        .background(menuBackgroundColor),
                    // 2026-05-27 : fix "espace noir/gris top/bottom" du dropdown M3 ouvert :
                    // containerColor = même que les items + zéro elevation → plus de
                    // padding tonal/shadow visible.
                    containerColor = menuBackgroundColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    options.forEach { option ->
                        val isSelected = option == selected

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelected) Modifier.border(1.dp, textColor, MaterialTheme.shapes.small)
                                    else Modifier
                                )
                                .clickable {
                                    onSelect(option)
                                    expanded = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) textColor else appColors.textPrimary,
                                fontSize = textSize.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else textWeight
                            )
                        }
                    }
                }
            }
        }
    }
}
