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
