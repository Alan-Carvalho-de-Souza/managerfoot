# -*- coding: utf-8 -*-
"""
gen_outros.py
Gera seed_outros.json com clubes do pool "Outros times" — clubes que NÃO
disputam uma liga nacional modelada no jogo, mas existem com sua nacionalidade
e reputação (para, futuramente, disputar torneios intercontinentais).

Códigos de divisão reservados para o pool (fora de todas as ligas nacionais,
que usam 1..14):
    90 = Outros — América do Sul (CONMEBOL)
    91 = Outros — Europa (UEFA)
    92 = Outros — resto do mundo (CONCACAF / Ásia / África / Oceania)

Mesmo pipeline dos demais gen_*: converte o "overall" nos 7 atributos do jogo.
Faixas de id reservadas: times 601+, jogadores 13000+.

Uso: python scripts/gen_outros.py
"""
import json
import os
import random

# Divisao do pool sul-americano. Clube desta divisao sem elenco escrito a mao
# recebe um elenco gerado — ver gen_roster_sintetico.
DIVISAO_POOL_SUL = 90

# ─────────────────── Metadados dos clubes ─────────────────────────────────────
# (id, nome, cidade, estado, divisao, pais, nivel, saldo(centavos), estadio, cap,
#  preco(centavos), tatica, estilo, reputacao)
TIMES = [
    (601, "Inter Miami", "Miami", "FL", 92, "Estados Unidos", 7, 40_000_000_00,
     "Chase Stadium", 21550, 5000, "4-3-3", "OFENSIVO", 72),

    # ── Pool europeu (divisao 91) — base da UEFA Champions League ─────────────
    # As reputacoes ordenam os potes do sorteio (ver regras_championsleague.md) e
    # alimentam o coeficiente historico. Elas convivem com as das ligas jogaveis:
    # Premier (401-420), La Liga (501-520) e Serie A (521-540). Ao mexer nelas,
    # rode SeedChampionsTest para conferir que nenhum pote concentra 5 clubes de
    # um mesmo pais (o limite por pote e 4).

    # Alemanha — 4 vagas diretas
    (602, "Bayern de Munique", "Munique", "BAY", 91, "Alemanha", 10, 140_000_000_00,
     "Allianz Arena", 75024, 8500, "4-2-3-1", "OFENSIVO", 94),
    (603, "Bayer Leverkusen", "Leverkusen", "NRW", 91, "Alemanha", 9, 70_000_000_00,
     "BayArena", 30210, 7000, "3-4-3", "OFENSIVO", 87),
    (604, "Borussia Dortmund", "Dortmund", "NRW", 91, "Alemanha", 9, 75_000_000_00,
     "Signal Iduna Park", 81365, 7000, "4-2-3-1", "OFENSIVO", 86),
    (605, "RB Leipzig", "Leipzig", "SAX", 91, "Alemanha", 8, 60_000_000_00,
     "Red Bull Arena", 47069, 6500, "4-2-3-1", "OFENSIVO", 83),

    # Franca — 3 vagas diretas
    (606, "Paris Saint-Germain", "Paris", "IDF", 91, "Franca", 10, 130_000_000_00,
     "Parc des Princes", 47929, 8500, "4-3-3", "OFENSIVO", 93),
    (607, "Olympique de Marseille", "Marselha", "PAC", 91, "Franca", 8, 50_000_000_00,
     "Stade Velodrome", 67394, 6500, "4-2-3-1", "OFENSIVO", 81),
    (608, "AS Monaco", "Monaco", "MON", 91, "Franca", 8, 48_000_000_00,
     "Stade Louis II", 18523, 6500, "4-2-3-1", "EQUILIBRADO", 80),

    # Holanda — 2 vagas diretas
    (609, "PSV Eindhoven", "Eindhoven", "NBR", 91, "Holanda", 8, 42_000_000_00,
     "Philips Stadion", 35000, 6000, "4-3-3", "OFENSIVO", 79),
    (610, "Ajax", "Amsterda", "NHL", 91, "Holanda", 8, 40_000_000_00,
     "Johan Cruijff Arena", 54990, 6000, "4-3-3", "OFENSIVO", 81),

    # Belgica, Tchequia, Turquia, Portugal, Ucrania — 1 vaga direta cada
    (611, "Club Brugge", "Bruges", "WVL", 91, "Belgica", 7, 30_000_000_00,
     "Jan Breydelstadion", 29042, 5500, "4-3-3", "EQUILIBRADO", 76),
    (612, "Slavia Praga", "Praga", "PRG", 91, "Tchequia", 7, 20_000_000_00,
     "Fortuna Arena", 19370, 4500, "4-2-3-1", "EQUILIBRADO", 73),
    (613, "Galatasaray", "Istambul", "IST", 91, "Turquia", 8, 45_000_000_00,
     "Rams Park", 52223, 6000, "4-2-3-1", "OFENSIVO", 80),
    (614, "Sporting CP", "Lisboa", "LIS", 91, "Portugal", 9, 55_000_000_00,
     "Estadio Jose Alvalade", 50095, 6000, "3-4-3", "OFENSIVO", 84),
    (615, "Shakhtar Donetsk", "Donetsk", "DON", 91, "Ucrania", 7, 22_000_000_00,
     "Arena Lviv", 34915, 4000, "4-2-3-1", "OFENSIVO", 74),

    # Associacoes menores — disputam as 8 vagas da qualificacao
    (616, "Celtic", "Glasgow", "GLA", 91, "Escocia", 7, 30_000_000_00,
     "Celtic Park", 60411, 5000, "4-3-3", "OFENSIVO", 76),
    (617, "Rangers", "Glasgow", "GLA", 91, "Escocia", 7, 24_000_000_00,
     "Ibrox Stadium", 50817, 5000, "4-3-3", "EQUILIBRADO", 72),
    (618, "Olympiacos", "Pireu", "ATT", 91, "Grecia", 7, 26_000_000_00,
     "Karaiskakis", 32115, 4500, "4-2-3-1", "EQUILIBRADO", 75),
    (619, "PAOK", "Salonica", "CMC", 91, "Grecia", 7, 20_000_000_00,
     "Toumba", 28701, 4200, "4-2-3-1", "EQUILIBRADO", 71),
    (620, "Red Bull Salzburg", "Salzburgo", "SBG", 91, "Austria", 7, 22_000_000_00,
     "Red Bull Arena Salzburg", 30188, 4500, "4-4-2", "OFENSIVO", 73),
    (621, "Sturm Graz", "Graz", "STY", 91, "Austria", 6, 14_000_000_00,
     "Merkur Arena", 16364, 3800, "4-4-2", "EQUILIBRADO", 68),
    (622, "Young Boys", "Berna", "BER", 91, "Suica", 7, 18_000_000_00,
     "Wankdorf", 31783, 4200, "4-4-2", "EQUILIBRADO", 70),
    (623, "Basel", "Basileia", "BSL", 91, "Suica", 6, 16_000_000_00,
     "St. Jakob-Park", 38512, 4200, "4-2-3-1", "EQUILIBRADO", 69),
    (624, "Copenhague", "Copenhague", "HOV", 91, "Dinamarca", 7, 20_000_000_00,
     "Parken", 38065, 4500, "4-3-3", "EQUILIBRADO", 72),
    (625, "Midtjylland", "Herning", "MIJ", 91, "Dinamarca", 6, 15_000_000_00,
     "MCH Arena", 11800, 3800, "4-2-3-1", "OFENSIVO", 69),
    (626, "Bodo Glimt", "Bodo", "NOR", 91, "Noruega", 7, 16_000_000_00,
     "Aspmyra Stadion", 8270, 3800, "4-3-3", "OFENSIVO", 71),
    (627, "Dinamo Zagreb", "Zagreb", "ZAG", 91, "Croacia", 7, 18_000_000_00,
     "Maksimir", 24851, 4000, "4-2-3-1", "EQUILIBRADO", 72),
    (628, "Estrela Vermelha", "Belgrado", "BEO", 91, "Servia", 7, 16_000_000_00,
     "Rajko Mitic", 51755, 3800, "4-2-3-1", "OFENSIVO", 71),
    (629, "Legia Varsovia", "Varsovia", "MAZ", 91, "Polonia", 6, 13_000_000_00,
     "Stadion Legii", 31103, 3500, "4-2-3-1", "EQUILIBRADO", 68),
    (630, "Malmo FF", "Malmo", "SKA", 91, "Suecia", 6, 12_000_000_00,
     "Eleda Stadion", 22500, 3500, "4-4-2", "EQUILIBRADO", 67),
    (631, "FCSB", "Bucareste", "BUC", 91, "Romenia", 6, 11_000_000_00,
     "Arena Nationala", 55634, 3200, "4-2-3-1", "EQUILIBRADO", 66),

    # ── Ampliacao do pool para a UEFA Europa League (ver europa_league.md) ────
    # A Champions sozinha consome 22 clubes de pool; com 30 no total, a Europa
    # League nao fechava os seus 36 sob o teto de 7 clubes por pais. Estes 22
    # levam o pool a 52 e dao 2 a 3 clubes por associacao.
    #
    # As reputacoes sao deliberadamente escalonadas DENTRO de cada pais: o
    # primeiro clube vai a Champions, o segundo e o que sobra para a vaga direta
    # da Europa League, e o terceiro alimenta a qualificacao. Subir um deles
    # acima do titular do pais troca as duas competicoes de lugar.

    # Alemanha — 6 no total: 4 vagas na Champions + 2 na Europa League
    (632, "Eintracht Frankfurt", "Frankfurt", "HES", 91, "Alemanha", 8, 38_000_000_00,
     "Deutsche Bank Park", 58000, 6000, "3-4-3", "OFENSIVO", 80),
    (633, "VfB Stuttgart", "Stuttgart", "BWB", 91, "Alemanha", 8, 32_000_000_00,
     "MHPArena", 60449, 5500, "4-2-3-1", "EQUILIBRADO", 78),

    # Franca — 5 no total: 3 na Champions + 2 na Europa League
    (634, "Lille", "Lille", "HDF", 91, "Franca", 7, 28_000_000_00,
     "Stade Pierre-Mauroy", 50186, 5000, "4-2-3-1", "EQUILIBRADO", 77),
    (635, "Olympique Lyonnais", "Lyon", "ARA", 91, "Franca", 7, 26_000_000_00,
     "Groupama Stadium", 59186, 5000, "4-3-3", "OFENSIVO", 76),

    # Holanda — 3 no total: 2 na Champions + 1 na Europa League
    (636, "Feyenoord", "Roterda", "ZHL", 91, "Holanda", 7, 24_000_000_00,
     "De Kuip", 47500, 5000, "4-3-3", "OFENSIVO", 77),

    # Portugal — 2 no total. Benfica entra acima do Sporting: a vaga da
    # Champions passa a ser dele e o Sporting assume a da Europa League.
    (637, "Benfica", "Lisboa", "LIS", 91, "Portugal", 9, 58_000_000_00,
     "Estadio da Luz", 64642, 6000, "4-2-3-1", "OFENSIVO", 85),

    # Belgica, Tchequia, Turquia, Ucrania — 2 no total cada
    (638, "Union Saint-Gilloise", "Bruxelas", "BRU", 91, "Belgica", 7, 14_000_000_00,
     "Stade Joseph Marien", 9400, 4000, "3-5-2", "EQUILIBRADO", 73),
    (639, "Sparta Praga", "Praga", "PRG", 91, "Tchequia", 7, 15_000_000_00,
     "epet Arena", 18887, 4000, "4-3-3", "EQUILIBRADO", 71),
    (640, "Fenerbahce", "Istambul", "IST", 91, "Turquia", 8, 46_000_000_00,
     "Sukru Saracoglu", 50509, 6000, "4-2-3-1", "OFENSIVO", 81),
    (641, "Dinamo Kiev", "Kiev", "KIE", 91, "Ucrania", 7, 14_000_000_00,
     "Estadio Olimpico", 70050, 3800, "4-3-3", "EQUILIBRADO", 71),

    # Escocia, Austria, Suica — 3 no total cada: a Champions leva os melhores
    # pela qualificacao, entao o terceiro e quem sobra para a Europa League.
    (642, "Hearts", "Edimburgo", "EDI", 91, "Escocia", 6, 10_000_000_00,
     "Tynecastle Park", 19852, 3200, "4-2-3-1", "EQUILIBRADO", 65),
    (643, "LASK", "Linz", "UAU", 91, "Austria", 6, 11_000_000_00,
     "Raiffeisen Arena", 19080, 3200, "4-2-3-1", "EQUILIBRADO", 66),
    (644, "Servette", "Genebra", "GEN", 91, "Suica", 5, 9_000_000_00,
     "Stade de Geneve", 30084, 3000, "4-4-2", "EQUILIBRADO", 64),

    # Servia — 2 no total: Estrela Vermelha vai a qualificacao da Champions e o
    # Partizan assume a vaga direta da Europa League.
    (645, "Partizan", "Belgrado", "BEO", 91, "Servia", 6, 12_000_000_00,
     "Estadio Partizan", 29882, 3500, "4-3-3", "OFENSIVO", 69),

    # Liechtenstein — a excecao da Europa League. O pais nao tem liga propria
    # (seus clubes jogam no campeonato suico), so a copa local, e por isso a
    # vaga e dele mesmo sem reputacao de elite. Sem este clube a vaga fica vaga.
    (646, "FC Vaduz", "Vaduz", "VAD", 91, "Liechtenstein", 5, 6_000_000_00,
     "Rheinpark Stadion", 7584, 2600, "4-4-2", "DEFENSIVO", 58),

    # Associacoes sem vaga direta nas duas competicoes — alimentam as
    # qualificatorias, que precisam de mais candidatos que vagas para ter disputa
    (647, "AEK Atenas", "Atenas", "ATT", 91, "Grecia", 7, 18_000_000_00,
     "Agia Sophia Stadium", 32500, 4200, "4-2-3-1", "EQUILIBRADO", 72),
    (648, "Brondby", "Brondby", "HOV", 91, "Dinamarca", 6, 10_000_000_00,
     "Brondby Stadion", 28000, 3200, "4-3-3", "EQUILIBRADO", 66),
    (649, "Molde", "Molde", "MOR", 91, "Noruega", 6, 11_000_000_00,
     "Aker Stadion", 11249, 3200, "4-3-3", "OFENSIVO", 67),
    (650, "Hajduk Split", "Split", "SPU", 91, "Croacia", 6, 11_000_000_00,
     "Poljud", 33987, 3200, "4-2-3-1", "EQUILIBRADO", 67),
    (651, "Lech Poznan", "Poznan", "WIE", 91, "Polonia", 6, 10_000_000_00,
     "Enea Stadion", 42837, 3200, "4-2-3-1", "EQUILIBRADO", 66),
    (652, "Djurgarden", "Estocolmo", "STO", 91, "Suecia", 6, 9_500_000_00,
     "Tele2 Arena", 30000, 3000, "4-3-3", "EQUILIBRADO", 65),
    (653, "CFR Cluj", "Cluj", "CLU", 91, "Romenia", 5, 8_500_000_00,
     "Dr. Constantin Radulescu", 23500, 3000, "4-2-3-1", "DEFENSIVO", 64),
    # ── Pool sul-americano (divisao 90) — base da CONMEBOL Libertadores ───────
    # Sao as 7 associacoes que o seed nao tinha: Bolivia, Chile, Colombia,
    # Equador, Paraguai, Peru e Venezuela. Com elas o jogo passa a ter as 10
    # associacoes da CONMEBOL, e ListaDeAcessoLibertadores.cotasEfetivas devolve
    # as COTAS OFICIAIS em vez de redistribuir as vagas orfas entre Brasil,
    # Argentina e Uruguai — ver regras_libertadores.md.
    #
    # Seis clubes por pais para as 4 vagas de cada um (2 nos grupos + 2 na Pre):
    # a sobra da concorrencia pelas vagas e rotacao entre temporadas, porque
    # dentro do pool a fila e ordenada pelo coeficiente do Ranking CONMEBOL e,
    # no desempate, pela reputacao.
    #
    # As reputacoes ficam na faixa 61-78: abaixo dos gigantes brasileiros (ate
    # 89) e argentinos (ate 97) e sobrepostas ao meio da tabela deles. E o que
    # mantem Brasil e Argentina nos potes 1 e 2 do sorteio sem tornar os outros
    # oito paises figurantes.

    # Bolivia
    (654, "Bolivar", "La Paz", "LPZ", 90, "Bolivia", 6, 12_000_000_00,
     "Estadio Hernando Siles", 41143, 3000, "4-3-3", "OFENSIVO", 72),
    (655, "The Strongest", "La Paz", "LPZ", 90, "Bolivia", 6, 9_000_000_00,
     "Estadio Rafael Mendoza", 15000, 2800, "4-4-2", "EQUILIBRADO", 69),
    (656, "Always Ready", "El Alto", "LPZ", 90, "Bolivia", 5, 7_500_000_00,
     "Estadio Municipal de El Alto", 24000, 2600, "4-4-2", "OFENSIVO", 67),
    (657, "Oriente Petrolero", "Santa Cruz de la Sierra", "SCZ", 90, "Bolivia", 5, 6_000_000_00,
     "Estadio Ramon Tahuichi Aguilera", 38000, 2400, "4-2-3-1", "EQUILIBRADO", 64),
    (658, "Blooming", "Santa Cruz de la Sierra", "SCZ", 90, "Bolivia", 5, 5_000_000_00,
     "Estadio Ramon Tahuichi Aguilera", 38000, 2200, "4-4-2", "EQUILIBRADO", 62),
    (659, "Jorge Wilstermann", "Cochabamba", "CBB", 90, "Bolivia", 4, 4_500_000_00,
     "Estadio Felix Capriles", 32000, 2200, "4-4-2", "DEFENSIVO", 61),

    # Chile
    (660, "Colo-Colo", "Santiago", "RMS", 90, "Chile", 7, 20_000_000_00,
     "Estadio Monumental David Arellano", 47347, 3800, "4-3-3", "OFENSIVO", 77),
    (661, "Universidad de Chile", "Santiago", "RMS", 90, "Chile", 7, 16_000_000_00,
     "Estadio Nacional Julio Martinez", 48665, 3600, "4-2-3-1", "OFENSIVO", 74),
    (662, "Universidad Catolica", "Santiago", "RMS", 90, "Chile", 7, 15_000_000_00,
     "Estadio San Carlos de Apoquindo", 20000, 3600, "4-3-3", "EQUILIBRADO", 73),
    (663, "Huachipato", "Talcahuano", "BIO", 90, "Chile", 5, 7_000_000_00,
     "Estadio CAP", 10500, 2600, "4-2-3-1", "EQUILIBRADO", 67),
    (664, "Palestino", "Santiago", "RMS", 90, "Chile", 5, 5_500_000_00,
     "Estadio Municipal La Cisterna", 8000, 2400, "4-4-2", "EQUILIBRADO", 64),
    (665, "Cobresal", "El Salvador", "ATC", 90, "Chile", 4, 4_500_000_00,
     "Estadio El Cobre", 12000, 2200, "4-4-2", "DEFENSIVO", 62),

    # Colombia
    (666, "Atletico Nacional", "Medellin", "ANT", 90, "Colombia", 7, 22_000_000_00,
     "Estadio Atanasio Girardot", 40043, 3800, "4-3-3", "OFENSIVO", 78),
    (667, "Millonarios", "Bogota", "BOG", 90, "Colombia", 7, 16_000_000_00,
     "Estadio El Campin", 36343, 3600, "4-2-3-1", "OFENSIVO", 74),
    (668, "America de Cali", "Cali", "VAC", 90, "Colombia", 6, 13_000_000_00,
     "Estadio Pascual Guerrero", 35405, 3200, "4-3-3", "OFENSIVO", 72),
    (669, "Junior", "Barranquilla", "ATL", 90, "Colombia", 6, 12_000_000_00,
     "Estadio Metropolitano Roberto Melendez", 46692, 3200, "4-2-3-1", "OFENSIVO", 71),
    (670, "Deportivo Cali", "Cali", "VAC", 90, "Colombia", 5, 8_000_000_00,
     "Estadio Deportivo Cali", 41000, 2800, "4-4-2", "EQUILIBRADO", 68),
    (671, "Independiente Medellin", "Medellin", "ANT", 90, "Colombia", 5, 7_000_000_00,
     "Estadio Atanasio Girardot", 40043, 2600, "4-2-3-1", "EQUILIBRADO", 66),

    # Equador
    (672, "Independiente del Valle", "Sangolqui", "PIC", 90, "Equador", 7, 15_000_000_00,
     "Estadio Banco Guayaquil", 12000, 3200, "4-3-3", "OFENSIVO", 76),
    (673, "LDU Quito", "Quito", "PIC", 90, "Equador", 7, 14_000_000_00,
     "Estadio Rodrigo Paz Delgado", 41575, 3200, "4-2-3-1", "EQUILIBRADO", 75),
    (674, "Barcelona de Guayaquil", "Guayaquil", "GUA", 90, "Equador", 6, 12_000_000_00,
     "Estadio Monumental Banco Pichincha", 59283, 3000, "4-3-3", "OFENSIVO", 73),
    (675, "Emelec", "Guayaquil", "GUA", 90, "Equador", 6, 9_000_000_00,
     "Estadio George Capwell", 20000, 2800, "4-2-3-1", "EQUILIBRADO", 69),
    (676, "El Nacional", "Quito", "PIC", 90, "Equador", 5, 5_500_000_00,
     "Estadio Olimpico Atahualpa", 35724, 2400, "4-4-2", "EQUILIBRADO", 64),
    (677, "Deportivo Cuenca", "Cuenca", "AZU", 90, "Equador", 4, 4_500_000_00,
     "Estadio Alejandro Serrano Aguilar", 18549, 2200, "4-4-2", "DEFENSIVO", 62),

    # Paraguai
    (678, "Olimpia", "Assuncao", "ASU", 90, "Paraguai", 7, 15_000_000_00,
     "Estadio Manuel Ferreira", 20000, 3200, "4-3-3", "OFENSIVO", 75),
    (679, "Cerro Porteno", "Assuncao", "ASU", 90, "Paraguai", 7, 14_000_000_00,
     "Estadio General Pablo Rojas", 45000, 3200, "4-2-3-1", "OFENSIVO", 74),
    (680, "Libertad", "Assuncao", "ASU", 90, "Paraguai", 6, 12_000_000_00,
     "Estadio Dr. Nicolas Leoz", 10500, 3000, "4-3-3", "EQUILIBRADO", 73),
    (681, "Guarani", "Assuncao", "ASU", 90, "Paraguai", 5, 7_000_000_00,
     "Estadio Rogelio Livieres", 8000, 2600, "4-4-2", "EQUILIBRADO", 67),
    (682, "Nacional de Assuncao", "Assuncao", "ASU", 90, "Paraguai", 5, 5_000_000_00,
     "Estadio Arsenio Erico", 5000, 2200, "4-4-2", "EQUILIBRADO", 64),
    (683, "Sportivo Luqueno", "Luque", "CEN", 90, "Paraguai", 4, 4_000_000_00,
     "Estadio Feliciano Caceres", 25000, 2200, "4-4-2", "DEFENSIVO", 61),

    # Peru
    (684, "Universitario", "Lima", "LIM", 90, "Peru", 7, 14_000_000_00,
     "Estadio Monumental de Ate", 80093, 3200, "4-2-3-1", "OFENSIVO", 74),
    (685, "Alianza Lima", "Lima", "LIM", 90, "Peru", 6, 13_000_000_00,
     "Estadio Alejandro Villanueva", 35000, 3000, "4-3-3", "OFENSIVO", 73),
    (686, "Sporting Cristal", "Lima", "LIM", 90, "Peru", 6, 12_000_000_00,
     "Estadio Alberto Gallardo", 11000, 3000, "4-3-3", "EQUILIBRADO", 72),
    (687, "Melgar", "Arequipa", "ARE", 90, "Peru", 5, 7_000_000_00,
     "Estadio Monumental de la UNSA", 40370, 2600, "4-2-3-1", "EQUILIBRADO", 67),
    (688, "Cienciano", "Cusco", "CUS", 90, "Peru", 5, 5_000_000_00,
     "Estadio Garcilaso", 42000, 2400, "4-4-2", "EQUILIBRADO", 64),
    (689, "Sport Huancayo", "Huancayo", "JUN", 90, "Peru", 4, 4_000_000_00,
     "Estadio Huancayo", 20000, 2200, "4-4-2", "DEFENSIVO", 61),

    # Venezuela
    (690, "Caracas", "Caracas", "DTC", 90, "Venezuela", 6, 10_000_000_00,
     "Estadio Olimpico de la UCV", 24900, 2800, "4-2-3-1", "OFENSIVO", 70),
    (691, "Deportivo Tachira", "San Cristobal", "TAC", 90, "Venezuela", 6, 8_500_000_00,
     "Estadio Pueblo Nuevo", 38000, 2600, "4-3-3", "EQUILIBRADO", 68),
    (692, "Zamora", "Barinas", "BAR", 90, "Venezuela", 5, 6_000_000_00,
     "Estadio La Carolina", 12000, 2400, "4-4-2", "EQUILIBRADO", 65),
    (693, "Monagas", "Maturin", "MON", 90, "Venezuela", 5, 5_500_000_00,
     "Estadio Monumental de Maturin", 51796, 2400, "4-2-3-1", "EQUILIBRADO", 64),
    (694, "Deportivo La Guaira", "Caracas", "LAG", 90, "Venezuela", 5, 5_000_000_00,
     "Estadio Olimpico de la UCV", 24900, 2200, "4-4-2", "EQUILIBRADO", 63),
    (695, "Carabobo", "Valencia", "CAR", 90, "Venezuela", 4, 4_000_000_00,
     "Estadio Misael Delgado", 15000, 2200, "4-4-2", "DEFENSIVO", 61),
]

# ─────────────────── Escudos (drawable) ───────────────────────────────────────
# O gerador escrevia escudo_res="" e os escudos eram preenchidos a mao no
# seed_data.json depois — de modo que rodar o pipeline de novo os apagava todos.
# Ficam aqui para a geracao ser idempotente. Clube sem PNG em res/drawable fica
# com "" e o app cai no escudo generico; e so acrescentar o nome do recurso
# quando a imagem existir.
ESCUDOS = {
    601: "escudo_intermiami",
    602: "escudo_bayernmunique",
    603: "escudo_bayerleverkusen",
    604: "escudo_borussiadortmund",
    605: "escudo_rbleipzig",
    606: "escudo_psg",
    607: "escudo_olympiquemarseille",
    608: "escudo_monaco",
    609: "escudo_psv",
    610: "escudo_ajax",
    611: "escudo_clubbrugge",
    612: "escudo_slaviapraga",
    613: "escudo_galatasaray",
    614: "escudo_sportingcp",
    615: "escudo_shakhtar",
    616: "escudo_celtic",
    617: "escudo_rangers",
    618: "escudo_olympiacos",
    619: "escudo_paok",
    620: "escudo_rbsalzburg",
    621: "escudo_sturmgraz",
    622: "escudo_youngboys",
    623: "escudo_basel",
    624: "escudo_copenhagen",
    625: "escudo_midtjylland",
    626: "escudo_bodoglimt",
    627: "escudo_dinamozagreb",
    628: "escudo_estrelavermelha",
    629: "escudo_legiawarszawa",
    630: "escudo_malmo",
    631: "escudo_fcsb",
}

# ─────────────────── Derivação dos 7 atributos a partir do overall ────────────
STAT_KEYS = ["forca", "tecnica", "passe", "velocidade", "finalizacao", "defesa", "fisico"]

DELTAS = {
    "GOLEIRO":          {"defesa": 6,  "fisico": 2,  "passe": -6,  "velocidade": -12, "finalizacao": -32, "tecnica": -10},
    "ZAGUEIRO":         {"defesa": 6,  "fisico": 5,  "forca": 4,   "finalizacao": -16, "tecnica": -4,  "velocidade": -2},
    "LATERAL_DIREITO":  {"defesa": 2,  "velocidade": 6, "fisico": 2, "finalizacao": -8, "tecnica": -1},
    "LATERAL_ESQUERDO": {"defesa": 2,  "velocidade": 6, "fisico": 2, "finalizacao": -8, "tecnica": -1},
    "VOLANTE":          {"defesa": 5,  "passe": 3,   "fisico": 3,  "finalizacao": -8, "velocidade": -2},
    "MEIA_CENTRAL":     {"passe": 5,   "tecnica": 4, "finalizacao": -3, "defesa": -2},
    "MEIA_ATACANTE":    {"tecnica": 6, "passe": 4,   "finalizacao": 2,  "defesa": -8,  "fisico": -2},
    "PONTA_DIREITA":    {"velocidade": 7, "tecnica": 4, "finalizacao": 2, "defesa": -12, "fisico": -2},
    "PONTA_ESQUERDA":   {"velocidade": 7, "tecnica": 4, "finalizacao": 2, "defesa": -12, "fisico": -2},
    "CENTROAVANTE":     {"finalizacao": 8, "fisico": 4, "forca": 3, "defesa": -16, "passe": -4},
    "SEGUNDA_ATACANTE": {"finalizacao": 5, "tecnica": 4, "velocidade": 3, "defesa": -12, "passe": -1},
}

def gen_stats(overall, pos, seed):
    r = random.Random(seed)
    deltas = DELTAS.get(pos, {})
    stats = {}
    for k in STAT_KEYS:
        base = overall + deltas.get(k, 0) + r.randint(-2, 2)
        stats[k] = max(1, min(99, base))
    return stats

def gen_salario_valor(overall, seed):
    r = random.Random(seed + 7777)
    if overall >= 88:
        sal = (500_000_00, 1_200_000_00); val = (100_000_000_00, 200_000_000_00)
    elif overall >= 84:
        sal = (280_000_00, 550_000_00);   val = (55_000_000_00, 120_000_000_00)
    elif overall >= 80:
        sal = (160_000_00, 320_000_00);   val = (28_000_000_00, 65_000_000_00)
    elif overall >= 75:
        sal = (90_000_00, 180_000_00);    val = (12_000_000_00, 32_000_000_00)
    elif overall >= 70:
        sal = (50_000_00, 110_000_00);    val = (5_500_000_00, 16_000_000_00)
    elif overall >= 65:
        sal = (28_000_00, 65_000_00);     val = (2_500_000_00, 8_000_000_00)
    else:
        sal = (15_000_00, 40_000_00);     val = (800_000_00, 3_500_000_00)
    return r.randint(*sal), r.randint(*val)

def abrev(nome):
    parts = nome.split()
    if len(parts) >= 2:
        return f"{parts[0][0]}. {parts[-1]}"[:15]
    return nome[:15]

# ─────────────────── Pool sul-americano: elencos SINTÉTICOS ───────────────────
# Os 53 clubes do pool europeu e o Inter Miami tem elenco REAL, escrito a mao em
# ROSTERS. Os 42 clubes sul-americanos da divisao 90 nao: os elencos deles sao
# GERADOS, com nomes montados a partir dos pools abaixo.
#
# A escolha e deliberada. Seriam 840 jogadores, e nomes reais em quantidade dessa
# ordem, para sete ligas, sairiam errados — jogador no clube trocado, idade e
# posicao chutadas. Nome gerado nao finge ser quem nao e, e o elenco cumpre o
# papel que o pool precisa: dar a cada clube um plantel coerente com a sua
# reputacao, para a Libertadores ter 10 associacoes de verdade.
#
# Para trocar um clube por um elenco real, basta acrescentar a entrada dele em
# ROSTERS: o gerador so e usado quando o clube NAO tem roster escrito.
SUL_NOMES = [
    "Alan", "Alexis", "Andres", "Angel", "Anibal", "Ariel", "Bruno", "Camilo",
    "Carlos", "Cesar", "Cristian", "Damian", "Daniel", "Dario", "David", "Diego",
    "Edgar", "Eduardo", "Emanuel", "Enzo", "Esteban", "Ezequiel", "Fabian",
    "Facundo", "Federico", "Felipe", "Fernando", "Francisco", "Gabriel", "Gaston",
    "Gonzalo", "Gustavo", "Hernan", "Hugo", "Ignacio", "Ivan", "Jaime", "Javier",
    "Jeremias", "Joaquin", "Jorge", "Jose", "Juan", "Julio", "Kevin", "Leandro",
    "Leonardo", "Lucas", "Luis", "Manuel", "Marcelo", "Marco", "Mariano",
    "Martin", "Mateo", "Matias", "Mauricio", "Maximiliano", "Miguel", "Nahuel",
    "Nestor", "Nicolas", "Octavio", "Omar", "Oscar", "Pablo", "Patricio", "Pedro",
    "Rafael", "Ramiro", "Raul", "Ricardo", "Roberto", "Rodrigo", "Ruben",
    "Santiago", "Sebastian", "Sergio", "Thiago", "Tomas", "Valentin", "Victor",
    "Walter", "Wilson", "Yerson",
]

SUL_SOBRENOMES = [
    "Acosta", "Aguilar", "Alvarado", "Alvarez", "Arce", "Arevalo", "Avila",
    "Benitez", "Bustos", "Caceres", "Cardenas", "Carrasco", "Castillo", "Castro",
    "Cespedes", "Chavez", "Cordoba", "Cordero", "Cortes", "Delgado", "Diaz",
    "Duarte", "Escobar", "Espinoza", "Estrada", "Figueroa", "Flores", "Fuentes",
    "Gaitan", "Galarza", "Gallardo", "Garcia", "Gimenez", "Godoy", "Gomez",
    "Guerrero", "Gutierrez", "Guzman", "Herrera", "Hurtado", "Ibarra", "Jara",
    "Lagos", "Lezcano", "Lopez", "Luna", "Maldonado", "Mamani", "Mancilla",
    "Marin", "Medina", "Mejia", "Mendez", "Mendoza", "Molina", "Montero",
    "Morales", "Moreno", "Munoz", "Navarro", "Nunez", "Ojeda", "Olivares",
    "Ortega", "Ortiz", "Osorio", "Paredes", "Pena", "Perez", "Pineda", "Quintana",
    "Quispe", "Ramirez", "Reyes", "Rios", "Rivera", "Rojas", "Roldan", "Romero",
    "Rueda", "Salazar", "Salcedo", "Sanabria", "Sanchez", "Sandoval", "Sarmiento",
    "Sosa", "Soto", "Suarez", "Tapia", "Tejada", "Toledo", "Torres", "Trejo",
    "Urbina", "Valencia", "Valdez", "Vargas", "Vasquez", "Vega", "Velasquez",
    "Vera", "Villalba", "Zambrano", "Zapata", "Zarate",
]

# Vizinhos de onde sai a minoria estrangeira de cada elenco. Mantem o jogo com
# transferencias plausiveis dentro do continente sem espalhar nacionalidade de
# fora da CONMEBOL pelo pool.
SUL_VIZINHOS = {
    "Bolivia":   ["Argentina", "Brasil", "Paraguai", "Peru"],
    "Chile":     ["Argentina", "Paraguai", "Uruguai", "Colombia"],
    "Colombia":  ["Venezuela", "Equador", "Argentina", "Brasil"],
    "Equador":   ["Colombia", "Peru", "Argentina", "Uruguai"],
    "Paraguai":  ["Argentina", "Brasil", "Uruguai", "Bolivia"],
    "Peru":      ["Argentina", "Colombia", "Uruguai", "Bolivia"],
    "Venezuela": ["Colombia", "Argentina", "Brasil", "Equador"],
}

# 20 jogadores: 11 titulares + 9 reservas, cobrindo todas as posicoes com dois
# goleiros e reserva em cada setor.
SUL_POSICOES = (
    ["GOLEIRO"] * 2 +
    ["ZAGUEIRO"] * 4 +
    ["LATERAL_DIREITO"] * 2 +
    ["LATERAL_ESQUERDO"] * 2 +
    ["VOLANTE"] * 3 +
    ["MEIA_CENTRAL"] * 2 +
    ["MEIA_ATACANTE"] * 1 +
    ["PONTA_DIREITA"] * 1 +
    ["PONTA_ESQUERDA"] * 1 +
    ["CENTROAVANTE"] * 2
)


def gen_roster_sintetico(tid, trep, pais):
    """Elenco de 20 jogadores para um clube do pool sul-americano.

    Semeado por [tid], entao o seed sai igual a cada execucao do script.

    Os overalls descem numa escada de reputacao+2 (o melhor do grupo) ate
    reputacao-7 (o ultimo reserva), o que reproduz a forma dos elencos reais do
    pool europeu: um clube de reputacao 64 tem jogadores de 61 a 68.
    """
    r = random.Random(tid * 97 + 13)
    vizinhos = SUL_VIZINHOS.get(pais, [])
    usados = set()
    roster = []

    for i, pos in enumerate(SUL_POSICOES):
        # Nome unico dentro do clube.
        for _ in range(50):
            nome = "%s %s" % (r.choice(SUL_NOMES), r.choice(SUL_SOBRENOMES))
            if nome not in usados:
                break
        usados.add(nome)

        # Escada de qualidade: titulares perto da reputacao, reservas abaixo.
        degrau = 2 - (i * 9.0 / max(1, len(SUL_POSICOES) - 1))
        overall = int(round(trep + degrau)) + r.randint(-1, 1)
        overall = max(45, min(90, overall))

        # Minoria estrangeira: ~1 em cada 5, sempre de um vizinho da CONMEBOL.
        nacionalidade = pais
        if vizinhos and r.random() < 0.2:
            nacionalidade = r.choice(vizinhos)

        idade = r.randint(19, 35) if pos != "GOLEIRO" else r.randint(21, 37)
        roster.append((nome, pos, idade, nacionalidade, overall))

    return roster


def gen_tecnico_sintetico(tid, trep, pais, tecnico_id):
    """Tecnico de um clube do pool sul-americano, com nome gerado do mesmo pool.

    Os clubes europeus do pool tem tecnico REAL em TECNICOS; aqui o nome e
    gerado, pelo mesmo motivo dos elencos. Tambem semeado por [tid].
    """
    r = random.Random(tid * 31 + 7)
    nome = "%s %s" % (r.choice(SUL_NOMES), r.choice(SUL_SOBRENOMES))
    parts = nome.split()
    nome_abrev = "%s. %s" % (parts[0][0], parts[-1])
    reputacao = max(30.0, min(90.0, trep * 0.75 + r.uniform(-8, 8)))
    return {
        "id": tecnico_id, "nome": nome, "nome_abrev": nome_abrev,
        "idade": r.randint(38, 62), "nacionalidade": pais, "time_id": tid,
        "salario": int(150_000 + (reputacao - 30) * 50_000),
        "contrato_anos": r.randint(2, 4),
        "reputacao": round(reputacao, 1),
    }

# ─────────────────── Elencos reais 2025 ───────────────────────────────────────
# Formato: (nome, posicao, idade, nacionalidade, overall)
ROSTERS = {
601: [  # Inter Miami (MLS 2025)
    ("Oscar Ustari",        "GOLEIRO",          39, "Argentina",      76),
    ("Drake Callender",     "GOLEIRO",          27, "Estados Unidos", 74),
    ("Rocco Rios Novo",     "GOLEIRO",          22, "Argentina",      67),
    ("Jordi Alba",          "LATERAL_ESQUERDO", 36, "Espanha",        82),
    ("Marcelo Weigandt",    "LATERAL_DIREITO",  25, "Argentina",      73),
    ("Maximiliano Falcon",  "ZAGUEIRO",         28, "Uruguai",        74),
    ("Tomas Aviles",        "ZAGUEIRO",         21, "Argentina",      74),
    ("Gonzalo Lujan",       "LATERAL_ESQUERDO", 24, "Argentina",      70),
    ("Noah Allen",          "LATERAL_ESQUERDO", 21, "Estados Unidos", 69),
    ("Ian Fray",            "LATERAL_DIREITO",  23, "Estados Unidos", 68),
    ("Ryan Sailor",         "ZAGUEIRO",         25, "Estados Unidos", 67),
    ("Sergio Busquets",     "VOLANTE",          37, "Espanha",        82),
    ("Federico Redondo",    "VOLANTE",          22, "Argentina",      75),
    ("Telasco Segovia",     "MEIA_CENTRAL",     22, "Venezuela",      74),
    ("Benjamin Cremaschi",  "MEIA_CENTRAL",     20, "Estados Unidos", 72),
    ("Baltasar Rodriguez",  "MEIA_ATACANTE",    22, "Argentina",      73),
    ("Yannick Bright",      "VOLANTE",          23, "Italia",         69),
    ("Lionel Messi",        "SEGUNDA_ATACANTE", 38, "Argentina",      90),
    ("Luis Suarez",         "CENTROAVANTE",     38, "Uruguai",        79),
    ("Tadeo Allende",       "PONTA_ESQUERDA",   26, "Argentina",      74),
    ("Robert Taylor",       "PONTA_DIREITA",    30, "Finlandia",      72),
    ("Leonardo Campana",    "CENTROAVANTE",     25, "Equador",        73),
    ("Fafa Picault",        "PONTA_ESQUERDA",   34, "Haiti",          70),
],

# ───────────────────────── Alemanha ──────────────────────────────────────────
602: [  # Bayern de Munique
    ("Manuel Neuer",         "GOLEIRO",          39, "Alemanha",      85),
    ("Jonas Urbig",          "GOLEIRO",          22, "Alemanha",      73),
    ("Dayot Upamecano",      "ZAGUEIRO",         27, "Franca",        85),
    ("Jonathan Tah",         "ZAGUEIRO",         29, "Alemanha",      84),
    ("Min-jae Kim",          "ZAGUEIRO",         29, "Coreia do Sul", 81),
    ("Hiroki Ito",           "ZAGUEIRO",         26, "Japao",         78),
    ("Josip Stanisic",       "LATERAL_DIREITO",  25, "Croacia",       78),
    ("Sacha Boey",           "LATERAL_DIREITO",  25, "Franca",        75),
    ("Alphonso Davies",      "LATERAL_ESQUERDO", 25, "Canada",        85),
    ("Raphael Guerreiro",    "LATERAL_ESQUERDO", 32, "Portugal",      79),
    ("Joshua Kimmich",       "VOLANTE",          30, "Alemanha",      87),
    ("Leon Goretzka",        "VOLANTE",          31, "Alemanha",      80),
    ("Joao Palhinha",        "MEIA_CENTRAL",     30, "Portugal",      80),
    ("Aleksandar Pavlovic",  "MEIA_CENTRAL",     21, "Alemanha",      79),
    ("Jamal Musiala",        "MEIA_ATACANTE",    22, "Alemanha",      86),
    ("Michael Olise",        "PONTA_DIREITA",    24, "Franca",        86),
    ("Serge Gnabry",         "PONTA_DIREITA",    30, "Alemanha",      81),
    ("Luis Diaz",            "PONTA_ESQUERDA",   28, "Colombia",      85),
    ("Harry Kane",           "CENTROAVANTE",     32, "Inglaterra",    89),
    ("Nicolas Jackson",      "CENTROAVANTE",     24, "Senegal",       78),
],
603: [  # Bayer Leverkusen
    ("Mark Flekken",         "GOLEIRO",          32, "Holanda",       78),
    ("Niklas Lomb",          "GOLEIRO",          32, "Alemanha",      68),
    ("Edmond Tapsoba",       "ZAGUEIRO",         26, "Burkina Faso",  81),
    ("Loic Bade",            "ZAGUEIRO",         25, "Franca",        79),
    ("Jarell Quansah",       "ZAGUEIRO",         22, "Inglaterra",    78),
    ("Axel Tape",            "ZAGUEIRO",         20, "Franca",        70),
    ("Lucas Vazquez",        "LATERAL_DIREITO",  34, "Espanha",       78),
    ("Arthur Augusto",       "LATERAL_DIREITO",  22, "Brasil",        73),
    ("Alejandro Grimaldo",   "LATERAL_ESQUERDO", 30, "Espanha",       84),
    ("Piero Hincapie",       "LATERAL_ESQUERDO", 24, "Equador",       81),
    ("Robert Andrich",       "VOLANTE",          31, "Alemanha",      79),
    ("Exequiel Palacios",    "VOLANTE",          27, "Argentina",     80),
    ("Aleix Garcia",         "MEIA_CENTRAL",     28, "Espanha",       78),
    ("Ibrahim Maza",         "MEIA_CENTRAL",     20, "Argelia",       74),
    ("Malik Tillman",        "MEIA_ATACANTE",    23, "Estados Unidos",79),
    ("Nathan Tella",         "PONTA_DIREITA",    26, "Nigeria",       77),
    ("Ernest Poku",          "PONTA_DIREITA",    21, "Holanda",       72),
    ("Eliesse Ben Seghir",   "PONTA_ESQUERDA",   20, "Marrocos",      77),
    ("Patrik Schick",        "CENTROAVANTE",     29, "Tchequia",      82),
    ("Christian Kofane",     "CENTROAVANTE",     19, "Camaroes",      71),
],
604: [  # Borussia Dortmund
    ("Gregor Kobel",         "GOLEIRO",          28, "Suica",         84),
    ("Alexander Meyer",      "GOLEIRO",          34, "Alemanha",      69),
    ("Nico Schlotterbeck",   "ZAGUEIRO",         26, "Alemanha",      83),
    ("Waldemar Anton",       "ZAGUEIRO",         29, "Alemanha",      79),
    ("Niklas Sule",          "ZAGUEIRO",         30, "Alemanha",      79),
    ("Filippo Mane",         "ZAGUEIRO",         21, "Italia",        68),
    ("Yan Couto",            "LATERAL_DIREITO",  23, "Brasil",        78),
    ("Julian Ryerson",       "LATERAL_DIREITO",  28, "Noruega",       78),
    ("Ramy Bensebaini",      "LATERAL_ESQUERDO", 30, "Argelia",       77),
    ("Daniel Svensson",      "LATERAL_ESQUERDO", 23, "Suecia",        74),
    ("Pascal Gross",         "VOLANTE",          34, "Alemanha",      78),
    ("Marcel Sabitzer",      "VOLANTE",          31, "Austria",       79),
    ("Felix Nmecha",         "MEIA_CENTRAL",     25, "Alemanha",      78),
    ("Carney Chukwuemeka",   "MEIA_CENTRAL",     22, "Inglaterra",    74),
    ("Julian Brandt",        "MEIA_ATACANTE",    29, "Alemanha",      80),
    ("Karim Adeyemi",        "PONTA_DIREITA",    24, "Alemanha",      79),
    ("Jobe Bellingham",      "PONTA_DIREITA",    20, "Inglaterra",    76),
    ("Maximilian Beier",     "PONTA_ESQUERDA",   23, "Alemanha",      78),
    ("Serhou Guirassy",      "CENTROAVANTE",     29, "Guine",         84),
    ("Fabio Silva",          "CENTROAVANTE",     23, "Portugal",      75),
],
605: [  # RB Leipzig
    ("Peter Gulacsi",        "GOLEIRO",          35, "Hungria",       79),
    ("Maarten Vandevoordt",  "GOLEIRO",          23, "Belgica",       73),
    ("Castello Lukeba",      "ZAGUEIRO",         23, "Franca",        80),
    ("Willi Orban",          "ZAGUEIRO",         33, "Hungria",       79),
    ("Lutsharel Geertruida", "ZAGUEIRO",         25, "Holanda",       77),
    ("El Chadaille Bitshiabu","ZAGUEIRO",        20, "Franca",        75),
    ("Ridle Baku",           "LATERAL_DIREITO",  27, "Alemanha",      76),
    ("Benjamin Henrichs",    "LATERAL_DIREITO",  28, "Alemanha",      76),
    ("David Raum",           "LATERAL_ESQUERDO", 27, "Alemanha",      79),
    ("Kosta Nedeljkovic",    "LATERAL_ESQUERDO", 20, "Servia",        73),
    ("Nicolas Seiwald",      "VOLANTE",          24, "Austria",       77),
    ("Xaver Schlager",       "VOLANTE",          28, "Austria",       78),
    ("Christoph Baumgartner","MEIA_CENTRAL",     26, "Austria",       78),
    ("Ezechiel Banzuzi",     "MEIA_CENTRAL",     20, "Belgica",       72),
    ("Romulo Cardoso",       "MEIA_ATACANTE",    22, "Brasil",        75),
    ("Antonio Nusa",         "PONTA_DIREITA",    20, "Noruega",       78),
    ("Assan Ouedraogo",      "PONTA_DIREITA",    19, "Alemanha",      74),
    ("Yan Diomande",         "PONTA_ESQUERDA",   19, "Costa do Marfim",75),
    ("Conrad Harder",        "CENTROAVANTE",     20, "Dinamarca",     75),
    ("Timo Werner",          "CENTROAVANTE",     29, "Alemanha",      74),
],

# ───────────────────────── Franca ────────────────────────────────────────────
606: [  # Paris Saint-Germain
    ("Lucas Chevalier",      "GOLEIRO",          24, "Franca",        81),
    ("Matvey Safonov",       "GOLEIRO",          26, "Russia",        77),
    ("Marquinhos",           "ZAGUEIRO",         31, "Brasil",        84),
    ("Willian Pacho",        "ZAGUEIRO",         24, "Equador",       84),
    ("Illya Zabarnyi",       "ZAGUEIRO",         23, "Ucrania",       81),
    ("Lucas Beraldo",        "ZAGUEIRO",         22, "Brasil",        77),
    ("Achraf Hakimi",        "LATERAL_DIREITO",  27, "Marrocos",      86),
    ("Yoram Zague",          "LATERAL_DIREITO",  19, "Franca",        70),
    ("Nuno Mendes",          "LATERAL_ESQUERDO", 27, "Portugal",      85),
    ("Lucas Hernandez",      "LATERAL_ESQUERDO", 29, "Franca",        78),
    ("Vitinha",              "VOLANTE",          25, "Portugal",      86),
    ("Joao Neves",           "VOLANTE",          21, "Portugal",      85),
    ("Fabian Ruiz",          "MEIA_CENTRAL",     29, "Espanha",       83),
    ("Warren Zaire-Emery",   "MEIA_CENTRAL",     19, "Franca",        82),
    ("Senny Mayulu",         "MEIA_ATACANTE",    19, "Franca",        75),
    ("Ousmane Dembele",      "PONTA_DIREITA",    28, "Franca",        88),
    ("Desire Doue",          "PONTA_DIREITA",    20, "Franca",        83),
    ("Khvicha Kvaratskhelia","PONTA_ESQUERDA",   24, "Georgia",       87),
    ("Bradley Barcola",      "CENTROAVANTE",     23, "Franca",        83),
    ("Goncalo Ramos",        "CENTROAVANTE",     24, "Portugal",      81),
],
607: [  # Olympique de Marselha
    ("Geronimo Rulli",       "GOLEIRO",          33, "Argentina",     79),
    ("Ruben Blanco",         "GOLEIRO",          30, "Espanha",       71),
    ("Leonardo Balerdi",     "ZAGUEIRO",         26, "Argentina",     78),
    ("Nayef Aguerd",         "ZAGUEIRO",         29, "Marrocos",      78),
    ("Facundo Medina",       "ZAGUEIRO",         26, "Argentina",     77),
    ("CJ Egan-Riley",        "ZAGUEIRO",         22, "Inglaterra",    73),
    ("Timothy Weah",         "LATERAL_DIREITO",  25, "Estados Unidos",75),
    ("Amir Murillo",         "LATERAL_DIREITO",  29, "Panama",        74),
    ("Emerson Palmieri",     "LATERAL_ESQUERDO", 31, "Italia",        76),
    ("Ulisses Garcia",       "LATERAL_ESQUERDO", 29, "Suica",         72),
    ("Geoffrey Kondogbia",   "VOLANTE",          32, "Republica Centro-Africana", 77),
    ("Arthur Vermeeren",     "VOLANTE",          20, "Belgica",       75),
    ("Pierre-Emile Hojbjerg","MEIA_CENTRAL",     30, "Dinamarca",     80),
    ("Angel Gomes",          "MEIA_CENTRAL",     25, "Inglaterra",    75),
    ("Matt O'Riley",         "MEIA_ATACANTE",    25, "Dinamarca",     76),
    ("Mason Greenwood",      "PONTA_DIREITA",    24, "Inglaterra",    80),
    ("Amine Gouiri",         "PONTA_DIREITA",    25, "Argelia",       78),
    ("Igor Paixao",          "PONTA_ESQUERDA",   25, "Brasil",        78),
    ("Pierre-Emerick Aubameyang","CENTROAVANTE", 36, "Gabao",         78),
    ("Robinio Vaz",          "CENTROAVANTE",     18, "Franca",        70),
],
608: [  # AS Monaco
    ("Philipp Kohn",         "GOLEIRO",          27, "Suica",         74),
    ("Radoslaw Majecki",     "GOLEIRO",          26, "Polonia",       74),
    ("Thilo Kehrer",         "ZAGUEIRO",         29, "Alemanha",      77),
    ("Mohammed Salisu",      "ZAGUEIRO",         26, "Gana",          76),
    ("Jordan Teze",          "ZAGUEIRO",         26, "Holanda",       76),
    ("Christian Mawissa",    "ZAGUEIRO",         20, "Franca",        73),
    ("Vanderson",            "LATERAL_DIREITO",  24, "Brasil",        78),
    ("Krepin Diatta",        "LATERAL_DIREITO",  26, "Senegal",       75),
    ("Caio Henrique",        "LATERAL_ESQUERDO", 28, "Brasil",        76),
    ("Mamadou Coulibaly",    "LATERAL_ESQUERDO", 20, "Franca",        70),
    ("Denis Zakaria",        "VOLANTE",          28, "Suica",         79),
    ("Lamine Camara",        "VOLANTE",          21, "Senegal",       76),
    ("Maghnes Akliouche",    "MEIA_CENTRAL",     23, "Franca",        80),
    ("Aleksandr Golovin",    "MEIA_CENTRAL",     29, "Russia",        79),
    ("Takumi Minamino",      "MEIA_ATACANTE",    30, "Japao",         76),
    ("Ansu Fati",            "PONTA_DIREITA",    22, "Espanha",       76),
    ("George Ilenikhena",    "PONTA_DIREITA",    19, "Nigeria",       74),
    ("Kassoum Ouattara",     "PONTA_ESQUERDA",   21, "Franca",        71),
    ("Mika Biereth",         "CENTROAVANTE",     22, "Dinamarca",     79),
    ("Folarin Balogun",      "CENTROAVANTE",     24, "Estados Unidos",76),
],

# ───────────────────────── Holanda ───────────────────────────────────────────
609: [  # PSV Eindhoven
    ("Walter Benitez",       "GOLEIRO",          32, "Argentina",     78),
    ("Nick Olij",            "GOLEIRO",          30, "Holanda",       72),
    ("Olivier Boscagli",     "ZAGUEIRO",         28, "Franca",        77),
    ("Ryan Flamingo",        "ZAGUEIRO",         22, "Holanda",       76),
    ("Armando Obispo",       "ZAGUEIRO",         26, "Holanda",       74),
    ("Yarek Gasiorowski",    "ZAGUEIRO",         20, "Espanha",       73),
    ("Sergino Dest",         "LATERAL_DIREITO",  25, "Estados Unidos",76),
    ("Richard Ledezma",      "LATERAL_DIREITO",  25, "Estados Unidos",72),
    ("Mauro Junior",         "LATERAL_ESQUERDO", 26, "Brasil",        74),
    ("Anass Salah-Eddine",   "LATERAL_ESQUERDO", 23, "Holanda",       73),
    ("Jerdy Schouten",       "VOLANTE",          29, "Holanda",       79),
    ("Joey Veerman",         "VOLANTE",          27, "Holanda",       78),
    ("Ismael Saibari",       "MEIA_CENTRAL",     24, "Marrocos",      79),
    ("Guus Til",             "MEIA_CENTRAL",     28, "Holanda",       76),
    ("Dennis Man",           "MEIA_ATACANTE",    27, "Romenia",       76),
    ("Ivan Perisic",         "PONTA_DIREITA",    36, "Croacia",       77),
    ("Esmir Bajraktarevic",  "PONTA_DIREITA",    20, "Estados Unidos",72),
    ("Couhaib Driouech",     "PONTA_ESQUERDA",   23, "Marrocos",      74),
    ("Ricardo Pepi",         "CENTROAVANTE",     22, "Estados Unidos",77),
    ("Alassane Plea",        "CENTROAVANTE",     32, "Franca",        76),
],
610: [  # Ajax
    ("Vitezslav Jaros",      "GOLEIRO",          24, "Tchequia",      74),
    ("Remko Pasveer",        "GOLEIRO",          42, "Holanda",       73),
    ("Ko Itakura",           "ZAGUEIRO",         28, "Japao",         77),
    ("Josip Sutalo",         "ZAGUEIRO",         25, "Croacia",       76),
    ("Youri Baas",           "ZAGUEIRO",         22, "Holanda",       75),
    ("Aaron Bouwman",        "ZAGUEIRO",         19, "Holanda",       69),
    ("Anton Gaaei",          "LATERAL_DIREITO",  23, "Dinamarca",     72),
    ("Lucas Rosa",           "LATERAL_DIREITO",  25, "Brasil",        71),
    ("Owen Wijndal",         "LATERAL_ESQUERDO", 26, "Holanda",       73),
    ("Youri Regeer",         "LATERAL_ESQUERDO", 22, "Holanda",       73),
    ("Kenneth Taylor",       "VOLANTE",          23, "Holanda",       78),
    ("Branco van den Boomen","VOLANTE",          30, "Holanda",       74),
    ("Davy Klaassen",        "MEIA_CENTRAL",     32, "Holanda",       76),
    ("Sivert Mannsverk",     "MEIA_CENTRAL",     23, "Noruega",       73),
    ("Mika Godts",           "MEIA_ATACANTE",    20, "Belgica",       76),
    ("Steven Berghuis",      "PONTA_DIREITA",    34, "Holanda",       77),
    ("Oscar Gloukh",         "PONTA_DIREITA",    21, "Israel",        77),
    ("Raul Moro",            "PONTA_ESQUERDA",   23, "Espanha",       73),
    ("Wout Weghorst",        "CENTROAVANTE",     33, "Holanda",       76),
    ("Kasper Dolberg",       "CENTROAVANTE",     28, "Dinamarca",     75),
],

# ───────────────────────── Belgica ───────────────────────────────────────────
611: [  # Club Brugge
    ("Simon Mignolet",       "GOLEIRO",          38, "Belgica",       77),
    ("Nordin Jackers",       "GOLEIRO",          28, "Belgica",       72),
    ("Joel Ordonez",         "ZAGUEIRO",         21, "Equador",       76),
    ("Brandon Mechele",      "ZAGUEIRO",         32, "Belgica",       74),
    ("Zaid Romero",          "ZAGUEIRO",         24, "Argentina",     73),
    ("Jorne Spileers",       "ZAGUEIRO",         20, "Belgica",       71),
    ("Kyriani Sabbe",        "LATERAL_DIREITO",  21, "Belgica",       74),
    ("Joaquin Seys",         "LATERAL_DIREITO",  20, "Belgica",       73),
    ("Bjorn Meijer",         "LATERAL_ESQUERDO", 22, "Holanda",       72),
    ("Joris Kayembe",        "LATERAL_ESQUERDO", 31, "RD Congo",      72),
    ("Hans Vanaken",         "VOLANTE",          33, "Belgica",       78),
    ("Raphael Onyedika",     "VOLANTE",          24, "Nigeria",       77),
    ("Aleksandar Stankovic", "MEIA_CENTRAL",     20, "Servia",        75),
    ("Hugo Vetlesen",        "MEIA_CENTRAL",     25, "Noruega",       74),
    ("Christos Tzolis",      "MEIA_ATACANTE",    24, "Grecia",        78),
    ("Carlos Forbs",         "PONTA_DIREITA",    22, "Portugal",      75),
    ("Chemsdine Talbi",      "PONTA_DIREITA",    20, "Marrocos",      75),
    ("Mamadou Diakhon",      "PONTA_ESQUERDA",   19, "Franca",        71),
    ("Nicolo Tresoldi",      "CENTROAVANTE",     21, "Italia",        74),
    ("Romeo Vermant",        "CENTROAVANTE",     21, "Belgica",       73),
],

# ───────────────────────── Tchequia ──────────────────────────────────────────
612: [  # Slavia Praga
    ("Jindrich Stanek",      "GOLEIRO",          29, "Tchequia",      76),
    ("Ales Mandous",         "GOLEIRO",          33, "Tchequia",      70),
    ("Igoh Ogbu",            "ZAGUEIRO",         26, "Nigeria",       75),
    ("David Zima",           "ZAGUEIRO",         25, "Tchequia",      74),
    ("Tomas Holes",          "ZAGUEIRO",         32, "Tchequia",      73),
    ("Stephan Chaloupek",    "ZAGUEIRO",         23, "Austria",       69),
    ("David Doudera",        "LATERAL_DIREITO",  27, "Tchequia",      72),
    ("Lukas Masopust",       "LATERAL_DIREITO",  32, "Tchequia",      72),
    ("Dominik Javorcek",     "LATERAL_ESQUERDO", 23, "Tchequia",      71),
    ("Jan Boril",            "LATERAL_ESQUERDO", 34, "Tchequia",      70),
    ("Christos Zafeiris",    "VOLANTE",          22, "Grecia",        75),
    ("Oscar Dorley",         "VOLANTE",          27, "Liberia",       73),
    ("Lukas Provod",         "MEIA_CENTRAL",     29, "Tchequia",      76),
    ("Michal Sadilek",       "MEIA_CENTRAL",     26, "Tchequia",      72),
    ("Ivan Schranz",         "MEIA_ATACANTE",    32, "Eslovaquia",    74),
    ("Matej Jurasek",        "PONTA_DIREITA",    22, "Tchequia",      73),
    ("Youssoupha Mbodji",    "PONTA_DIREITA",    22, "Senegal",       70),
    ("Vasil Kusej",          "PONTA_ESQUERDA",   25, "Tchequia",      74),
    ("Tomas Chory",          "CENTROAVANTE",     30, "Tchequia",      75),
    ("Mojmir Chytil",        "CENTROAVANTE",     27, "Tchequia",      74),
],

# ───────────────────────── Turquia ───────────────────────────────────────────
613: [  # Galatasaray
    ("Ugurcan Cakir",        "GOLEIRO",          29, "Turquia",       79),
    ("Gunay Guvenc",         "GOLEIRO",          34, "Turquia",       70),
    ("Davinson Sanchez",     "ZAGUEIRO",         29, "Colombia",      79),
    ("Abdulkerim Bardakci",  "ZAGUEIRO",         31, "Turquia",       76),
    ("Ismail Jakobs",        "ZAGUEIRO",         26, "Senegal",       75),
    ("Metehan Baltaci",      "ZAGUEIRO",         22, "Turquia",       71),
    ("Wilfried Singo",       "LATERAL_DIREITO",  25, "Costa do Marfim",78),
    ("Roland Sallai",        "LATERAL_DIREITO",  28, "Hungria",       76),
    ("Eren Elmali",          "LATERAL_ESQUERDO", 25, "Turquia",       74),
    ("Kazimcan Karatas",     "LATERAL_ESQUERDO", 22, "Turquia",       72),
    ("Lucas Torreira",       "VOLANTE",          29, "Uruguai",       79),
    ("Mario Lemina",         "VOLANTE",          32, "Gabao",         76),
    ("Ilkay Gundogan",       "MEIA_CENTRAL",     35, "Alemanha",      80),
    ("Gabriel Sara",         "MEIA_CENTRAL",     26, "Brasil",        78),
    ("Yunus Akgun",          "MEIA_ATACANTE",    25, "Turquia",       76),
    ("Leroy Sane",           "PONTA_DIREITA",    29, "Alemanha",      82),
    ("Baris Alper Yilmaz",   "PONTA_DIREITA",    25, "Turquia",       77),
    ("Ahmed Kutucu",         "PONTA_ESQUERDA",   25, "Turquia",       72),
    ("Victor Osimhen",       "CENTROAVANTE",     27, "Nigeria",       87),
    ("Mauro Icardi",         "CENTROAVANTE",     32, "Argentina",     79),
],

# ───────────────────────── Portugal ──────────────────────────────────────────
614: [  # Sporting CP
    ("Rui Silva",            "GOLEIRO",          31, "Portugal",      77),
    ("Joao Virginia",        "GOLEIRO",          26, "Portugal",      72),
    ("Goncalo Inacio",       "ZAGUEIRO",         24, "Portugal",      82),
    ("Ousmane Diomande",     "ZAGUEIRO",         22, "Costa do Marfim",81),
    ("Zeno Debast",          "ZAGUEIRO",         22, "Belgica",       77),
    ("Jerry St. Juste",      "ZAGUEIRO",         29, "Holanda",       74),
    ("Geovany Quenda",       "LATERAL_DIREITO",  18, "Portugal",      78),
    ("Ivan Fresneda",        "LATERAL_DIREITO",  21, "Espanha",       75),
    ("Maxi Araujo",          "LATERAL_ESQUERDO", 25, "Uruguai",       76),
    ("Matheus Reis",         "LATERAL_ESQUERDO", 30, "Brasil",        74),
    ("Morten Hjulmand",      "VOLANTE",          26, "Dinamarca",     81),
    ("Hidemasa Morita",      "VOLANTE",          30, "Japao",         77),
    ("Pedro Goncalves",      "MEIA_CENTRAL",     27, "Portugal",      81),
    ("Joao Simoes",          "MEIA_CENTRAL",     20, "Portugal",      73),
    ("Francisco Trincao",    "MEIA_ATACANTE",    26, "Portugal",      79),
    ("Luis Suarez",          "PONTA_DIREITA",    28, "Colombia",      77),
    ("Geny Catamo",          "PONTA_DIREITA",    24, "Mocambique",    75),
    ("Nuno Santos",          "PONTA_ESQUERDA",   30, "Portugal",      75),
    ("Fotis Ioannidis",      "CENTROAVANTE",     25, "Grecia",        78),
    ("Alisson Santos",       "CENTROAVANTE",     26, "Brasil",        74),
],

# ───────────────────────── Ucrania ───────────────────────────────────────────
615: [  # Shakhtar Donetsk
    ("Dmytro Riznyk",        "GOLEIRO",          26, "Ucrania",       76),
    ("Kyrylo Fesiun",        "GOLEIRO",          24, "Ucrania",       69),
    ("Mykola Matviyenko",    "ZAGUEIRO",         29, "Ucrania",       77),
    ("Valeriy Bondar",       "ZAGUEIRO",         26, "Ucrania",       74),
    ("Marlon Santos",        "ZAGUEIRO",         30, "Brasil",        73),
    ("Pedro Henrique",       "ZAGUEIRO",         25, "Brasil",        72),
    ("Yukhym Konoplia",      "LATERAL_DIREITO",  26, "Ucrania",       74),
    ("Vinicius Tobias",      "LATERAL_DIREITO",  22, "Brasil",        72),
    ("Irakli Azarovi",       "LATERAL_ESQUERDO", 23, "Georgia",       73),
    ("Hryhoriy Pedko",       "LATERAL_ESQUERDO", 21, "Ucrania",       68),
    ("Oleh Ocheretko",       "VOLANTE",          21, "Ucrania",       74),
    ("Julio Romao",          "VOLANTE",          24, "Portugal",      74),
    ("Dmytro Kryskiv",       "MEIA_CENTRAL",     23, "Ucrania",       73),
    ("Yegor Nazaryna",       "MEIA_CENTRAL",     27, "Ucrania",       73),
    ("Georgiy Sudakov",      "MEIA_ATACANTE",    23, "Ucrania",       81),
    ("Pedrinho",             "PONTA_DIREITA",    27, "Brasil",        75),
    ("Eguinaldo",            "PONTA_DIREITA",    21, "Brasil",        73),
    ("Marian Shved",         "PONTA_ESQUERDA",   28, "Ucrania",       73),
    ("Lassina Traore",       "CENTROAVANTE",     25, "Burkina Faso",  75),
    ("Luca Meirelles",       "CENTROAVANTE",     20, "Brasil",        72),
],

# ───────────────────────── Escocia ───────────────────────────────────────────
616: [  # Celtic
    ("Kasper Schmeichel",    "GOLEIRO",          39, "Dinamarca",     76),
    ("Viljami Sinisalo",     "GOLEIRO",          24, "Finlandia",     72),
    ("Cameron Carter-Vickers","ZAGUEIRO",        28, "Estados Unidos",76),
    ("Auston Trusty",        "ZAGUEIRO",         27, "Estados Unidos",74),
    ("Liam Scales",          "ZAGUEIRO",         27, "Irlanda",       73),
    ("Dane Murray",          "ZAGUEIRO",         23, "Escocia",       68),
    ("Alistair Johnston",    "LATERAL_DIREITO",  27, "Canada",        75),
    ("Anthony Ralston",      "LATERAL_DIREITO",  27, "Escocia",       71),
    ("Kieran Tierney",       "LATERAL_ESQUERDO", 28, "Escocia",       76),
    ("Marcelo Saracchi",     "LATERAL_ESQUERDO", 27, "Uruguai",       72),
    ("Callum McGregor",      "VOLANTE",          32, "Escocia",       76),
    ("Reo Hatate",           "VOLANTE",          28, "Japao",         76),
    ("Arne Engels",          "MEIA_CENTRAL",     22, "Belgica",       75),
    ("Paulo Bernardo",       "MEIA_CENTRAL",     23, "Portugal",      73),
    ("Benjamin Nygren",      "MEIA_ATACANTE",    24, "Suecia",        75),
    ("Daizen Maeda",         "PONTA_DIREITA",    28, "Japao",         78),
    ("James Forrest",        "PONTA_DIREITA",    34, "Escocia",       71),
    ("Yang Hyun-jun",        "PONTA_ESQUERDA",   23, "Coreia do Sul", 71),
    ("Adam Idah",            "CENTROAVANTE",     24, "Irlanda",       73),
    ("Johnny Kenny",         "CENTROAVANTE",     22, "Irlanda",       70),
],
617: [  # Rangers
    ("Jack Butland",         "GOLEIRO",          32, "Inglaterra",    75),
    ("Liam Kelly",           "GOLEIRO",          29, "Escocia",       70),
    ("John Souttar",         "ZAGUEIRO",         29, "Escocia",       74),
    ("Nasser Djiga",         "ZAGUEIRO",         23, "Burkina Faso",  73),
    ("Robin Propper",        "ZAGUEIRO",         32, "Holanda",       72),
    ("Emmanuel Fernandez",   "ZAGUEIRO",         24, "Espanha",       71),
    ("Max Aarons",           "LATERAL_DIREITO",  25, "Inglaterra",    74),
    ("James Tavernier",      "LATERAL_DIREITO",  34, "Inglaterra",    73),
    ("Jefte",                "LATERAL_ESQUERDO", 22, "Brasil",        72),
    ("Ridvan Yilmaz",        "LATERAL_ESQUERDO", 24, "Turquia",       71),
    ("Nicolas Raskin",       "VOLANTE",          24, "Belgica",       75),
    ("Mohamed Diomande",     "VOLANTE",          24, "Costa do Marfim",73),
    ("Thelo Aasgaard",       "MEIA_CENTRAL",     23, "Noruega",       73),
    ("Connor Barron",        "MEIA_CENTRAL",     23, "Escocia",       71),
    ("Joe Rothwell",         "MEIA_ATACANTE",    30, "Inglaterra",    72),
    ("Oliver Antman",        "PONTA_DIREITA",    24, "Finlandia",     73),
    ("Djeidi Gassama",       "PONTA_DIREITA",    22, "Franca",        73),
    ("Vaclav Cerny",         "PONTA_ESQUERDA",   28, "Tchequia",      74),
    ("Cyriel Dessers",       "CENTROAVANTE",     31, "Nigeria",       74),
    ("Hamza Igamane",        "CENTROAVANTE",     23, "Marrocos",      73),
],

# ───────────────────────── Grecia ────────────────────────────────────────────
618: [  # Olympiacos
    ("Konstantinos Tzolakis","GOLEIRO",          23, "Grecia",        75),
    ("Alexandros Paschalakis","GOLEIRO",         36, "Grecia",        70),
    ("Lorenzo Pirola",       "ZAGUEIRO",         23, "Italia",        75),
    ("Panagiotis Retsos",    "ZAGUEIRO",         27, "Grecia",        74),
    ("David Carmo",          "ZAGUEIRO",         26, "Portugal",      74),
    ("Nikolas Michelis",     "ZAGUEIRO",         22, "Grecia",        69),
    ("Rodinei",              "LATERAL_DIREITO",  33, "Brasil",        74),
    ("Costinha",             "LATERAL_DIREITO",  26, "Portugal",      72),
    ("Francisco Ortega",     "LATERAL_ESQUERDO", 26, "Argentina",     74),
    ("Bruno Onyemaechi",     "LATERAL_ESQUERDO", 26, "Nigeria",       72),
    ("Santiago Hezze",       "VOLANTE",          24, "Argentina",     75),
    ("Dani Garcia",          "VOLANTE",          35, "Espanha",       72),
    ("Chiquinho",            "MEIA_CENTRAL",     30, "Portugal",      75),
    ("Christos Mouzakitis",  "MEIA_CENTRAL",     19, "Grecia",        74),
    ("Daniel Podence",       "MEIA_ATACANTE",    30, "Portugal",      76),
    ("Gelson Martins",       "PONTA_DIREITA",    30, "Portugal",      74),
    ("Stevan Jovetic",       "PONTA_DIREITA",    36, "Montenegro",    72),
    ("Yusuf Yazici",         "PONTA_ESQUERDA",   28, "Turquia",       75),
    ("Ayoub El Kaabi",       "CENTROAVANTE",     32, "Marrocos",      79),
    ("Roman Yaremchuk",      "CENTROAVANTE",     30, "Ucrania",       74),
],
619: [  # PAOK
    ("Dominik Kotarski",     "GOLEIRO",          25, "Croacia",       74),
    ("Antonis Tsiftsis",     "GOLEIRO",          25, "Grecia",        68),
    ("Giannis Michailidis",  "ZAGUEIRO",         25, "Grecia",        74),
    ("Abdul Mumin",          "ZAGUEIRO",         27, "Gana",          74),
    ("Dimitrios Kourbelis",  "ZAGUEIRO",         31, "Grecia",        72),
    ("Antonis Martis",       "ZAGUEIRO",         22, "Grecia",        68),
    ("Lazaros Rota",         "LATERAL_DIREITO",  28, "Grecia",        72),
    ("Kenny Lala",           "LATERAL_DIREITO",  34, "Franca",        71),
    ("Jonny Otto",           "LATERAL_ESQUERDO", 31, "Espanha",       73),
    ("Rafa Soares",          "LATERAL_ESQUERDO", 29, "Portugal",      72),
    ("Soualiho Meite",       "VOLANTE",          31, "Franca",        74),
    ("Mady Camara",          "VOLANTE",          28, "Guine",         73),
    ("Giannis Konstantelias","MEIA_CENTRAL",     22, "Grecia",        76),
    ("Magomed Ozdoev",       "MEIA_CENTRAL",     33, "Russia",        72),
    ("Andrija Zivkovic",     "MEIA_ATACANTE",    29, "Servia",        74),
    ("Kiril Despodov",       "PONTA_DIREITA",    29, "Bulgaria",      75),
    ("Taison",               "PONTA_DIREITA",    37, "Brasil",        71),
    ("Luis Palma",           "PONTA_ESQUERDA",   26, "Honduras",      73),
    ("Chuba Akpom",          "CENTROAVANTE",     30, "Inglaterra",    74),
    ("Fedor Chalov",         "CENTROAVANTE",     27, "Russia",        73),
],

# ───────────────────────── Austria ───────────────────────────────────────────
620: [  # Red Bull Salzburg
    ("Alexander Schlager",   "GOLEIRO",          29, "Austria",       74),
    ("Janis Blaswich",       "GOLEIRO",          34, "Alemanha",      73),
    ("Hendry Blank",         "ZAGUEIRO",         21, "Alemanha",      74),
    ("Jacob Rasmussen",      "ZAGUEIRO",         28, "Dinamarca",     73),
    ("Samson Baidoo",        "ZAGUEIRO",         21, "Austria",       73),
    ("Joane Gadou",          "ZAGUEIRO",         19, "Franca",        71),
    ("Amar Dedic",           "LATERAL_DIREITO",  23, "Bosnia",        75),
    ("Stefan Lainer",        "LATERAL_DIREITO",  33, "Austria",       71),
    ("Aleksa Terzic",        "LATERAL_ESQUERDO", 26, "Servia",        72),
    ("Frans Kratzig",        "LATERAL_ESQUERDO", 22, "Alemanha",      72),
    ("Mamady Diambou",       "VOLANTE",          23, "Mali",          73),
    ("Soumaila Diabate",     "VOLANTE",          22, "Costa do Marfim",72),
    ("Maurits Kjaergaard",   "MEIA_CENTRAL",     22, "Dinamarca",     75),
    ("Bobby Clark",          "MEIA_CENTRAL",     20, "Inglaterra",    73),
    ("Takumu Kawamura",      "MEIA_ATACANTE",    24, "Japao",         72),
    ("Yorbe Vertessen",      "PONTA_DIREITA",    24, "Belgica",       73),
    ("Edmund Baidoo",        "PONTA_DIREITA",    21, "Gana",          71),
    ("Dorgeles Nene",        "PONTA_ESQUERDA",   22, "Mali",          75),
    ("Karim Konate",         "CENTROAVANTE",     21, "Costa do Marfim",76),
    ("Petar Ratkov",         "CENTROAVANTE",     22, "Servia",        74),
],
621: [  # Sturm Graz
    ("Kjell Scherpen",       "GOLEIRO",          25, "Holanda",       73),
    ("Christian Zieglbauer", "GOLEIRO",          22, "Austria",       66),
    ("Gregory Wuthrich",     "ZAGUEIRO",         30, "Suica",         71),
    ("Jon Gorenc Stankovic", "ZAGUEIRO",         29, "Eslovenia",     71),
    ("Emanuel Aiwu",         "ZAGUEIRO",         25, "Austria",       70),
    ("Niklas Geyrhofer",     "ZAGUEIRO",         25, "Austria",       69),
    ("Dimitri Lavalee",      "LATERAL_DIREITO",  28, "Belgica",       70),
    ("Jusuf Gazibegovic",    "LATERAL_DIREITO",  25, "Bosnia",        70),
    ("Max Johnston",         "LATERAL_ESQUERDO", 22, "Escocia",       71),
    ("Stefan Hierlander",    "LATERAL_ESQUERDO", 34, "Austria",       68),
    ("Tochi Chukwuani",      "VOLANTE",          22, "Dinamarca",     70),
    ("Arjan Malic",          "VOLANTE",          22, "Austria",       68),
    ("Otar Kiteishvili",     "MEIA_CENTRAL",     29, "Georgia",       73),
    ("Tomi Horvat",          "MEIA_CENTRAL",     26, "Eslovenia",     71),
    ("Leon Grgic",           "MEIA_ATACANTE",    19, "Austria",       70),
    ("William Boving",       "PONTA_DIREITA",    22, "Dinamarca",     72),
    ("Dimitri Oberlin",      "PONTA_DIREITA",    28, "Suica",         69),
    ("Seedy Jatta",          "PONTA_ESQUERDA",   22, "Gambia",        69),
    ("Maurice Malone",       "CENTROAVANTE",     25, "Alemanha",      71),
    ("Jacob Horn",           "CENTROAVANTE",     23, "Alemanha",      68),
],

# ───────────────────────── Suica ─────────────────────────────────────────────
622: [  # Young Boys
    ("Marvin Keller",        "GOLEIRO",          23, "Suica",         72),
    ("David von Ballmoos",   "GOLEIRO",          30, "Suica",         71),
    ("Mohamed Ali Camara",   "ZAGUEIRO",         28, "Guine",         73),
    ("Loris Benito",         "ZAGUEIRO",         33, "Suica",         71),
    ("Marco Burch",          "ZAGUEIRO",         25, "Suica",         70),
    ("Noah Persson",         "ZAGUEIRO",         23, "Suecia",        69),
    ("Lewin Blum",           "LATERAL_DIREITO",  24, "Suica",         71),
    ("Facinet Conte",        "LATERAL_DIREITO",  21, "Guine",         68),
    ("Jaouen Hadjam",        "LATERAL_ESQUERDO", 23, "Argelia",       73),
    ("Saidy Janko",          "LATERAL_ESQUERDO", 30, "Gambia",        70),
    ("Sandro Lauper",        "VOLANTE",          29, "Suica",         71),
    ("Armin Gigovic",        "VOLANTE",          23, "Suecia",        72),
    ("Alvyn Sanches",        "MEIA_CENTRAL",     22, "Suica",         74),
    ("Filip Ugrinic",        "MEIA_CENTRAL",     26, "Suica",         73),
    ("Darian Males",         "MEIA_ATACANTE",    24, "Suica",         72),
    ("Edimilson Fernandes",  "PONTA_DIREITA",    29, "Suica",         72),
    ("Joel Monteiro",        "PONTA_DIREITA",    26, "Suica",         71),
    ("Ebrima Colley",        "PONTA_ESQUERDA",   25, "Gambia",        71),
    ("Chris Bedia",          "CENTROAVANTE",     29, "Costa do Marfim",72),
    ("Alan Virginius",       "CENTROAVANTE",     22, "Franca",        70),
],
623: [  # Basel
    ("Marwin Hitz",          "GOLEIRO",          38, "Suica",         72),
    ("Mirko Salvi",          "GOLEIRO",          32, "Suica",         67),
    ("Jonas Adjetey",        "ZAGUEIRO",         21, "Gana",          72),
    ("Adrian Barisic",       "ZAGUEIRO",         24, "Croacia",       71),
    ("Finn van Breemen",     "ZAGUEIRO",         22, "Holanda",       71),
    ("Nicolas Vouilloz",     "ZAGUEIRO",         22, "Suica",         68),
    ("Flavius Daniliuc",     "LATERAL_DIREITO",  24, "Austria",       71),
    ("Metinho",              "LATERAL_DIREITO",  22, "Brasil",        70),
    ("Dominik Schmid",       "LATERAL_ESQUERDO", 27, "Suica",         71),
    ("Keigo Tsunemoto",      "LATERAL_ESQUERDO", 24, "Japao",         70),
    ("Leon Avdullahu",       "VOLANTE",          21, "Kosovo",        73),
    ("Taulant Xhaka",        "VOLANTE",          34, "Albania",       68),
    ("Xherdan Shaqiri",      "MEIA_CENTRAL",     34, "Suica",         77),
    ("Koba Koindredi",       "MEIA_CENTRAL",     24, "Franca",        71),
    ("Ibrahim Salah",        "MEIA_ATACANTE",    24, "Marrocos",      72),
    ("Philip Otele",         "PONTA_DIREITA",    26, "Nigeria",       72),
    ("Marin Soticek",        "PONTA_DIREITA",    24, "Croacia",       71),
    ("Jonathan Dubasin",     "PONTA_ESQUERDA",   25, "Espanha",       72),
    ("Albian Ajeti",         "CENTROAVANTE",     28, "Suica",         71),
    ("Bradley Fink",         "CENTROAVANTE",     22, "Alemanha",      70),
],

# ───────────────────────── Dinamarca ─────────────────────────────────────────
624: [  # Copenhague
    ("Theo Sander",          "GOLEIRO",          21, "Dinamarca",     70),
    ("Jacob Rinne",          "GOLEIRO",          32, "Suecia",        70),
    ("Pantelis Hatzidiakos", "ZAGUEIRO",         29, "Grecia",        74),
    ("Denis Vavro",          "ZAGUEIRO",         29, "Eslovaquia",    74),
    ("Gabriel Pereira",      "ZAGUEIRO",         26, "Brasil",        73),
    ("Villads Nielsen",      "ZAGUEIRO",         20, "Dinamarca",     69),
    ("Rodrigo Huescas",      "LATERAL_DIREITO",  22, "Mexico",        73),
    ("Tobias Christensen",   "LATERAL_DIREITO",  24, "Noruega",       70),
    ("Birger Meling",        "LATERAL_ESQUERDO", 31, "Noruega",       74),
    ("Marcos Lopez",         "LATERAL_ESQUERDO", 26, "Peru",          73),
    ("William Clem",         "VOLANTE",          24, "Dinamarca",     73),
    ("Robert Silva",         "VOLANTE",          23, "Uruguai",       71),
    ("Hakon Arnar Haraldsson","MEIA_CENTRAL",    22, "Islandia",      76),
    ("Oscar Hojlund",        "MEIA_CENTRAL",     20, "Dinamarca",     72),
    ("Mohamed Elyounoussi",  "MEIA_ATACANTE",    31, "Noruega",       74),
    ("Elias Achouri",        "PONTA_DIREITA",    26, "Tunisia",       75),
    ("Jordan Larsson",       "PONTA_DIREITA",    28, "Suecia",        73),
    ("Viktor Dadason",       "PONTA_ESQUERDA",   20, "Islandia",      70),
    ("Youssoufa Moukoko",    "CENTROAVANTE",     21, "Alemanha",      75),
    ("Filip Bundgaard",      "CENTROAVANTE",     20, "Dinamarca",     72),
],
625: [  # Midtjylland
    ("Elias Olafsson",       "GOLEIRO",          25, "Islandia",      72),
    ("Jonas Lossl",          "GOLEIRO",          36, "Dinamarca",     70),
    ("Mads Bech Sorensen",   "ZAGUEIRO",         26, "Dinamarca",     72),
    ("Stefan Gartenmann",    "ZAGUEIRO",         28, "Dinamarca",     71),
    ("Juninho",              "ZAGUEIRO",         25, "Brasil",        71),
    ("Valdemar Byskov",      "ZAGUEIRO",         22, "Dinamarca",     69),
    ("Oliver Sorensen",      "LATERAL_DIREITO",  23, "Dinamarca",     70),
    ("Daniel Gaarde",        "LATERAL_DIREITO",  22, "Dinamarca",     69),
    ("Nikolas Dyhr",         "LATERAL_ESQUERDO", 24, "Dinamarca",     71),
    ("Mikel Gogorza",        "LATERAL_ESQUERDO", 27, "Espanha",       70),
    ("Mads Bidstrup",        "VOLANTE",          24, "Dinamarca",     73),
    ("Aral Simsir",          "VOLANTE",          24, "Dinamarca",     72),
    ("Charles",              "MEIA_CENTRAL",     24, "Brasil",        72),
    ("Paulinho",             "MEIA_CENTRAL",     25, "Brasil",        71),
    ("Dario Osorio",         "MEIA_ATACANTE",    22, "Chile",         74),
    ("Philip Billing",       "PONTA_DIREITA",    29, "Dinamarca",     74),
    ("Ousmane Diao",         "PONTA_DIREITA",    22, "Senegal",       70),
    ("Simon Hedlund",        "PONTA_ESQUERDA",   32, "Suecia",        70),
    ("Franculino Dju",       "CENTROAVANTE",     21, "Guine-Bissau",  77),
    ("Adam Buksa",           "CENTROAVANTE",     29, "Polonia",       73),
],

# ───────────────────────── Noruega ───────────────────────────────────────────
626: [  # Bodo Glimt
    ("Nikita Haikin",        "GOLEIRO",          30, "Russia",        73),
    ("Julian Faye Lund",     "GOLEIRO",          25, "Noruega",       69),
    ("Odin Bjortuft",        "ZAGUEIRO",         25, "Noruega",       73),
    ("Jostein Gundersen",    "ZAGUEIRO",         29, "Noruega",       72),
    ("Brede Moe",            "ZAGUEIRO",         34, "Noruega",       71),
    ("Isak Maattaa",         "ZAGUEIRO",         22, "Suecia",        68),
    ("Fredrik Sjovold",      "LATERAL_DIREITO",  22, "Noruega",       70),
    ("Brice Wembangomo",     "LATERAL_DIREITO",  28, "Noruega",       70),
    ("Fredrik Bjorkan",      "LATERAL_ESQUERDO", 27, "Noruega",       73),
    ("Gaute Vetti",          "LATERAL_ESQUERDO", 22, "Noruega",       68),
    ("Patrick Berg",         "VOLANTE",          28, "Noruega",       76),
    ("Ulrik Saltnes",        "VOLANTE",          33, "Noruega",       72),
    ("Hakon Evjen",          "MEIA_CENTRAL",     25, "Noruega",       74),
    ("Sondre Brunstad Fet",  "MEIA_CENTRAL",     27, "Noruega",       71),
    ("Jens Petter Hauge",    "MEIA_ATACANTE",    26, "Noruega",       75),
    ("Ole Didrik Blomberg",  "PONTA_DIREITA",    24, "Noruega",       72),
    ("Daniel Bassi",         "PONTA_DIREITA",    24, "Noruega",       72),
    ("Sondre Sorli",         "PONTA_ESQUERDA",   25, "Noruega",       71),
    ("Kasper Hogh",          "CENTROAVANTE",     25, "Dinamarca",     74),
    ("Andreas Helmersen",    "CENTROAVANTE",     26, "Noruega",       71),
],

# ───────────────────────── Croacia ───────────────────────────────────────────
627: [  # Dinamo Zagreb
    ("Ivan Nevistic",        "GOLEIRO",          27, "Croacia",       73),
    ("Danijel Zagorac",      "GOLEIRO",          38, "Croacia",       67),
    ("Scott McKenna",        "ZAGUEIRO",         29, "Escocia",       74),
    ("Maximilian Hjalde",    "ZAGUEIRO",         24, "Suecia",        72),
    ("Sergi Dominguez",      "ZAGUEIRO",         20, "Espanha",       71),
    ("Mauro Perkovic",       "ZAGUEIRO",         22, "Croacia",       69),
    ("Sadegh Moharrami",     "LATERAL_DIREITO",  29, "Ira",           72),
    ("Luka Stojkovic",       "LATERAL_DIREITO",  22, "Croacia",       70),
    ("Niko Galesic",         "LATERAL_ESQUERDO", 25, "Croacia",       71),
    ("Raul Torrente",        "LATERAL_ESQUERDO", 23, "Espanha",       71),
    ("Josip Misic",          "VOLANTE",          31, "Croacia",       74),
    ("Juan Cordoba",         "VOLANTE",          22, "Colombia",      71),
    ("Martin Baturina",      "MEIA_CENTRAL",     22, "Croacia",       77),
    ("Marko Soldo",          "MEIA_CENTRAL",     22, "Croacia",       71),
    ("Dario Spikic",         "MEIA_ATACANTE",    26, "Croacia",       72),
    ("Arber Hoxha",          "PONTA_DIREITA",    26, "Albania",       73),
    ("Takuya Ogiwara",       "PONTA_DIREITA",    24, "Japao",         71),
    ("Monsef Bakrar",        "PONTA_ESQUERDA",   24, "Argelia",       73),
    ("Sandro Kulenovic",     "CENTROAVANTE",     26, "Croacia",       74),
    ("Dion Drena Beljo",     "CENTROAVANTE",     23, "Croacia",       73),
],

# ───────────────────────── Servia ────────────────────────────────────────────
628: [  # Estrela Vermelha
    ("Omri Glazer",          "GOLEIRO",          29, "Israel",        74),
    ("Ivan Gutesa",          "GOLEIRO",          23, "Servia",        68),
    ("Uros Spajic",          "ZAGUEIRO",         32, "Servia",        73),
    ("Aleksandar Dragovic",  "ZAGUEIRO",         34, "Austria",       72),
    ("Rodrigao",             "ZAGUEIRO",         30, "Brasil",        72),
    ("Veljko Milosavljevic", "ZAGUEIRO",         18, "Servia",        71),
    ("Ognjen Mimovic",       "LATERAL_DIREITO",  21, "Servia",        70),
    ("Seol Young-woo",       "LATERAL_DIREITO",  27, "Coreia do Sul", 71),
    ("Milan Rodic",          "LATERAL_ESQUERDO", 34, "Servia",        70),
    ("Nikola Stankovic",     "LATERAL_ESQUERDO", 22, "Servia",        69),
    ("Mirko Ivanic",         "VOLANTE",          32, "Montenegro",    74),
    ("Timi Elsnik",          "VOLANTE",          27, "Eslovenia",     72),
    ("Rade Krunic",          "MEIA_CENTRAL",     32, "Bosnia",        74),
    ("Vasilije Kostov",      "MEIA_CENTRAL",     20, "Servia",        70),
    ("Andrija Maksimovic",   "MEIA_ATACANTE",    18, "Servia",        73),
    ("Silas Katompa",        "PONTA_DIREITA",    27, "RD Congo",      75),
    ("Nemanja Radonjic",     "PONTA_DIREITA",    29, "Servia",        73),
    ("Felicio Milson",       "PONTA_ESQUERDA",   24, "Angola",        72),
    ("Cherif Ndiaye",        "CENTROAVANTE",     29, "Senegal",       74),
    ("Marko Arnautovic",     "CENTROAVANTE",     36, "Austria",       74),
],

# ───────────────────────── Polonia ───────────────────────────────────────────
629: [  # Legia Varsovia
    ("Kacper Tobiasz",       "GOLEIRO",          23, "Polonia",       71),
    ("Dominik Hladun",       "GOLEIRO",          30, "Polonia",       68),
    ("Steve Kapuadi",        "ZAGUEIRO",         27, "Franca",        72),
    ("Radovan Pankov",       "ZAGUEIRO",         31, "Servia",        71),
    ("Jan Ziolkowski",       "ZAGUEIRO",         20, "Polonia",       71),
    ("Wojciech Urbanski",    "ZAGUEIRO",         22, "Polonia",       68),
    ("Pawel Wszolek",        "LATERAL_DIREITO",  33, "Polonia",       71),
    ("Ruben Vinagre",        "LATERAL_DIREITO",  26, "Portugal",      72),
    ("Patryk Kun",           "LATERAL_ESQUERDO", 30, "Polonia",       70),
    ("Arkadiusz Reca",       "LATERAL_ESQUERDO", 30, "Polonia",       70),
    ("Juergen Elitim",       "VOLANTE",          26, "Colombia",      72),
    ("Rafal Augustyniak",    "VOLANTE",          32, "Polonia",       71),
    ("Bartosz Kapustka",     "MEIA_CENTRAL",     29, "Polonia",       72),
    ("Claude Goncalves",     "MEIA_CENTRAL",     31, "Portugal",      70),
    ("Kacper Chodyna",       "MEIA_ATACANTE",    27, "Polonia",       70),
    ("Luquinhas",            "PONTA_DIREITA",    29, "Brasil",        72),
    ("Petar Stojanovic",     "PONTA_DIREITA",    30, "Eslovenia",     71),
    ("Migouel Alfarela",     "PONTA_ESQUERDA",   27, "Franca",        70),
    ("Mileta Rajovic",       "CENTROAVANTE",     26, "Servia",        72),
    ("Jean-Pierre Nsame",    "CENTROAVANTE",     32, "Camaroes",      71),
],

# ───────────────────────── Suecia ────────────────────────────────────────────
630: [  # Malmo FF
    ("Johan Dahlin",         "GOLEIRO",          39, "Suecia",        70),
    ("Ismael Diawara",       "GOLEIRO",          24, "Suecia",        67),
    ("Dennis Hadzikadunic",  "ZAGUEIRO",         27, "Bosnia",        71),
    ("Pontus Jansson",       "ZAGUEIRO",         34, "Suecia",        71),
    ("Colin Rosler",         "ZAGUEIRO",         25, "Alemanha",      69),
    ("Hugo Bolin",           "ZAGUEIRO",         22, "Suecia",        68),
    ("Jens Stryger Larsen",  "LATERAL_DIREITO",  34, "Dinamarca",     71),
    ("Noel Tornqvist",       "LATERAL_DIREITO",  22, "Suecia",        68),
    ("Gabriel Busanello",    "LATERAL_ESQUERDO", 27, "Brasil",        70),
    ("Martin Sviberg",       "LATERAL_ESQUERDO", 22, "Suecia",        67),
    ("Lasse Berg Johnsen",   "VOLANTE",          26, "Noruega",       71),
    ("Adi Nalic",            "VOLANTE",          26, "Bosnia",        70),
    ("Sergio Pena",          "MEIA_CENTRAL",     30, "Peru",          72),
    ("Otto Rosengren",       "MEIA_CENTRAL",     23, "Suecia",        69),
    ("Taha Ali",             "MEIA_ATACANTE",    27, "Noruega",       71),
    ("Arnor Sigurdsson",     "PONTA_DIREITA",    26, "Islandia",      72),
    ("Oliver Berg",          "PONTA_DIREITA",    32, "Suecia",        70),
    ("Sead Haksabanovic",    "PONTA_ESQUERDA",   26, "Montenegro",    72),
    ("Erik Botheim",         "CENTROAVANTE",     25, "Noruega",       73),
    ("Mohamed Buya Turay",   "CENTROAVANTE",     30, "Serra Leoa",    71),
],

# ───────────────────────── Romenia ───────────────────────────────────────────
631: [  # FCSB
    ("Stefan Tarnovanu",     "GOLEIRO",          25, "Romenia",       71),
    ("Lukas Zima",           "GOLEIRO",          31, "Tchequia",      68),
    ("Siyabonga Ngezana",    "ZAGUEIRO",         28, "Africa do Sul", 71),
    ("Mihai Popescu",        "ZAGUEIRO",         32, "Romenia",       70),
    ("Joyskim Dawa",         "ZAGUEIRO",         29, "Camaroes",      70),
    ("Daniel Graovac",       "ZAGUEIRO",         32, "Bosnia",        69),
    ("Alexandru Pantea",     "LATERAL_DIREITO",  24, "Romenia",       69),
    ("Valentin Cretu",       "LATERAL_DIREITO",  36, "Romenia",       68),
    ("Risto Radunovic",      "LATERAL_ESQUERDO", 33, "Montenegro",    70),
    ("Vlad Mihalcea",        "LATERAL_ESQUERDO", 21, "Romenia",       66),
    ("Marius Marin",         "VOLANTE",          27, "Romenia",       71),
    ("Adrian Sut",           "VOLANTE",          27, "Romenia",       70),
    ("Darius Olaru",         "MEIA_CENTRAL",     27, "Romenia",       73),
    ("Mihai Lixandru",       "MEIA_CENTRAL",     23, "Romenia",       69),
    ("Florin Tanase",        "MEIA_ATACANTE",    31, "Romenia",       73),
    ("Dennis Politic",       "PONTA_DIREITA",    25, "Romenia",       71),
    ("David Miculescu",      "PONTA_DIREITA",    24, "Romenia",       70),
    ("Juri Cisotti",         "PONTA_ESQUERDA",   32, "Italia",        69),
    ("Daniel Birligea",      "CENTROAVANTE",     25, "Romenia",       72),
    ("Alexandru Stoian",     "CENTROAVANTE",     19, "Romenia",       67),
],

# ══════════ Ampliacao do pool para a Europa League (ver europa_league.md) ═════
# Mesmo tamanho de elenco dos 30 primeiros: 20 jogadores, 2 deles goleiros.

# ───────────────────────── Alemanha (Europa League) ──────────────────────────
632: [  # Eintracht Frankfurt
    ("Kaua Santos",           "GOLEIRO",          24, "Brasil",         76),
    ("Michael Zetterer",      "GOLEIRO",          30, "Alemanha",       74),
    ("Robin Koch",            "ZAGUEIRO",         29, "Alemanha",       80),
    ("Arthur Theate",         "ZAGUEIRO",         25, "Belgica",        77),
    ("Rasmus Kristensen",     "ZAGUEIRO",         28, "Dinamarca",      75),
    ("Nnamdi Collins",        "LATERAL_DIREITO",  22, "Alemanha",       73),
    ("Nathaniel Brown",       "LATERAL_ESQUERDO", 22, "Alemanha",       73),
    ("Hugo Larsson",          "VOLANTE",          21, "Suecia",         79),
    ("Ellyes Skhiri",         "VOLANTE",          30, "Tunisia",        77),
    ("Oscar Hojlund",         "VOLANTE",          20, "Dinamarca",      70),
    ("Mario Gotze",           "MEIA_CENTRAL",     33, "Alemanha",       76),
    ("Can Uzun",              "MEIA_CENTRAL",     20, "Turquia",        76),
    ("Fares Chaibi",          "MEIA_ATACANTE",    23, "Argelia",        74),
    ("Ansgar Knauff",         "PONTA_DIREITA",    23, "Alemanha",       76),
    ("Jean-Matteo Bahoya",    "PONTA_DIREITA",    20, "Franca",         74),
    ("Ritsu Doan",            "PONTA_ESQUERDA",   27, "Japao",          79),
    ("Elye Wahi",             "PONTA_ESQUERDA",   23, "Franca",         75),
    ("Jonathan Burkardt",     "CENTROAVANTE",     25, "Alemanha",       79),
    ("Michy Batshuayi",       "CENTROAVANTE",     32, "Belgica",        72),
    ("Igor Matanovic",        "SEGUNDA_ATACANTE", 22, "Croacia",        72),
],

633: [  # VfB Stuttgart
    ("Alexander Nubel",       "GOLEIRO",          29, "Alemanha",       80),
    ("Fabian Bredlow",        "GOLEIRO",          30, "Alemanha",       72),
    ("Jeff Chabot",           "ZAGUEIRO",         27, "Alemanha",       76),
    ("Ramon Hendriks",        "ZAGUEIRO",         24, "Holanda",        73),
    ("Finn Jeltsch",          "ZAGUEIRO",         19, "Alemanha",       71),
    ("Josha Vagnoman",        "LATERAL_DIREITO",  25, "Alemanha",       73),
    ("Maximilian Mittelstadt","LATERAL_ESQUERDO", 29, "Alemanha",       78),
    ("Angelo Stiller",        "VOLANTE",          24, "Alemanha",       80),
    ("Atakan Karazor",        "VOLANTE",          29, "Turquia",        75),
    ("Yannik Keitel",         "VOLANTE",          25, "Alemanha",       71),
    ("Chema Andres",          "MEIA_CENTRAL",     21, "Espanha",        72),
    ("Bilal El Khannouss",    "MEIA_CENTRAL",     21, "Marrocos",       76),
    ("Enzo Millot",           "MEIA_ATACANTE",    23, "Franca",         77),
    ("Chris Fuhrich",         "PONTA_DIREITA",    28, "Alemanha",       76),
    ("Jamie Leweling",        "PONTA_DIREITA",    24, "Alemanha",       75),
    ("El Bilal Toure",        "PONTA_ESQUERDA",   24, "Mali",           74),
    ("Lazar Jovanovic",       "PONTA_ESQUERDA",   21, "Servia",         70),
    ("Nick Woltemade",        "CENTROAVANTE",     24, "Alemanha",       78),
    ("Deniz Undav",           "CENTROAVANTE",     29, "Alemanha",       78),
    ("Ermedin Demirovic",     "SEGUNDA_ATACANTE", 27, "Bosnia",         76),
],

# ───────────────────────── Franca (Europa League) ────────────────────────────
634: [  # Lille
    ("Berke Ozer",            "GOLEIRO",          25, "Turquia",        76),
    ("Arnaud Bodart",         "GOLEIRO",          27, "Belgica",        73),
    ("Alexsandro",            "ZAGUEIRO",         26, "Brasil",         78),
    ("Aissa Mandi",           "ZAGUEIRO",         33, "Argelia",        75),
    ("Nathan Ngoy",           "ZAGUEIRO",         22, "Belgica",        73),
    ("Thomas Meunier",        "LATERAL_DIREITO",  34, "Belgica",        74),
    ("Romain Perraud",        "LATERAL_ESQUERDO", 28, "Franca",         74),
    ("Benjamin Andre",        "VOLANTE",          35, "Franca",         76),
    ("Ngalayel Mukau",        "VOLANTE",          21, "Congo",          74),
    ("Ayyoub Bouaddi",        "VOLANTE",          18, "Franca",         74),
    ("Nabil Bentaleb",        "MEIA_CENTRAL",     31, "Argelia",        75),
    ("Felix Correia",         "MEIA_CENTRAL",     24, "Portugal",       73),
    ("Matias Fernandez-Pardo","MEIA_ATACANTE",    20, "Belgica",        74),
    ("Osame Sahraoui",        "PONTA_DIREITA",    24, "Noruega",        76),
    ("Ethan Mbappe",          "PONTA_DIREITA",    18, "Franca",         71),
    ("Tiago Santos",          "PONTA_ESQUERDA",   23, "Portugal",       74),
    ("Marius Broholm",        "PONTA_ESQUERDA",   22, "Noruega",        70),
    ("Olivier Giroud",        "CENTROAVANTE",     39, "Franca",         74),
    ("Hamza Igamane",         "CENTROAVANTE",     23, "Marrocos",       74),
    ("Mohamed Bayo",          "SEGUNDA_ATACANTE", 27, "Guine",          73),
],

635: [  # Olympique Lyonnais
    ("Dominik Greif",         "GOLEIRO",          28, "Eslovaquia",     75),
    ("Remy Descamps",         "GOLEIRO",          29, "Franca",         73),
    ("Moussa Niakhate",       "ZAGUEIRO",         29, "Senegal",        77),
    ("Clinton Mata",          "ZAGUEIRO",         33, "Angola",         76),
    ("Ruben Kluivert",        "ZAGUEIRO",         24, "Holanda",        73),
    ("Ainsley Maitland-Niles","LATERAL_DIREITO",  28, "Inglaterra",     74),
    ("Nicolas Tagliafico",    "LATERAL_ESQUERDO", 33, "Argentina",      76),
    ("Corentin Tolisso",      "VOLANTE",          31, "Franca",         77),
    ("Pavel Sulc",            "VOLANTE",          25, "Tchequia",       75),
    ("Tanner Tessmann",       "VOLANTE",          24, "Estados Unidos", 74),
    ("Tyler Morton",          "MEIA_CENTRAL",     23, "Inglaterra",     74),
    ("Khalis Merah",          "MEIA_CENTRAL",     19, "Franca",         70),
    ("Rachid Ghezzal",        "MEIA_ATACANTE",    33, "Argelia",        72),
    ("Malick Fofana",         "PONTA_DIREITA",    20, "Belgica",        78),
    ("Adam Karabec",          "PONTA_DIREITA",    22, "Tchequia",       73),
    ("Abner",                 "PONTA_ESQUERDA",   25, "Brasil",         74),
    ("Afonso Moreira",        "PONTA_ESQUERDA",   20, "Portugal",       72),
    ("Georges Mikautadze",    "CENTROAVANTE",     25, "Georgia",        78),
    ("Martin Satriano",       "CENTROAVANTE",     24, "Uruguai",        74),
    ("Enzo Molebe",           "SEGUNDA_ATACANTE", 18, "Franca",         69),
],

# ───────────────────────── Holanda (Europa League) ───────────────────────────
636: [  # Feyenoord
    ("Timon Wellenreuther",   "GOLEIRO",          30, "Alemanha",       76),
    ("Mio Backhaus",          "GOLEIRO",          21, "Alemanha",       72),
    ("Tsuyoshi Watanabe",     "ZAGUEIRO",         29, "Japao",          76),
    ("Gernot Trauner",        "ZAGUEIRO",         33, "Austria",        76),
    ("Thomas Beelen",         "ZAGUEIRO",         21, "Holanda",        74),
    ("Givairo Read",          "LATERAL_DIREITO",  19, "Holanda",        75),
    ("Gijs Smal",             "LATERAL_ESQUERDO", 28, "Holanda",        74),
    ("Quinten Timber",        "VOLANTE",          24, "Holanda",        79),
    ("In-beom Hwang",         "VOLANTE",          29, "Coreia do Sul",  76),
    ("Quilindschy Hartman",   "VOLANTE",          24, "Holanda",        74),
    ("Jakub Moder",           "MEIA_CENTRAL",     26, "Polonia",        75),
    ("Luciano Valente",       "MEIA_CENTRAL",     22, "Holanda",        74),
    ("Antoni Milambo",        "MEIA_ATACANTE",    20, "Holanda",        76),
    ("Igor Paixao",           "PONTA_DIREITA",    25, "Brasil",         79),
    ("Anis Hadj Moussa",      "PONTA_DIREITA",    23, "Argelia",        75),
    ("Goncalo Borges",        "PONTA_ESQUERDA",   24, "Portugal",       73),
    ("Oussama Targhalline",   "PONTA_ESQUERDA",   23, "Marrocos",       72),
    ("Ayase Ueda",            "CENTROAVANTE",     27, "Japao",          78),
    ("Ibrahim Osman",         "CENTROAVANTE",     21, "Gana",           73),
    ("Casper Tengstedt",      "SEGUNDA_ATACANTE", 25, "Dinamarca",      74),
],

# ───────────────────────── Portugal (Europa League) ──────────────────────────
637: [  # Benfica
    ("Anatoliy Trubin",       "GOLEIRO",          24, "Ucrania",        84),
    ("Samuel Soares",         "GOLEIRO",          23, "Portugal",       74),
    ("Nicolas Otamendi",      "ZAGUEIRO",         37, "Argentina",      82),
    ("Antonio Silva",         "ZAGUEIRO",         22, "Portugal",       81),
    ("Tomas Araujo",          "ZAGUEIRO",         23, "Portugal",       79),
    ("Alexander Bah",         "LATERAL_DIREITO",  28, "Dinamarca",      78),
    ("Alvaro Carreras",       "LATERAL_ESQUERDO", 22, "Espanha",        81),
    ("Florentino Luis",       "VOLANTE",          26, "Portugal",       82),
    ("Leandro Barreiro",      "VOLANTE",          25, "Luxemburgo",     77),
    ("Enzo Barrenechea",      "VOLANTE",          24, "Argentina",      77),
    ("Orkun Kokcu",           "MEIA_CENTRAL",     25, "Turquia",        81),
    ("Fredrik Aursnes",       "MEIA_CENTRAL",     30, "Noruega",        80),
    ("Dodi Lukebakio",        "MEIA_ATACANTE",    28, "Belgica",        79),
    ("Kerem Akturkoglu",      "PONTA_DIREITA",    27, "Turquia",        80),
    ("Andreas Schjelderup",   "PONTA_DIREITA",    21, "Noruega",        77),
    ("Gianluca Prestianni",   "PONTA_ESQUERDA",   19, "Argentina",      76),
    ("Franjo Ivanovic",       "PONTA_ESQUERDA",   22, "Croacia",        76),
    ("Vangelis Pavlidis",     "CENTROAVANTE",     27, "Grecia",         81),
    ("Henrique Araujo",       "CENTROAVANTE",     23, "Portugal",       73),
    ("Zeki Amdouni",          "SEGUNDA_ATACANTE", 25, "Suica",          77),
],

# ───────────────────────── Belgica (Europa League) ───────────────────────────
638: [  # Union Saint-Gilloise
    ("Kjell Scherpen",        "GOLEIRO",          25, "Holanda",        74),
    ("Vic Chambaere",         "GOLEIRO",          22, "Belgica",        68),
    ("Christian Burgess",     "ZAGUEIRO",         34, "Inglaterra",     73),
    ("Ross Sykes",            "ZAGUEIRO",         26, "Inglaterra",     72),
    ("Fedde Leysen",          "ZAGUEIRO",         20, "Belgica",        71),
    ("Kevin Mac Allister",    "LATERAL_DIREITO",  27, "Argentina",      74),
    ("Guilherme Smith",       "LATERAL_ESQUERDO", 22, "Brasil",         69),
    ("Charles Vanhoutte",     "VOLANTE",          27, "Belgica",        74),
    ("Adem Zorgane",          "VOLANTE",          25, "Argelia",        73),
    ("Mathias Rasmussen",     "VOLANTE",          28, "Noruega",        71),
    ("Cameron Puertas",       "MEIA_CENTRAL",     27, "Suica",          76),
    ("Alessio Castro-Montes", "MEIA_CENTRAL",     28, "Belgica",        71),
    ("Noah Sadiki",           "MEIA_ATACANTE",    20, "Congo",          74),
    ("Kevin Rodriguez",       "PONTA_DIREITA",    25, "Equador",        72),
    ("Anan Khalaili",         "PONTA_DIREITA",    23, "Israel",         71),
    ("Anouar Ait El Hadj",    "PONTA_ESQUERDA",   23, "Belgica",        73),
    ("Dennis Eckert Ayensa",  "PONTA_ESQUERDA",   28, "Espanha",        70),
    ("Promise David",         "CENTROAVANTE",     24, "Canada",         76),
    ("Raul Florucz",          "CENTROAVANTE",     24, "Austria",        74),
    ("Mohammed Fuseini",      "SEGUNDA_ATACANTE", 22, "Gana",           70),
],

# ───────────────────────── Tchequia (Europa League) ──────────────────────────
639: [  # Sparta Praga
    ("Peter Vindahl",         "GOLEIRO",          27, "Dinamarca",      73),
    ("Milan Heca",            "GOLEIRO",          34, "Tchequia",       67),
    ("Asger Sorensen",        "ZAGUEIRO",         29, "Dinamarca",      73),
    ("Martin Vitik",          "ZAGUEIRO",         22, "Tchequia",       72),
    ("Filip Panak",           "ZAGUEIRO",         29, "Tchequia",       72),
    ("Jaroslav Zeleny",       "LATERAL_DIREITO",  33, "Tchequia",       69),
    ("Angelo Preciado",       "LATERAL_ESQUERDO", 27, "Equador",        72),
    ("Kaan Kairinen",         "VOLANTE",          27, "Finlandia",      73),
    ("Lukas Sadilek",         "VOLANTE",          28, "Tchequia",       71),
    ("Qazim Laci",            "VOLANTE",          29, "Albania",        71),
    ("Veljko Birmancevic",    "MEIA_CENTRAL",     27, "Servia",         73),
    ("Matej Rynes",           "MEIA_CENTRAL",     25, "Tchequia",       69),
    ("Lukas Haraslin",        "MEIA_ATACANTE",    29, "Eslovaquia",     74),
    ("Pavel Kaderabek",       "PONTA_DIREITA",    33, "Tchequia",       70),
    ("Victor Olatunji",       "PONTA_DIREITA",    26, "Nigeria",        72),
    ("Mikael Soisalo",        "PONTA_ESQUERDA",   28, "Finlandia",      70),
    ("John Mercado",          "PONTA_ESQUERDA",   22, "Equador",        69),
    ("Albion Rrahmani",       "CENTROAVANTE",     25, "Albania",        74),
    ("Jan Kuchta",            "CENTROAVANTE",     28, "Tchequia",       72),
    ("Daniel Vasulin",        "SEGUNDA_ATACANTE", 24, "Tchequia",       68),
],

# ───────────────────────── Turquia (Europa League) ───────────────────────────
640: [  # Fenerbahce
    ("Dominik Livakovic",     "GOLEIRO",          31, "Croacia",        80),
    ("Irfan Can Egribayat",   "GOLEIRO",          27, "Turquia",        74),
    ("Milan Skriniar",        "ZAGUEIRO",         31, "Eslovaquia",     82),
    ("Jayden Oosterwolde",    "ZAGUEIRO",         24, "Holanda",        78),
    ("Caglar Soyuncu",        "ZAGUEIRO",         29, "Turquia",        77),
    ("Mert Muldur",           "LATERAL_DIREITO",  26, "Turquia",        76),
    ("Levent Mercan",         "LATERAL_ESQUERDO", 25, "Turquia",        74),
    ("Fred",                  "VOLANTE",          32, "Brasil",         79),
    ("Ismail Yuksek",         "VOLANTE",          26, "Turquia",        77),
    ("Mert Hakan Yandas",     "VOLANTE",          31, "Turquia",        73),
    ("Marco Asensio",         "MEIA_CENTRAL",     29, "Espanha",        82),
    ("Sofyan Amrabat",        "MEIA_CENTRAL",     29, "Marrocos",       78),
    ("Irfan Can Kahveci",     "MEIA_ATACANTE",    30, "Turquia",        78),
    ("Allan Saint-Maximin",   "PONTA_DIREITA",    28, "Franca",         77),
    ("Oguz Aydin",            "PONTA_DIREITA",    25, "Turquia",        73),
    ("Sebastian Szymanski",   "PONTA_ESQUERDA",   26, "Polonia",        79),
    ("Anderson Talisca",      "PONTA_ESQUERDA",   31, "Brasil",         78),
    ("Youssef En-Nesyri",     "CENTROAVANTE",     28, "Marrocos",       80),
    ("Cenk Tosun",            "CENTROAVANTE",     34, "Turquia",        71),
    ("Jhon Duran",            "SEGUNDA_ATACANTE", 22, "Colombia",       78),
],

# ───────────────────────── Ucrania (Europa League) ───────────────────────────
641: [  # Dinamo Kiev
    ("Ruslan Neshcheret",     "GOLEIRO",          23, "Ucrania",        73),
    ("Georgiy Bushchan",      "GOLEIRO",          31, "Ucrania",        73),
    ("Denys Popov",           "ZAGUEIRO",         26, "Ucrania",        72),
    ("Oleksandr Syrota",      "ZAGUEIRO",         25, "Ucrania",        71),
    ("Mykola Mykhailenko",    "ZAGUEIRO",         27, "Ucrania",        70),
    ("Oleksandr Tymchyk",     "LATERAL_DIREITO",  28, "Ucrania",        72),
    ("Kristian Bilovar",      "LATERAL_ESQUERDO", 26, "Ucrania",        69),
    ("Volodymyr Brazhko",     "VOLANTE",          23, "Ucrania",        74),
    ("Mykola Shaparenko",     "VOLANTE",          27, "Ucrania",        74),
    ("Oleksandr Pikhalyonok", "VOLANTE",          27, "Ucrania",        70),
    ("Vladyslav Kabaev",      "MEIA_CENTRAL",     29, "Ucrania",        73),
    ("Oleksandr Yatsyk",      "MEIA_CENTRAL",     26, "Ucrania",        69),
    ("Nazar Voloshyn",        "MEIA_ATACANTE",    23, "Ucrania",        72),
    ("Andriy Yarmolenko",     "PONTA_DIREITA",    36, "Ucrania",        72),
    ("Vitaliy Buyalskyi",     "PONTA_DIREITA",    32, "Ucrania",        72),
    ("Vladyslav Dubinchak",   "PONTA_ESQUERDA",   26, "Ucrania",        69),
    ("Matviy Ponomarenko",    "PONTA_ESQUERDA",   19, "Ucrania",        67),
    ("Vladyslav Vanat",       "CENTROAVANTE",     23, "Ucrania",        76),
    ("Nazarii Rusyn",         "CENTROAVANTE",     27, "Ucrania",        70),
    ("Eduardo Guerrero",      "SEGUNDA_ATACANTE", 25, "Panama",         69),
],

# ───────────────── Escocia, Austria, Suica (Europa League) ───────────────────
642: [  # Hearts
    ("Craig Gordon",          "GOLEIRO",          42, "Escocia",        68),
    ("Zander Clark",          "GOLEIRO",          33, "Escocia",        67),
    ("Frankie Kent",          "ZAGUEIRO",         30, "Inglaterra",     67),
    ("Stuart Findlay",        "ZAGUEIRO",         30, "Escocia",        66),
    ("Michael Steinwender",   "ZAGUEIRO",         26, "Austria",        66),
    ("Adam Forrester",        "LATERAL_DIREITO",  22, "Escocia",        63),
    ("Oisin McEntee",         "LATERAL_ESQUERDO", 25, "Irlanda",        64),
    ("Beni Baningime",        "VOLANTE",          27, "Congo",          68),
    ("Cammy Devlin",          "VOLANTE",          27, "Australia",      66),
    ("Harry Milne",           "VOLANTE",          27, "Escocia",        63),
    ("Blair Spittal",         "MEIA_CENTRAL",     30, "Escocia",        67),
    ("Sander Kartum",         "MEIA_CENTRAL",     30, "Noruega",        66),
    ("Alexandros Kyziridis",  "MEIA_ATACANTE",    26, "Grecia",         66),
    ("Elton Kabangu",         "PONTA_DIREITA",    27, "Belgica",        66),
    ("Yan Dhanda",            "PONTA_DIREITA",    27, "Inglaterra",     65),
    ("Musa Drammeh",          "PONTA_ESQUERDA",   23, "Espanha",        63),
    ("James Wilson",          "PONTA_ESQUERDA",   19, "Escocia",        62),
    ("Lawrence Shankland",    "CENTROAVANTE",     30, "Escocia",        70),
    ("Claudio Braga",         "CENTROAVANTE",     26, "Portugal",       65),
    ("Kenneth Vargas",        "SEGUNDA_ATACANTE", 24, "Costa Rica",     64),
],

643: [  # LASK
    ("Jorg Siebenhandl",      "GOLEIRO",          35, "Austria",        68),
    ("Tobias Lawal",          "GOLEIRO",          26, "Austria",        68),
    ("Philipp Ziereis",       "ZAGUEIRO",         32, "Alemanha",       68),
    ("Maksym Talovierov",     "ZAGUEIRO",         25, "Ucrania",        68),
    ("Jurgen Berg",           "ZAGUEIRO",         24, "Austria",        65),
    ("Andres Andrade",        "LATERAL_DIREITO",  27, "Panama",         66),
    ("Flavius Daniliuc",      "LATERAL_ESQUERDO", 24, "Austria",        68),
    ("Sascha Horvath",        "VOLANTE",          29, "Austria",        66),
    ("Branko Jovicic",        "VOLANTE",          32, "Servia",         66),
    ("Ibrahim Mustapha",      "VOLANTE",          23, "Gana",           64),
    ("Robert Zulj",           "MEIA_CENTRAL",     34, "Austria",        69),
    ("Jerome Boateng",        "MEIA_CENTRAL",     24, "Austria",        65),
    ("Moussa Koulibaly",      "MEIA_ATACANTE",    24, "Guine",          65),
    ("Ousmane Diakite",       "PONTA_DIREITA",    25, "Mali",           67),
    ("Valon Berisha",         "PONTA_DIREITA",    33, "Kosovo",         67),
    ("Maurice Malone",        "PONTA_ESQUERDA",   25, "Alemanha",       65),
    ("Florian Flecker",       "PONTA_ESQUERDA",   30, "Austria",        64),
    ("Adil Taoui",            "CENTROAVANTE",     24, "Franca",         66),
    ("Rene Renner",           "CENTROAVANTE",     32, "Austria",        64),
    ("Thomas Goiginger",      "SEGUNDA_ATACANTE", 32, "Austria",        66),
],

644: [  # Servette
    ("Joel Mall",             "GOLEIRO",          34, "Suica",          67),
    ("Jeremy Frick",          "GOLEIRO",          32, "Suica",          66),
    ("Steve Rouiller",        "ZAGUEIRO",         35, "Suica",          66),
    ("Yoan Severin",          "ZAGUEIRO",         28, "Suica",          65),
    ("Keyliane Abdallah",     "ZAGUEIRO",         21, "Franca",         62),
    ("Bradley Mazikou",       "LATERAL_DIREITO",  29, "Congo",          64),
    ("Jonathan Fabbro",       "LATERAL_ESQUERDO", 24, "Suica",          62),
    ("Timothe Cognat",        "VOLANTE",          27, "Franca",         67),
    ("Gael Ondoua",           "VOLANTE",          30, "Camaroes",       65),
    ("Lilian Njoh",           "VOLANTE",          23, "Suica",          62),
    ("Alexis Antunes",        "MEIA_CENTRAL",     24, "Peru",           65),
    ("Florian Ayer",          "MEIA_CENTRAL",     26, "Suica",          63),
    ("Miroslav Stevanovic",   "MEIA_ATACANTE",    35, "Bosnia",         66),
    ("Dereck Kutesa",         "PONTA_DIREITA",    28, "Suica",          68),
    ("Enzo Crivelli",         "PONTA_DIREITA",    30, "Franca",         64),
    ("Jeremy Guillemenot",    "PONTA_ESQUERDA",   27, "Suica",          64),
    ("Theo Magnin",           "PONTA_ESQUERDA",   22, "Suica",          61),
    ("Chris Bedia",           "CENTROAVANTE",     29, "Costa do Marfim",66),
    ("Lamine Fomba",          "CENTROAVANTE",     27, "Mali",           63),
    ("Alexis Jaquerod",       "SEGUNDA_ATACANTE", 23, "Suica",          61),
],

# ───────────────────────── Servia (Europa League) ────────────────────────────
645: [  # Partizan
    ("Aleksandar Jovicic",    "GOLEIRO",          26, "Servia",         70),
    ("Nemanja Stevanovic",    "GOLEIRO",          33, "Servia",         68),
    ("Svetozar Markovic",     "ZAGUEIRO",         27, "Servia",         70),
    ("Dusan Jovancic",        "ZAGUEIRO",         31, "Servia",         68),
    ("Milan Roganovic",       "ZAGUEIRO",         24, "Montenegro",     66),
    ("Nemanja Trifunovic",    "LATERAL_DIREITO",  23, "Servia",         66),
    ("Vanja Dragojevic",      "LATERAL_ESQUERDO", 25, "Servia",         66),
    ("Bibras Natcho",         "VOLANTE",          37, "Israel",         69),
    ("Nemanja Jovic",         "VOLANTE",          24, "Servia",         67),
    ("Jovan Milosevic",       "VOLANTE",          22, "Servia",         65),
    ("Vukotic Vukotic",       "MEIA_CENTRAL",     25, "Montenegro",     67),
    ("Mateja Djordjevic",     "MEIA_CENTRAL",     21, "Servia",         65),
    ("Andrej Kostic",         "MEIA_ATACANTE",    22, "Servia",         68),
    ("Ognjen Ugresic",        "PONTA_DIREITA",    23, "Servia",         67),
    ("Milan Vukotic",         "PONTA_DIREITA",    24, "Servia",         66),
    ("Nikola Simic",          "PONTA_ESQUERDA",   26, "Servia",         66),
    ("Luka Ilic",             "PONTA_ESQUERDA",   27, "Servia",         68),
    ("Kosta Nedeljkovic",     "CENTROAVANTE",     21, "Servia",         68),
    ("Jovan Sljivic",         "CENTROAVANTE",     23, "Servia",         66),
    ("Nikola Jojic",          "SEGUNDA_ATACANTE", 20, "Servia",         64),
],

# ───────────────────── Liechtenstein (Europa League) ─────────────────────────
# A excecao da lista de acesso: o pais nao tem liga propria, so a copa local.
646: [  # FC Vaduz
    ("Benjamin Buchel",       "GOLEIRO",          36, "Liechtenstein",  60),
    ("Mattia Zanotti",        "GOLEIRO",          23, "Suica",          57),
    ("Maximilian Goppel",     "ZAGUEIRO",         26, "Alemanha",       58),
    ("Lars Traber",           "ZAGUEIRO",         24, "Suica",          57),
    ("Nicolas Hasler",        "ZAGUEIRO",         34, "Liechtenstein",  58),
    ("Daniel Brandhuber",     "LATERAL_DIREITO",  25, "Austria",        56),
    ("Milan Gajic",           "LATERAL_ESQUERDO", 23, "Suica",          55),
    ("Tim Prica",             "VOLANTE",          25, "Suecia",         58),
    ("Lukas Hoti",            "VOLANTE",          24, "Suica",          57),
    ("Ferdinand Oswald",      "VOLANTE",          27, "Austria",        56),
    ("Sandro Wieser",         "MEIA_CENTRAL",     33, "Liechtenstein",  58),
    ("Noah Frommelt",         "MEIA_CENTRAL",     22, "Liechtenstein",  55),
    ("Fabio Fehr",            "MEIA_ATACANTE",    24, "Liechtenstein",  57),
    ("Gabriel Luchinger",     "PONTA_DIREITA",    23, "Suica",          56),
    ("Marco Gajic",           "PONTA_DIREITA",    26, "Suica",          56),
    ("Dejvid Sinani",         "PONTA_ESQUERDA",   27, "Liechtenstein",  58),
    ("Livio Meier",           "PONTA_ESQUERDA",   22, "Suica",          54),
    ("Anton Fehr",            "CENTROAVANTE",     26, "Liechtenstein",  58),
    ("Mohamed Coulibaly",     "CENTROAVANTE",     25, "Mali",           57),
    ("Aron Sele",             "SEGUNDA_ATACANTE", 24, "Liechtenstein",  55),
],

# ── Associacoes sem vaga direta: alimentam as duas qualificatorias ───────────
647: [  # AEK Atenas
    ("Christos Tzolakis",     "GOLEIRO",          23, "Grecia",         72),
    ("Jan Strakosha",         "GOLEIRO",          26, "Albania",        69),
    ("Domagoj Vida",          "ZAGUEIRO",         36, "Croacia",        72),
    ("Harold Moukoudi",       "ZAGUEIRO",         28, "Camaroes",       72),
    ("Milad Mohammadi",       "ZAGUEIRO",         32, "Ira",            70),
    ("Giannis Kotsiras",      "LATERAL_DIREITO",  24, "Grecia",         69),
    ("Lazaros Rota",          "LATERAL_ESQUERDO", 29, "Grecia",         70),
    ("Erik Lamela",           "VOLANTE",          33, "Argentina",      73),
    ("Lamine Diaby-Fadiga",   "VOLANTE",          25, "Franca",         69),
    ("Nordin Amrabat",        "VOLANTE",          39, "Marrocos",       68),
    ("Petros Mantalos",       "MEIA_CENTRAL",     34, "Grecia",         71),
    ("Orbelin Pineda",        "MEIA_CENTRAL",     30, "Mexico",         73),
    ("Luka Jovic",            "MEIA_ATACANTE",    28, "Servia",         74),
    ("Anton Gonzalez",        "PONTA_DIREITA",    24, "Espanha",        69),
    ("Konstantinos Koulierakis","PONTA_DIREITA",  22, "Grecia",         70),
    ("Razvan Marin",          "PONTA_ESQUERDA",   29, "Romenia",        73),
    ("Stavros Pilios",        "PONTA_ESQUERDA",   24, "Grecia",         68),
    ("Derrick Jensen",        "CENTROAVANTE",     26, "Dinamarca",      70),
    ("Levi Garcia",           "CENTROAVANTE",     28, "Trinidad",       72),
    ("Fotios Ioannidis",      "SEGUNDA_ATACANTE", 25, "Grecia",         72),
],

648: [  # Brondby
    ("Mads Hermansen",        "GOLEIRO",          25, "Dinamarca",      70),
    ("Patrick Pentz",         "GOLEIRO",          28, "Austria",        68),
    ("Rasmus Lauritsen",      "ZAGUEIRO",         29, "Dinamarca",      67),
    ("Frederik Winther",      "ZAGUEIRO",         24, "Dinamarca",      67),
    ("Sean Klaiber",          "ZAGUEIRO",         31, "Holanda",        65),
    ("Daniel Wass",           "LATERAL_DIREITO",  36, "Dinamarca",      68),
    ("Christian Cappis",      "LATERAL_ESQUERDO", 26, "Estados Unidos", 64),
    ("Mathias Kvistgaarden",  "VOLANTE",          23, "Dinamarca",      68),
    ("Ohi Omoijuanfo",        "VOLANTE",          31, "Noruega",        66),
    ("Oskar Fallenius",       "VOLANTE",          22, "Suecia",         64),
    ("Nicolai Vallys",        "MEIA_CENTRAL",     30, "Dinamarca",      67),
    ("Yuito Suzuki",          "MEIA_CENTRAL",     24, "Japao",          68),
    ("Mathias Greve",         "MEIA_ATACANTE",    30, "Dinamarca",      65),
    ("Filip Bundgaard",       "PONTA_DIREITA",    20, "Dinamarca",      66),
    ("Tobias Borkeeiet",      "PONTA_DIREITA",    26, "Noruega",        64),
    ("Clement Bischoff",      "PONTA_ESQUERDA",   21, "Dinamarca",      67),
    ("Marko Divkovic",        "PONTA_ESQUERDA",   24, "Croacia",        64),
    ("Nicolai Jorgensen",     "CENTROAVANTE",     35, "Dinamarca",      66),
    ("Sean Steur",            "CENTROAVANTE",     21, "Holanda",        63),
    ("Mikael Uhre",           "SEGUNDA_ATACANTE", 31, "Dinamarca",      67),
],

649: [  # Molde
    ("Jacob Karlstrom",       "GOLEIRO",          27, "Suecia",         68),
    ("Oliver Petersen",       "GOLEIRO",          23, "Noruega",        65),
    ("Martin Linnes",         "ZAGUEIRO",         34, "Noruega",        67),
    ("Mats Moller Daehli",    "ZAGUEIRO",         30, "Noruega",        69),
    ("Benjamin Bjortuft",     "ZAGUEIRO",         26, "Noruega",        66),
    ("Christoffer Kleven",    "LATERAL_DIREITO",  25, "Noruega",        65),
    ("David Fofana",          "LATERAL_ESQUERDO", 23, "Noruega",        64),
    ("Fredrik Gulbrandsen",   "VOLANTE",          33, "Noruega",        67),
    ("Emil Breivik",          "VOLANTE",          25, "Noruega",        67),
    ("Kristoffer Haugen",     "VOLANTE",          31, "Noruega",        65),
    ("Magnus Eikrem",         "MEIA_CENTRAL",     35, "Noruega",        67),
    ("Eirik Hestad",          "MEIA_CENTRAL",     30, "Noruega",        65),
    ("Markus Kaasa",          "MEIA_ATACANTE",    28, "Noruega",        67),
    ("Valdemar Jensen",       "PONTA_DIREITA",    24, "Dinamarca",      65),
    ("Johan Bakke",           "PONTA_DIREITA",    22, "Noruega",        64),
    ("Mathias Love",          "PONTA_ESQUERDA",   24, "Noruega",        64),
    ("Sivert Mannsverk",      "PONTA_ESQUERDA",   23, "Noruega",        66),
    ("Ola Brynhildsen",       "CENTROAVANTE",     27, "Noruega",        69),
    ("Oscar Hiljemark",       "CENTROAVANTE",     33, "Suecia",         65),
    ("Elias Hoff Melkersen",  "SEGUNDA_ATACANTE", 24, "Noruega",        66),
],

650: [  # Hajduk Split
    ("Ivan Lucic",            "GOLEIRO",          31, "Croacia",        68),
    ("Toni Silic",            "GOLEIRO",          24, "Croacia",        65),
    ("Nikola Kalinic",        "ZAGUEIRO",         28, "Croacia",        68),
    ("Zvonimir Sarlija",      "ZAGUEIRO",         29, "Croacia",        67),
    ("Branimir Mlacic",       "ZAGUEIRO",         22, "Croacia",        65),
    ("Luka Hodak",            "LATERAL_DIREITO",  23, "Croacia",        64),
    ("Filip Uremovic",        "LATERAL_ESQUERDO", 29, "Croacia",        67),
    ("Emir Sahiti",           "VOLANTE",          27, "Kosovo",         67),
    ("Jan Mlakar",            "VOLANTE",          27, "Eslovenia",      68),
    ("Rokas Pukstas",         "VOLANTE",          22, "Estados Unidos", 66),
    ("Marko Livaja",          "MEIA_CENTRAL",     32, "Croacia",        72),
    ("Ivan Rakitic",          "MEIA_CENTRAL",     38, "Croacia",        71),
    ("Michele Sego",          "MEIA_ATACANTE",    23, "Croacia",        65),
    ("Niko Sigur",            "PONTA_DIREITA",    23, "Croacia",        67),
    ("Stipe Biuk",            "PONTA_DIREITA",    23, "Croacia",        66),
    ("Sandro Kulenovic",      "PONTA_ESQUERDA",   26, "Croacia",        66),
    ("Dario Melnjak",         "PONTA_ESQUERDA",   33, "Croacia",        64),
    ("Adrion Pajaziti",       "CENTROAVANTE",     24, "Kosovo",         66),
    ("Vuk Teodosic",          "CENTROAVANTE",     22, "Servia",         64),
    ("Tonio Teklic",          "SEGUNDA_ATACANTE", 25, "Croacia",        65),
],

651: [  # Lech Poznan
    ("Bartosz Mrozek",        "GOLEIRO",          25, "Polonia",        68),
    ("Filip Bednarek",        "GOLEIRO",          33, "Polonia",        67),
    ("Antoni Kozubal",        "ZAGUEIRO",         21, "Polonia",        66),
    ("Dino Hotic",            "ZAGUEIRO",         30, "Eslovenia",      67),
    ("Alan Czerwinski",       "ZAGUEIRO",         32, "Polonia",        66),
    ("Joel Pereira",          "LATERAL_DIREITO",  29, "Portugal",       65),
    ("Pablo Rodriguez",       "LATERAL_ESQUERDO", 24, "Espanha",        66),
    ("Radoslaw Murawski",     "VOLANTE",          32, "Polonia",        66),
    ("Afonso Sousa",          "VOLANTE",          26, "Portugal",       67),
    ("Michal Gurgul",         "VOLANTE",          21, "Polonia",        64),
    ("Kristoffer Velde",      "MEIA_CENTRAL",     26, "Noruega",        69),
    ("Filip Szymczak",        "MEIA_CENTRAL",     23, "Polonia",        65),
    ("Luis Palma",            "MEIA_ATACANTE",    26, "Honduras",       68),
    ("Mikael Ishak",          "PONTA_DIREITA",    32, "Suecia",         68),
    ("Bryan Fiabema",         "PONTA_DIREITA",    22, "Noruega",        65),
    ("Taofeek Ismaheel",      "PONTA_ESQUERDA",   25, "Nigeria",        66),
    ("Gabriel Tomasiewicz",   "PONTA_ESQUERDA",   21, "Polonia",        63),
    ("Mihai Caimacov",        "CENTROAVANTE",     27, "Moldavia",       65),
    ("Luis Diaz Monsalve",    "CENTROAVANTE",     24, "Colombia",       65),
    ("Daniel Hakans",         "SEGUNDA_ATACANTE", 28, "Finlandia",      66),
],

652: [  # Djurgarden
    ("Jacob Rinne",           "GOLEIRO",          32, "Suecia",         67),
    ("Filip Manojlovic",      "GOLEIRO",          29, "Servia",         66),
    ("Hjalmar Ekdal",         "ZAGUEIRO",         27, "Suecia",         67),
    ("Elliot Stroud",         "ZAGUEIRO",         23, "Suecia",         64),
    ("Adam Strale",           "ZAGUEIRO",         25, "Suecia",         64),
    ("Tobias Gulliksen",      "LATERAL_DIREITO",  22, "Noruega",        67),
    ("Daniel Stensson",       "LATERAL_ESQUERDO", 24, "Suecia",         64),
    ("Magnus Eriksson",       "VOLANTE",          35, "Suecia",         67),
    ("Hampus Finndell",       "VOLANTE",          25, "Suecia",         66),
    ("Rasmus Bengtsson",      "VOLANTE",          36, "Suecia",         63),
    ("Besard Sabovic",        "MEIA_CENTRAL",     29, "Suecia",         66),
    ("Gustav Wikheim",        "MEIA_CENTRAL",     32, "Noruega",        65),
    ("Keita Kosugi",          "MEIA_ATACANTE",    20, "Japao",          65),
    ("Nino Zugelj",           "PONTA_DIREITA",    25, "Eslovenia",      66),
    ("Lucas Bergvall",        "PONTA_DIREITA",    20, "Suecia",         68),
    ("Joel Asoro",            "PONTA_ESQUERDA",   26, "Suecia",         66),
    ("Adam Hellborg",         "PONTA_ESQUERDA",   22, "Suecia",         62),
    ("Isak Alemayehu",        "CENTROAVANTE",     21, "Suecia",         64),
    ("Jesper Karlstrom",      "CENTROAVANTE",     30, "Suecia",         65),
    ("Tokmac Nguen",          "SEGUNDA_ATACANTE", 32, "Noruega",        66),
],

653: [  # CFR Cluj
    ("Otto Hindrich",         "GOLEIRO",          23, "Romenia",        66),
    ("Simone Scuffet",        "GOLEIRO",          29, "Italia",         67),
    ("Andrei Burca",          "ZAGUEIRO",         32, "Romenia",        66),
    ("Monday Etim",           "ZAGUEIRO",         23, "Nigeria",        63),
    ("Yuri Matias",           "ZAGUEIRO",         28, "Portugal",       65),
    ("Karlo Muhar",           "LATERAL_DIREITO",  29, "Croacia",        64),
    ("Cristian Manea",        "LATERAL_ESQUERDO", 28, "Romenia",        66),
    ("Alexandru Chipciu",     "VOLANTE",          36, "Romenia",        65),
    ("Daniel Birligea",       "VOLANTE",          25, "Romenia",        65),
    ("Luka Zima",             "VOLANTE",          24, "Croacia",        62),
    ("Alexandru Cordea",      "MEIA_CENTRAL",     29, "Romenia",        64),
    ("Jonathan Rodriguez",    "MEIA_CENTRAL",     25, "Uruguai",        64),
    ("Louis Munteanu",        "MEIA_ATACANTE",    23, "Romenia",        68),
    ("Daniel Bitton",         "PONTA_DIREITA",    24, "Israel",         63),
    ("Mamadou Thiam",         "PONTA_DIREITA",    30, "Senegal",        64),
    ("Islam Slimani",         "PONTA_ESQUERDA",   37, "Argelia",        65),
    ("Bogdan Cojocaru",       "PONTA_ESQUERDA",   22, "Romenia",        61),
    ("Emanuel Emegha",        "CENTROAVANTE",     23, "Holanda",        66),
    ("Victor Dican",          "CENTROAVANTE",     22, "Romenia",        62),
    ("Alin Fica",             "SEGUNDA_ATACANTE", 24, "Romenia",        62),
],
}


# ─────────────────── Técnicos dos clubes novos ──────────────────
# Os 31 primeiros tem o seu tecnico em scripts/gen_tecnicos.py, que regera a
# chave "tecnicos" INTEIRA com random sem semente: rodar aquele script de novo
# trocaria o tecnico de todos os 285 clubes do seed. Para nao precisar disso, o
# pool novo emite os seus proprios tecnicos aqui, em seed_outros.json, e o
# merge_seeds.py faz upsert deles por id. Os overrides tambem foram acrescentados
# ao gen_tecnicos.py, para que uma regeracao completa continue dando nomes
# europeus a estes clubes em vez de cair no pool de nomes brasileiros.
#
# Os ids continuam de TECNICO_ID_BASE, logo depois dos 285 que o seed ja tem, que
# e exatamente onde o gen_tecnicos.py os poria (ele numera na ordem dos times, e
# os clubes novos entram no fim da lista).
TECNICO_ID_BASE = 286

TECNICOS = {
    632: ("Dino Toppmoller",      45, "Alemanha"),      # Eintracht Frankfurt
    633: ("Sebastian Hoeness",    43, "Alemanha"),      # VfB Stuttgart
    634: ("Bruno Genesio",        59, "Franca"),        # Lille
    635: ("Paulo Fonseca",        52, "Portugal"),      # Olympique Lyonnais
    636: ("Robin van Persie",     42, "Holanda"),       # Feyenoord
    637: ("Jose Mourinho",        62, "Portugal"),      # Benfica
    638: ("David Hubert",         37, "Belgica"),       # Union Saint-Gilloise
    639: ("Brian Priske",         48, "Dinamarca"),     # Sparta Praga
    640: ("Domenico Tedesco",     40, "Italia"),        # Fenerbahce
    641: ("Oleksandr Shovkovskyi",50, "Ucrania"),       # Dinamo Kiev
    642: ("Derek McInnes",        54, "Escocia"),       # Hearts
    643: ("Dietmar Kuhbauer",     54, "Austria"),       # LASK
    644: ("Jocelyn Gourvennec",   53, "Franca"),        # Servette
    645: ("Srdjan Blagojevic",    49, "Servia"),        # Partizan
    646: ("Marc Schneider",       45, "Suica"),         # FC Vaduz
    647: ("Marko Nikolic",        46, "Servia"),        # AEK Atenas
    648: ("Frederik Birk",        41, "Dinamarca"),     # Brondby
    649: ("Erling Moe",           50, "Noruega"),       # Molde
    650: ("Gonzalo Garcia",       44, "Espanha"),       # Hajduk Split
    651: ("Niels Frederiksen",    54, "Dinamarca"),     # Lech Poznan
    652: ("Jani Honkavaara",      49, "Finlandia"),     # Djurgarden
    653: ("Andrea Mandorlini",    65, "Italia"),        # CFR Cluj
}


def gen_tecnico(tid, trep, tecnico_id):
    """Mesma formula de scripts/gen_tecnicos.py, mas com semente fixa por clube,
    para o seed_outros.json sair igual a cada execucao."""
    nome, idade, nacionalidade = TECNICOS[tid]
    parts = nome.split()
    nome_abrev = "%s. %s" % (parts[0][0], parts[-1]) if len(parts) >= 2 else nome
    r = random.Random(tid + 555)
    reputacao = max(30.0, min(90.0, trep * 0.75 + r.uniform(-8, 8)))
    return {
        "id": tecnico_id, "nome": nome, "nome_abrev": nome_abrev,
        "idade": idade, "nacionalidade": nacionalidade, "time_id": tid,
        "salario": int(150_000 + (reputacao - 30) * 50_000),
        "contrato_anos": r.randint(2, 4),
        "reputacao": round(reputacao, 1),
    }

# ─────────────────── Validação e monta seed ───────────────────────────────────
VALID_POS = set(DELTAS.keys())
data = {"times": [], "jogadores": [], "tecnicos": []}
jogador_id = 13000

for (tid, tnome, tcidade, testado, tdiv, tpais, tnivel, tsaldo, testadio, tcap, tpreco, ttatica, testilo, trep) in TIMES:
    data["times"].append({
        "id": tid, "nome": tnome, "cidade": tcidade, "estado": testado,
        "divisao": tdiv, "pais": tpais, "nivel": tnivel, "saldo": tsaldo,
        "estadio_nome": testadio, "estadio_capacidade": tcap, "preco_ingresso": tpreco,
        "tatica": ttatica, "estilo": testilo, "reputacao": trep,
        "escudo_res": ESCUDOS.get(tid, ""),
    })

    if tid in TECNICOS:
        data["tecnicos"].append(gen_tecnico(
            tid, trep, TECNICO_ID_BASE + len(data["tecnicos"])
        ))
    elif tdiv == DIVISAO_POOL_SUL:
        data["tecnicos"].append(gen_tecnico_sintetico(
            tid, trep, tpais, TECNICO_ID_BASE + len(data["tecnicos"])
        ))

    roster = ROSTERS.get(tid, [])
    if not roster and tdiv == DIVISAO_POOL_SUL:
        # Pool sul-americano: elenco gerado (ver gen_roster_sintetico).
        roster = gen_roster_sintetico(tid, trep, tpais)
    if not roster:
        raise SystemExit(f"Roster ausente para time id {tid} ({tnome})")

    for (nome, pos, idade, nac, overall) in roster:
        if pos not in VALID_POS:
            raise SystemExit(f"Posicao invalida ({pos}) em {tnome}: {nome}")
        attrs = gen_stats(overall, pos, jogador_id)
        sal, val = gen_salario_valor(overall, jogador_id)
        r = random.Random(jogador_id + 31)
        contrato = r.randint(2, 5) if overall >= 74 else r.randint(1, 4)
        data["jogadores"].append({
            "id": jogador_id, "time_id": tid, "nome": nome,
            "nome_abrev": abrev(nome), "idade": int(idade), "posicao": pos,
            "nacionalidade": nac,
            "forca": attrs["forca"], "tecnica": attrs["tecnica"], "passe": attrs["passe"],
            "velocidade": attrs["velocidade"], "finalizacao": attrs["finalizacao"],
            "defesa": attrs["defesa"], "fisico": attrs["fisico"],
            "salario": sal, "valor_mercado": val, "contrato": contrato, "nivel": tnivel,
        })
        jogador_id += 1

# ─────────────────── Escreve o arquivo ────────────────────────────────────────
out_path = os.path.join("app", "src", "main", "assets", "seed_outros.json")
os.makedirs(os.path.dirname(out_path), exist_ok=True)
if not data["tecnicos"]:
    del data["tecnicos"]

with open(out_path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

por_time = {}
for j in data["jogadores"]:
    por_time[j["time_id"]] = por_time.get(j["time_id"], 0) + 1
print(f"Escrito {len(data['times'])} clubes e {len(data['jogadores'])} jogadores em {out_path}")
menores = [tid for tid, n in por_time.items() if n < 16]
if menores:
    print(f"  AVISO: clubes com <16 jogadores: {menores}")
