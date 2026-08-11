package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectDropdown(
    label: String,
    options: List<String>,
    selectedItems: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        // Texte d'affichage des items sélectionnés
        val displayText = if (selectedItems.isEmpty()) {
            ""
        } else {
            selectedItems.joinToString(", ")
        }

        TextField(
            readOnly = true,
            value = displayText,
            onValueChange = {},
            label = {
                Text(
                    text = label,
                    color = appColors.textTertiary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = if (expanded) appColors.primaryAction else appColors.textTertiary
                )
            },
            modifier = modifier
                .menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small),
            textStyle = LocalTextStyle.current.copy(
                color = appColors.primaryAction,
                fontSize = 14.sp
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = appColors.bgRecessed,
                unfocusedContainerColor = appColors.bgRecessed,
                focusedTextColor = appColors.textSecondary,
                unfocusedTextColor = appColors.textPrimary,
                cursorColor = appColors.textPrimary,
                focusedIndicatorColor = appColors.primaryAction,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(appColors.bgRecessed)
        ) {
            options.forEach { option ->
                val isSelected = option in selectedItems

                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = option,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (isSelected) appColors.primaryAction else appColors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_rounded_check), // ✅ check icon
                                    contentDescription = "Selected",
                                    tint = appColors.primaryAction,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        val newSelection = if (isSelected) {
                            selectedItems - option
                        } else {
                            selectedItems + option
                        }
                        onSelectionChange(newSelection)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
