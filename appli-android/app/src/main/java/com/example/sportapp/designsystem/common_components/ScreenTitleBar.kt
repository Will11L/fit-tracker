package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.designsystem.theme.appColors

/**
 * Barre de titre d'écran : Box pleine largeur 44dp sur fond [bgSurface], titre
 * centré 16sp SemiBold. Canonique partagé — remplace 10 ex-doublons (6 « headers »
 * simples + 4 `*Title`) (R9, ↔ organism O3a TitleBar). Un [onClick] non-null rend
 * la barre cliquable (ex. revenir à aujourd'hui, ouvrir une sheet d'options).
 */
@Composable
fun ScreenTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(appColors.bgSurface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = appColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
