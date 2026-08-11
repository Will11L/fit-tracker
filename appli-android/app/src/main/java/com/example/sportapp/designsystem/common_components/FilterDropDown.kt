package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        TextField(
            readOnly = true,
            value = selected ?: "",
            onValueChange = {},
            label = {
                Text(
                    text = label,
                    color = appColors.textPrimary,
                    modifier = Modifier.padding(bottom = 4.dp) // ← un peu d’air sans casser l’animation
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
                fontSize = 14.sp // ← taille du texte sélectionné
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
            modifier = Modifier
                .background(appColors.bgRecessed)
        ) {
            options.forEach { opt ->
                val isSelected = opt == selected
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = opt,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (isSelected) appColors.primaryAction else appColors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_rounded_check),
                                    contentDescription = "Selected",
                                    tint = appColors.primaryAction,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(appColors.bgRecessed)
                )
            }
        }
    }
}
