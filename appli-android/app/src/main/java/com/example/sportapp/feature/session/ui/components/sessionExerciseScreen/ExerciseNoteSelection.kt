package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.theme.appColors

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle

@Composable
fun ExerciseNoteSection(
    note: String,
    onNoteChange: (String) -> Unit,
    textStyle: TextStyle = LocalTextStyle.current // 👈 par défaut = inchangé
) {
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    var isKeyboardVisible by remember { mutableStateOf(false) }

    var draft by remember(note) { mutableStateOf(note) }

    DisposableEffect(view) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            insets
        }
        onDispose { ViewCompat.setOnApplyWindowInsetsListener(view, null) }
    }

    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) focusManager.clearFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(MaterialTheme.shapes.small)
            .background(appColors.bgSurface, shape = MaterialTheme.shapes.small)
            .padding(2.dp)
    ) {
        CustomTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = "Write a note about this exercise...",
            singleLine = false,
            textStyle = textStyle, // 👈 ici
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .heightIn(min = 60.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && draft != note) {
                        onNoteChange(draft)
                    }
                }
        )
    }
}

