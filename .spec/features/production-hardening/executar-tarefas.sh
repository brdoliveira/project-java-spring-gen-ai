#!/usr/bin/env bash
# executar-tarefas.sh — gerado por `onp-spec plano production-hardening` em 2026-08-18 19:07
# NÃO edite à mão: mudou tasks.md ou a config, regenere o plano.
#
# uso:
#   bash executar-tarefas.sh                  tudo (ondas → sequenciais → gate)
#   bash executar-tarefas.sh --faixa <id>     reexecuta UMA faixa (+ merge + gate)
#   bash executar-tarefas.sh --seq <T-xxx>    reexecuta UMA tarefa sequencial
#   bash executar-tarefas.sh --gate           só o gate (verify + audit)
#   bash executar-tarefas.sh --listar         mostra faixas, tarefas e estados
#   (acrescente --sem-gate para não rodar o gate ao final)
#
# resumo do que está rolando, a qualquer momento: onp-spec resumo production-hardening
set -u
set -o pipefail

RUN_ID='project-java-spring-gen-ai-production-hardening-msz1b979'
FEATURE='production-hardening'
BASE_BRANCH='spec/production-hardening'
ENGINE='C:\Users\brufe\.agents\skills\onp-spec-driven\scripts\onp-spec.mjs'
CODEX_FLAGS=(--sandbox 'workspace-write')
STREAM_FLAGS=(--json)
FALHAS=""
COM_GATE=1
RESUMO_MODEL='gpt-5.6-luna'
RESUMO_PID=""

verde()    { printf '\033[32m%s\033[0m\n' "$*"; }
vermelho() { printf '\033[31m%s\033[0m\n' "$*"; }
amarelo()  { printf '\033[33m%s\033[0m\n' "$*"; }
info()     { printf '· %s\n' "$*"; }
falhar()   { vermelho "✘ $*"; exit 1; }

# eventos vão para o ledger GLOBAL (~/.onp-spec/painel/ledger.jsonl):
# um arquivo para todos os projetos, é o que o onp-spec resumo lê
evento() { node "$ENGINE" evento --run "$RUN_ID" "$@" >/dev/null 2>&1 || true; }

# ── ambiente (todos os modos passam por aqui) ────────────────────────
preparar_ambiente() {
  command -v git >/dev/null 2>&1 || falhar "git não encontrado"
  command -v node >/dev/null 2>&1 || falhar "node não encontrado"
  command -v codex >/dev/null 2>&1 || falhar "Codex CLI (codex) não encontrado — instale-o ou siga o modo manual em plano-execucao.md"
  TOPLEVEL=$(git rev-parse --show-toplevel 2>/dev/null) || falhar "fora de um repositório git"
  cd "$TOPLEVEL" || exit 1
  # artefatos recém-gerados pelo `onp-spec plano` são sujeira esperada:
  # se forem a ÚNICA sujeira, o script mesmo commita; qualquer outra, aborta
  if [ -n "$(git status --porcelain)" ]; then
    if [ -z "$(git status --porcelain | grep -v -e 'plano-execucao\.' -e 'plano\.json' -e 'executar-tarefas\.sh')" ]; then
      git add -A
      git commit -q -m "plano de execução: $FEATURE (artefatos gerados)"
      info "artefatos do plano commitados"
    else
      falhar "árvore suja além dos artefatos do plano — commite ou faça git stash antes (os worktrees partem do último commit)"
    fi
  fi
  git ls-files --error-unmatch -- '.spec/features/production-hardening/spec.md' >/dev/null 2>&1 || falhar "spec.md não está commitada — os worktrees das faixas precisam dela no git"
  ATUAL=$(git rev-parse --abbrev-ref HEAD)
  [ "$ATUAL" != "HEAD" ] || falhar "HEAD destacado — troque para uma branch"
  if [ "$ATUAL" != "$BASE_BRANCH" ]; then
    if git show-ref --verify --quiet "refs/heads/$BASE_BRANCH"; then
      git checkout -q "$BASE_BRANCH" || falhar "não consegui trocar para $BASE_BRANCH"
    else
      git checkout -q -b "$BASE_BRANCH" || falhar "não consegui criar $BASE_BRANCH"
    fi
    info "branch de trabalho: $BASE_BRANCH (a partir de $ATUAL)"
  fi
  git worktree prune
  LOG_DIR="$(dirname "$TOPLEVEL")/onp-worktrees/project-java-spring-gen-ai-production-hardening-logs"
  WT_BASE="$(dirname "$TOPLEVEL")/onp-worktrees/project-java-spring-gen-ai-production-hardening"
  STREAMS_DIR="${ONP_SPEC_HOME:-$HOME/.onp-spec}/painel/streams/$RUN_ID"
  mkdir -p "$LOG_DIR" "$STREAMS_DIR"
}

# worktree limpo mesmo depois de uma tentativa que falhou
preparar_worktree() { # $1=faixa $2=branch $3=worktree
  git worktree prune
  if [ -e "$3" ]; then git worktree remove --force "$3" >/dev/null 2>&1; rm -rf "$3"; fi
  if git show-ref --verify --quiet "refs/heads/$2"; then git branch -D "$2" >/dev/null 2>&1; fi
  git worktree add "$3" -b "$2" >/dev/null 2>&1 || { vermelho "✘ não consegui criar o worktree de $1 em $3"; return 1; }
}

tentativa() { # $1=faixa — conta reexecuções (vai para o ledger)
  local arq="$LOG_DIR/.tentativa-$1"
  local n=1
  [ -f "$arq" ] && n=$(( $(cat "$arq") + 1 ))
  printf "%s" "$n" > "$arq"
  printf "%s" "$n"
}

# uma tarefa = uma sessão codex exec headless com contexto limpo.
# o JSONL da sessão vira o stream da tarefa no ledger
rodar_tarefa() { # $1=escopo(faixa|seq) $2=T-xxx $3=prompt $4=modelo $5=esforço
  local chave="$1--$2"
  local stream="$STREAMS_DIR/$chave.jsonl"
  evento --tipo tarefa --tarefa "$2" --faixa "$1" --estado executando --stream "$chave"
  info "$2 — codex exec ($4 · $5) · stream: $chave"
  # --add-dir: o .git compartilhado dos worktrees mora no repo principal —
  # sem ele o sandbox workspace-write bloquearia o commit da tarefa
  if codex exec "$3" --model "$4" -c model_reasoning_effort="$5" "${STREAM_FLAGS[@]}" "${CODEX_FLAGS[@]}" --add-dir "$TOPLEVEL" > "$stream" 2>>"$LOG_DIR/$1.log"; then
    evento --tipo tarefa --tarefa "$2" --faixa "$1" --estado concluida --stream "$chave"
    node "$ENGINE" stream-resumo "$RUN_ID" "$chave" 2>/dev/null || true
    return 0
  fi
  evento --tipo tarefa --tarefa "$2" --faixa "$1" --estado falhou --stream "$chave"
  node "$ENGINE" stream-resumo "$RUN_ID" "$chave" 2>/dev/null || true
  return 1
}

mesclar_faixa() { # $1=faixa $2=branch $3=worktree $4=exit-da-faixa
  if [ "$4" -ne 0 ]; then
    evento --tipo faixa --faixa "$1" --estado falhou
    vermelho "✘ $1 falhou (log: $LOG_DIR/$1.log) — worktree mantido para inspeção: $3"
    amarelo "  reexecute só ela: bash .spec/features/production-hardening/executar-tarefas.sh --faixa $1"
    FALHAS="$FALHAS $1"; return 1
  fi
  evento --tipo faixa --faixa "$1" --estado mesclando
  if git merge --no-ff "$2" -m "merge $1 ($FEATURE)"; then
    git worktree remove --force "$3" >/dev/null 2>&1
    git branch -d "$2" >/dev/null 2>&1
    evento --tipo faixa --faixa "$1" --estado mesclada
    verde "✔ $1 mesclada em $BASE_BRANCH"
  else
    git merge --abort >/dev/null 2>&1
    evento --tipo faixa --faixa "$1" --estado conflito
    vermelho "✘ conflito ao mesclar $1 — resolva na mão: git merge $2 (worktree mantido: $3)"
    FALHAS="$FALHAS $1"; return 1
  fi
}

marcar_concluidas() { # $@=T-xxx
  for t in "$@"; do node "$ENGINE" tarefa "$FEATURE" "$t" concluida >/dev/null || true; done
}

# ── resumo geral de andamento: 1/min enquanto a execução roda ─────────
# escrito por IA (codex exec somente leitura) com fallback do motor; vai
# para o terminal e para o ledger — o agente repassa o texto no chat.
gerar_resumo() {
  local ctx ia
  ctx=$(node "$ENGINE" resumo "$FEATURE" --contexto 2>/dev/null) || ctx=""
  [ -n "$ctx" ] || return 0
  ia=$(codex exec "Você narra, para o dono do produto, uma execução de tarefas de código em andamento. Estado mecânico:

$ctx

Escreva o RESUMO GERAL DE ANDAMENTO: um parágrafo único de 2 a 4 frases, em português simples, dizendo o que está acontecendo agora, o que já terminou, o que falhou e se o usuário precisa agir. Sem markdown, sem listas." --model "$RESUMO_MODEL" --sandbox read-only --ephemeral 2>/dev/null)
  if [ -n "$ia" ]; then
    node "$ENGINE" resumo "$FEATURE" --gravar --origem ia --texto "$ia" >/dev/null 2>&1 || true
    printf '\n📣 resumo (IA): %s\n' "$ia"
  else
    node "$ENGINE" resumo "$FEATURE" --gravar >/dev/null 2>&1 || true
    printf '\n📣 resumo: %s\n' "$(node "$ENGINE" resumo "$FEATURE" 2>/dev/null)"
  fi
}

# mata o loop E o sleep filho — senão o sleep herda o stdout e quem chamou
# o script via pipe fica esperando EOF por até 60s depois do exit
parar_resumos() {
  [ -n "$RESUMO_PID" ] || return 0
  command -v pkill >/dev/null 2>&1 && pkill -P "$RESUMO_PID" 2>/dev/null
  kill "$RESUMO_PID" 2>/dev/null
  RESUMO_PID=""
}

iniciar_resumos() {
  ( while :; do sleep 60; gerar_resumo; done ) &
  RESUMO_PID=$!
  # ao sair: para o loop e grava um último resumo (o estado final, do motor)
  trap 'parar_resumos; node "$ENGINE" resumo "$FEATURE" --gravar >/dev/null 2>&1 || true' EXIT
}

# ── faixa-1: T-002 ──
executar_faixa_1() {
  local WT="$WT_BASE-faixa-1"
  preparar_worktree 'faixa-1' 'spec/production-hardening-faixa-1' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-1' --estado executando --tentativa "$(tentativa 'faixa-1')"
  : > "$LOG_DIR/faixa-1.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-1' 'T-002' 'Você executa UMA tarefa da feature "production-hardening" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/production-hardening/spec.md, .spec/features/production-hardening/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-002 — "Tornar configuração e migrações não destrutivas"
  critérios/refs: AC-001 (Startup não executa SQL destrutivo), AC-002 (Reindexação RAG é opt-in), AC-003 (Migrações preservam registros existentes), AC-004 (Defaults não dependem de uma máquina específica), AC-006 (Produção usa observabilidade segura)
  arquivos permitidos (e seus testes): src/main/resources/application.yml, src/main/resources/application-local.yml, src/main/resources/application-prod.yml, src/main/resources/db/data.sql, src/main/resources/db/schema.sql, src/main/resources/db/migration/V1__create_review_state_snapshot.sql, posture-service/src/main/resources/application.yml, posture-service/src/main/resources/application-local.yml, posture-service/src/main/resources/application-prod.yml, posture-service/src/main/resources/db/data.sql, posture-service/src/main/resources/db/schema.sql, posture-service/src/main/resources/db/migration/V1__create_security_posture.sql, posture-service/src/main/resources/db/local-data.sql, src/test/java/com/genai/java/spring/config/ConfigurationSafetyTest.java, src/test/java/com/genai/java/spring/config/DatabaseMigrationIT.java, posture-service/src/test/java/com/genai/posture/config/PostureMigrationIT.java
  mensagem de commit: "T-002 production-hardening: Tornar configuração e migrações não destrutivas"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node scripts/run-spec-tests.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-sol' high
  ) >> "$LOG_DIR/faixa-1.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-1' 'spec/production-hardening-faixa-1' "$WT" "$st" || return 1
  marcar_concluidas T-002
  return 0
}

# ── faixa-2: T-003 ──
executar_faixa_2() {
  local WT="$WT_BASE-faixa-2"
  preparar_worktree 'faixa-2' 'spec/production-hardening-faixa-2' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-2' --estado executando --tentativa "$(tentativa 'faixa-2')"
  : > "$LOG_DIR/faixa-2.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-2' 'T-003' 'Você executa UMA tarefa da feature "production-hardening" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/production-hardening/spec.md, .spec/features/production-hardening/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-003 — "Proteger APIs e garantir posse/idempotência de revisão"
  critérios/refs: AC-007 (API de produção exige JWT), AC-008 (Relatório pertence ao usuário autenticado), AC-009 (Modo local permanece utilizável), AC-010 (Payload e upload inválidos são rejeitados), AC-011 (Conteúdo sensível não aparece em logs), AC-014 (Aprovação repetida não duplica o agente)
  arquivos permitidos (e seus testes): src/main/java/com/genai/java/spring/security/SecurityConfiguration.java, src/main/java/com/genai/java/spring/security/CurrentSubject.java, src/main/java/com/genai/java/spring/aiagent/controller/SecurityReviewController.java, src/main/java/com/genai/java/spring/aiagent/service/SecurityReviewService.java, src/main/java/com/genai/java/spring/aiagent/service/impl/SecurityReviewServiceImpl.java, src/main/java/com/genai/java/spring/aiagent/dataaccess/repository/ReviewStateRepository.java, src/main/java/com/genai/java/spring/aiagent/dataaccess/repository/impl/ReviewStateRepositoryImpl.java, src/main/java/com/genai/java/spring/aiagent/dto/ReviewState.java, src/main/java/com/genai/java/spring/aiagent/dto/ApprovalRequest.java, src/main/java/com/genai/java/spring/aiagent/dto/FollowUpRequestDto.java, src/main/resources/db/migration/V2__add_review_owner_and_version.sql, src/test/java/com/genai/java/spring/security/ApiSecurityTest.java, src/test/java/com/genai/java/spring/aiagent/SecurityReviewAuthorizationTest.java, src/test/java/com/genai/java/spring/aiagent/ApprovalIdempotencyTest.java, src/test/java/com/genai/java/spring/aiagent/SensitiveLoggingTest.java
  mensagem de commit: "T-003 production-hardening: Proteger APIs e garantir posse/idempotência de revisão"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node scripts/run-spec-tests.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-sol' high
  ) >> "$LOG_DIR/faixa-2.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-2' 'spec/production-hardening-faixa-2' "$WT" "$st" || return 1
  marcar_concluidas T-003
  return 0
}

# ── faixa-3: T-004 ──
executar_faixa_3() {
  local WT="$WT_BASE-faixa-3"
  preparar_worktree 'faixa-3' 'spec/production-hardening-faixa-3' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-3' --estado executando --tentativa "$(tentativa 'faixa-3')"
  : > "$LOG_DIR/faixa-3.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-3' 'T-004' 'Você executa UMA tarefa da feature "production-hardening" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/production-hardening/spec.md, .spec/features/production-hardening/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-004 — "Endurecer uploads e armazenamento de imagens"
  critérios/refs: AC-010 (Payload e upload inválidos são rejeitados), AC-013 (Imagens concorrentes não se sobrescrevem)
  arquivos permitidos (e seus testes): src/main/java/com/genai/java/spring/aiagent/service/impl/FileStorageServiceImpl.java, src/main/java/com/genai/java/spring/multimodality/texttoimage/GeneratedImageStorage.java, src/main/java/com/genai/java/spring/multimodality/texttoimage/MarketingAssetController.java, src/test/java/com/genai/java/spring/aiagent/FileStorageServiceTest.java, src/test/java/com/genai/java/spring/multimodality/GeneratedImageStorageTest.java
  mensagem de commit: "T-004 production-hardening: Endurecer uploads e armazenamento de imagens"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node scripts/run-spec-tests.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' high
  ) >> "$LOG_DIR/faixa-3.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-3' 'spec/production-hardening-faixa-3' "$WT" "$st" || return 1
  marcar_concluidas T-004
  return 0
}

# ── faixa-4: T-005 ──
executar_faixa_4() {
  local WT="$WT_BASE-faixa-4"
  preparar_worktree 'faixa-4' 'spec/production-hardening-faixa-4' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-4' --estado executando --tentativa "$(tentativa 'faixa-4')"
  : > "$LOG_DIR/faixa-4.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-4' 'T-005' 'Você executa UMA tarefa da feature "production-hardening" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/production-hardening/spec.md, .spec/features/production-hardening/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-005 — "Isolar providers e limitar chamadas externas"
  critérios/refs: AC-005 (Provedores opcionais não impedem o startup local), AC-012 (Chamadas externas possuem limite de tempo)
  arquivos permitidos (e seus testes): src/main/java/com/genai/java/spring/config/AIProviderConfig.java, src/main/java/com/genai/java/spring/config/ProviderProperties.java, src/main/java/com/genai/java/spring/aiagent/tools/web/WebTools.java, src/main/java/com/genai/java/spring/aiagent/tools/posture/PostureTools.java, src/main/java/com/genai/java/spring/rag/rerank/processor/RerankPostProcessor.java, src/test/java/com/genai/java/spring/config/ProviderIsolationTest.java, src/test/java/com/genai/java/spring/aiagent/OutboundToolResilienceTest.java
  mensagem de commit: "T-005 production-hardening: Isolar providers e limitar chamadas externas"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node scripts/run-spec-tests.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-sol' high
  ) >> "$LOG_DIR/faixa-4.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-4' 'spec/production-hardening-faixa-4' "$WT" "$st" || return 1
  marcar_concluidas T-005
  return 0
}

# ── faixa-5: T-006 ──
executar_faixa_5() {
  local WT="$WT_BASE-faixa-5"
  preparar_worktree 'faixa-5' 'spec/production-hardening-faixa-5' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-5' --estado executando --tentativa "$(tentativa 'faixa-5')"
  : > "$LOG_DIR/faixa-5.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-5' 'T-006' 'Você executa UMA tarefa da feature "production-hardening" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/production-hardening/spec.md, .spec/features/production-hardening/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-006 — "Criar avaliação determinística do RAG"
  critérios/refs: AC-018 (Produção filtra contexto irrelevante), AC-019 (Dataset dourado produz relatório de avaliação)
  arquivos permitidos (e seus testes): src/main/java/com/genai/java/spring/rag/evaluation/RagEvaluationService.java, src/main/java/com/genai/java/spring/rag/evaluation/RagEvaluationReport.java, src/test/resources/rag/evaluation/golden-dataset.json, src/test/java/com/genai/java/spring/rag/RagProductionThresholdTest.java, src/test/java/com/genai/java/spring/rag/RagEvaluationServiceTest.java
  mensagem de commit: "T-006 production-hardening: Criar avaliação determinística do RAG"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node scripts/run-spec-tests.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-5.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-5' 'spec/production-hardening-faixa-5' "$WT" "$st" || return 1
  marcar_concluidas T-006
  return 0
}

# ── sequencial T-007 (fora da seleção do usuário) ──
executar_seq_T_007() {
  info 'sequencial T-007 — Documentar execução, segurança e operação'
  if rodar_tarefa seq 'T-007' 'Você executa UMA tarefa da feature "production-hardening" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/production-hardening/spec.md, .spec/features/production-hardening/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-007 — "Documentar execução, segurança e operação"
  critérios/refs: AC-020 (README descreve o caminho completo)
  arquivos permitidos (e seus testes): README.md, gen-ai-with-java-spring.postman_collection.json, docker/dev/compose.yml, docker/dev/Dockerfile, docker/dev/posture-service.Dockerfile, src/test/java/com/genai/java/spring/docs/DocumentationContractTest.java
  mensagem de commit: "T-007 production-hardening: Documentar execução, segurança e operação"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node scripts/run-spec-tests.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium >> "$LOG_DIR/seq.log" 2>&1; then
    # commit de segurança se o agente esqueceu (rastreabilidade > perfeição)
    if [ -n "$(git status --porcelain)" ]; then
      git add -A && git commit -q -m 'T-007 production-hardening: Documentar execução, segurança e operação (auto-commit do plano)'
    fi
    marcar_concluidas T-007
    verde "✔ T-007 concluída"
    return 0
  fi
  vermelho "✘ T-007 falhou (log: $LOG_DIR/seq.log)"
  amarelo "  reexecute só ela: bash .spec/features/production-hardening/executar-tarefas.sh --seq T-007"
  FALHAS="$FALHAS T-007"
  return 1
}

# ── gate: quem decide é a máquina ────────────────────────────────────
rodar_gate() {
  echo
  info "gate: verify + audit --ci"
  evento --tipo gate --etapa inicio
  node "$ENGINE" verify "$FEATURE"
  local v=$?
  evento --tipo gate --etapa verify --exit "$v"
  node "$ENGINE" audit --ci
  AUDIT=$?
  evento --tipo gate --etapa audit --exit "$AUDIT"
  # fecha a contabilidade: status das tarefas + prova do verify no git
  if [ -n "$(git status --porcelain -- '.spec')" ]; then
    git add -A -- '.spec'
    git commit -q -m "$FEATURE: status das tarefas + prova do verify (plano)"
    info "status das tarefas e prova do verify commitados"
  fi
  return "$AUDIT"
}

encerrar() { # $1=escopo
  echo
  if [ -n "$FALHAS" ]; then vermelho "faixas/tarefas com falha:$FALHAS"; fi
  # sem gate não existe veredito: NUNCA anunciar alinhamento sem o audit
  if [ "$COM_GATE" -eq 0 ]; then
    evento --tipo fim --exit 1 --escopo "$1"
    if [ -z "$FALHAS" ]; then
      amarelo "○ trabalho de '$1' terminou SEM o gate (--sem-gate) — isto NÃO é prova de nada"
      amarelo "  para o veredito: bash .spec/features/production-hardening/executar-tarefas.sh --gate"
      exit 0
    fi
    vermelho "e ainda há falhas — conserte e rode o gate"
    exit 1
  fi
  rodar_gate
  local audit=$?
  if [ "$audit" -eq 0 ] && [ -z "$FALHAS" ]; then
    evento --tipo fim --exit 0 --escopo "$1"
    verde "✔ plano concluído — especificação e código alinhados (audit exit 0) na branch $BASE_BRANCH"
    info "próximo passo: revise e leve para a main quando quiser (git merge $BASE_BRANCH)"
    exit 0
  fi
  evento --tipo fim --exit 1 --escopo "$1"
  vermelho "plano terminou com pendências — leia a saída do audit acima e os logs em $LOG_DIR"
  amarelo "dica: reexecute só o que falhou (--faixa <id> / --seq <T-xxx>)"
  exit 1
}

executar_tudo() {
  evento --tipo inicio --escopo tudo
  iniciar_resumos
  info "logs em: $LOG_DIR"
  info "resumo geral de andamento: a cada 1 min aqui no terminal (e via: onp-spec resumo)"
  # onda 1: faixa-1 ∥ faixa-2 ∥ faixa-3
  info "onda 1: faixa-1 ∥ faixa-2 ∥ faixa-3 — janelas limpas em paralelo"
  executar_faixa_1 & PID_FAIXA_1=$!
  executar_faixa_2 & PID_FAIXA_2=$!
  executar_faixa_3 & PID_FAIXA_3=$!
  wait "$PID_FAIXA_1" || true
  wait "$PID_FAIXA_2" || true
  wait "$PID_FAIXA_3" || true
  # onda 2: faixa-4 ∥ faixa-5
  info "onda 2: faixa-4 ∥ faixa-5 — janelas limpas em paralelo"
  executar_faixa_4 & PID_FAIXA_4=$!
  executar_faixa_5 & PID_FAIXA_5=$!
  wait "$PID_FAIXA_4" || true
  wait "$PID_FAIXA_5" || true
  executar_seq_T_007 || true
  encerrar tudo
}

listar() {
  echo "execução: $RUN_ID (feature $FEATURE, branch $BASE_BRANCH)"
  echo "  faixa-1  onda 1  T-002"
  echo "  faixa-2  onda 1  T-003"
  echo "  faixa-3  onda 1  T-004"
  echo "  faixa-4  onda 2  T-005"
  echo "  faixa-5  onda 2  T-006"
  echo "  seq       T-007 (sequencial)"
  echo
  echo "reexecutar uma faixa:    --faixa <id>"
  echo "reexecutar sequencial:   --seq <T-xxx>"
  echo "só o gate:               --gate"
}

MODO="tudo"
ALVO=""
while [ $# -gt 0 ]; do
  case "$1" in
    --listar) MODO="listar" ;;
    --gate) MODO="gate" ;;
    --sem-gate) COM_GATE=0 ;;
    --faixa) MODO="faixa"; ALVO="${2:-}"; shift ;;
    --seq) MODO="seq"; ALVO="${2:-}"; shift ;;
    -h|--help) sed -n "2,14p" "$0"; exit 0 ;;
    *) vermelho "argumento desconhecido: $1"; sed -n "2,14p" "$0"; exit 2 ;;
  esac
  shift
done

if [ "$MODO" = "listar" ]; then listar; exit 0; fi

preparar_ambiente

case "$MODO" in
  tudo) executar_tudo ;;
  gate) COM_GATE=1; iniciar_resumos; encerrar gate ;;
  faixa)
    case "$ALVO" in
      faixa-1) evento --tipo inicio --escopo "faixa:faixa-1"; iniciar_resumos; executar_faixa_1 || true; encerrar "faixa:faixa-1" ;;
      faixa-2) evento --tipo inicio --escopo "faixa:faixa-2"; iniciar_resumos; executar_faixa_2 || true; encerrar "faixa:faixa-2" ;;
      faixa-3) evento --tipo inicio --escopo "faixa:faixa-3"; iniciar_resumos; executar_faixa_3 || true; encerrar "faixa:faixa-3" ;;
      faixa-4) evento --tipo inicio --escopo "faixa:faixa-4"; iniciar_resumos; executar_faixa_4 || true; encerrar "faixa:faixa-4" ;;
      faixa-5) evento --tipo inicio --escopo "faixa:faixa-5"; iniciar_resumos; executar_faixa_5 || true; encerrar "faixa:faixa-5" ;;
      *) falhar "faixa desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
  seq)
    case "$ALVO" in
      T-007) evento --tipo inicio --escopo "seq:T-007"; iniciar_resumos; executar_seq_T_007 || true; encerrar "seq:T-007" ;;
      *) falhar "tarefa sequencial desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
esac
