package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.ExerciseEquipment
import com.example.sportapp.designsystem.theme.*

/**
 * Bottom sheet de sélection d'exercice : filtre par équipement + recherche +
 * liste d'exercices avec actions « voir » / « ajouter ».
 * Canonique partagé — remplace les ex-doublons `AddExerciseToSessionBottomSheet`
 * et `AddExerciseToPlannedWorkoutBottomSheet` (R3). [title] = en-tête de la sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerBottomSheet(
    title: String,
    allExercises: List<Exercise>,
    allEquipments: List<Equipment>,
    allExerciseEquipments: List<ExerciseEquipment>,
    onSelectExercise: (Exercise) -> Unit,
    onViewExercise: (Exercise) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedEquipment by remember { mutableStateOf<String?>(null) }

    val equipmentOptions = remember(allEquipments) {
        listOf("All") + allEquipments.map { it.name }.distinct()
    }

    val filteredExercises by remember(query, selectedEquipment, allExercises, allEquipments, allExerciseEquipments) {
        derivedStateOf {
            allExercises.filter { exercise ->
                val matchesQuery = query.isBlank() || exercise.name.contains(query, ignoreCase = true)

                val matchesEquipment = selectedEquipment == null ||
                        selectedEquipment == "All" ||
                        run {
                            val equipmentUUID = allEquipments.firstOrNull { it.name == selectedEquipment }?.uuid
                            equipmentUUID != null &&
                                    allExerciseEquipments.any {
                                        it.exerciseUUID == exercise.uuid && it.equipmentUUID == equipmentUUID
                                    }
                        }

                matchesQuery && matchesEquipment
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = appColors.bgScreen,
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
        // Fix nav bar blanche sous le sheet (Window du Dialog M3), cf. AppBottomSheet.
        ForceSheetSystemBars(lightStatusBars = false, lightNavBars = false)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TitledDivider(title)

            // 🟦 Barre de filtres et recherche
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterDropdown(
                    label = stringResource(R.string.sheet_add_exercise_equipment_label),
                    options = equipmentOptions,
                    selected = selectedEquipment,
                    onSelect = { selectedEquipment = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(R.string.sheet_add_exercise_search),
                            color = appColors.textTertiary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.small),
                    textStyle = LocalTextStyle.current.copy(
                        color = appColors.primaryAction,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = appColors.bgRecessed,
                        unfocusedContainerColor = appColors.bgRecessed,
                        focusedTextColor = appColors.textSecondary,
                        unfocusedTextColor = appColors.textPrimary,
                        cursorColor = appColors.textPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            TitledDivider(stringResource(R.string.sheet_add_exercise_exercises_section))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredExercises) { exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(appColors.bgRecessed)
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = exercise.name,
                            color = appColors.textPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionIconButton(
                                iconRes = R.drawable.ic_rounded_eye_tracking,
                                onClick = { onViewExercise(exercise) },
                                hasBackground = true,
                                customBackgroundColor = appColors.selectedFill
                            )

                            ActionIconButton(
                                iconRes = R.drawable.ic_add,
                                onClick = { onSelectExercise(exercise) },
                                hasBackground = true,
                                customBackgroundColor = appColors.primaryAction.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}
