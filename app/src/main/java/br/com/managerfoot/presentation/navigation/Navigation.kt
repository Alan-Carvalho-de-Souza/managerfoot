package br.com.managerfoot.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.*
import androidx.navigation.compose.*
import br.com.managerfoot.presentation.ui.screens.*
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.presentation.viewmodel.DashboardViewModel

// ─────────────────────────────────────────────
//  Rotas de navegação
// ─────────────────────────────────────────────
sealed class Rota(val caminho: String) {
    object Inicio          : Rota("inicio")
    object SelecionarTime  : Rota("selecionar_time")
    object Dashboard       : Rota("dashboard/{timeId}") {
        fun comTimeId(id: Int) = "dashboard/$id"
    }
    object Escalacao       : Rota("escalacao/{timeId}?campeonatoId={campeonatoId}&rodada={rodada}&adversarioId={adversarioId}") {
        fun comTimeId(id: Int) = "escalacao/$id?campeonatoId=-1&rodada=-1&adversarioId=-1"
        fun preJogo(timeId: Int, campeonatoId: Int, rodada: Int, adversarioId: Int) =
            "escalacao/$timeId?campeonatoId=$campeonatoId&rodada=$rodada&adversarioId=$adversarioId"
    }
    object Tabela          : Rota("tabela/{campeonatoId}/{campeonatoBId}/{campeonatoCId}/{campeonatoDId}/{timeJogadorId}") {
        fun com(campAId: Int, campBId: Int, campCId: Int, campDId: Int, timeId: Int) =
            "tabela/$campAId/$campBId/$campCId/$campDId/$timeId"
    }
    object Artilheiros     : Rota("artilheiros/{campeonatoId}/{campeonatoBId}/{campeonatoCId}/{campeonatoDId}/{copaId}") {
        fun com(campAId: Int, campBId: Int, campCId: Int, campDId: Int, copaId: Int) =
            "artilheiros/$campAId/$campBId/$campCId/$campDId/$copaId"
    }
    object Mercado         : Rota("mercado/{timeId}") {
        fun comTimeId(id: Int) = "mercado/$id"
    }
    object Clubes          : Rota("clubes/{timeId}") {
        fun comTimeId(id: Int) = "clubes/$id"
    }
    object Financas        : Rota("financas/{timeId}") {
        fun comTimeId(id: Int) = "financas/$id"
    }
    object HallDaFama      : Rota("hall_da_fama")
    object Confronto       : Rota("confronto/{timeId}") {
        fun com(timeId: Int) = "confronto/$timeId"
    }
    object Calendario      : Rota("calendario/{timeId}") {
        fun comTimeId(id: Int) = "calendario/$id"
    }
    object CopaChaveamento : Rota("copa_chaveamento/{copaId}/{timeId}") {
        fun com(copaId: Int, timeId: Int) = "copa_chaveamento/$copaId/$timeId"
    }
    object RankingGeral    : Rota("ranking_geral")
    object EstatisticasTime : Rota("estatisticas_time/{timeId}") {
        fun com(timeId: Int) = "estatisticas_time/$timeId"
    }
    object Estadio          : Rota("estadio/{timeId}") {
        fun com(timeId: Int) = "estadio/$timeId"
    }
    object Juniores         : Rota("juniores/{timeId}") {
        fun comTimeId(id: Int) = "juniores/$id"
    }
    object Jogadores        : Rota("jogadores/{timeId}") {
        fun comTimeId(id: Int) = "jogadores/$id"
    }
    object Patrocinadores   : Rota("patrocinadores")
    object Treinamento      : Rota("treinamento/{timeId}") {
        fun comTimeId(id: Int) = "treinamento/$id"
    }
    object Rodada           : Rota("rodada/{campeonatoAId}/{campeonatoBId}/{campeonatoCId}/{campeonatoDId}") {
        fun com(campAId: Int, campBId: Int, campCId: Int, campDId: Int) =
            "rodada/$campAId/$campBId/$campCId/$campDId"
    }
}

// ─────────────────────────────────────────────
//  Bottom navigation tabs (visíveis no jogo)
// ─────────────────────────────────────────────
data class TabInfo(val label: String, val icone: ImageVector, val rota: String)

// ─────────────────────────────────────────────
//  ManagerFootNavGraph — grafo principal
// ─────────────────────────────────────────────
@Composable
fun ManagerFootNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Rota.Inicio.caminho) {

        // Tela inicial (novo jogo / continuar)
        composable(Rota.Inicio.caminho) {
            InicioScreen(
                onJogoIniciado = { timeId ->
                    navController.navigate(Rota.Dashboard.comTimeId(timeId)) {
                        popUpTo(Rota.Inicio.caminho) { inclusive = true }
                    }
                }
            )
        }

        // Seleção de time para novo jogo
        composable(Rota.SelecionarTime.caminho) {
            PlaceholderScreen("Seleção de time — em breve")
        }

        // Dashboard principal
        composable(
            route = Rota.Dashboard.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            val dashVm: DashboardViewModel = hiltViewModel()
            val saveState by dashVm.saveState.collectAsState()
            DashboardScreen(
                timeId = timeId,
                vm = dashVm,
                onIrParaEscalacao   = { navController.navigate(Rota.Escalacao.comTimeId(timeId)) },
                onIrParaPreJogo     = { campId, rod, advId ->
                    navController.navigate(Rota.Escalacao.preJogo(timeId, campId, rod, advId))
                },
                onIrParaMercado     = { navController.navigate(Rota.Mercado.comTimeId(timeId)) },
                onIrParaTabela      = { navController.navigate(Rota.Tabela.com(
                    campAId = saveState?.campeonatoAId ?: -1,
                    campBId = saveState?.campeonatoBId ?: -1,
                    campCId = saveState?.campeonatoCId ?: -1,
                    campDId = saveState?.campeonatoDId ?: -1,
                    timeId  = timeId
                )) },
                onIrParaArtilheiros = { navController.navigate(Rota.Artilheiros.com(
                    campAId = saveState?.campeonatoAId ?: -1,
                    campBId = saveState?.campeonatoBId ?: -1,
                    campCId = saveState?.campeonatoCId ?: -1,
                    campDId = saveState?.campeonatoDId ?: -1,
                    copaId  = saveState?.copaId ?: -1
                )) },
                onIrParaFinancas    = { navController.navigate(Rota.Financas.comTimeId(timeId)) },
                onIrParaHallDaFama  = { navController.navigate(Rota.HallDaFama.caminho) },
                onIrParaConfronto   = { navController.navigate(Rota.Confronto.com(timeId)) },
                onIrParaCalendario  = { navController.navigate(Rota.Calendario.comTimeId(timeId)) },
                onIrParaCopaChaveamento = {
                    val copaId = saveState?.copaId ?: -1
                    if (copaId > 0) navController.navigate(Rota.CopaChaveamento.com(copaId, timeId))
                },
                onIrParaRankingGeral = { navController.navigate(Rota.RankingGeral.caminho) },
                onIrParaEstatisticasTime = { navController.navigate(Rota.EstatisticasTime.com(timeId)) },
                onIrParaEstadio      = { navController.navigate(Rota.Estadio.com(timeId)) },
                onIrParaJuniores     = { navController.navigate(Rota.Juniores.comTimeId(timeId)) },
                onIrParaJogadores    = { navController.navigate(Rota.Jogadores.comTimeId(timeId)) },
                onIrParaRodada       = { navController.navigate(Rota.Rodada.com(
                    campAId = saveState?.campeonatoAId ?: -1,
                    campBId = saveState?.campeonatoBId ?: -1,
                    campCId = saveState?.campeonatoCId ?: -1,
                    campDId = saveState?.campeonatoDId ?: -1
                )) },
                onIrParaClubes            = { navController.navigate(Rota.Clubes.comTimeId(timeId)) },
                onIrParaPatrocinadores    = { navController.navigate(Rota.Patrocinadores.caminho) },
                onIrParaTreinamento       = { navController.navigate(Rota.Treinamento.comTimeId(timeId)) }
            )
        }

        // Tela de escalação (modo normal e modo pré-jogo)
        composable(
            route = Rota.Escalacao.caminho,
            arguments = listOf(
                navArgument("timeId")      { type = NavType.IntType },
                navArgument("campeonatoId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("rodada")       { type = NavType.IntType; defaultValue = -1 },
                navArgument("adversarioId") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStack ->
            val timeId       = backStack.arguments!!.getInt("timeId")
            val campeonatoId = backStack.arguments!!.getInt("campeonatoId")
            val rodada       = backStack.arguments!!.getInt("rodada")
            val adversarioId = backStack.arguments!!.getInt("adversarioId")
            val modoPreJogo  = campeonatoId > 0
            if (modoPreJogo) {
                val dashboardEntry = remember(navController) {
                    try { navController.getBackStackEntry(Rota.Dashboard.comTimeId(timeId)) }
                    catch (e: Exception) { null }
                }
                val dashVm: DashboardViewModel? = dashboardEntry?.let { hiltViewModel(it) }
                EscalacaoScreen(
                    timeId          = timeId,
                    modoPreJogo     = true,
                    adversarioId    = adversarioId,
                    onIniciarPartida = {
                        dashVm?.simularProximaPartida(campeonatoId, rodada)
                        navController.popBackStack()
                    }
                )
            } else {
                EscalacaoScreen(timeId = timeId)
            }
        }

        // Tabela de classificação
        composable(
            route = Rota.Tabela.caminho,
            arguments = listOf(
                navArgument("campeonatoId")   { type = NavType.IntType },
                navArgument("campeonatoBId")  { type = NavType.IntType },
                navArgument("campeonatoCId")  { type = NavType.IntType },
                navArgument("campeonatoDId")  { type = NavType.IntType },
                navArgument("timeJogadorId")  { type = NavType.IntType }
            )
        ) { backStack ->
            val campeonatoId  = backStack.arguments!!.getInt("campeonatoId")
            val campeonatoBId = backStack.arguments!!.getInt("campeonatoBId")
            val campeonatoCId = backStack.arguments!!.getInt("campeonatoCId")
            val campeonatoDId = backStack.arguments!!.getInt("campeonatoDId")
            val timeJogadorId = backStack.arguments!!.getInt("timeJogadorId")
            TabelaScreen(
                campeonatoAId = campeonatoId,
                campeonatoBId = campeonatoBId,
                campeonatoCId = campeonatoCId,
                campeonatoDId = campeonatoDId,
                timeJogadorId = timeJogadorId,
                onVoltar      = { navController.popBackStack() }
            )
        }

        // Artilharia & Assistências
        composable(
            route = Rota.Artilheiros.caminho,
            arguments = listOf(
                navArgument("campeonatoId")   { type = NavType.IntType },
                navArgument("campeonatoBId")  { type = NavType.IntType },
                navArgument("campeonatoCId")  { type = NavType.IntType },
                navArgument("campeonatoDId")  { type = NavType.IntType },
                navArgument("copaId")         { type = NavType.IntType }
            )
        ) { backStack ->
            val campeonatoId  = backStack.arguments!!.getInt("campeonatoId")
            val campeonatoBId = backStack.arguments!!.getInt("campeonatoBId")
            val campeonatoCId = backStack.arguments!!.getInt("campeonatoCId")
            val campeonatoDId = backStack.arguments!!.getInt("campeonatoDId")
            val copaId        = backStack.arguments!!.getInt("copaId")
            ArtilheirosScreen(
                campeonatoAId = campeonatoId,
                campeonatoBId = campeonatoBId,
                campeonatoCId = campeonatoCId,
                campeonatoDId = campeonatoDId,
                copaId        = copaId,
                onVoltar      = { navController.popBackStack() }
            )
        }

        // Mercado de transferências
        composable(
            route = Rota.Mercado.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            MercadoScreen(
                timeId = timeId,
                onVoltar = { navController.popBackStack() },
                onIrParaClubes = { navController.navigate(Rota.Clubes.comTimeId(timeId)) }
            )
        }

        // Clubes
        composable(
            route = Rota.Clubes.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            ClubesScreen(
                timeId = timeId,
                onVoltar = { navController.popBackStack() }
            )
        }

        // Finanças
        composable(
            route = Rota.Financas.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            FinancasScreen(timeId = timeId, onVoltar = { navController.popBackStack() })
        }

        // Hall da Fama
        composable(route = Rota.HallDaFama.caminho) {
            HallDaFamaScreen(onVoltar = { navController.popBackStack() })
        }

        // Histórico de confrontos
        composable(
            route = Rota.Confronto.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            ConfrontoScreen(preTimeId = timeId, onVoltar = { navController.popBackStack() })
        }

        // Calendário do clube
        composable(
            route = Rota.Calendario.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            CalendarioScreen(timeId = timeId, onVoltar = { navController.popBackStack() })
        }

        // Chaveamento da Copa
        composable(
            route = Rota.CopaChaveamento.caminho,
            arguments = listOf(
                navArgument("copaId") { type = NavType.IntType },
                navArgument("timeId") { type = NavType.IntType }
            )
        ) { backStack ->
            val copaId = backStack.arguments!!.getInt("copaId")
            val timeId = backStack.arguments!!.getInt("timeId")
            CopaChaveamentoScreen(copaId = copaId, timeJogadorId = timeId, onVoltar = { navController.popBackStack() })
        }

        // Ranking Geral
        composable(route = Rota.RankingGeral.caminho) {
            RankingGeralScreen(onVoltar = { navController.popBackStack() })
        }

        // Estatísticas do Time
        composable(
            route = Rota.EstatisticasTime.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            EstatisticasTimeScreen(timeId = timeId, onVoltar = { navController.popBackStack() })
        }

        // Estádio
        composable(
            route = Rota.Estadio.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            EstadioScreen(timeId = timeId, onVoltar = { navController.popBackStack() })
        }

        // Base de Juniores
        composable(
            route = Rota.Juniores.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            JunioresScreen(timeId = timeId, onVoltar = { navController.popBackStack() })
        }

        // Jogadores — elenco + pesquisa global
        composable(
            route = Rota.Jogadores.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            JogadoresScreen(timeId = timeId, onVoltar = { navController.popBackStack() })
        }

        // Patrocinadores
        composable(route = Rota.Patrocinadores.caminho) {
            PatrocinioScreen(onVoltar = { navController.popBackStack() })
        }

        // Treinamento do Elenco
        composable(
            route = Rota.Treinamento.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            TreinamentoScreen(timeId = timeId, onVoltar = { navController.popBackStack() })
        }

        // Rodadas das Séries
        composable(
            route = Rota.Rodada.caminho,
            arguments = listOf(
                navArgument("campeonatoAId") { type = NavType.IntType },
                navArgument("campeonatoBId") { type = NavType.IntType },
                navArgument("campeonatoCId") { type = NavType.IntType },
                navArgument("campeonatoDId") { type = NavType.IntType }
            )
        ) { backStack ->
            val campAId = backStack.arguments!!.getInt("campeonatoAId")
            val campBId = backStack.arguments!!.getInt("campeonatoBId")
            val campCId = backStack.arguments!!.getInt("campeonatoCId")
            val campDId = backStack.arguments!!.getInt("campeonatoDId")
            RodadaScreen(
                campeonatoAId = campAId,
                campeonatoBId = campBId,
                campeonatoCId = campCId,
                campeonatoDId = campDId,
                onVoltar      = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(mensagem: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(mensagem)
    }
}

