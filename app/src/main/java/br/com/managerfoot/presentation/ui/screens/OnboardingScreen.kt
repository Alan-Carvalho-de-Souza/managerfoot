package br.com.managerfoot.presentation.ui.screens

import br.com.managerfoot.presentation.ui.theme.fundoApp

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.managerfoot.presentation.ui.theme.GreenElectric
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val icone: ImageVector,
    val titulo: String,
    val subtitulo: String,
    val bullets: List<String>
)

private val SLIDES = listOf(
    OnboardingSlide(
        icone = Icons.Filled.SportsSoccer,
        titulo = "Bem-vindo, Técnico!",
        subtitulo = "Você assume o comando de um clube. Vamos ver o básico em 4 telas rápidas.",
        bullets = listOf(
            "Aba Início: próxima partida, últimos resultados e atalhos de gestão",
            "Aba Mais: acesso a todas as ferramentas (mercado, finanças, estádio, base)",
            "Aba Notif.: propostas de contratação e alertas do clube"
        )
    ),
    OnboardingSlide(
        icone = Icons.Filled.Groups,
        titulo = "Elenco e Mercado",
        subtitulo = "Monte seu time e reforce onde precisa.",
        bullets = listOf(
            "Escalação: escolha 11 titulares e até 11 reservas",
            "Mercado: contrate jogadores livres ou receba propostas de clubes",
            "Jogadores lesionados ou suspensos ficam fora da escalação automaticamente"
        )
    ),
    OnboardingSlide(
        icone = Icons.Filled.FitnessCenter,
        titulo = "Treinar e Simular",
        subtitulo = "O ciclo do jogo entre partidas.",
        bullets = listOf(
            "Treinar consome energia (5%) e acumula progresso de evolução",
            "Jogadores cansados (< 60%) têm 3× mais chance de se lesionar",
            "3 cartões amarelos ou 1 vermelho = suspensão na próxima partida",
            "Moral do elenco reflete os últimos 5 resultados e afeta força em campo"
        )
    ),
    OnboardingSlide(
        icone = Icons.Filled.EmojiEvents,
        titulo = "Objetivos",
        subtitulo = "Construa sua carreira como técnico.",
        bullets = listOf(
            "Vença campeonatos e acumule títulos na aba Conquistas",
            "Suba de divisão (série D → C → B → A) e receba propostas de clubes maiores",
            "Ranking Técnicos: seu desempenho é comparado com os outros treinadores",
            "Jogadores lendários vão para o Hall da Fama ao se aposentarem"
        )
    )
)

/**
 * Tela de tutorial inicial (4 slides) exibida na primeira execução do save.
 * Ao concluir ou pular, chama [onConcluir] — o Dashboard passa a ser mostrado.
 */
@Composable
fun OnboardingScreen(onConcluir: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { SLIDES.size })
    val scope = rememberCoroutineScope()
    val ultimoSlide = pagerState.currentPage == SLIDES.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fundoApp()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Header — Pular fica sempre acessível no canto superior direito
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onConcluir) {
                Text("Pular tutorial", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.md)
        ) { page ->
            SlideConteudo(SLIDES[page])
        }

        // Indicadores de página (dots)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
            horizontalArrangement = Arrangement.Center
        ) {
            SLIDES.indices.forEach { i ->
                val ativo = i == pagerState.currentPage
                val larguraAlvo by animateDpAsState(if (ativo) 24.dp else 8.dp, label = "dot_width")
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(larguraAlvo)
                        .clip(CircleShape)
                        .background(
                            if (ativo) GreenElectric
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                )
            }
        }

        // Footer — Voltar / Próximo (ou Começar no último slide)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                enabled = pagerState.currentPage > 0,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Radius.md)
            ) { Text("Voltar") }

            Button(
                onClick = {
                    if (ultimoSlide) onConcluir()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.weight(2f),
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (ultimoSlide) "Começar" else "Próximo", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SlideConteudo(slide: OnboardingSlide) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Spacer(Modifier.height(Spacing.lg))

        // Ícone destaque
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(GreenElectric.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = slide.icone,
                contentDescription = null,
                tint = GreenElectric,
                modifier = Modifier.size(64.dp)
            )
        }

        Text(
            text = slide.titulo,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Text(
            text = slide.subtitulo,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            slide.bullets.forEach { texto ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(GreenElectric)
                    )
                    Text(
                        text = texto,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
