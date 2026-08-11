package com.example.sportapp.feature.session.ui.components.sessionExerciseScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextFieldDefaults
import com.example.sportapp.R
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteBottomSheet(
    note: String,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    AppBottomSheet(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(appColors.divider)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitledDivider(title = stringResource(R.string.sheet_note_title),)

            CustomTextField(
                value = note,
                onValueChange = onNoteChange,
                placeholder = stringResource(R.string.sheet_note_placeholder)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.common_delete), color = redMedium)
                }

                Row {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.common_cancel), color = appColors.textTertiary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.common_save), color = appColors.primaryAction)
                    }
                }
            }
        }
    }
}
