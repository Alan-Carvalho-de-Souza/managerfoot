# -*- coding: utf-8 -*-
"""
Aplica nomes REAIS de tecnicos aos times do seed_data.json, casando por nome
do time. Times sem entrada no mapa mantem o nome ficticio gerado por
gen_tecnicos.py.

IMPORTANTE — precisao: os nomes refletem a comissao tecnica por volta de 2025
(conhecimento ate jan/2026). O "carrossel" de tecnicos e intenso, entao varios
podem estar desatualizados. Edite REAIS abaixo para corrigir/estender.
Rode DEPOIS de gen_tecnicos.py:
    python scripts/merge_seeds.py
    python scripts/gen_tecnicos.py
    python scripts/set_real_tecnicos.py
"""
import json
import io

PATH = r"C:\Dev\ManagerFoot\app\src\main\assets\seed_data.json"

# nome_do_time -> (nome_tecnico, nacionalidade)
REAIS = {
    # ── Premier League (Inglaterra) ──────────────────────────────
    "Manchester City":     ("Pep Guardiola",        "Espanha"),
    "Liverpool":           ("Arne Slot",            "Holanda"),  # ingles (o URU e "Liverpool FC")
    "Arsenal":             ("Mikel Arteta",         "Espanha"),
    "Chelsea":             ("Enzo Maresca",         "Italia"),
    "Manchester United":   ("Ruben Amorim",         "Portugal"),
    "Newcastle United":    ("Eddie Howe",           "Inglaterra"),
    "Tottenham Hotspur":   ("Thomas Frank",         "Dinamarca"),
    "Aston Villa":         ("Unai Emery",           "Espanha"),
    "Brighton":            ("Fabian Hurzeler",      "Alemanha"),
    "Crystal Palace":      ("Oliver Glasner",       "Austria"),
    "Bournemouth":         ("Andoni Iraola",        "Espanha"),
    "Everton":             ("David Moyes",          "Escocia"),
    "Fulham":              ("Marco Silva",          "Portugal"),
    "Brentford":           ("Keith Andrews",        "Irlanda"),
    "Leeds United":        ("Daniel Farke",         "Alemanha"),
    "Sunderland":          ("Regis Le Bris",        "Franca"),
    "Coventry City":       ("Frank Lampard",        "Inglaterra"),
    "Ipswich Town":        ("Kieran McKenna",       "Irlanda do Norte"),
    "Nottingham Forest":   ("Nuno Espirito Santo",  "Portugal"),
    # Hull City deixado ficticio (baixa confianca).

    # ── Brasileirao Serie A ──────────────────────────────────────
    "Flamengo":            ("Filipe Luis",          "Brasil"),
    "Palmeiras":           ("Abel Ferreira",        "Portugal"),
    "Atletico-MG":         ("Cuca",                 "Brasil"),
    "Sao Paulo":           ("Luis Zubeldia",        "Argentina"),
    "Corinthians":         ("Dorival Junior",       "Brasil"),
    "Fluminense":          ("Renato Gaucho",        "Brasil"),
    "Botafogo":            ("Renato Paiva",         "Portugal"),
    "Internacional":       ("Roger Machado",        "Brasil"),
    "Gremio":              ("Gustavo Quinteros",    "Argentina"),
    "Cruzeiro":            ("Leonardo Jardim",      "Portugal"),
    "Vasco da Gama":       ("Fernando Diniz",       "Brasil"),
    "Bahia":               ("Rogerio Ceni",         "Brasil"),
    "Santos":              ("Cleber Xavier",        "Brasil"),

    # ── Argentina Primera ────────────────────────────────────────
    "River Plate":         ("Marcelo Gallardo",     "Argentina"),
    "Boca Juniors":        ("Miguel Angel Russo",   "Argentina"),
    "Racing Club":         ("Gustavo Costas",       "Argentina"),
    "Estudiantes LP":      ("Eduardo Dominguez",    "Argentina"),
    "Rosario Central":     ("Ariel Holan",          "Argentina"),
    "Lanus":               ("Mauricio Pellegrino",  "Argentina"),
    "Velez Sarsfield":     ("Guillermo Barros Schelotto", "Argentina"),
    "Independiente":       ("Julio Vaccari",        "Argentina"),

    # ── Uruguai Primera ──────────────────────────────────────────
    "Peñarol":             ("Diego Aguirre",        "Uruguai"),
}

# Remove placeholders None (chaves ambiguas nao aplicadas automaticamente)
REAIS = {k: v for k, v in REAIS.items() if v is not None}


def abrev(nome: str) -> str:
    partes = [p for p in nome.split(" ") if p]
    if len(partes) >= 2:
        return f"{partes[0][0]}. {partes[-1]}"
    return nome[:15]


def main():
    with io.open(PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    time_por_id = {t["id"]: t for t in data["times"]}
    aplicados, faltando = [], []
    usados = set()

    for tec in data.get("tecnicos", []):
        time = time_por_id.get(tec.get("time_id"))
        if not time:
            continue
        nome_time = time.get("nome", "")
        real = REAIS.get(nome_time)
        if real:
            nome, nac = real
            tec["nome"] = nome
            tec["nome_abrev"] = abrev(nome)
            tec["nacionalidade"] = nac
            aplicados.append(nome_time)
            usados.add(nome_time)

    # Times sem nome real (mantiveram ficticio)
    for t in data["times"]:
        if t["nome"] not in usados:
            faltando.append(t["nome"])

    with io.open(PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))

    nao_mapeados = [k for k in REAIS if k not in usados]
    print(f"Tecnicos reais aplicados: {len(aplicados)}")
    print(f"Times mantendo nome ficticio: {len(faltando)}")
    if nao_mapeados:
        print(f"[AVISO] chaves REAIS sem time correspondente: {nao_mapeados}")


if __name__ == "__main__":
    main()
