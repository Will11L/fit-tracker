package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.*

@Composable
fun StyledSearchField(
    modifier: Modifier = Modifier,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholderText: String = "Search",
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            // 2026-05-12 : maxLines=1 + ellipsis -- evite que les longs
            // placeholders FR (ex. "Rechercher des exercices") cassent le layout
            // ou disparaissent sous la zone de saisie quand le widget est etroit.
            Text(
                text = placeholderText,
                color = appColors.textPrimary.copy(alpha = 0.6f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = appColors.primaryAction,
            fontSize = 14.sp
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = appColors.bgRecessed,
            unfocusedContainerColor = appColors.bgRecessed,
            focusedTextColor = appColors.primaryAction,
            unfocusedTextColor = appColors.primaryAction,
            cursorColor = appColors.primaryAction,
            focusedIndicatorColor = appColors.primaryAction,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}
