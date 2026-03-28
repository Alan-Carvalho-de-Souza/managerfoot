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
    object Escalacao       : Rota("escalacao/{timeId}") {
        fun comTimeId(id: Int) = "escalacao/$id"
    }
    object Tabela          : Rota("tabela/{campeonatoId}/{campeonatoBId}/{campeonatoCId}/{campeonatoDId}/{timeJogadorId}") {
        fun com(campAId: Int, campBId: Int, campCId: Int, campDId: Int, timeId: Int) =
            "tabela/$campAId/$campBId/$campCId/$campDId/$timeId"
    }
    object Artilheiros     : Rota("artilheiros/{campeonatoId}/{campeonatoBId}/{campeonatoCId}/{campeonatoDId}") {
        fun com(campAId: Int, campBId: Int, campCId: Int, campDId: Int) =
            "artilheiros/$campAId/$campBId/$campCId/$campDId"
    }
    object Mercado         : Rota("mercado/{timeId}") {
        fun comTimeId(id: Int) = "mercado/$id"
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
                    campDId = saveState?.campeonatoDId ?: -1
                )) },
                onIrParaFinancas    = { navController.navigate(Rota.Financas.comTimeId(timeId)) },
                onIrParaHallDaFama  = { navController.navigate(Rota.HallDaFama.caminho) },
                onIrParaConfronto   = { navController.navigate(Rota.Confronto.com(timeId)) },
                onIrParaCalendario  = { navController.navigate(Rota.Calendario.comTimeId(timeId)) }
            )
        }

        // Tela de escalação
        composable(
            route = Rota.Escalacao.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) { backStack ->
            val timeId = backStack.arguments!!.getInt("timeId")
            EscalacaoScreen(timeId = timeId)
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
                navArgument("campeonatoDId")  { type = NavType.IntType }
            )
        ) { backStack ->
            val campeonatoId  = backStack.arguments!!.getInt("campeonatoId")
            val campeonatoBId = backStack.arguments!!.getInt("campeonatoBId")
            val campeonatoCId = backStack.arguments!!.getInt("campeonatoCId")
            val campeonatoDId = backStack.arguments!!.getInt("campeonatoDId")
            ArtilheirosScreen(
                campeonatoAId = campeonatoId,
                campeonatoBId = campeonatoBId,
                campeonatoCId = campeonatoCId,
                campeonatoDId = campeonatoDId,
                onVoltar      = { navController.popBackStack() }
            )
        }

        // Mercado de transferências
        composable(
            route = Rota.Mercado.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) {
            PlaceholderScreen("Mercado de transferências — em breve")
        }

        // Finanças (placeholder — próxima fase)
        composable(
            route = Rota.Financas.caminho,
            arguments = listOf(navArgument("timeId") { type = NavType.IntType })
        ) {
            FinancasPlaceholderScreen()
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

@Composable
private fun FinancasPlaceholderScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Tela de Finanças — em breve")
    }
}
