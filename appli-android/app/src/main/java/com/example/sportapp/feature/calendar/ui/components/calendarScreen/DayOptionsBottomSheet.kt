package com.example.sportapp.feature.calendar.ui.components.calendarScreen

import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors
import java.time.LocalDate

@Composable
fun DayOptionsBottomSheet(
    selectedDate: LocalDate,
    onDismissRequest: () -> Unit,
    onAddNewActualWorkoutClick: () -> Unit,
    onAddFromPlannedClick: () -> Unit,
) {
    val actions = listOf(
        SheetAction(
            label = stringResource(R.string.calendar_day_option_add_new_workout),
            iconRes = R.drawable.ic_add,
            color = appColors.primaryAction,
            onClick = onAddNewActualWorkoutClick
        ),
        SheetAction(
            label = stringResource(R.string.calendar_day_option_add_from_planned),
            iconRes = R.drawable.ic_rounded_add_link,
            color = appColors.selectedFill,
            onClick = onAddFromPlannedClick
        )
    )

    OptionsBottomSheet(
        title = stringResource(R.string.calendar_day_options_sheet_title),
        actions = actions,
        onDismissRequest = onDismissRequest,
        containerColor = appColors.bgScreen
    )
}
