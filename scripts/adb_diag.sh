#!/usr/bin/env bash
# adb_diag.sh — Coleta diagnostica de um save em andamento no celular (ManagerFoot).
#
# Uso:
#   bash scripts/adb_diag.sh            # coleta snapshot: banco + datastore + logcat (erros ja no buffer)
#   bash scripts/adb_diag.sh watch      # captura logcat AO VIVO do app (Ctrl+C para parar) — jogue e reproduza a falha
#   bash scripts/adb_diag.sh pull       # so puxa banco + datastore (sem logcat)
#
# Requisitos no celular: Depuracao USB ligada + app instalado em build DEBUG (debuggable).
# Nada e alterado no save — tudo leitura.
set -uo pipefail

PKG="br.com.managerfoot"
OUTDIR="${OUTDIR:-adb_diag_out}"
MODE="${1:-snapshot}"

# ── Localiza o adb ────────────────────────────────────────────────────────────
find_adb() {
  if command -v adb >/dev/null 2>&1; then echo "adb"; return; fi
  local cands=(
    "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
    "$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
    "C:/Android/platform-tools/adb.exe"
  )
  # sdk.dir do local.properties
  if [ -f local.properties ]; then
    local sdk
    sdk=$(grep -i '^sdk.dir=' local.properties | sed 's/^sdk.dir=//; s/\\\\/\//g; s/\\:/:/g' | tr -d '\r')
    [ -n "$sdk" ] && cands+=("$sdk/platform-tools/adb.exe")
  fi
  for c in "${cands[@]}"; do [ -x "$c" ] && { echo "$c"; return; }; done
  echo ""
}

ADB="$(find_adb)"
if [ -z "$ADB" ]; then
  echo "ERRO: adb nao encontrado. Instale o Android SDK Platform-Tools ou ajuste o PATH." >&2
  exit 1
fi
echo "adb: $ADB"
"$ADB" version | head -1

# ── Confere dispositivo conectado ─────────────────────────────────────────────
DEVLINE=$("$ADB" devices | awk 'NR>1 && $2=="device" {print; c++} END{exit !c}')
if [ $? -ne 0 ]; then
  echo ""
  echo "NENHUM dispositivo autorizado conectado."
  echo "  1) Conecte o celular via USB"
  echo "  2) Ative 'Depuracao USB' nas Opcoes de desenvolvedor"
  echo "  3) Aceite o prompt 'Permitir depuracao USB' na tela do celular"
  echo "  4) Rode de novo: bash scripts/adb_diag.sh"
  "$ADB" devices -l
  exit 2
fi
echo "Dispositivo: $DEVLINE"

# ── Confere que o app esta instalado e e debuggable (run-as funciona) ─────────
if ! "$ADB" shell pm list packages 2>/dev/null | tr -d '\r' | grep -q "package:$PKG"; then
  echo "ERRO: pacote $PKG nao instalado no dispositivo." >&2
  exit 3
fi
if ! "$ADB" exec-out run-as "$PKG" id >/dev/null 2>&1; then
  echo "ERRO: run-as falhou para $PKG. O app instalado NAO e debuggable (build release?)." >&2
  echo "      Instale o build debug:  ./gradlew installDebug" >&2
  exit 4
fi
echo "run-as OK (build debuggable)."

mkdir -p "$OUTDIR"

# ── Modo WATCH: logcat ao vivo do processo do app ─────────────────────────────
if [ "$MODE" = "watch" ]; then
  PID="$("$ADB" shell pidof "$PKG" | tr -d '\r')"
  echo ""
  if [ -z "$PID" ]; then
    echo "App nao esta em execucao. Abra o ManagerFoot no celular e rode de novo em modo watch."
    echo "Capturando TODO o logcat filtrado por '$PKG' (menos preciso). Ctrl+C para parar."
    "$ADB" logcat -c
    "$ADB" logcat -v time 2>&1 | grep --line-buffered -iE "managerfoot|AndroidRuntime|FATAL|Exception|Room|SQLite" | tee "$OUTDIR/logcat_watch.txt"
  else
    echo "Capturando logcat AO VIVO do PID $PID. Jogue e reproduza a falha. Ctrl+C para parar."
    "$ADB" logcat -c
    "$ADB" logcat -v time --pid="$PID" 2>&1 | tee "$OUTDIR/logcat_watch.txt"
  fi
  exit 0
fi

# ── Puxa banco Room + DataStore ───────────────────────────────────────────────
echo ""
echo "== Arquivos do app =="
"$ADB" exec-out run-as "$PKG" sh -c 'ls -R databases datastore files 2>/dev/null' | tr -d '\r' | tee "$OUTDIR/file_list.txt"

echo ""
echo "== Puxando bancos =="
DBS=$("$ADB" exec-out run-as "$PKG" sh -c 'ls databases 2>/dev/null' | tr -d '\r' | grep -E '\.db$' || true)
for db in $DBS; do
  echo "  -> $db"
  "$ADB" exec-out run-as "$PKG" cat "databases/$db"      > "$OUTDIR/$db"        2>/dev/null || true
  "$ADB" exec-out run-as "$PKG" cat "databases/$db-wal"  > "$OUTDIR/$db-wal"    2>/dev/null || true
  "$ADB" exec-out run-as "$PKG" cat "databases/$db-shm"  > "$OUTDIR/$db-shm"    2>/dev/null || true
done

echo ""
echo "== Puxando DataStore (preferences) =="
PBS=$("$ADB" exec-out run-as "$PKG" sh -c 'ls datastore 2>/dev/null' | tr -d '\r' | grep -E '\.preferences_pb$' || true)
for pb in $PBS; do
  echo "  -> $pb"
  "$ADB" exec-out run-as "$PKG" cat "datastore/$pb" > "$OUTDIR/$pb" 2>/dev/null || true
done

# ── Snapshot de erros ja no buffer do logcat ──────────────────────────────────
if [ "$MODE" != "pull" ]; then
  echo ""
  echo "== Snapshot de logcat (erros no buffer atual) =="
  "$ADB" logcat -d -v time            > "$OUTDIR/logcat_full.txt" 2>/dev/null || true
  "$ADB" logcat -d -b crash -v time   > "$OUTDIR/logcat_crash.txt" 2>/dev/null || true
  grep -iE "managerfoot|AndroidRuntime|FATAL|E ActivityManager|Exception|Room|SQLite" "$OUTDIR/logcat_full.txt" \
    > "$OUTDIR/logcat_erros.txt" 2>/dev/null || true
  echo "  logcat_full.txt / logcat_crash.txt / logcat_erros.txt"
fi

# ── Analise do banco ──────────────────────────────────────────────────────────
MAINDB=""
for cand in $DBS; do MAINDB="$cand"; done   # ultimo .db (normalmente o do Room)
echo ""
if [ -n "$MAINDB" ] && command -v python >/dev/null 2>&1; then
  echo "== Analise do banco ($MAINDB) =="
  python scripts/analyze_save.py "$OUTDIR/$MAINDB" | tee "$OUTDIR/analise.txt"
else
  echo "(Banco nao puxado ou python indisponivel — pulei a analise automatica.)"
fi

echo ""
echo "OK. Saida em: $OUTDIR/"
