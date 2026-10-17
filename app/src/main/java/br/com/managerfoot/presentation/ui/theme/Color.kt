package br.com.managerfoot.presentation.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
//  Design System — "Blue Ink" (dark refinado, identidade azul)
//  Pilar 1: COR. Base neutra azul-escura + acento azul tech.
//  Os nomes Green* são mantidos como ALIASES de acento (brand) para
//  não quebrar as telas existentes — hoje carregam o azul da marca.
// ═══════════════════════════════════════════════════════════════

// ── Acento da marca (azul tech) ─────────────────────────────
val AccentBlue      = Color(0xFF3B82F6)   // fill (botões, primary)
val AccentBlueBright = Color(0xFF60A5FA)  // acento vivo p/ texto/ícone/stroke no escuro

// Aliases de compatibilidade (eram verdes; agora são o acento azul):
val GreenElectric  = AccentBlueBright     // accent principal (texto/realces)
val GreenDim       = Color(0x1F3B82F6)    // fundo de badges do acento
val GreenMid       = Color(0x403B82F6)    // bordas sutis do acento

val AmberAccent    = Color(0xFFF5A524)    // empates / avisos (âmbar mais limpo)
val RedAccent      = Color(0xFFF2545B)    // derrotas / erros

// ── Base neutra (ink azul-acinzentado) ──────────────────────
val BgPrimary      = Color(0xFF0B111F)   // fundo mais profundo do app
val SurfaceCard    = Color(0xFF161F33)   // cards e headers (um degrau acima do fundo)
val SurfaceVariant = Color(0xFF1F2A42)   // variante de surface (chips, trilhos)
val TextPrimary    = Color(0xFFEAEFF7)   // texto principal
val TextMuted      = Color(0xFF94A3B8)   // texto secundário (slate)
val OutlineSubtle  = Color(0x14FFFFFF)   // bordas (8% branco)

// ── Tokens semânticos por contexto ─────────────────────────
// Conquistas / pódio / Hall da Fama
val GoldChampion    = Color(0xFFFFD54F)  // campeão / 1º lugar
val SilverRunnerUp  = Color(0xFFCBD5E1)  // vice / 2º lugar
val BronzePlace     = Color(0xFFB08968)  // 3º lugar / Libertadores

// Tabela / posições (mantêm o significado: verde=acesso, vermelho=queda)
val PromotionGreen  = Color(0xFF22C55E)  // zona de acesso / G4
val PromotionBg     = Color(0x2222C55E)  // fundo da zona de acesso (sutil)
val LibertadoresBlue = Color(0xFF3B82F6) // zona Libertadores G6
val SulAmericanaTeal = Color(0xFF2DD4BF) // zona Sul-Americana G12
val RelegationRed   = Color(0xFFEF4444)  // rebaixamento
val RelegationBg    = Color(0x33EF4444)  // fundo da zona de rebaixamento (sutil)

// Dinheiro (verde = positivo / vermelho = negativo — significado preservado)
val MoneyPositive   = Color(0xFF34D399)  // ganho / receita (emerald)
val MoneyNegative   = Color(0xFFFB7185)  // gasto / despesa

// Estados de moral (5 níveis)
val MoraleExcelente    = Color(0xFF34D399)
val MoraleBom          = Color(0xFF86EFAC)
val MoraleNormal       = Color(0xFF94A3B8)
val MoraleInsatisfeito = Color(0xFFF5A524)
val MoraleRevoltado    = Color(0xFFF2545B)

// Fadiga (3 faixas)
val FadigaAlta    = Color(0xFF34D399)  // ≥ 0.80 (descansado)
val FadigaMedia   = Color(0xFFF5A524)  // 0.40–0.80
val FadigaBaixa   = Color(0xFFF2545B)  // < 0.40 (esgotado)

// Setores / posições
val SetorGoleiro  = Color(0xFFC084FC)
val SetorDefesa   = Color(0xFF60A5FA)
val SetorMeio     = Color(0xFF4ADE80)
val SetorAtaque   = Color(0xFFF87171)

// Gramado (referência)
val PitchGreenDark  = Color(0xFF14532D)
val PitchGreenLight = Color(0xFF166534)
