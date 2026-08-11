package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.GrayBlue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.sportapp.designsystem.theme.appColors

@Composable
internal fun WheelPicker(
    range: IntRange,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visibleItems: Int = 5
) {
    require(visibleItems % 2 == 1) { "visibleItems must be odd" }

    val values = remember(range) { range.toList() }
    val size = values.size

    val loopCount = 2000
    val totalCount = size * loopCount
    val middle = totalCount / 2

    val half = visibleItems / 2
    val listHeight = itemHeight * visibleItems
    val pad = itemHeight * half

    val normalizedSelected = selected.coerceIn(range)

    val startIndex = remember(range, normalizedSelected) {
        val base = middle - (middle % size)
        val targetMod = values.indexOf(normalizedSelected).coerceAtLeast(0)
        base + targetMod
    }

    val state = rememberLazyListState()

    val snapProvider = remember(state) { SnapLayoutInfoProvider(state, SnapPosition.Center) }
    val fling = rememberSnapFlingBehavior(snapProvider)

    val scope = rememberCoroutineScope()

    // ✅ évite les captures “vieilles” dans les coroutines
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelected by rememberUpdatedState(onSelected)

    fun globalIndexToValue(globalIndex: Int): Int {
        val vIndex = mod(globalIndex, size)
        return values[vIndex]
    }

    fun centeredGlobalIndex(): Int? {
        val layout = state.layoutInfo
        val items = layout.visibleItemsInfo
        if (items.isEmpty()) return null

        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
        val closest = items.minByOrNull { info ->
            val itemCenter = info.offset + (info.size / 2)
            abs(itemCenter - viewportCenter)
        }
        return closest?.index
    }

    suspend fun animateCenterTo(globalIndex: Int) {
        state.animateScrollToItem(globalIndex)
    }

    suspend fun snapCenterTo(globalIndex: Int) {
        state.scrollToItem(globalIndex)
    }

    // ✅ position initiale
    LaunchedEffect(startIndex) {
        snapCenterTo(startIndex)
    }

    // ✅ si selected change depuis l’extérieur
    LaunchedEffect(normalizedSelected, range) {
        val centered = centeredGlobalIndex()
        if (centered != null) {
            val currentValue = globalIndexToValue(centered)
            if (currentValue != normalizedSelected) {
                val currentMod = mod(centered, size)
                val targetMod = values.indexOf(normalizedSelected).coerceAtLeast(0)
                val delta = shortestDelta(currentMod, targetMod, size)
                animateCenterTo(centered + delta)
            }
        } else {
            snapCenterTo(startIndex)
        }
    }

    // ✅ valeur réellement au centre (pour le surlignage)
    var centeredValue by remember { mutableIntStateOf(normalizedSelected) }

    // 1) Highlight: on suit le centre en continu
    LaunchedEffect(state, range) {
        snapshotFlow { centeredGlobalIndex() }
            .filter { it != null }
            .map { globalIndexToValue(it!!) }
            .distinctUntilChanged()
            .collect { newCenterValue ->
                centeredValue = newCenterValue
            }
    }

    // 2) Commit: quand le scroll s’arrête, on applique la sélection
    LaunchedEffect(state, range) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .filter { inProgress -> !inProgress }
            .map {
                val c = centeredGlobalIndex()
                if (c == null) null else globalIndexToValue(c)
            }
            .filter { it != null }
            .map { it!! }
            .distinctUntilChanged()
            .collect { newValue ->
                if (newValue != latestSelected) {
                    latestOnSelected(newValue)
                }
            }
    }

    // Style aligné sur le wheel-picker web (2026-07-14) : fente recessed, valeurs GrayBlue,
    // valeur centrale en bleu primaire, bande de sélection teintée + filets, fondu haut/bas.
    val primary = appColors.primaryAction
    Box(
        modifier = modifier
            .height(listHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(appColors.bgRecessed)
    ) {
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = pad),
            modifier = Modifier
                .fillMaxSize()
                // Dégradé de profondeur (roue physique) : net au centre, estompé vers les bords.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to Color.Black,
                            0.7f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(totalCount) { globalIndex ->
                val v = globalIndexToValue(globalIndex)

                // ✅ surligne l’item au centre, pas “selected”
                val isSelected = (v == centeredValue)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable {
                            scope.launch { animateCenterTo(globalIndex) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%02d".format(v),
                        fontSize = if (isSelected) 16.sp else 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) primary else GrayBlue,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Fente de sélection : teinte bleue + filets haut/bas (façon picker iOS, dans le thème).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(primary.copy(alpha = 0.14f))
                .drawWithContent {
                    drawContent()
                    // `this.size` (DrawScope) : la locale `size` (nb d'items) masque le membre.
                    val w = this.size.width
                    val h = this.size.height
                    val line = primary.copy(alpha = 0.35f)
                    drawLine(line, Offset(0f, 0.5f), Offset(w, 0.5f), strokeWidth = 1.dp.toPx())
                    drawLine(line, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), strokeWidth = 1.dp.toPx())
                }
        )
    }
}

private fun mod(a: Int, b: Int): Int {
    val r = a % b
    return if (r < 0) r + b else r
}

private fun shortestDelta(from: Int, to: Int, n: Int): Int {
    val forward = (to - from + n) % n
    val backward = forward - n
    return if (abs(backward) < abs(forward)) backward else forward
}
