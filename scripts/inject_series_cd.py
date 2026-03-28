"""
Script to inject Série C and D teams + players into seed_brasileirao.json
"""
import json, random, os, copy

JSON_PATH = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'seed_brasileirao.json')

# ── Team data ─────────────────────────────────────────────────────
SERIE_C_TIMES = [
    (41,"Volta Redonda","Volta Redonda","RJ","C",4,280000000,"Estadio Raulino de Oliveira",18000,1600,"4-4-2","EQUILIBRADO",51,""),
    (42,"Ferroviaria","Araraquara","SP","C",4,260000000,"Estadio Fonte Luminosa",11600,1500,"4-3-3","DEFENSIVO",49,""),
    (43,"Londrina","Londrina","PR","C",4,270000000,"Estadio do Cafe",19800,1500,"4-4-2","EQUILIBRADO",50,""),
    (44,"Brusque","Brusque","SC","C",3,220000000,"Estadio Augusta Bauer",4200,1200,"4-4-2","DEFENSIVO",44,""),
    (45,"Figueirense","Florianopolis","SC","C",4,250000000,"Orlando Scarpelli",19500,1400,"4-3-3","EQUILIBRADO",48,""),
    (46,"Ypiranga","Bage","RS","C",3,200000000,"Estadio Colosso da Lagoa",18500,1200,"4-4-2","DEFENSIVO",43,""),
    (47,"Nautico","Recife","PE","C",4,280000000,"Estadio dos Aflitos",17800,1600,"4-3-3","OFENSIVO",52,""),
    (48,"Remo","Belem","PA","C",4,260000000,"Estadio Baenao",16700,1500,"4-4-2","EQUILIBRADO",50,""),
    (49,"Manaus","Manaus","AM","C",3,220000000,"Arena da Amazonia",40549,1300,"4-4-2","DEFENSIVO",45,""),
    (50,"Sao Bernardo","Sao Bernardo do Campo","SP","C",4,270000000,"Estadio 1 Maio",15000,1500,"4-3-3","OFENSIVO",51,""),
    (51,"Botafogo-PB","Joao Pessoa","PB","C",3,230000000,"Estadio Almeidao",27000,1300,"4-4-2","EQUILIBRADO",46,""),
    (52,"Maringa","Maringa","PR","C",4,240000000,"Willie Davids",11000,1400,"4-3-3","OFENSIVO",48,""),
    (53,"Ferroviario","Fortaleza","CE","C",3,210000000,"Presidente Vargas",55000,1200,"4-4-2","DEFENSIVO",43,""),
    (54,"CSA","Maceio","AL","C",4,260000000,"Estadio Rei Pele",17600,1500,"4-4-2","EQUILIBRADO",49,""),
    (55,"Pouso Alegre","Pouso Alegre","MG","C",3,200000000,"Manduzao",4500,1200,"4-4-2","DEFENSIVO",42,""),
    (56,"ABC","Natal","RN","C",4,250000000,"Frasqueirado",12000,1400,"4-3-3","EQUILIBRADO",47,""),
    (57,"Sampaio Correa","Sao Luis","MA","C",4,255000000,"Estadio Castelao MA",12000,1400,"4-4-2","EQUILIBRADO",48,""),
    (58,"Confianca","Aracaju","SE","C",3,210000000,"Estadio Batistao",11000,1200,"4-4-2","DEFENSIVO",43,""),
    (59,"Floresta","Fortaleza","CE","C",3,200000000,"Domingao",4000,1100,"4-4-2","DEFENSIVO",41,""),
    (60,"Sao Jose","Porto Alegre","RS","C",3,210000000,"Estadio Passo d'Areia",8000,1200,"4-4-2","EQUILIBRADO",42,""),
]

SERIE_D_TIMES = [
    (61,"Madureira","Rio de Janeiro","RJ","D",3,140000000,"Estadio Conselheiro Galvao",6000,1000,"4-4-2","EQUILIBRADO",38,""),
    (62,"Fast Clube","Manaus","AM","D",2,100000000,"Carlos Zamith",5000,900,"4-4-2","DEFENSIVO",32,""),
    (63,"Mixto","Cuiaba","MT","D",2,110000000,"Estadio Presidente Varginha",3000,900,"4-4-2","DEFENSIVO",31,""),
    (64,"Treze","Campina Grande","PB","D",3,130000000,"Estadio Presidente Vargas PB",18000,1000,"4-4-2","EQUILIBRADO",37,""),
    (65,"ASA","Arapiraca","AL","D",2,120000000,"Estadio Coaracy da Mata Fonseca",14000,900,"4-4-2","DEFENSIVO",33,""),
    (66,"Nacional","Manaus","AM","D",2,100000000,"Estadio Arena da Floresta",11000,800,"4-4-2","DEFENSIVO",31,""),
    (67,"Cianorte","Cianorte","PR","D",3,130000000,"Estadio Beneditoao",4200,1000,"4-4-2","EQUILIBRADO",36,""),
    (68,"Dom Bosco","Cuiaba","MT","D",2,110000000,"Estadio Arena Pantanal",44009,900,"4-4-2","DEFENSIVO",32,""),
    (69,"Real Noroeste","Agua Boa","ES","D",2,100000000,"Estadio Serrano",3000,800,"4-4-2","DEFENSIVO",30,""),
    (70,"Gama","Brasilia","DF","D",3,140000000,"Estadio Bezerao",30000,1100,"4-4-2","EQUILIBRADO",38,""),
    (71,"Brasiliense","Brasilia","DF","D",3,130000000,"Estadio Abadia",12000,1000,"4-4-2","EQUILIBRADO",36,""),
    (72,"Potiguar de Mossoro","Mossoro","RN","D",2,100000000,"Nazarazao",16000,800,"4-4-2","DEFENSIVO",30,""),
    (73,"Tocantinopolis","Tocantinopolis","TO","D",2,90000000,"Estadio Mirandaao",3000,700,"4-4-2","DEFENSIVO",28,""),
    (74,"Rio Branco","Vitoria","ES","D",2,110000000,"Estadio Estadio Kleber Andrade",19000,900,"4-4-2","DEFENSIVO",31,""),
    (75,"Castanhal","Castanhal","PA","D",2,100000000,"Modelao",8000,800,"4-4-2","DEFENSIVO",30,""),
    (76,"Independente","Tucurui","PA","D",2,90000000,"Estadio Navegantao",2500,700,"4-4-2","DEFENSIVO",28,""),
    (77,"Atletico-CE","Juazeiro do Norte","CE","D",3,120000000,"Estadio Romeirão",12000,900,"4-4-2","EQUILIBRADO",35,""),
    (78,"Juazeirense","Juazeiro","BA","D",2,100000000,"Adauto Moraes",10000,800,"4-4-2","DEFENSIVO",30,""),
    (79,"Campinense","Campina Grande","PB","D",3,130000000,"Americo Pereira",15000,1000,"4-4-2","EQUILIBRADO",36,""),
    (80,"Caxias","Caxias do Sul","RS","D",3,140000000,"Estadio Centenario",20000,1100,"4-4-2","EQUILIBRADO",38,""),
]

# ── Player generation ──────────────────────────────────────────────
POSITIONS_PER_TEAM = [
    "GOLEIRO","GOLEIRO",
    "ZAGUEIRO","ZAGUEIRO","ZAGUEIRO",
    "LATERAL_DIREITO","LATERAL_DIREITO",
    "LATERAL_ESQUERDO","LATERAL_ESQUERDO",
    "VOLANTE","VOLANTE","VOLANTE",
    "MEIA_CENTRAL","MEIA_CENTRAL",
    "MEIA_ATACANTE","MEIA_ATACANTE",
    "PONTA_DIREITA",
    "CENTROAVANTE","CENTROAVANTE","SEGUNDA_ATACANTE",
]  # 20 per team

FIRST_NAMES = ["Carlos","Lucas","Rafael","Felipe","Diego","Rodrigo","Thiago","Anderson",
               "Marcos","Bruno","Fabio","Alex","Gabriel","Daniel","Andre","Joao","Paulo",
               "Victor","Eduardo","Leandro","Pedro","Manuel","Sergio","Antonio","Fernando",
               "Marco","Leonardo","Henrique","Douglas","Guilherme","Wellington","Caio","Vitor",
               "Alan","Matheus","Patrick","Erick","Igor","Nando","Ronaldo"]
LAST_NAMES  = ["Silva","Santos","Oliveira","Lima","Costa","Souza","Ferreira","Rodrigues",
               "Alves","Pereira","Nascimento","Carvalho","Melo","Ribeiro","Machado","Gomes",
               "Mendes","Barros","Moreira","Nunes","Rocha","Correia","Pinto","Miranda",
               "Cruz","Cardoso","Lopes","Cavalcante","Rezende","Monteiro","Araujo","Marques"]

def random_name(rng):
    first = rng.choice(FIRST_NAMES)
    last  = rng.choice(LAST_NAMES)
    return f"{first} {last}", f"{first[0]}. {last}"

def player_stat(base, spread, rng):
    v = base + rng.randint(-spread, spread)
    return max(20, min(99, v))

def salary_for_stats(avg_stat):
    # rough salary based on avg stat
    if avg_stat >= 75: return rng_global.randint(5000000, 30000000)
    if avg_stat >= 65: return rng_global.randint(800000, 5000000)
    if avg_stat >= 55: return rng_global.randint(300000, 800000)
    if avg_stat >= 45: return rng_global.randint(100000, 300000)
    return rng_global.randint(50000, 100000)

def market_value(salary):
    return salary * rng_global.randint(20, 40)

rng_global = random.Random(42)  # fixed seed for reproducibility

def generate_players_for_team(time_id, nivel, start_id, rng):
    players = []
    pid = start_id
    # Base stat for this team based on nivel
    base = {2: 38, 3: 45, 4: 54}.get(nivel, 45)
    for pos in POSITIONS_PER_TEAM:
        nome, abrev = random_name(rng)
        age = rng.randint(18, 35)
        contrato = rng.randint(1, 4)
        forca      = player_stat(base, 6, rng)
        tecnica    = player_stat(base, 6, rng)
        passe      = player_stat(base, 6, rng)
        velocidade = player_stat(base, 8, rng)
        finalizacao= player_stat(base-3, 8, rng) if pos not in ("ATACANTE","MEIA_ATACANTE","PONTA_DIREITA") else player_stat(base+2, 6, rng)
        defesa     = player_stat(base+3, 6, rng) if pos in ("GOLEIRO","ZAGUEIRO","LATERAL_DIREITO","LATERAL_ESQUERDO","VOLANTE") else player_stat(base-5, 6, rng)
        fisico     = player_stat(base, 6, rng)
        avg = (forca+tecnica+passe+velocidade+finalizacao+defesa+fisico)//7
        sal = salary_for_stats(avg)
        players.append({
            "id": pid,
            "time_id": time_id,
            "nome": nome,
            "nome_abrev": abrev,
            "idade": age,
            "posicao": pos,
            "forca": forca,
            "tecnica": tecnica,
            "passe": passe,
            "velocidade": velocidade,
            "finalizacao": finalizacao,
            "defesa": defesa,
            "fisico": fisico,
            "salario": sal,
            "contrato": contrato,
            "valor_mercado": market_value(sal)
        })
        pid += 1
    return players, pid

# ── Load existing JSON ─────────────────────────────────────────────
with open(JSON_PATH, encoding='utf-8-sig') as f:
    data = json.load(f)

print(f"Before: {len(data['times'])} times, {len(data['jogadores'])} jogadores")

# ── Add teams ──────────────────────────────────────────────────────
for t in SERIE_C_TIMES + SERIE_D_TIMES:
    (tid,nome,cidade,estado,div,nivel,saldo,est_nome,est_cap,preco,tatica,estilo,rep,escudo) = t
    data['times'].append({
        "id": tid, "nome": nome, "cidade": cidade, "estado": estado,
        "divisao": div, "nivel": nivel, "saldo": saldo,
        "estadio_nome": est_nome, "estadio_capacidade": est_cap,
        "preco_ingresso": preco, "tatica": tatica, "estilo": estilo,
        "reputacao": rep, "escudo_res": escudo
    })

# ── Add players ────────────────────────────────────────────────────
next_pid = data['jogadores'][-1]['id'] + 1
rng_player = random.Random(2024)

for t in SERIE_C_TIMES + SERIE_D_TIMES:
    nivel = t[5]
    tid   = t[0]
    players, next_pid = generate_players_for_team(tid, nivel, next_pid, rng_player)
    data['jogadores'].extend(players)

print(f"After:  {len(data['times'])} times, {len(data['jogadores'])} jogadores")

# ── Write back ─────────────────────────────────────────────────────
with open(JSON_PATH, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print("Done! File written without BOM.")
