# -*- coding: utf-8 -*-
"""
Gera 1 tecnico para cada time existente no seed_data.json e adiciona
a chave 'tecnicos' no JSON.

Regras:
- Brasileiros para times do Brasil, argentinos para Argentina, uruguaios para Uruguay
- Idades entre 38-65 (range realista de tecnicos)
- Reputacao base alinhada com a do time (reputacao do time / 1.5, capada em 90)
- Salario base entre R$ 1.500/mes e R$ 30.000/mes (em centavos)
- contratoAnos = 2-4 (aleatorio)
- IDs comecam em 1 e crescem
"""
import json
import io
import random

random.seed(42)  # determinismo

PATH = r"C:\Dev\ManagerFoot\app\src\main\assets\seed_data.json"

# Nomes ficticios mas realistas — todos sem acentos para evitar issues
NOMES_BR_PRENOMES = [
    "Adilson", "Alberto", "Alexandre", "Andre", "Antonio", "Bruno", "Carlos", "Cesar",
    "Cuca", "Daniel", "Diego", "Dorival", "Eduardo", "Eder", "Emerson", "Fabio",
    "Felipe", "Fernando", "Filipe", "Flavio", "Gabriel", "Geninho", "Geraldo",
    "Guilherme", "Gustavo", "Helio", "Hugo", "Jair", "Joao", "Jorge", "Jorginho",
    "Lisca", "Lucas", "Luiz", "Luxemburgo", "Marcao", "Marcelo", "Marcos", "Mario",
    "Mauricio", "Mauro", "Murilo", "Nelson", "Odair", "Oswaldo", "Paulo", "Pedro",
    "Rafael", "Ramon", "Renato", "Ricardo", "Roberto", "Rodolfo", "Rogerio",
    "Sergio", "Tite", "Vagner", "Valdir", "Vanderlei", "Vinicius", "Wagner", "Zinho"
]
NOMES_BR_SOBRENOMES = [
    "Almeida", "Alves", "Andrade", "Araujo", "Barbosa", "Barros", "Bittencourt",
    "Cardoso", "Carvalho", "Castro", "Cavalcante", "Costa", "Diniz", "Dias",
    "Faria", "Felipao", "Fernandes", "Ferreira", "Freitas", "Gomes", "Goncalves",
    "Lima", "Lopes", "Machado", "Marinho", "Martins", "Mattos", "Medeiros",
    "Mendes", "Menezes", "Moreira", "Nascimento", "Nunes", "Oliveira", "Pereira",
    "Pinto", "Ramos", "Reis", "Ribeiro", "Rocha", "Rodrigues", "Sa", "Salgueiro",
    "Sampaio", "Santos", "Silva", "Simoes", "Sousa", "Souza", "Teixeira",
    "Vasconcelos", "Vieira", "Xavier", "Zubeldia"
]
NOMES_AR_PRENOMES = [
    "Adolfo", "Alejandro", "Antonio", "Carlos", "Daniel", "Diego", "Eduardo",
    "Emiliano", "Ernesto", "Esteban", "Fernando", "Gabriel", "Gerardo", "Gustavo",
    "Hernan", "Hugo", "Javier", "Jorge", "Jose", "Juan", "Julio", "Lionel",
    "Luis", "Marcelo", "Mariano", "Martin", "Matias", "Mauricio", "Miguel",
    "Nestor", "Nicolas", "Omar", "Oscar", "Pablo", "Pedro", "Rafael", "Ramon",
    "Raul", "Ricardo", "Roberto", "Rodolfo", "Sebastian", "Sergio", "Tomas", "Walter"
]
NOMES_AR_SOBRENOMES = [
    "Aguero", "Almiron", "Alonso", "Aimar", "Bauza", "Beccacece", "Bianchi",
    "Bielsa", "Burruchaga", "Cantero", "Caruso", "Cordoba", "Coudet", "Crespo",
    "Diaz", "Dominguez", "Falcioni", "Fernandez", "Gago", "Gallardo", "Garcia",
    "Gimenez", "Gomez", "Gonzalez", "Gorosito", "Heinze", "Holan", "Lopez",
    "Lucca", "Lujambio", "Madelon", "Maradona", "Martinez", "Medina", "Mohamed",
    "Ortigoza", "Pellegrini", "Perez", "Pizzi", "Quinteros", "Rial", "Rodriguez",
    "Russo", "Saja", "Sampaoli", "Sanchez", "Scaloni", "Simeone", "Soso",
    "Suarez", "Veron", "Vidal", "Zubeldia"
]
NOMES_UR_PRENOMES = [
    "Alvaro", "Antonio", "Diego", "Eduardo", "Enzo", "Fabian", "Federico",
    "Gerardo", "Gonzalo", "Gustavo", "Hector", "Jorge", "Jose", "Juan", "Julio",
    "Luis", "Marcelo", "Martin", "Matias", "Miguel", "Nestor", "Omar", "Oscar",
    "Pablo", "Pedro", "Rafael", "Raul", "Ricardo", "Roberto", "Ruben", "Sebastian",
    "Sergio", "Tabarez", "Walter"
]
NOMES_UR_SOBRENOMES = [
    "Aguirre", "Antunez", "Bengoechea", "Cabrera", "Cabreira", "Caceres", "Cardozo",
    "Castro", "Coito", "Davila", "De Los Santos", "Diaz", "Dominguez", "Fossati",
    "Garcia", "Gaston", "Gil", "Gomez", "Gonzalez", "Larriera", "Lopez", "Marquez",
    "Martinez", "Munoz", "Olivera", "Paez", "Perez", "Pisano", "Pereira", "Recoba",
    "Rodriguez", "Rojas", "Romero", "Rossi", "Sanguinetti", "Santos", "Sarazua",
    "Silva", "Sosa", "Suarez", "Tabarez", "Varela"
]


# ── Overrides de tecnicos REAIS por time_id ────────────────────────────────
# Chave = time_id; valor = (nome, idade, nacionalidade).
# Usado para forcar tecnicos reais em times cujo elenco tambem eh real. Sem
# override, o gerador cai no sorteio aleatorio por pais (com fallback BR).
#
# Brasileirao 2025 (Serie A): tecnicos ativos em ~meados/fim de 2025.
# Alguns clubes tiveram trocas — usamos o tecnico mais notavel do ano.
OVERRIDES = {
    # ── Serie A 2025 ─────────────────────────────────────────
    1:  ("Filipe Luis",         40, "Brasil"),      # Flamengo
    2:  ("Abel Ferreira",       47, "Portugal"),    # Palmeiras
    3:  ("Cuca",                62, "Brasil"),      # Atletico-MG
    4:  ("Hernan Crespo",       50, "Argentina"),   # Sao Paulo
    5:  ("Dorival Junior",      63, "Brasil"),      # Corinthians
    6:  ("Renato Gaucho",       63, "Brasil"),      # Fluminense
    7:  ("Davide Ancelotti",    36, "Italia"),      # Botafogo
    8:  ("Roger Machado",       51, "Brasil"),      # Internacional
    9:  ("Mano Menezes",        63, "Brasil"),      # Gremio
    10: ("Leonardo Jardim",     51, "Portugal"),    # Cruzeiro
    11: ("Odair Hellmann",      52, "Brasil"),      # Athletico-PR
    12: ("Fernando Diniz",      51, "Brasil"),      # Vasco da Gama
    14: ("Rogerio Ceni",        52, "Brasil"),      # Bahia
    15: ("Fernando Seabra",     41, "Brasil"),      # RB Bragantino
    20: ("Fabio Carille",       52, "Brasil"),      # Vitoria
    21: ("Juan Pablo Vojvoda",  50, "Argentina"),   # Santos
    23: ("Mozart Santos",       61, "Brasil"),      # Coritiba
    28: ("Emerson Avila",       47, "Brasil"),      # Chapecoense
    40: ("Rafael Guanaes",      44, "Brasil"),      # Mirassol
    48: ("Guto Ferreira",       60, "Brasil"),      # Remo

    # ── Serie B 2025 (subset com conhecimento confiavel) ─────
    13: ("Martin Palermo",      51, "Argentina"),   # Fortaleza (assumiu meados/2025)
    22: ("Pepa",                53, "Portugal"),    # Sport Recife
    25: ("Leo Conde",           47, "Brasil"),      # Ceara
    26: ("Vagner Mancini",      59, "Brasil"),      # Goias
    27: ("Eduardo Barroca",     46, "Brasil"),      # CRB
    33: ("Eduardo Baptista",    55, "Brasil"),      # Novorizontino
    56: ("Enderson Moreira",    55, "Brasil"),      # Avai


    # Real Madrid
    501: ("Xabi Alonso",        43, "Espanha"),
    # Barcelona
    502: ("Hansi Flick",        60, "Alemanha"),
    # Atletico de Madrid
    503: ("Diego Simeone",      55, "Argentina"),
    # Athletic Bilbao
    504: ("Ernesto Valverde",   61, "Espanha"),
    # Real Sociedad
    505: ("Sergio Francisco",   40, "Espanha"),
    # Real Betis
    506: ("Manuel Pellegrini",  71, "Chile"),
    # Villarreal
    507: ("Marcelino Garcia Toral", 60, "Espanha"),
    # Sevilla
    508: ("Matias Almeyda",     51, "Argentina"),
    # Valencia
    509: ("Carlos Corberan",    42, "Espanha"),
    # Celta Vigo
    510: ("Claudio Giraldez",   37, "Espanha"),
    # Osasuna
    511: ("Alessio Lisci",      39, "Italia"),
    # Getafe
    512: ("Jose Bordalas",      61, "Espanha"),
    # Rayo Vallecano
    513: ("Inigo Perez",        37, "Espanha"),
    # Mallorca
    514: ("Jagoba Arrasate",    47, "Espanha"),
    # Girona
    515: ("Michel",             50, "Espanha"),
    # Espanyol
    516: ("Manolo Gonzalez",    53, "Espanha"),
    # Alaves
    517: ("Eduardo Coudet",     50, "Argentina"),
    # Elche
    518: ("Eder Sarabia",       45, "Espanha"),
    # Levante
    519: ("Julian Calero",      55, "Espanha"),
    # Real Oviedo
    520: ("Veljko Paunovic",    48, "Servia"),

    # ── Serie A italiana 2025/26 ─────────────────────────────
    521: ("Cristian Chivu",     45, "Romenia"),     # Inter
    522: ("Antonio Conte",      56, "Italia"),      # Napoli
    523: ("Igor Tudor",         47, "Croacia"),     # Juventus
    524: ("Massimiliano Allegri", 58, "Italia"),    # Milan
    525: ("Ivan Juric",         50, "Croacia"),     # Atalanta
    526: ("Gian Piero Gasperini", 67, "Italia"),    # Roma
    527: ("Maurizio Sarri",     66, "Italia"),      # Lazio
    528: ("Stefano Pioli",      60, "Italia"),      # Fiorentina
    529: ("Vincenzo Italiano",  48, "Italia"),      # Bologna
    530: ("Cesc Fabregas",      38, "Espanha"),     # Como
    531: ("Marco Baroni",       62, "Italia"),      # Torino
    532: ("Kosta Runjaic",      54, "Alemanha"),    # Udinese
    533: ("Patrick Vieira",     49, "Franca"),      # Genoa
    534: ("Fabio Pisacane",     39, "Italia"),      # Cagliari
    535: ("Carlos Cuesta",      30, "Espanha"),     # Parma
    536: ("Eusebio Di Francesco", 56, "Italia"),    # Lecce
    537: ("Fabio Grosso",       48, "Italia"),      # Sassuolo
    538: ("Paolo Zanetti",      42, "Italia"),      # Hellas Verona
    539: ("Davide Nicola",      52, "Italia"),      # Cremonese
    540: ("Alberto Gilardino",  43, "Italia"),      # Pisa

    # ── Outros times (pool intercontinental) ─────────────────
    601: ("Javier Mascherano",  41, "Argentina"),   # Inter Miami
}


def gen_nome(pais: str) -> tuple[str, str]:
    if pais == "Brasil":
        prenome = random.choice(NOMES_BR_PRENOMES)
        sobrenome = random.choice(NOMES_BR_SOBRENOMES)
    elif pais in ("Argentina",):
        prenome = random.choice(NOMES_AR_PRENOMES)
        sobrenome = random.choice(NOMES_AR_SOBRENOMES)
    elif pais in ("Uruguay", "Uruguai"):
        prenome = random.choice(NOMES_UR_PRENOMES)
        sobrenome = random.choice(NOMES_UR_SOBRENOMES)
    else:
        prenome = random.choice(NOMES_BR_PRENOMES)
        sobrenome = random.choice(NOMES_BR_SOBRENOMES)
    nome = f"{prenome} {sobrenome}"
    nome_abrev = f"{prenome[0]}. {sobrenome}"
    return nome, nome_abrev


def gen_tecnico(time, tecnico_id: int):
    pais = time.get("pais", "Brasil")
    nacionalidade = "Uruguai" if pais in ("Uruguay", "Uruguai") else pais
    override = OVERRIDES.get(time["id"])
    if override is not None:
        nome, idade, nacionalidade = override
        parts = nome.split()
        nome_abrev = f"{parts[0][0]}. {parts[-1]}" if len(parts) >= 2 else nome
    else:
        nome, nome_abrev = gen_nome(pais)
        idade = random.randint(38, 65)
    rep_base = float(time.get("reputacao", 50))
    # Reputacao do tecnico: ~70% da reputacao do time, com variacao
    reputacao = max(30.0, min(90.0, rep_base * 0.75 + random.uniform(-8, 8)))
    # Salario: proporcional a reputacao, em centavos. R$ 1.500 a R$ 30.000 / mes
    salario = int(150_000 + (reputacao - 30) * 50_000)  # centavos
    contrato = random.randint(2, 4)
    return {
        "id": tecnico_id,
        "nome": nome,
        "nome_abrev": nome_abrev,
        "idade": idade,
        "nacionalidade": nacionalidade,
        "time_id": time["id"],
        "salario": salario,
        "contrato_anos": contrato,
        "reputacao": round(reputacao, 1)
    }


def main():
    with io.open(PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    times = data["times"]
    tecnicos = []
    for i, time in enumerate(times, start=1):
        tecnicos.append(gen_tecnico(time, tecnico_id=i))

    # Ordena chaves: tecnicos vai no final
    data["tecnicos"] = tecnicos

    with io.open(PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

    # Validacao
    with io.open(PATH, "r", encoding="utf-8") as f:
        check = json.load(f)
    print(f"Times: {len(check['times'])}")
    print(f"Tecnicos gerados: {len(check['tecnicos'])}")
    print(f"Brasileiros: {sum(1 for t in check['tecnicos'] if t['nacionalidade'] == 'Brasil')}")
    print(f"Argentinos:  {sum(1 for t in check['tecnicos'] if t['nacionalidade'] == 'Argentina')}")
    print(f"Uruguaios:   {sum(1 for t in check['tecnicos'] if t['nacionalidade'] == 'Uruguai')}")
    print(f"Sample 1:    {check['tecnicos'][0]}")


if __name__ == "__main__":
    main()
