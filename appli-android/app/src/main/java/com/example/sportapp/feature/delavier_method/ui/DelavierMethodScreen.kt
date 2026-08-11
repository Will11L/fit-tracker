package com.example.sportapp.feature.delavier_method.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun DelavierMethodScreen(
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    val pages = listOf(
        R.drawable.delavier_page_1,
        R.drawable.delavier_page_2,
        // Ajoute autant que nécessaire
    )
    DelavierImagePager(pages = pages)
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun DelavierImagePager(@DrawableRes pages: List<Int>) {
    var currentPage by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp)
            ) {
                val ratio = 1280f / 959f
                val boxWidth = constraints.maxWidth.toFloat()
                val boxHeight = constraints.maxHeight.toFloat()

                val targetWidth: Float
                val targetHeight: Float

                if (boxWidth / ratio <= boxHeight) {
                    targetWidth = boxWidth
                    targetHeight = boxWidth / ratio
                } else {
                    targetHeight = boxHeight
                    targetWidth = boxHeight * ratio
                }

                val state = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale + (zoomChange - 1f)).coerceIn(1f, 5f)

                    val extraWidth = (scale - 1) * targetWidth
                    val extraHeight = (scale - 1) * targetHeight

                    val maxX = extraWidth / 2f
                    val maxY = extraHeight / 2f

                    offset = Offset(
                        x = (offset.x + scale * panChange.x).coerceIn(-maxX, maxX),
                        y = (offset.y + scale * panChange.y).coerceIn(-maxY, maxY)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(targetWidth.dp, targetHeight.dp)
                        .border(2.dp, color = appColors.textTertiary)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .transformable(state)
                ) {
                    Image(
                        painter = painterResource(id = pages[currentPage]),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 🔁 Flèches navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionIconButton(
                    iconRes = R.drawable.ic_arrow_left_alt,
                    clickable = currentPage > 0,
                    onClick = {
                        if (currentPage > 0) {
                            currentPage--
                            scale = 1f
                            offset = Offset.Zero
                        }
                    }
                )

                // Page indicator
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.delavier_page_count, currentPage + 1, pages.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // Zoom Reset
                ActionIconButton(
                    iconRes = R.drawable.ic_rounded_refresh, // ou autre icône de reset
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    hasBackground = true
                )

                ActionIconButton(
                    iconRes = R.drawable.ic_arrow_right_alt,
                    clickable = currentPage < pages.lastIndex,
                    onClick = {
                        if (currentPage < pages.lastIndex) {
                            currentPage++
                            scale = 1f
                            offset = Offset.Zero
                        }
                    }
                )
            }

        }
    }
}
