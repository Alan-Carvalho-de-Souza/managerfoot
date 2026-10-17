package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Lançamentos de pontos do Ranking Nacional de Clubes da CBF (ver ranking_brasileiro.md).
//
// Tabela própria, e não uma extensão de ranking_conmebol, porque os dois regimes de
// pontuação não compartilham nada além da ideia de "um lançamento por ano": o RNC
// usa pesos inteiros 5/4/3/2/1 numa janela de 5 anos, enquanto o CONMEBOL deprecia
// 10% ao ano numa janela de 10, com lançamentos históricos que não decaem. Juntar
// os dois numa tabela exigiria uma coluna de regime e um CASE em cada consulta, e
// a decomposição exibida na UI também é diferente (liga/copa aqui, continental/
// liga/histórico lá). Separadas, cada regra evolui sem tocar na outra.
//
// Uma linha por clube/ano/competição. Como o clube disputa uma única série por ano,
// um ano rende no máximo dois lançamentos: a liga e a Copa do Brasil.
// Sem foreign key para TimeEntity: os lançamentos são históricos e sobrevivem ao clube.
@Entity(
    tableName = "ranking_brasileiro",
    indices = [Index("timeId"), Index("ano")]
)
data class RankingBrasileiroEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timeId: Int,
    // Ano da temporada avaliada. Determina o peso (5/4/3/2/1) na leitura.
    val ano: Int,
    // Nome do enum CompeticaoRnc.
    val competicao: String,
    // Como os pontos foram obtidos ("3º colocado", "Semifinal", "Campeão"...).
    val motivo: String = "",
    // Pontos brutos da temporada, antes do peso do ano. Inteiros por definição:
    // a escala do RNC arredonda a pontuação de cada colocação/fase.
    val pontos: Int = 0
)
