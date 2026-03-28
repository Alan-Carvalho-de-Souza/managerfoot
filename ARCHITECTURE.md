# ManagerFoot — Documento de Arquitetura

> **Versão**: 1.0  
> **Data**: 27 de Março de 2026  
> **Plataforma**: Android (minSdk 26 / targetSdk 35)

---

## Índice

1. [Visão Geral do Projeto](#1-visão-geral-do-projeto)
2. [Stack Tecnológica](#2-stack-tecnológica)
3. [Padrão Arquitetural](#3-padrão-arquitetural)
4. [Estrutura de Módulos](#4-estrutura-de-módulos)
5. [Camada de Dados](#5-camada-de-dados)
6. [Camada de Domínio](#6-camada-de-domínio)
7. [Camada de Apresentação](#7-camada-de-apresentação)
8. [Injeção de Dependência](#8-injeção-de-dependência)
9. [Persistência de Dados](#9-persistência-de-dados)
10. [Funcionalidades e Fluxos Principais](#10-funcionalidades-e-fluxos-principais)
11. [Diagrama de Dependências](#11-diagrama-de-dependências)

---

## 1. Visão Geral do Projeto

**ManagerFoot** é um jogo de simulação de gerenciamento de clubes de futebol para Android, temático ao futebol brasileiro. O jogador assume o papel de técnico/dirigente de um clube e é responsável por:

- Selecionar e gerenciar um clube do sistema de ligas brasileiro
- Montar e administrar o elenco de jogadores com atributos individuais
- Simular partidas com motor probabilístico baseado na força do elenco
- Gerenciar finanças do clube (receitas, despesas, salários, transferências)
- Disputar campeonatos (rodadas, tabela de classificação)
- Acompanhar a reputação do clube e o moral dos jogadores
- Progressão sazonal com desenvolvimento de jogadores

O jogo é **single-player e totalmente offline**, com persistência completa do estado via banco de dados local e preferências.

---

## 2. Stack Tecnológica

### Core

| Componente | Versão |
|-----------|--------|
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.0.21 |
| Java Target | 17 |
| minSdk | 26 (Android 8.0) |
| targetSdk | 35 (Android 15) |

### UI

| Biblioteca | Versão | Finalidade |
|-----------|--------|-----------|
| Jetpack Compose BOM | 2024.12.01 | Framework declarativo de UI |
| Material 3 | (via BOM) | Design system |
| Compose Navigation | 2.8.9 | Navegação entre telas |
| Material Icons Extended | (via BOM) | Ícones da interface |

### Persistência

| Biblioteca | Versão | Finalidade |
|-----------|--------|-----------|
| Room | 2.7.1 | ORM / banco de dados SQLite |
| DataStore Preferences | 1.1.3 | Armazenamento chave-valor (estado do jogo) |

### Assincronicidade

| Biblioteca | Versão | Finalidade |
|-----------|--------|-----------|
| Kotlin Coroutines | 1.10.1 | Operações assíncronas |
| Lifecycle ViewModel Compose | 2.9.0 | ViewModel com Compose |
| Lifecycle Runtime Compose | 2.9.0 | Estado do ciclo de vida |

### Injeção de Dependência

| Biblioteca | Versão | Finalidade |
|-----------|--------|-----------|
| Hilt | 2.55 | Injeção de dependência |
| Hilt Navigation Compose | 1.2.0 | Injeção em ViewModels de navegação |
| KSP | 2.0.21-1.0.27 | Geração de código (Room + Hilt) |

---

## 3. Padrão Arquitetural

O projeto segue **Clean Architecture** com separação clara em três camadas, combinada com o padrão **MVVM** (Model-View-ViewModel) na camada de apresentação e **StateFlow** para reatividade de UI.

```
┌──────────────────────────────────────────────────────┐
│                  PRESENTATION LAYER                   │
│   Screens (Compose) · ViewModels · Navigation        │
├──────────────────────────────────────────────────────┤
│                   DOMAIN LAYER                        │
│   Domain Models · Game Engine · Business Logic       │
├──────────────────────────────────────────────────────┤
│                    DATA LAYER                         │
│   Room Database · DAOs · Repositories · DataStore    │
└──────────────────────────────────────────────────────┘
```

### Princípios aplicados

- **Separação de responsabilidades**: UI não conhece detalhes de banco de dados; entidades Room não chegam à UI.
- **Fluxo unidirecional de dados (UDF)**: UI emite eventos → ViewModel processa → estado é exposto via `StateFlow` → UI recompõe.
- **Inversão de dependência**: ViewModels dependem de Repositórios (interfaces/domínio), nunca de DAOs diretamente.
- **Modelos de domínio desacoplados**: Entidades Room são mapeadas para `DomainModels` antes de chegarem à UI.

---

## 4. Estrutura de Módulos

O projeto é **single-module** com organização por camadas:

```
app/src/main/java/br/com/managerfoot/
│
├── MainActivity.kt                  # Entry point Activity (@AndroidEntryPoint)
├── ManagerFootApp.kt                # Application class (@HiltAndroidApp)
│
├── di/
│   └── AppModules.kt               # Módulos Hilt (Database, Repositories)
│
├── data/                           # Camada de Dados
│   ├── database/
│   │   └── AppDatabase.kt          # Room Database (v1, managerfoot.db)
│   ├── dao/                        # Data Access Objects
│   │   ├── TimeDao.kt
│   │   ├── JogadorDao.kt
│   │   ├── PartidaDao.kt
│   │   ├── CampeonatoDao.kt
│   │   ├── ClassificacaoDao.kt
│   │   └── FinancaDao.kt
│   ├── entity/                     # Entidades Room
│   ├── repository/                 # Repositórios
│   │   ├── TimeRepository.kt
│   │   ├── JogadorRepository.kt
│   │   └── GameRepository.kt
│   └── datasource/
│       └── SeedDataSource.kt       # Carregamento inicial de dados (JSON)
│
├── domain/                         # Camada de Domínio
│   ├── model/
│   │   └── DomainModels.kt         # Modelos de negócio desacoplados
│   └── engine/
│       ├── CalculadoraForca.kt     # Cálculo de probabilidade de vitória
│       ├── SimuladorPartida.kt     # Motor de simulação de partidas
│       ├── MotorCampeonato.kt      # Motor de campeonato e calendário
│       └── IATimeRival.kt          # IA para times adversários
│
└── presentation/                   # Camada de Apresentação
    ├── navigation/
    │   └── Navigation.kt           # Grafo de navegação Compose
    ├── viewmodel/
    │   └── ViewModels.kt           # InicioVM, DashboardVM, EscalacaoVM, MercadoVM
    └── ui/
        ├── screens/                # Telas individuais
        ├── components/
        │   └── Components.kt       # Componentes reutilizáveis
        └── theme/
            └── Theme.kt            # ManagerFootTheme (Material 3)

app/src/main/assets/
└── seed_brasileirao.json           # Dados iniciais: 20 clubes + elencos
```

---

## 5. Camada de Dados

### 5.1 Banco de Dados Room (`AppDatabase`)

- **Arquivo**: `managerfoot.db` (SQLite)
- **Versão**: 1
- **Estratégia de migração**: Destrutiva (fallback — recria o banco em caso de incompatibilidade de versão)

### 5.2 Entidades (Tabelas)

| Entidade | Tabela | Descrição |
|---------|--------|-----------|
| `TimeEntity` | `times` | Clubes com nome, cidade, estado, estádio, finanças, reputação |
| `JogadorEntity` | `jogadores` | Jogadores com posição, atributos (força, técnica, velocidade, defesa…), contrato, salário |
| `CampeonatoEntity` | `campeonatos` | Campeonatos com tipo, formato, rodada atual |
| `TemporadaEntity` | `temporadas` | Temporadas (anos) |
| `PartidaEntity` | `partidas` | Partidas: times, placar, rodada, status (jogado/não jogado) |
| `EscalacaoEntity` | `escalacoes` | Escalações — atribuição de jogadores a posições por partida |
| `EventoPartidaEntity` | `eventos_partida` | Eventos por minuto (gol, cartão, lesão) |
| `ClassificacaoEntity` | `classificacoes` | Tabela de classificação (pontos, V/E/D, saldo de gols) |
| `FinancaEntity` | `financas` | Registros financeiros mensais (receitas, despesas) |
| `CampeonatoTimeEntity` | `campeonato_times` | Tabela de junção: campeonatos ↔ times |
| `TransferenciaEntity` | `transferencias` | Histórico de transferências |

### 5.3 Enumerações e Type Converters

| Enum | Valores |
|------|---------|
| `Posicao` | GOLEIRO, ZAGUEIRO, LATERAL_DIREITO, LATERAL_ESQUERDO, VOLANTE, MEIA_CENTRAL, MEIA_ATACANTE, PONTA_DIREITA, PONTA_ESQUERDA, CENTROAVANTE, SEGUNDA_ATACANTE |
| `Setor` | GOLEIRO, DEFESA, MEIO, ATAQUE |
| `EstiloJogo` | OFENSIVO, EQUILIBRADO, DEFENSIVO, CONTRA_ATAQUE |
| `MoraleEstado` | EXCELENTE, BOM, NORMAL, INSATISFEITO, REVOLTADO |
| `TipoCampeonato` | NACIONAL_DIVISAO1–4, ESTADUAL, COPA_NACIONAL, CONTINENTAL, MUNDIAL_CLUBES, SELECOES |
| `FormatoCampeonato` | PONTOS_CORRIDOS, GRUPOS_E_MATA_MATA, MATA_MATA_SIMPLES, MATA_MATA_IDA_VOLTA |
| `TipoEvento` | GOL, GOL_CONTRA, PENALTI_CONVERTIDO, PENALTI_PERDIDO, CARTAO_AMARELO, CARTAO_VERMELHO, LESAO |

### 5.4 DAOs

All DAOs expõem `Flow<>` para subscrição reativa pela UI:

| DAO | Responsabilidades |
|-----|------------------|
| `TimeDao` | CRUD de times, consultas por divisão, crédito/débito financeiro, reputação |
| `JogadorDao` | Gerência de elenco, agentes livres, atualização de atributos e contratos |
| `PartidaDao` | CRUD de partidas, busca por rodada, registro de resultado e eventos |
| `CampeonatoDao` | Gerência de campeonatos, avanço de rodada, participantes |
| `ClassificacaoDao` | Consultas de tabela, atualização de estatísticas após partidas |
| `FinancaDao` | Registros financeiros, fechamento mensal, totais por temporada |

### 5.5 Repositórios

#### `TimeRepository`
- Gerencia CRUD de times e operações financeiras
- Mapeia `TimeEntity` → `Time` (modelo de domínio)
- Operações: crédito/débito, expansão de estádio, reputação

#### `JogadorRepository`
- CRUD de jogadores e gerência de elenco
- Transferências, consultas de mercado, gestão de contratos
- Atributos: `força`, `técnica`, `passe`, `velocidade`, `finalização`, `defesa`, `físico`
- Estado de moral impacta a `forcaEfetiva()` do jogador

#### `GameRepository` (`@Singleton`)
- **Orquestrador central** do loop de jogo
- Criação de campeonatos e calendário de partidas
- Coordenação da simulação de partidas (chama o motor + persiste resultados)
- Fechamento mensal (contabilidade, salários, receitas)
- Processamento de fim de temporada (progressão/regressão de jogadores)

---

## 6. Camada de Domínio

### 6.1 Modelos de Domínio (`DomainModels.kt`)

Modelos **desacoplados** das entidades Room, representando conceitos de negócio:

| Modelo | Descrição |
|--------|-----------|
| `Time` | Clube com atributos (nome, divisão, saldo, reputação, estilo, formação) |
| `Jogador` | Jogador com stats e termos contratuais. Método `forcaEfetiva()` aplica penalidade de posição e moral |
| `Escalacao` | Escalação: 11 titulares + até 7 reservas |
| `JogadorNaEscalacao` | Jogador atribuído a uma posição numa partida específica |
| `ResultadoPartida` | Resultado completo (placar, eventos, estatísticas) |
| `EventoSimulado` | Evento individual com minuto e descrição |
| `EstatisticasTime` | Défense/ataque stats: finalizações, posse %, faltas, cartões |
| `OfertaTransferencia` | Proposta de transferência com valor e salário |
| `SaldoFinanceiro` | Status financeiro: saldo, folha mensal, receita estimada |

**Comportamento-chave**:
```kotlin
// Penalidade por posição errada + estado de moral
fun Jogador.forcaEfetiva(): Int {
    val penalidade = if (posicaoErrada) 0.8f else 1.0f
    val fatorMoral = when (moral) {
        MoraleEstado.EXCELENTE -> 1.05f
        MoraleEstado.INSATISFEITO -> 0.92f
        MoraleEstado.REVOLTADO -> 0.85f
        else -> 1.0f
    }
    return (forca * penalidade * fatorMoral).toInt()
}
```

### 6.2 Motor de Jogo (`domain/engine/`)

#### `CalculadoraForca`
Calcula a probabilidade de vitória a partir da força do elenco escalado:

| Fator | Peso/Multiplicador |
|-------|-------------------|
| Goleiro | 15% |
| Defesa | 30% |
| Meio-campo | 30% |
| Ataque | 25% |
| Mando de campo | ×1,08 para o mandante |
| Cansaço | Redução proporcional a partidas recentes |
| Estilo de jogo | Fator de confronto (ex.: Contra-Ataque vs. Ofensivo) |

#### `SimuladorPartida`
Gera resultados realistas de partidas:

- Calcula razão de força entre os times → probabilidade de vitória
- **Distribuição de Poisson** para geração de gols (agrupamento realista)
- Média de gols: `2,7 por jogo` (média histórica do Brasileirão)
- Gera eventos aleatórios por minuto
- Deriva estatísticas: posse %, finalizações, precisão

```kotlin
const val MEDIA_GOLS_JOGO     = 2.7
const val PROB_CARTAO_AMARELO = 0.12  // por partida/time
const val PROB_LESAO          = 0.03  // por partida/time
```

#### `MotorCampeonato`
- Geração de calendário round-robin (turno + returno)
- Cálculo de pontuação: V×3 + E×1
- Delta updates de pontos/gols após cada partida
- Determinação de desfechos sazonais (promoção/rebaixamento — planejado)

#### `IATimeRival`
- Escalação automática: seleciona melhores jogadores por força/setor
- Parsing de formação: `"4-4-2"` → [1 GOL, 4 DEF, 4 MEI, 2 ATA]
- Decisões de transferência: aquisições dentro do orçamento visando lacunas do elenco
- Detecção de carência: identifica posições sub-estaffadas

---

## 7. Camada de Apresentação

### 7.1 Navegação

**Tipo**: Jetpack Compose Navigation com rotas tipadas (sealed class)

```kotlin
sealed class Rota(val caminho: String) {
    object Inicio          : Rota("inicio")           // Iniciar/Continuar jogo
    object SelecionarTime  : Rota("selecionar_time")  // Seleção de clube (placeholder)
    object Dashboard       : Rota("dashboard/{timeId}")
    object Escalacao       : Rota("escalacao/{timeId}/{partidaId}")
    object Tabela          : Rota("tabela/{campeonatoId}")
    object Mercado         : Rota("mercado/{timeId}")
    object Financas        : Rota("financas/{timeId}")
}
```

**Grafo único**: `NavHost` em `MainActivity` com todas as rotas composable, passagem de argumentos via `NavBackStackEntry`.

### 7.2 ViewModels

Todos usam `@HiltViewModel` e expõem `StateFlow` para gerência de estado reativo:

#### `InicioViewModel`
| Estado | Tipo |
|--------|------|
| `saveState` | `GameSaveState?` (lido do DataStore) |
| `timesDisponiveis` | `List<Time>` |
| `uiState` | `InicioUiState` |

```kotlin
sealed class InicioUiState {
    object Carregando                          : InicioUiState()
    object SemSave                             : InicioUiState()
    object TemSave                             : InicioUiState()
    data class JogoIniciado(val timeId: Int)   : InicioUiState()
    data class Erro(val mensagem: String)      : InicioUiState()
}
```

**Ações**: `iniciarNovoJogo(timeId)`, `continuarJogo()`

#### `DashboardViewModel`
| Estado | Tipo |
|--------|------|
| `timeJogador` | `Time?` |
| `todosOsTimes` | `List<Time>` |
| `proximaPartida` | `PartidaEntity?` |
| `ultimosResultados` | `List<ResultadoPartida>` |
| `resultadoSimulado` | `ResultadoPartida?` |
| `uiState` | `DashboardUiState` |

**Ações**: `carregar(timeId)`, `simularProximaPartida()`, `fecharMes()`, `fecharSimulacao()`

#### `EscalacaoViewModel`
- Gerencia escalação para a próxima partida
- Ações: `AddTitular`, `RemoveTitular`, `SwapPosition`

#### `MercadoViewModel`
- Operações do mercado de transferências
- Lista jogadores disponíveis, propõe/recebe ofertas

### 7.3 Telas (Screens)

#### `InicioScreen`
- Botões "Novo Jogo" / "Continuar"
- Seleção de clube (caso não haja save)
- Tratamento de estados: carregando, erro

#### `DashboardScreen`
- Card de cabeçalho do time (saldo, reputação)
- Preview da próxima partida + botão "Simular"
- Feed de últimos resultados
- Botões de ação: Tabela, Mercado, Finanças, Avançar Mês

#### `EscalacaoScreen`
- Interface com abas: Titulares | Reservas | Elenco completo
- Informações de formação e média de força do elenco
- Atribuição de jogadores a posições

#### `PartidaSimulacaoScreen`
- Visualização em tempo real da simulação
- Barra de progresso / relógio (0–90 min + acréscimos)
- Placar ao vivo e feed de eventos com animações
- Resumo final com estatísticas

#### Telas Placeholder
- **`TabelaScreen`**: Classificação do campeonato
- **`MercadoScreen`**: Mercado de transferências
- **`FinancasScreen`**: Finanças do clube

### 7.4 Componentes Reutilizáveis (`Components.kt`)

| Componente | Finalidade |
|-----------|-----------|
| `TimeHeaderCard` | Nome, saldo, reputação, divisão |
| `JogadorRow` | Item de lista de jogador com badge de força |
| `ForcaBadge` | Indicador visual de força (1–99) |
| `InfoChip` | Badge chave-valor |
| `ResultadoCard` | Resultado de partida com placar |
| `TimeItemRow` | Seletor de time clicável |
| `SecaoHeader` | Título de seção |
| `EmptyState` | Placeholder "sem dados" |

**Utilitário**: `formatarSaldo(Long): String` — formatação monetária em BRL.

### 7.5 Tema (`Theme.kt`)

- `ManagerFootTheme`: Material 3 com cores dinâmicas no Android 12+
- Paleta: base roxo (modo claro e escuro)
- Tipografia: padrões Material 3

---

## 8. Injeção de Dependência

### Setup

```
@HiltAndroidApp    ←  ManagerFootApp
@AndroidEntryPoint ←  MainActivity
@HiltViewModel     ←  Todos os ViewModels
@Inject constructor ← Repositórios (@Singleton)
```

### `AppModules.kt`

#### `DatabaseModule`
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase

    @Provides fun provideTimeDao(db: AppDatabase): TimeDao
    @Provides fun provideJogadorDao(db: AppDatabase): JogadorDao
    @Provides fun providePartidaDao(db: AppDatabase): PartidaDao
    @Provides fun provideCampeonatoDao(db: AppDatabase): CampeonatoDao
    @Provides fun provideClassificacaoDao(db: AppDatabase): ClassificacaoDao
    @Provides fun provideFinancaDao(db: AppDatabase): FinancaDao
}
```

#### `RepositoryModule`
Repositórios com `@Inject constructor` são automaticamente resolvidos pelo Hilt como `@Singleton`.

---

## 9. Persistência de Dados

### 9.1 Room (SQLite)

- Chaves estrangeiras com `CASCADE DELETE`
- Índices nas colunas de FK para performance de queries
- Type converters: enums ↔ strings

### 9.2 DataStore Preferences (`GameDataStore`)

Armazena o estado persistente da sessão de jogo:

| Chave | Tipo | Descrição |
|-------|------|-----------|
| `timeIdJogador` | `Int` | ID do time gerenciado |
| `temporadaId` | `Int` | ID da temporada atual |
| `anoAtual` | `Int` | Ano corrente no jogo |
| `mesAtual` | `Int` | Mês corrente (1–12) |
| `jogoInicializado` | `Boolean` | Flag de jogo inicializado |

**Operações**:
- `salvarNovoJogo(timeId, temporadaId, ano)` — inicialização de novo jogo
- `avancarMes()` — progressão mensal
- `resetar()` — limpar todos os dados de save

### 9.3 Dados Iniciais (Seeding)

**Arquivo**: `assets/seed_brasileirao.json`  
**Classe**: `SeedDataSource.kt`

- 20 clubes brasileiros com atributos completos (cidade, estádio, capacidade, nível)
- Elencos iniciais com atributos de jogadores por posição
- Inserção no Room na primeira inicialização de jogo (`iniciarNovoJogo`)

---

## 10. Funcionalidades e Fluxos Principais

### Fluxo: Inicialização do Jogo

```
App Launch
    └─► InicioScreen
          ├─[Sem Save]─► Seleção de clube
          │                └─► iniciarNovoJogo(timeId)
          │                      ├─ Carrega seed_brasileirao.json
          │                      ├─ Insere Times + Jogadores no Room
          │                      ├─ Cria CampeonatoEntity (38 rodadas)
          │                      ├─ Gera calendário (MotorCampeonato)
          │                      ├─ Salva estado no DataStore
          │                      └─► Dashboard
          └─[Com Save]──► continuarJogo()
                            └─► Dashboard (com timeId do DataStore)
```

### Fluxo: Simulação de Partida

```
Dashboard
    └─► "Simular Próxima Partida"
          └─► DashboardViewModel.simularProximaPartida()
                ├─ IATimeRival gera escalação adversária
                ├─ Escalação do jogador (manual ou automática)
                ├─ CalculadoraForca → probabilidade de vitória
                ├─ SimuladorPartida → ResultadoPartida
                │     ├─ Distribuição Poisson → gols
                │     ├─ Eventos minuto a minuto (gols, cartões, lesões)
                │     └─ Estatísticas (posse, finalizações)
                ├─ Persiste resultado (PartidaEntity atualizada)
                ├─ Atualiza ClassificacaoEntity (delta de pontos/gols)
                └─► PartidaSimulacaoScreen (animação em tempo real)
```

### Fluxo: Fechamento de Mês

```
Dashboard
    └─► "Avançar Mês"
          └─► GameRepository.fecharMes()
                ├─ Calcula receitas (bilheteria, TV, patrocínios)
                ├─ Debita folha de pagamento (salários × jogadores)
                ├─ Gera FinancaEntity para o mês
                ├─ DataStore.avancarMes()
                └─[Mês 12]─► Processamento de Fim de Temporada
                                ├─ Incrementa idade dos jogadores
                                ├─ Progressão jovens (+1 força até 25 anos)
                                ├─ Declínio veteranos (−1 força após 30 anos)
                                ├─ Contratos expirados → mercado
                                └─ Nova temporada criada
```

### Fluxo: Transferências

```
MercadoScreen
    ├─► Consulta JogadorRepository (agentes livres)
    ├─► OfertaTransferencia (valor + salário proposto)
    ├─► JogadorRepository.transferir(jogadorId, timeId, valor)
    │     ├─ TimeRepository.debitar(timeId, valor) — time comprador
    │     ├─ TimeRepository.creditar(timeIdOrigem, valor) — time vendedor
    │     ├─ JogadorEntity.timeId atualizado
    │     └─ TransferenciaEntity registrada
    └─► Elenco atualizado
```

---

## 11. Diagrama de Dependências

```
                ┌─────────────────────────────┐
                │         MainActivity         │
                │    (@AndroidEntryPoint)      │
                └──────────────┬──────────────┘
                               │ hosts
                ┌──────────────▼──────────────┐
                │        NavHost (Compose)     │
                │  Inicio · Dashboard · Esc.  │
                │  Tabela · Mercado · Finanças │
                └──┬────────┬────────┬────────┘
         observa   │        │        │  observa
         StateFlow │        │        │ StateFlow
           ┌───────▼──┐ ┌───▼────┐ ┌─▼──────────┐
           │Inicio    │ │Dashbd  │ │Escalacao   │
           │ViewModel │ │ViewMod.│ │ViewModel   │
           └─────┬────┘ └───┬───┘ └─────┬──────┘
  @HiltViewModel │          │           │
                 │ inject   │ inject    │ inject
           ┌─────▼──────────▼───────────▼──────┐
           │         Repositories               │
           │  TimeRepo · JogadorRepo · GameRepo │
           └──────────────┬────────────────────┘
                          │ usa
            ┌─────────────┼──────────────┐
            │             │              │
     ┌──────▼─────┐ ┌─────▼────┐ ┌──────▼──────┐
     │   Room DB  │ │DataStore │ │ Game Engine  │
     │ (DAOs/     │ │(GameSave │ │CalcForca    │
     │  Entities) │ │ State)   │ │SimulPartida │
     └────────────┘ └──────────┘ │MotorCamp.  │
                                 │IATimeRival │
                                 └────────────┘
```

---

*Documento gerado automaticamente via análise estática do código-fonte do projeto ManagerFoot.*
