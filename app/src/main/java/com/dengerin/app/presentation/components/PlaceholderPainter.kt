package com.dengerin.app.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

/**
 * Placeholder polos untuk sampul yang belum termuat atau kosong.
 * Memakai warna surface gelap, tanpa merek atau ikon tambahan.
 */
@Composable
fun darkSlatePlaceholder(): Painter = ColorPainter(Color(0xFF181818))
