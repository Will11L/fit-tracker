package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.sportapp.designsystem.theme.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue


@Composable
fun HorizontalNumberPicker(
    range: IntRange,
    selected: Int,
    targetRange: IntRange? = null,
    scrollOnSelect: Boolean = true,
    itemSize: Int = 40, // ✅ Taille des cases paramétrable
    fontSize: Int = 16, // ✅ Taille du texte paramétrable
    label: String? = null, // ✅ Nouveau label
    onValueChange: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var hasInitialScroll by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // ✅ Affiche le label seulement s'il est fourni
        label?.let {
            Text(
                text = it,
                color = appColors.textTertiary,
                modifier = Modifier.padding(start = 2.dp),
                fontSize = 12.sp
            )
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(itemSize.dp + 16.dp)
        ) {
            items(range.toList(), key = { it }) { value ->
                val isSelected = value == selected
                val isRecommended = targetRange?.contains(value) == true

                val backgroundColor = when {
                    isSelected -> appColors.primaryAction
                    targetRange == null -> appColors.bgRecessed
                    isRecommended -> appColors.bgRecessed
                    else -> redMedium.copy(alpha = 0.5f)
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(itemSize.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .clickable {
                            onValueChange(value)
                            if (scrollOnSelect) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(value - range.first)
                                }
                            }
                        }
                ) {
                    Text(
                        text = value.toString(),
                        fontSize = if (isSelected) (fontSize + 2).sp else fontSize.sp,
                        color = appColors.textPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        LaunchedEffect(selected, scrollOnSelect) {
            if (scrollOnSelect && !hasInitialScroll) {
                listState.scrollToItem(selected - range.first)
                hasInitialScroll = true
            }
        }
    }
}
