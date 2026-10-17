package br.com.managerfoot.presentation.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Fundo do app ────────────────────────────────────────────
// Gradiente vertical com o mesmo "efeito splash" da tela inicial
// (Início / Novo jogo / Continuar). Centralizado aqui: edite este
// único ponto para mudar o fundo de TODAS as telas de uma vez.
//
// Ordem das cores = de cima → meio → base da tela.
val AppBackgroundBrush: Brush
    get() = Brush.verticalGradient(
        // Topo levemente elevado (glow azul sutil) → base profunda. Discreto e moderno.
        colors = listOf(
            Color(0xFF14213B),  // topo: navy elevado
            BgPrimary,          // meio
            Color(0xFF080D18)   // base: quase preto azulado
        )
    )

/** Aplica o fundo padrão do app (gradiente splash). Uso: `Modifier.fundoApp()`. */
fun Modifier.fundoApp(): Modifier = this.background(AppBackgroundBrush)
