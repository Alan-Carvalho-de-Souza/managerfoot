package br.com.managerfoot.presentation.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Categoria de largura da janela — segue a mesma convenção do Material 3.
 *
 *  - [Compact]  → phones em portrait (< 600dp de largura)
 *  - [Medium]   → tablets pequenos e phones em landscape (600–839dp)
 *  - [Expanded] → tablets grandes e foldables abertos (≥ 840dp)
 *
 * Uso típico em composables:
 * ```
 * val ws = rememberWindowSizeClass()
 * val padding = if (ws == WindowSizeClass.Compact) 8.dp else 16.dp
 * ```
 */
enum class WindowSizeClass { Compact, Medium, Expanded }

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) {
        when {
            widthDp < 600  -> WindowSizeClass.Compact
            widthDp < 840  -> WindowSizeClass.Medium
            else           -> WindowSizeClass.Expanded
        }
    }
}

/**
 * Escolhe um valor `Dp` conforme a categoria de largura da tela.
 * Útil para ajustar padding/tamanhos sem espalhar `if`s pelo UI.
 */
@Composable
fun responsiveDp(compact: Dp, medium: Dp = compact, expanded: Dp = medium): Dp =
    when (rememberWindowSizeClass()) {
        WindowSizeClass.Compact  -> compact
        WindowSizeClass.Medium   -> medium
        WindowSizeClass.Expanded -> expanded
    }

/**
 * Retorna true quando a tela é considerada "estreita" (Compact).
 * Atalho para lógicas que só distinguem entre phone estreito e o resto.
 */
@Composable
fun isCompactWidth(): Boolean = rememberWindowSizeClass() == WindowSizeClass.Compact

/**
 * Valor default para "espaçamento horizontal externo" (padding lateral do conteúdo).
 * Compact = 16dp, Medium = 24dp, Expanded = 32dp.
 */
@Composable
fun defaultHorizontalPadding(): Dp = responsiveDp(16.dp, 24.dp, 32.dp)
