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
    object Escalacao       : Rota("escalacao/{timeId}?campeonatoId={campeonatoId}&rodada={rodada}&adversarioId={adversarioId}&amistoso={amistoso}") {
        fun comTimeId(id: Int) = "escalacao/$id?campeonatoId=-1&rodada=-1&adversarioId=-1&amistoso=false"
        fun preJogo(timeId: Int, campeonatoId: Int, rodada: Int, adversarioId: Int) =
            "escalacao/$timeId?campeonatoId=$campeonatoId&rodada=$rodada&adversarioId=$adversarioId&amistoso=false"
        // Pré-jogo de amistoso: rodada codifica o mando (1=casa, 0=fora).
        fun preAmistoso(timeId: Int, adversarioId: Int, ehCasa: Boolean) =
            "escalacao/$timeId?campeonatoId=-1&rodada=${if (ehCasa) 1 else 0}&adversarioId=$adversarioId&amistoso=true"
    }
    object Tabela          : Rota("tabela/{campeonatoId}/{campeonatoBId}/{campeonatoCId}/{campeonatoDId}/{campArgAId}/{campArgBId}/{campArgClausuraId}/{campUruAperturaId}/{campUruBId}/{campUruClausuraId}/{campUruIntermedId}/{campUruBCompetId}/{copaId}/{timeJogadorId}/{campPremierId}/{campChampionshipId}/{campLaLigaId}/{campSerieAId}/{campChampionsId}/{campEuropaId}") {
        fun com(campAId: Int, campBId: Int, campCId: Int, campDId: Int, campArgAId: Int, campArgBId: Int, campArgClausuraId: Int = -1, campUruAperturaId: Int = -1, campUruBId: Int = -1, campUruClausuraId: Int = -1, campUruIntermedId: Int = -1, campUruBCompetId: Int = -1, copaId: Int = -1, timeId: Int, campPremierId: Int = -1, campChampionshipId: Int = -1, campLaLigaId: Int = -1, campSerieAId: Int = -1, campChampionsId: Int = -1, campEuropaId: Int = -1) =
            "tabela/$campAId/$campBId/$campCId/$campDId/$campArgAId/$campArgBId/$campArgClausuraId/$campUruAperturaId/$campUruBId/$campUruClausuraId/$campUruIntermedId/$campUruBCompetId/$copaId/$timeId/$campPremierId/$campChampionshipId/$campLaLigaId/$campSerieAId/$campChampionsId/$campEuropaId"
    }
    object Artilheiros     : Rota("artilheiros/{campeonatoId}/{campeonatoBId}/{campeonatoCId}/{campeonatoDId}/{copaId}/{copaArgId}/{campArgAId}/{campArgBId}/{campArgClausuraId}/{campUruApertId}/{campUruInterId}/{campUruClausId}/{campUruBId}/{campUruBCompetId}/{campPremierId}/{campChampionshipId}/{campLaLigaId}/{campCopaIngId}/{campSerieAId}/{campAmistososId}/{campChampionsId}/{campEuropaId}") {
        fun com(campAId: Int, campBId: Int, campCId: Int, campDId: Int, copaId: Int, copaArgId: Int, campArgAId: Int, campArgBId: Int, campArgClausuraId: Int = -1, campUruApertId: Int = -1, campUruInterId: Int = -1, campUruClausId: Int = -1, campUruBId: Int = -1, campUruBCompetId: Int = -1, campPremierId: Int = -1, campChampionshipId: Int = -1, campLaLigaId: Int = -1, campCopaIngId: Int = -1, campSerieAId: Int = -1, campAmistososId: Int = -1, campChampionsId: Int = -1, campEuropaId: Int = -1) =
            "artilheiros/$campAId/$campBId/$campCId/$campDId/$copaId/$copaArgId/$campArgAId/$campArgBId/$campArgClausuraId/$campUruApertId/$campUruInterId/$campUruClausId/$campUruBId/$campUruBCompetId/$campPremierId/$campChampionshipId/$campLaLigaId/$campCopaIngId/$campSerieAId/$campAmistososId/$campChampionsId/$campEuropaId"
    }
    object Mercado         : Rota("mercado/{timeId}?aba={aba}") {
        fun comTimeId(id: Int) = "mercado/$id"
        fun comTimeIdEAba(id: Int, aba: Int) = "mercado/$id?aba=$aba"
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
    object CopaChaveamento : Rota("copa_chaveamento/{copaId}/{copaArgId}/{copaIngId}/{copaEspId}/{copaItaId}/{championsId}/{europaLeagueId}/{timeId}") {
        fun com(copaId: Int, copaArgId: Int, copaIngId: Int, copaEspId: Int, copaItaId: Int, championsId: Int, europaLeagueId: Int, timeId: Int) =
            "copa_chaveamento/$copaId/$copaArgId/$copaIngId/$copaEspId/$copaItaId/$championsId/$europaLeagueId/$timeId"
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
    object Rodada           : Rota("rodada/{campeonatoAId}/{campeonatoBId}/{campeonatoCId}/{campeonatoDId}/{campArgAId}/{campArgBId}/{campArgClausuraId}/{campUruApertId}/{campUruIntermedId}/{campUruClausId}/{campUruBId}/{campUruBCompetId}/{campPremierId}/{campChampionshipId}/{campLaLigaId}/{campSerieAId}/{campChampionsId}/{campEuropaId}") {
        fun com(campAId: Int, campBId: Int, campCId: Int, campDId: Int, campArgAId: Int, campArgBId: Int, campArgClausuraId: Int = -1,
                campUruApertId: Int = -1, campUruIntermedId: Int = -1, campUruClausId: Int = -1, campUruBId: Int = -1, campUruBCompetId: Int = -1,
                campPremierId: Int = -1, campChampionshipId: Int = -1, campLaLigaId: Int = -1, campSerieAId: Int = -1,
                campChampionsId: Int = -1, campEuropaId: Int = -1) =
            "rodada/$campAId/$campBId/$campCId/$campDId/$campArgAId/$campArgBId/$campArgClausuraId/$campUruApertId/$campUruIntermedId/$campUruClausId/$campUruBId/$campUruBCompetId/$campPremierId/$campChampionshipId/$campLaLigaId/$campSerieAId/$campChampionsId/$campEuropaId"
    }
    object Conquistas       : Rota("conquistas/{timeId}") {
        fun com(timeId: Int) = "conquistas/$timeId"
    }
    object HistoricoJogador : Rota("historico_jogador/{jogadorId}") {
        fun com(jogadorId: Int) = "historico_jogador/$jogadorId"
    }
    object RankingTecnicos  : Rota("ranking_tecnicos")
    object Metas            : Rota("metas")
    object Amistosos        : Rota("amistosos/{timeId}") {
        fun com(timeId: Int) = "amistosos/$timeId"
    }
    object HistoricoTecnico : Rota("historico_tecnico/{tecnicoId}") {
        fun com(tecnicoId: Int) = "historico_tecnico/$tecnicoId"
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

            // Quando o jogador aceita uma proposta de clube, timeIdJogador muda no DataStore.
            // Detectamos isso e navegamos para o dashboard do novo time.
            val novoTimeId = saveState?.timeIdJogador
            LaunchedEffect(novoTimeId) {
                if (novoTimeId != null && novoTimeId > 0 && novoTimeId != timeId) {
                    navController.navigate(Rota.Dashboard.comTimeId(novoTimeId)) {
                        popUpTo(Rota.Dashboard.comTimeId(timeId)) { inclusive = true }
                    }
                }
            }

            DashboardScreen(
                timeId = timeId,
                vm = dashVm,
                onIrParaEscalacao   = { navController.navigate(Rota.Escalacao.comTimeId(timeId)) },
                onIrParaPreJogo     = { campId, rod, advId ->
                    navController.navigate(Rota.Escalacao.preJogo(timeId, campId, rod, advId))
                },
                onIrParaMercado     = { navController.navigate(Rota.Mercado.comTimeId(timeId)) },
                onIrParaPropostas   = { navController.navigate(Rota.Mercado.comTimeIdEAba(timeId, 3)) },
                onIrParaTabela      = { navController.navigate(Rota.Tabela.com(
                    campAId            = saveState?.campeonatoAId ?: -1,
                    campBId            = saveState?.campeonatoBId ?: -1,
                    campCId            = saveState?.campeonatoCId ?: -1,
                    campDId            = saveState?.campeonatoDId ?: -1,
                    campArgAId         = saveState?.campeonatoArgAId ?: -1,
                    campArgBId         = saveState?.campeonatoArgBId ?: -1,
                    campArgClausuraId  = saveState?.campeonatoArgClausuraId ?: -1,
                    campUruAperturaId  = saveState?.campeonatoUruAperturaId ?: -1,
                    campUruBId         = saveState?.campeonatoUruBId ?: -1,
                    campUruClausuraId  = saveState?.campeonatoUruClausuraId ?: -1,
                    campUruIntermedId  = saveState?.campeonatoUruIntermedId ?: -1,
                    campUruBCompetId   = saveState?.campeonatoUruBCompetId ?: -1,
                    copaId             = saveState?.copaId ?: -1,
                    timeId             = timeId,
                    campPremierId      = saveState?.campeonatoPremierId ?: -1,
                    campChampionshipId = saveState?.campeonatoChampionshipId ?: -1,
                    campLaLigaId       = saveState?.campeonatoLaLigaId ?: -1,
                    campSerieAId       = saveState?.campeonatoSerieAId ?: -1,
                    campChampionsId    = saveState?.championsId ?: -1,
                    campEuropaId       = saveState?.europaLeagueId ?: -1
                )) },
                onIrParaArtilheiros = { navController.navigate(Rota.Artilheiros.com(
                    campAId           = saveState?.campeonatoAId ?: -1,
                    campBId           = saveState?.campeonatoBId ?: -1,
                    campCId           = saveState?.campeonatoCId ?: -1,
                    campDId           = saveState?.campeonatoDId ?: -1,
                    copaId            = saveState?.copaId ?: -1,
                    copaArgId         = saveState?.copaArgId ?: -1,
                    campArgAId        = saveState?.campeonatoArgAId ?: -1,
                    campArgBId        = saveState?.campeonatoArgBId ?: -1,
                    campArgClausuraId = saveState?.campeonatoArgClausuraId ?: -1,
                    campUruApertId    = saveState?.campeonatoUruAperturaId ?: -1,
                    campUruInterId    = saveState?.campeonatoUruIntermedId ?: -1,
                    campUruClausId    = saveState?.campeonatoUruClausuraId ?: -1,
                    campUruBId        = saveState?.campeonatoUruBId ?: -1,
                    campUruBCompetId  = saveState?.campeonatoUruBCompetId ?: -1,
                    campPremierId     = saveState?.campeonatoPremierId ?: -1,
                    campChampionshipId = saveState?.campeonatoChampionshipId ?: -1,
                    campLaLigaId      = saveState?.campeonatoLaLigaId ?: -1,
                    campCopaIngId     = saveState?.copaInglaterraId ?: -1,
                    campSerieAId      = saveState?.campeonatoSerieAId ?: -1,
                    campAmistososId   = saveState?.campeonatoAmistososId ?: -1,
                    campChampionsId   = saveState?.championsId ?: -1,
                    campEuropaId      = saveState?.europaLeagueId ?: -1
                )) },
                onIrParaFinancas    = { navController.navigate(Rota.Financas.comTimeId(timeId)) },
                onIrParaHallDaFama  = { navController.navigate(Rota.HallDaFama.caminho) },
                onIrParaConfronto   = { navController.navigate(Rota.Confronto.com(timeId)) },
                onIrParaCalendario  = { navController.navigate(Rota.Calendario.comTimeId(timeId)) },
                onIrParaCopaChaveamento = {
                    val copaId = saveState?.copaId ?: -1
                    val copaArgId = saveState?.copaArgId ?: -1
                    val copaIngId = saveState?.copaInglaterraId ?: -1
                    val copaEspId = saveState?.copaEspanholaId ?: -1
                    val copaItaId = saveState?.copaItalianaId ?: -1
                    val championsId = saveState?.championsId ?: -1
                    val europaLeagueId = saveState?.europaLeagueId ?: -1
                    if (copaId > 0 || copaArgId > 0 || copaIngId > 0 || copaEspId > 0 || copaItaId > 0 || championsId > 0 || europaLeagueId > 0)
                        navController.navigate(Rota.CopaChaveamento.com(copaId, copaArgId, copaIngId, copaEspId, copaItaId, championsId, europaLeagueId, timeId))
                },
                onIrParaRankingGeral = { navController.navigate(Rota.RankingGeral.caminho) },
                onIrParaEstatisticasTime = { navController.navigate(Rota.EstatisticasTime.com(timeId)) },
                onIrParaEstadio      = { navController.navigate(Rota.Estadio.com(timeId)) },
                onIrParaJuniores     = { navController.navigate(Rota.Juniores.comTimeId(timeId)) },
                onIrParaJogadores    = { navController.navigate(Rota.Jogadores.comTimeId(timeId)) },
                onIrParaRodada       = { navController.navigate(Rota.Rodada.com(
                    campAId           = saveState?.campeonatoAId ?: -1,
                    campBId           = saveState?.campeonatoBId ?: -1,
                    campCId           = saveState?.campeonatoCId ?: -1,
                    campDId           = saveState?.campeonatoDId ?: -1,
                    campArgAId        = saveState?.campeonatoArgAId ?: -1,
                    campArgBId        = saveState?.campeonatoArgBId ?: -1,
                    campArgClausuraId = saveState?.campeonatoArgClausuraId ?: -1,
                    campUruApertId    = saveState?.campeonatoUruAperturaId ?: -1,
                    campUruIntermedId = saveState?.campeonatoUruIntermedId ?: -1,
                    campUruClausId    = saveState?.campeonatoUruClausuraId ?: -1,
                    campUruBId        = saveState?.campeonatoUruBId ?: -1,
                    campUruBCompetId  = saveState?.campeonatoUruBCompetId ?: -1,
                    campPremierId     = saveState?.campeonatoPremierId ?: -1,
                    campChampionshipId = saveState?.campeonatoChampionshipId ?: -1,
                    campLaLigaId      = saveState?.campeonatoLaLigaId ?: -1,
                    campSerieAId      = saveState?.campeonatoSerieAId ?: -1,
                    campChampionsId   = saveState?.championsId ?: -1,
                    campEuropaId      = saveState?.europaLeagueId ?: -1
                )) },
                onIrParaClubes            = { navController.navigate(Rota.Clubes.comTimeId(timeId)) },
                onIrParaPatrocinadores    = { navController.navigate(Rota.Patrocinadores.caminho) },
                onIrParaTreinamento       = { navController.navigate(Rota.Treinamento.comTimeId(timeId)) },
                onIrParaConquistas        = { navController.navigate(Rota.Conquistas.com(timeId)) },
                onIrParaRankingTecnicos   = { navController.navigate(Rota.RankingTecnicos.caminho) },
                onIrParaMetas             = { navController.navigate(Rota.Metas.caminho) },
                onIrParaAmistosos         = { navController.navigate(Rota.Amistosos.com(timeId)) }
            )
        }

        // Tela de escalação (modo normal e modo pré-jogo)
        composable(
            route = Rota.Escalacao.caminho,
            arguments = listOf(
                navArgument("timeId")      { type = NavType.IntType },
                navArgument("campeonatoId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("rodada")       { type = NavType.IntType; defaultValue = -1 },
                navArgument("adversarioId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("amistoso")     { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStack ->
            val timeId       = backStack.arguments!!.getInt("timeId")
            val campeonatoId = backStack.arguments!!.getInt("campeonatoId")
            val rodada       = backStack.arguments!!.getInt("rodada")
            val adversarioId = backStack.arguments!!.getInt("adversarioId")
            val ehAmistoso   = backStack.arguments!!.getBoolean("amistoso")
            val modoPreJogo  = campeonatoId > 0 || ehAmistoso
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
                        if (ehAmistoso) {
                            dashVm?.iniciarAmistoso(adversarioId, ehCasa = rodada == 1)
                        } else {
                            dashVm?.simularProximaPartida(campeonatoId, rodada)
                        }
                        // Volta direto ao Dashboard (onde a simulação abre no overlay),
                        // pulando a AmistososScreen/tela anterior.
                        navController.popBackStack(Rota.Dashboard.comTimeId(timeId), inclusive = false)
                    },
                    onIrParaHistorico = { jogId -> navController.navigate(Rota.HistoricoJogador.com(jogId)) }
                )
            } else {
                EscalacaoScreen(
                    timeId = timeId,
                    onIrParaHistorico = { jogId -> navController.navigate(Rota.HistoricoJogador.com(jogId)) }
                )
            }
        }

        // Tabela de classificação
        composable(
            route = Rota.Tabela.caminho,
            arguments = listOf(
                navArgument("campeonatoId")     { type = NavType.IntType },
                navArgument("campeonatoBId")    { type = NavType.IntType },
                navArgument("campeonatoCId")    { type = NavType.IntType },
                navArgument("campeonatoDId")    { type = NavType.IntType },
                navArgument("campArgAId")       { type = NavType.IntType },
                navArgument("campArgBId")       { type = NavType.IntType },
                navArgument("campArgClausuraId") { type = NavType.IntType },
                navArgument("campUruAperturaId") { type = NavType.IntType },
                navArgument("campUruBId")        { type = NavType.IntType },
                navArgument("campUruClausuraId") { type = NavType.IntType },
                navArgument("campUruIntermedId") { type = NavType.IntType },
                navArgument("campUruBCompetId")  { type = NavType.IntType },
                navArgument("copaId")            { type = NavType.IntType; defaultValue = -1 },
                navArgument("timeJogadorId")    { type = NavType.IntType },
                navArgument("campPremierId")     { type = NavType.IntType; defaultValue = -1 },
                navArgument("campChampionshipId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("campLaLigaId")       { type = NavType.IntType; defaultValue = -1 },
                navArgument("campSerieAId")       { type = NavType.IntType; defaultValue = -1 },
                navArgument("campChampionsId")    { type = NavType.IntType; defaultValue = -1 },
                navArgument("campEuropaId")       { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStack ->
            val campeonatoId       = backStack.arguments!!.getInt("campeonatoId")
            val campeonatoBId      = backStack.arguments!!.getInt("campeonatoBId")
            val campeonatoCId      = backStack.arguments!!.getInt("campeonatoCId")
            val campeonatoDId      = backStack.arguments!!.getInt("campeonatoDId")
            val campArgAId         = backStack.arguments!!.getInt("campArgAId")
            val campArgBId         = backStack.arguments!!.getInt("campArgBId")
            val campArgClausuraId  = backStack.arguments!!.getInt("campArgClausuraId")
            val campUruAperturaId  = backStack.arguments!!.getInt("campUruAperturaId")
            val campUruBId         = backStack.arguments!!.getInt("campUruBId")
            val campUruClausuraId  = backStack.arguments!!.getInt("campUruClausuraId")
            val campUruIntermedId  = backStack.arguments!!.getInt("campUruIntermedId")
            val campUruBCompetId   = backStack.arguments!!.getInt("campUruBCompetId")
            val copaId             = backStack.arguments!!.getInt("copaId")
            val timeJogadorId      = backStack.arguments!!.getInt("timeJogadorId")
            val campPremierId      = backStack.arguments!!.getInt("campPremierId")
            val campChampionshipId = backStack.arguments!!.getInt("campChampionshipId")
            val campLaLigaId       = backStack.arguments!!.getInt("campLaLigaId")
            val campSerieAId       = backStack.arguments!!.getInt("campSerieAId")
            val campChampionsId    = backStack.arguments!!.getInt("campChampionsId")
            val campEuropaId      = backStack.arguments!!.getInt("campEuropaId")
            TabelaScreen(
                campeonatoAId           = campeonatoId,
                campeonatoBId           = campeonatoBId,
                campeonatoCId           = campeonatoCId,
                campeonatoDId           = campeonatoDId,
                campeonatoArgAId        = campArgAId,
                campeonatoArgBId        = campArgBId,
                campeonatoArgClausuraId = campArgClausuraId,
                campeonatoUruAperturaId = campUruAperturaId,
                campeonatoUruBId        = campUruBId,
                campeonatoUruClausuraId = campUruClausuraId,
                campeonatoUruIntermedId = campUruIntermedId,
                campeonatoUruBCompetId  = campUruBCompetId,
                copaId                  = copaId,
                timeJogadorId           = timeJogadorId,
                campeonatoPremierId     = campPremierId,
                campeonatoChampionshipId = campChampionshipId,
                campeonatoLaLigaId      = campLaLigaId,
                campeonatoSerieAId      = campSerieAId,
                campeonatoChampionsId   = campChampionsId,
                campeonatoEuropaId      = campEuropaId,
                onVoltar                = { navController.popBackStack() }
            )
        }

        // Artilharia & Assistências
        composable(
            route = Rota.Artilheiros.caminho,
            arguments = listOf(
                navArgument("campeonatoId")      { type = NavType.IntType },
                navArgument("campeonatoBId")     { type = NavType.IntType },
                navArgument("campeonatoCId")     { type = NavType.IntType },
                navArgument("campeonatoDId")     { type = NavType.IntType },
                navArgument("copaId")            { type = NavType.IntType },
                navArgument("copaArgId")         { type = NavType.IntType; defaultValue = -1 },
                navArgument("campArgAId")        { type = NavType.IntType },
                navArgument("campArgBId")        { type = NavType.IntType },
                navArgument("campArgClausuraId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("campUruApertId")    { type = NavType.IntType; defaultValue = -1 },
                navArgument("campUruInterId")    { type = NavType.IntType; defaultValue = -1 },
                navArgument("campUruClausId")    { type = NavType.IntType; defaultValue = -1 },
                navArgument("campUruBId")        { type = NavType.IntType; defaultValue = -1 },
                navArgument("campUruBCompetId")  { type = NavType.IntType; defaultValue = -1 },
                navArgument("campPremierId")     { type = NavType.IntType; defaultValue = -1 },
                navArgument("campChampionshipId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("campLaLigaId")       { type = NavType.IntType; defaultValue = -1 },
                navArgument("campCopaIngId")      { type = NavType.IntType; defaultValue = -1 },
                navArgument("campSerieAId")       { type = NavType.IntType; defaultValue = -1 },
                navArgument("campAmistososId")    { type = NavType.IntType; defaultValue = -1 },
                navArgument("campChampionsId")    { type = NavType.IntType; defaultValue = -1 },
                navArgument("campEuropaId")       { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStack ->
            val campeonatoId      = backStack.arguments!!.getInt("campeonatoId")
            val campeonatoBId     = backStack.arguments!!.getInt("campeonatoBId")
            val campeonatoCId     = backStack.arguments!!.getInt("campeonatoCId")
            val campeonatoDId     = backStack.arguments!!.getInt("campeonatoDId")
            val copaId            = backStack.arguments!!.getInt("copaId")
            val copaArgId         = backStack.arguments!!.getInt("copaArgId")
            val campArgAId        = backStack.arguments!!.getInt("campArgAId")
            val campArgBId        = backStack.arguments!!.getInt("campArgBId")
            val campArgClausuraId = backStack.arguments!!.getInt("campArgClausuraId")
            val campUruApertId    = backStack.arguments!!.getInt("campUruApertId")
            val campUruInterId    = backStack.arguments!!.getInt("campUruInterId")
            val campUruClausId    = backStack.arguments!!.getInt("campUruClausId")
            val campUruBId        = backStack.arguments!!.getInt("campUruBId")
            val campUruBCompetId  = backStack.arguments!!.getInt("campUruBCompetId")
            val campPremierId     = backStack.arguments!!.getInt("campPremierId")
            val campChampionshipId = backStack.arguments!!.getInt("campChampionshipId")
            val campLaLigaId      = backStack.arguments!!.getInt("campLaLigaId")
            val campCopaIngId     = backStack.arguments!!.getInt("campCopaIngId")
            val campSerieAId      = backStack.arguments!!.getInt("campSerieAId")
            val campAmistososId   = backStack.arguments!!.getInt("campAmistososId")
            val campChampionsId   = backStack.arguments!!.getInt("campChampionsId")
            val campEuropaId      = backStack.arguments!!.getInt("campEuropaId")
            ArtilheirosScreen(
                campeonatoAId           = campeonatoId,
                campeonatoBId           = campeonatoBId,
                campeonatoCId           = campeonatoCId,
                campeonatoDId           = campeonatoDId,
                copaId                  = copaId,
                copaArgId               = copaArgId,
                campeonatoArgAId        = campArgAId,
                campeonatoArgBId        = campArgBId,
                campeonatoArgClausuraId = campArgClausuraId,
                campeonatoUruAperturaId = campUruApertId,
                campeonatoUruIntermedId = campUruInterId,
                campeonatoUruClausuraId = campUruClausId,
                campeonatoUruBId        = campUruBId,
                campeonatoUruBCompetId  = campUruBCompetId,
                campeonatoPremierId     = campPremierId,
                campeonatoChampionshipId = campChampionshipId,
                campeonatoLaLigaId      = campLaLigaId,
                copaInglaterraId        = campCopaIngId,
                campeonatoSerieAId      = campSerieAId,
                campeonatoAmistososId   = campAmistososId,
                campeonatoChampionsId   = campChampionsId,
                campeonatoEuropaId      = campEuropaId,
                onVoltar                = { navController.popBackStack() }
            )
        }

        // Mercado de transferências
        composable(
            route = Rota.Mercado.caminho,
            arguments = listOf(
                navArgument("timeId") { type = NavType.IntType },
                navArgument("aba") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            val aba = backStack.arguments!!.getInt("aba")
            MercadoScreen(
                timeId = timeId,
                abaInicial = aba,
                onVoltar = { navController.popBackStack() },
                onIrParaClubes = { navController.navigate(Rota.Clubes.comTimeId(timeId)) },
                onIrParaHistorico = { jogId -> navController.navigate(Rota.HistoricoJogador.com(jogId)) }
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
                onVoltar = { navController.popBackStack() },
                onIrParaConquistas = { clubeId -> navController.navigate(Rota.Conquistas.com(clubeId)) },
                onIrParaHistorico = { jogId -> navController.navigate(Rota.HistoricoJogador.com(jogId)) }
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
                navArgument("copaArgId") { type = NavType.IntType },
                navArgument("copaIngId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("copaEspId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("copaItaId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("championsId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("europaLeagueId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("timeId") { type = NavType.IntType }
            )
        ) { backStack ->
            val copaId = backStack.arguments!!.getInt("copaId")
            val copaArgId = backStack.arguments!!.getInt("copaArgId")
            val copaIngId = backStack.arguments!!.getInt("copaIngId")
            val copaEspId = backStack.arguments!!.getInt("copaEspId")
            val copaItaId = backStack.arguments!!.getInt("copaItaId")
            val championsId = backStack.arguments!!.getInt("championsId")
            val europaLeagueId = backStack.arguments!!.getInt("europaLeagueId")
            val timeId = backStack.arguments!!.getInt("timeId")
            CopaChaveamentoScreen(copaId = copaId, copaArgId = copaArgId, copaInglaterraId = copaIngId, copaEspanholaId = copaEspId, copaItalianaId = copaItaId, championsId = championsId, europaLeagueId = europaLeagueId, timeJogadorId = timeId, onVoltar = { navController.popBackStack() })
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
            JogadoresScreen(
                timeId = timeId,
                onVoltar = { navController.popBackStack() },
                onIrParaHistorico = { jogId -> navController.navigate(Rota.HistoricoJogador.com(jogId)) }
            )
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
                navArgument("campeonatoAId")    { type = NavType.IntType },
                navArgument("campeonatoBId")    { type = NavType.IntType },
                navArgument("campeonatoCId")    { type = NavType.IntType },
                navArgument("campeonatoDId")    { type = NavType.IntType },
                navArgument("campArgAId")       { type = NavType.IntType },
                navArgument("campArgBId")       { type = NavType.IntType },
                navArgument("campArgClausuraId") { type = NavType.IntType },
                navArgument("campUruApertId")    { type = NavType.IntType },
                navArgument("campUruIntermedId") { type = NavType.IntType },
                navArgument("campUruClausId")    { type = NavType.IntType },
                navArgument("campUruBId")        { type = NavType.IntType },
                navArgument("campUruBCompetId")  { type = NavType.IntType },
                navArgument("campPremierId")     { type = NavType.IntType; defaultValue = -1 },
                navArgument("campChampionshipId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("campLaLigaId")       { type = NavType.IntType; defaultValue = -1 },
                navArgument("campSerieAId")       { type = NavType.IntType; defaultValue = -1 },
                navArgument("campChampionsId")    { type = NavType.IntType; defaultValue = -1 },
                navArgument("campEuropaId")       { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStack ->
            val campAId           = backStack.arguments!!.getInt("campeonatoAId")
            val campBId           = backStack.arguments!!.getInt("campeonatoBId")
            val campCId           = backStack.arguments!!.getInt("campeonatoCId")
            val campDId           = backStack.arguments!!.getInt("campeonatoDId")
            val campArgAId        = backStack.arguments!!.getInt("campArgAId")
            val campArgBId        = backStack.arguments!!.getInt("campArgBId")
            val campArgClausuraId = backStack.arguments!!.getInt("campArgClausuraId")
            val campUruApertId    = backStack.arguments!!.getInt("campUruApertId")
            val campUruIntermedId = backStack.arguments!!.getInt("campUruIntermedId")
            val campUruClausId    = backStack.arguments!!.getInt("campUruClausId")
            val campUruBId        = backStack.arguments!!.getInt("campUruBId")
            val campUruBCompetId  = backStack.arguments!!.getInt("campUruBCompetId")
            val campPremierId     = backStack.arguments!!.getInt("campPremierId")
            val campChampionshipId = backStack.arguments!!.getInt("campChampionshipId")
            val campLaLigaId      = backStack.arguments!!.getInt("campLaLigaId")
            val campSerieAId      = backStack.arguments!!.getInt("campSerieAId")
            val campChampionsId   = backStack.arguments!!.getInt("campChampionsId")
            val campEuropaId      = backStack.arguments!!.getInt("campEuropaId")
            RodadaScreen(
                campeonatoAId           = campAId,
                campeonatoBId           = campBId,
                campeonatoCId           = campCId,
                campeonatoDId           = campDId,
                campeonatoArgAId        = campArgAId,
                campeonatoArgBId        = campArgBId,
                campeonatoArgClausuraId = campArgClausuraId,
                campeonatoUruAperturaId = campUruApertId,
                campeonatoUruIntermedId = campUruIntermedId,
                campeonatoUruClausuraId = campUruClausId,
                campeonatoUruBId        = campUruBId,
                campeonatoUruBCompetId  = campUruBCompetId,
                campeonatoPremierId     = campPremierId,
                campeonatoChampionshipId = campChampionshipId,
                campeonatoLaLigaId      = campLaLigaId,
                campeonatoSerieAId      = campSerieAId,
                campeonatoChampionsId   = campChampionsId,
                campeonatoEuropaId      = campEuropaId,
                onVoltar                = { navController.popBackStack() }
            )
        }

        // Conquistas (sala de troféus do clube)
        composable(
            route = Rota.Conquistas.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            ConquistasScreen(
                timeId = timeId,
                onVoltar = { navController.popBackStack() }
            )
        }

        // Histórico de carreira do jogador
        composable(
            route = Rota.HistoricoJogador.caminho,
            arguments = listOf(navArgument("jogadorId") { type = NavType.IntType })
        ) { backStack ->
            val jogadorId = backStack.arguments!!.getInt("jogadorId")
            HistoricoJogadorScreen(
                jogadorId = jogadorId,
                onVoltar = { navController.popBackStack() }
            )
        }

        // Metas da temporada de cada time
        composable(route = Rota.Metas.caminho) {
            MetasScreen(onVoltar = { navController.popBackStack() })
        }

        // Amistosos de entressafra (compartilha a DashboardViewModel do time)
        composable(
            route = Rota.Amistosos.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            val dashboardEntry = remember(navController) {
                try { navController.getBackStackEntry(Rota.Dashboard.comTimeId(timeId)) }
                catch (e: Exception) { null }
            }
            val dashVm: DashboardViewModel? = dashboardEntry?.let { hiltViewModel(it) }
            if (dashVm != null) {
                AmistososScreen(
                    vm = dashVm,
                    onEscalar = { advId, ehCasa ->
                        navController.navigate(Rota.Escalacao.preAmistoso(timeId, advId, ehCasa))
                    },
                    onVoltar = { navController.popBackStack() }
                )
            }
        }

        // Ranking de Técnicos
        composable(route = Rota.RankingTecnicos.caminho) {
            RankingTecnicosScreen(
                onVoltar = { navController.popBackStack() },
                onAbrirHistorico = { tecnicoId ->
                    navController.navigate(Rota.HistoricoTecnico.com(tecnicoId))
                }
            )
        }

        // Histórico de carreira do técnico
        composable(
            route = Rota.HistoricoTecnico.caminho,
            arguments = listOf(navArgument("tecnicoId") { type = NavType.IntType })
        ) { backStack ->
            val tecnicoId = backStack.arguments!!.getInt("tecnicoId")
            HistoricoTecnicoScreen(
                tecnicoId = tecnicoId,
                onVoltar = { navController.popBackStack() }
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

