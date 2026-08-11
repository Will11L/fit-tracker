package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.example.sportapp.designsystem.theme.appColors

/**
 * Shell générique des bottom sheets de l'app : un [ModalBottomSheet] M3 avec le
 * fond `appColors.bgScreen` par défaut. Tous les autres paramètres reprennent les
 * défauts M3 pour rester pixel-identiques aux sheets existants.
 *
 * - [shape] / [dragHandle] à `null` => défauts M3 ([BottomSheetDefaults.ExpandedShape]
 *   + [BottomSheetDefaults.DragHandle]). Un sheet qui veut un look custom (ex. handle
 *   maison) passe ses propres valeurs.
 * - [forceDarkSystemBars] = true (défaut) => applique [ForceSheetSystemBars] (status +
 *   nav bars sombres) à l'intérieur du sheet. Sans ça, la Window du Dialog M3 repeint
 *   la barre de navigation système en blanc sous le sheet (app entièrement sombre =>
 *   toujours voulu ; ne passer false que pour un futur sheet à fond clair).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = appColors.bgScreen,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    dragHandle: (@Composable () -> Unit)? = null,
    forceDarkSystemBars: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = shape,
        dragHandle = dragHandle ?: { BottomSheetDefaults.DragHandle() },
    ) {
        if (forceDarkSystemBars) {
            ForceSheetSystemBars(lightStatusBars = false, lightNavBars = false)
        }
        content()
    }
}
