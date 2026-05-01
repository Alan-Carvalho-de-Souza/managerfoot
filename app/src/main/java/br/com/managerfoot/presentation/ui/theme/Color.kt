package br.com.managerfoot.presentation.ui.theme

import androidx.compose.ui.graphics.Color

// ── Tokens de cor — Tactical Dark ───────────────────────────
val GreenElectric  = Color(0xFF00E676)    // accent principal
val GreenDim       = Color(0x2000E676)    // fundo de badges verdes
val GreenMid       = Color(0x4000E676)    // bordas sutis
val AmberAccent    = Color(0xFFFFB300)    // empates / avisos
val RedAccent      = Color(0xFFFF4444)    // derrotas / erros

val BgPrimary      = Color(0xFF0D0F14)   // fundo profundo
val SurfaceCard    = Color(0xFF161922)   // cards e headers
val SurfaceVariant = Color(0xFF1E2330)   // variante de surface
val TextPrimary    = Color(0xFFF0F2F5)   // texto principal
val TextMuted      = Color(0xFF7B8394)   // texto secundário
val OutlineSubtle  = Color(0x1AFFFFFF)   // bordas (10% branco)

// ── Tokens semânticos por contexto ─────────────────────────
// Conquistas / pódio / Hall da Fama
val GoldChampion    = Color(0xFFFFD54F)  // campeão / 1º lugar
val SilverRunnerUp  = Color(0xFFB0BEC5)  // vice / 2º lugar
val BronzePlace     = Color(0xFFA1887F)  // 3º lugar / Libertadores

// Tabela / posições
val PromotionGreen  = Color(0xFF00C853)  // zona de acesso / G4 (forte)
val PromotionBg     = Color(0x2200E676)  // fundo da zona de acesso (sutil)
val LibertadoresBlue = Color(0xFF1E88E5) // zona Libertadores G6
val SulAmericanaTeal = Color(0xFF26A69A) // zona Sul-Americana G12
val RelegationRed   = Color(0xFFE53935)  // rebaixamento (forte)
val RelegationBg    = Color(0x33FF4444)  // fundo da zona de rebaixamento (sutil)

// Dinheiro
val MoneyPositive   = Color(0xFF00E676)  // ganho / receita
val MoneyNegative   = Color(0xFFFF6F60)  // gasto / despesa

// Estados de moral (5 níveis)
val MoraleExcelente   = Color(0xFF00E676)
val MoraleBom         = Color(0xFF66BB6A)
val MoraleNormal      = Color(0xFFB0BEC5)
val MoraleInsatisfeito = Color(0xFFFFB300)
val MoraleRevoltado   = Color(0xFFFF4444)

// Fadiga (3 faixas)
val FadigaAlta    = Color(0xFF00E676)  // ≥ 0.80 (descansado)
val FadigaMedia   = Color(0xFFFFB300)  // 0.40–0.80
val FadigaBaixa   = Color(0xFFFF4444)  // < 0.40 (esgotado)

// Setores / posições
val SetorGoleiro  = Color(0xFFAB47BC)
val SetorDefesa   = Color(0xFF42A5F5)
val SetorMeio     = Color(0xFF66BB6A)
val SetorAtaque   = Color(0xFFEF5350)

// Gramado (referência)
val PitchGreenDark  = Color(0xFF1B5E20)
val PitchGreenLight = Color(0xFF2E7D32)