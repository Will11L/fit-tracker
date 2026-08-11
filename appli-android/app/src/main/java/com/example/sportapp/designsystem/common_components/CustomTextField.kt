package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.*
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current.copy(color = appColors.textPrimary),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default // ✅ AJOUT
) {
    var isFocused by remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = appColors.primaryAction,
            backgroundColor = appColors.primaryAction.copy(alpha = 0.4f)
        )
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            label = label?.let {
                {
                    Text(
                        text = it,
                        color = if (isFocused || value.isNotBlank()) appColors.primaryAction else appColors.textTertiary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            },
            placeholder = { Text(placeholder, color = appColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focusState -> isFocused = focusState.isFocused },
            textStyle = textStyle,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
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
    }
}
