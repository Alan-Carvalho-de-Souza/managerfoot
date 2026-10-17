# -*- coding: utf-8 -*-
"""
analyze_save.py — Diagnostico de um save do ManagerFoot puxado do celular.

Uso: python scripts/analyze_save.py <caminho/para/o.db>

Le o banco Room (somente leitura), introspecta o schema e imprime:
- campeonatos (com pais, rodada atual/total, encerrado)
- partidas jogadas x totais por campeonato
- eventos_partida por tipo, e GOLs por campeonato (base dos artilheiros)
- top artilheiros das ligas estrangeiras (Espanha/Italia/Inglaterra)
- checagem de integridade (classificacao x partidas; times/jogadores por divisao)
- ANOMALIAS provaveis (liga com jogos mas sem gols, time sem jogadores, etc.)
"""
import sys, os, sqlite3

def main():
    if len(sys.argv) < 2:
        print("Uso: python scripts/analyze_save.py <arquivo.db>"); sys.exit(1)
    path = sys.argv[1]
    if not os.path.exists(path):
        print(f"ERRO: arquivo nao encontrado: {path}"); sys.exit(1)

    con = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    cur = con.cursor()

    def tables():
        return {r[0] for r in cur.execute(
            "SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
    def cols(t):
        try:
            return {r[1] for r in cur.execute(f"PRAGMA table_info({t})").fetchall()}
        except sqlite3.Error:
            return set()
    def has(t, *cs):
        c = cols(t); return t in T and all(x in c for x in cs)
    def q(sql, args=()):
        try: return cur.execute(sql, args).fetchall()
        except sqlite3.Error as e:
            print(f"  (query falhou: {e})"); return []

    T = tables()
    print(f"Banco: {path}  ({os.path.getsize(path)/1024:.0f} KB)")
    print(f"Tabelas: {', '.join(sorted(T))}\n")

    # ── Campeonatos ───────────────────────────────────────────────────────────
    camp_pais = {}   # id -> pais
    camp_nome = {}
    if has("campeonatos", "id"):
        c = cols("campeonatos")
        sel = ["id", "nome"]
        for x in ("pais","tipo","formato","rodadaAtual","totalRodadas","encerrado","temporadaId"):
            if x in c: sel.append(x)
        rows = q(f"SELECT {','.join(sel)} FROM campeonatos ORDER BY id")
        print(f"== CAMPEONATOS ({len(rows)}) ==")
        for r in rows:
            camp_pais[r["id"]] = (r["pais"] if "pais" in r.keys() else None)
            camp_nome[r["id"]] = r["nome"]
            enc = r["encerrado"] if "encerrado" in r.keys() else ""
            ra  = r["rodadaAtual"] if "rodadaAtual" in r.keys() else "?"
            tr  = r["totalRodadas"] if "totalRodadas" in r.keys() else "?"
            pais= r["pais"] if "pais" in r.keys() else ""
            tmp = r["temporadaId"] if "temporadaId" in r.keys() else ""
            print(f"  #{r['id']:>4} T{tmp} [{pais or '-':<9}] rod {ra}/{tr} enc={enc}  {r['nome']}")
        print()

    # ── Partidas jogadas x totais por campeonato ──────────────────────────────
    jogadas_por_camp = {}
    if has("partidas", "campeonatoId", "jogada"):
        rows = q("""SELECT campeonatoId,
                           SUM(CASE WHEN jogada=1 THEN 1 ELSE 0 END) AS jog,
                           COUNT(*) AS tot
                    FROM partidas GROUP BY campeonatoId ORDER BY campeonatoId""")
        print("== PARTIDAS (jogadas/total por campeonato) ==")
        for r in rows:
            jogadas_por_camp[r["campeonatoId"]] = r["jog"]
            nome = camp_nome.get(r["campeonatoId"], "?")
            pais = camp_pais.get(r["campeonatoId"]) or "-"
            print(f"  camp #{r['campeonatoId']:>4} [{pais:<9}] {r['jog']:>3}/{r['tot']:<3} jogadas  {nome}")
        print()

    # ── Eventos por tipo ──────────────────────────────────────────────────────
    if has("eventos_partida", "tipo"):
        rows = q("SELECT tipo, COUNT(*) n FROM eventos_partida GROUP BY tipo ORDER BY n DESC")
        print("== EVENTOS_PARTIDA (por tipo) ==")
        for r in rows: print(f"  {r['tipo']:<18} {r['n']}")
        print()

    # ── GOLs por campeonato (base dos artilheiros) ────────────────────────────
    gols_por_camp = {}
    if has("eventos_partida", "partidaId", "tipo") and has("partidas", "id", "campeonatoId"):
        rows = q("""SELECT p.campeonatoId AS cid, COUNT(*) AS gols
                    FROM eventos_partida ep JOIN partidas p ON ep.partidaId=p.id
                    WHERE ep.tipo='GOL' GROUP BY p.campeonatoId ORDER BY gols DESC""")
        print("== GOLs registrados por campeonato (artilheiros) ==")
        for r in rows:
            gols_por_camp[r["cid"]] = r["gols"]
            nome = camp_nome.get(r["cid"], "?"); pais = camp_pais.get(r["cid"]) or "-"
            print(f"  camp #{r['cid']:>4} [{pais:<9}] {r['gols']:>4} gols  {nome}")
        print()

    # ── Top artilheiros das ligas estrangeiras ────────────────────────────────
    if (has("eventos_partida","partidaId","jogadorId","tipo") and has("partidas","id","campeonatoId")
            and has("jogadores","id","nome")):
        alvo = [cid for cid,p in camp_pais.items() if p in ("Espanha","Italia","Inglaterra")]
        for cid in alvo:
            rows = q("""SELECT j.nome AS nome, COUNT(*) AS g
                        FROM eventos_partida ep
                        JOIN partidas p ON ep.partidaId=p.id
                        JOIN jogadores j ON ep.jogadorId=j.id
                        WHERE ep.tipo='GOL' AND p.campeonatoId=?
                        GROUP BY ep.jogadorId ORDER BY g DESC LIMIT 5""", (cid,))
            if rows:
                print(f"== ARTILHEIROS  {camp_nome.get(cid,'?')} (camp #{cid}, {camp_pais.get(cid)}) ==")
                for r in rows: print(f"    {r['g']:>2}  {r['nome']}")
                print()

    # ── Times/jogadores por divisao ───────────────────────────────────────────
    if has("times","id","divisao"):
        rows = q("SELECT divisao, COUNT(*) n FROM times GROUP BY divisao ORDER BY divisao")
        print("== TIMES por divisao ==")
        for r in rows: print(f"  div {r['divisao']:>3}: {r['n']} times")
        print()
    if has("jogadores","timeId") and has("times","id","divisao"):
        rows = q("""SELECT t.divisao AS d, COUNT(*) AS n, COUNT(DISTINCT j.timeId) AS times
                    FROM jogadores j JOIN times t ON j.timeId=t.id GROUP BY t.divisao ORDER BY t.divisao""")
        print("== JOGADORES por divisao ==")
        for r in rows:
            media = r["n"]/r["times"] if r["times"] else 0
            print(f"  div {r['d']:>3}: {r['n']} jogadores em {r['times']} times (media {media:.1f})")
        print()

    # ── ANOMALIAS ─────────────────────────────────────────────────────────────
    print("== ANOMALIAS / ALERTAS ==")
    achou = False
    # liga com partidas jogadas mas sem nenhum gol registrado
    for cid, jog in jogadas_por_camp.items():
        if jog > 0 and gols_por_camp.get(cid, 0) == 0:
            print(f"  [!] camp #{cid} ({camp_nome.get(cid,'?')}) tem {jog} jogos mas 0 gols em eventos_partida "
                  f"-> artilheiros ficarao vazios"); achou = True
    # time sem jogadores suficientes
    if has("jogadores","timeId") and has("times","id","nome"):
        rows = q("""SELECT t.id, t.nome, COUNT(j.id) n FROM times t
                    LEFT JOIN jogadores j ON j.timeId=t.id GROUP BY t.id HAVING n < 11 ORDER BY n""")
        for r in rows:
            print(f"  [!] time #{r['id']} {r['nome']}: so {r['n']} jogadores (<11)"); achou = True
    if not achou:
        print("  nenhuma anomalia obvia detectada.")
    con.close()

if __name__ == "__main__":
    main()
