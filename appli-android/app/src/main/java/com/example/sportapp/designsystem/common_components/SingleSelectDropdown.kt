package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleSelectDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    disabledOptions: Set<String> = emptySet(),
    disabledSuffix: String = " (current)",
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        TextField(
            readOnly = true,
            value = selected,
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
                val isSelected = option == selected
                val isDisabled = option in disabledOptions

                DropdownMenuItem(
                    text = {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (isDisabled) option + disabledSuffix else option,
                                fontSize = 14.sp,
                                color = when {
                                    isDisabled -> appColors.textTertiary
                                    isSelected -> appColors.primaryAction
                                    else -> appColors.textPrimary
                                },
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
                    enabled = !isDisabled,
                    onClick = {
                        if (!isDisabled) {
                            onSelect(option)
                            expanded = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
