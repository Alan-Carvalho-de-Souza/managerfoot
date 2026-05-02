"""Fix take(3) -> take(2) for Uruguay promotion/relegation and add novoUruBCompetId."""
import re

REPO = r'app/src/main/java/br/com/managerfoot/data/repository/GameRepository.kt'

with open(REPO, encoding='utf-8') as f:
    content = f.read()

# 1. Fix take(3) -> take(2) for rebaixadosUruA
content = content.replace(
    '// 3 últimos rebaixados para a Segunda Divisão\n'
    '        val rebaixadosUruA = tabelaAnualUru.entries.sortedBy { it.value }.take(3).map { it.key }\n'
    '        // 3 primeiros da Segunda División Uruguaia promovidos\n'
    '        val promovidosUruB = if (campeonatoUruBId > 0) {\n'
    '            classificacaoDao.buscarTabelaOrdenada(campeonatoUruBId)\n'
    '                .sortedWith(compareByDescending<ClassificacaoEntity> { it.pontos }\n'
    '                    .thenByDescending { it.vitorias }.thenByDescending { it.saldoGols })\n'
    '                .take(3).map { it.timeId }',
    '// 2 últimos rebaixados para a Segunda Divisão\n'
    '        val rebaixadosUruA = tabelaAnualUru.entries.sortedBy { it.value }.take(2).map { it.key }\n'
    '        // 2 primeiros da Segunda División Uruguaia promovidos (acesso direto)\n'
    '        val promovidosUruB = if (campeonatoUruBId > 0) {\n'
    '            classificacaoDao.buscarTabelaOrdenada(campeonatoUruBId)\n'
    '                .sortedWith(compareByDescending<ClassificacaoEntity> { it.pontos }\n'
    '                    .thenByDescending { it.vitorias }.thenByDescending { it.saldoGols })\n'
    '                .take(2).map { it.timeId }'
)

# 2. Add novoUruBCompetId after novoUruBId creation
content = content.replace(
    '                totalRodadas = (novosUruB.size - 1) * 2, pais = "Uruguay"), novosUruB) else -1\n'
    '\n'
    '        // Cria Copa do Brasil',
    '                totalRodadas = (novosUruB.size - 1) * 2, pais = "Uruguay"), novosUruB) else -1\n'
    '        val novoUruBCompetId = if (novosUruB.size >= 2) criarUruguaiSegundaDivCompetencia(novosUruB, novoTemporadaId, novoAno) else -1\n'
    '\n'
    '        // Cria Copa do Brasil'
)

with open(REPO, 'w', encoding='utf-8') as f:
    f.write(content)

print('Done')
