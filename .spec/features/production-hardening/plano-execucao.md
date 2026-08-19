# Plano de execução — production-hardening

> gerado por `onp-spec plano` em 2026-08-18 19:07 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano production-hardening --paralelizar T-002,T-003,T-004,T-005,T-006`

## Resumo — o que vai acontecer

- **6 tarefa(s) pendente(s)**: 5 em 5 faixa(s) paralela(s) + 1 sequencial(is) (1 já concluída(s): T-001)
- **seleção do usuário**: paralelizar só T-002, T-003, T-004, T-005, T-006 — as demais rodam uma após a outra, ao final
- **1 faixa = 1 worktree + 1 branch + 1 janela de contexto limpa** — faixas não compartilham nenhum arquivo entre si
- prefere outra seleção ou uma após a outra? Regenere com `onp-spec plano production-hardening --paralelizar T-xxx,T-yyy` ou `--sequencial`
- tudo acontece na branch de trabalho `spec/production-hardening`; levar para a main é decisão sua

## Faixas e ondas

### Onda 1 — faixa-1 ∥ faixa-2 ∥ faixa-3

#### faixa-1 — branch `spec/production-hardening-faixa-1` — worktree `../onp-worktrees/project-java-spring-gen-ai-production-hardening-faixa-1`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-002 | Tornar configuração e migrações não destrutivas | `gpt-5.6-sol` | high | `src/main/resources/application.yml`, `src/main/resources/application-local.yml`, `src/main/resources/application-prod.yml`, `src/main/resources/db/data.sql`, `src/main/resources/db/schema.sql`, `src/main/resources/db/migration/V1__create_review_state_snapshot.sql`, `posture-service/src/main/resources/application.yml`, `posture-service/src/main/resources/application-local.yml`, `posture-service/src/main/resources/application-prod.yml`, `posture-service/src/main/resources/db/data.sql`, `posture-service/src/main/resources/db/schema.sql`, `posture-service/src/main/resources/db/migration/V1__create_security_posture.sql`, `posture-service/src/main/resources/db/local-data.sql`, `src/test/java/com/genai/java/spring/config/ConfigurationSafetyTest.java`, `src/test/java/com/genai/java/spring/config/DatabaseMigrationIT.java`, `posture-service/src/test/java/com/genai/posture/config/PostureMigrationIT.java` |

#### faixa-2 — branch `spec/production-hardening-faixa-2` — worktree `../onp-worktrees/project-java-spring-gen-ai-production-hardening-faixa-2`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-003 | Proteger APIs e garantir posse/idempotência de revisão | `gpt-5.6-sol` | high | `src/main/java/com/genai/java/spring/security/SecurityConfiguration.java`, `src/main/java/com/genai/java/spring/security/CurrentSubject.java`, `src/main/java/com/genai/java/spring/aiagent/controller/SecurityReviewController.java`, `src/main/java/com/genai/java/spring/aiagent/service/SecurityReviewService.java`, `src/main/java/com/genai/java/spring/aiagent/service/impl/SecurityReviewServiceImpl.java`, `src/main/java/com/genai/java/spring/aiagent/dataaccess/repository/ReviewStateRepository.java`, `src/main/java/com/genai/java/spring/aiagent/dataaccess/repository/impl/ReviewStateRepositoryImpl.java`, `src/main/java/com/genai/java/spring/aiagent/dto/ReviewState.java`, `src/main/java/com/genai/java/spring/aiagent/dto/ApprovalRequest.java`, `src/main/java/com/genai/java/spring/aiagent/dto/FollowUpRequestDto.java`, `src/main/resources/db/migration/V2__add_review_owner_and_version.sql`, `src/test/java/com/genai/java/spring/security/ApiSecurityTest.java`, `src/test/java/com/genai/java/spring/aiagent/SecurityReviewAuthorizationTest.java`, `src/test/java/com/genai/java/spring/aiagent/ApprovalIdempotencyTest.java`, `src/test/java/com/genai/java/spring/aiagent/SensitiveLoggingTest.java` |

#### faixa-3 — branch `spec/production-hardening-faixa-3` — worktree `../onp-worktrees/project-java-spring-gen-ai-production-hardening-faixa-3`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-004 | Endurecer uploads e armazenamento de imagens | `gpt-5.6-terra` | high | `src/main/java/com/genai/java/spring/aiagent/service/impl/FileStorageServiceImpl.java`, `src/main/java/com/genai/java/spring/multimodality/texttoimage/GeneratedImageStorage.java`, `src/main/java/com/genai/java/spring/multimodality/texttoimage/MarketingAssetController.java`, `src/test/java/com/genai/java/spring/aiagent/FileStorageServiceTest.java`, `src/test/java/com/genai/java/spring/multimodality/GeneratedImageStorageTest.java` |

### Onda 2 — faixa-4 ∥ faixa-5

#### faixa-4 — branch `spec/production-hardening-faixa-4` — worktree `../onp-worktrees/project-java-spring-gen-ai-production-hardening-faixa-4`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-005 | Isolar providers e limitar chamadas externas | `gpt-5.6-sol` | high | `src/main/java/com/genai/java/spring/config/AIProviderConfig.java`, `src/main/java/com/genai/java/spring/config/ProviderProperties.java`, `src/main/java/com/genai/java/spring/aiagent/tools/web/WebTools.java`, `src/main/java/com/genai/java/spring/aiagent/tools/posture/PostureTools.java`, `src/main/java/com/genai/java/spring/rag/rerank/processor/RerankPostProcessor.java`, `src/test/java/com/genai/java/spring/config/ProviderIsolationTest.java`, `src/test/java/com/genai/java/spring/aiagent/OutboundToolResilienceTest.java` |

#### faixa-5 — branch `spec/production-hardening-faixa-5` — worktree `../onp-worktrees/project-java-spring-gen-ai-production-hardening-faixa-5`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-006 | Criar avaliação determinística do RAG | `gpt-5.6-terra` | medium | `src/main/java/com/genai/java/spring/rag/evaluation/RagEvaluationService.java`, `src/main/java/com/genai/java/spring/rag/evaluation/RagEvaluationReport.java`, `src/test/resources/rag/evaluation/golden-dataset.json`, `src/test/java/com/genai/java/spring/rag/RagProductionThresholdTest.java`, `src/test/java/com/genai/java/spring/rag/RagEvaluationServiceTest.java` |

## Tarefas sequenciais (após as ondas, na árvore principal)

| tarefa | título | modelo | esforço | por que sequencial |
|---|---|---|---|---|
| T-007 | Documentar execução, segurança e operação | `gpt-5.6-terra` | medium | fora da seleção do usuário |

## Gestão de branches e commits

1. branch de trabalho `spec/production-hardening` criada do ponto atual (se ainda não existir)
2. cada faixa nasce dela como branch própria e roda no seu worktree — **1 tarefa = 1 commit** (`T-xxx feature: título`)
3. terminou a onda → merge `--no-ff` de cada faixa de volta, na ordem; conflito interrompe a faixa e pede resolução humana
4. faixa mesclada → worktree removido, branch apagada, tarefa marcada `[concluida]` no tasks.md
5. gate final na branch de trabalho: `onp-spec verify production-hardening` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Codex headless (codex exec)

```bash
bash .spec/features/production-hardening/executar-tarefas.sh
```

Cada faixa roda `codex exec` com **janela de contexto limpa**, no seu worktree, com
`--model` e `model_reasoning_effort` já definidos por tarefa e sandbox `workspace-write`. Os prompts exatos estão
embutidos no script — quer rodar uma faixa na mão, é só copiá-los de lá.
Logs: `../onp-worktrees/project-java-spring-gen-ai-production-hardening-logs/`.

**Confirmação de custos — antes de executar**: os modelos e esforços por
tarefa estão nas tabelas acima; o agente CONFIRMA com o usuário se estão
dentro da licença/cota dele (modelo forte + esforço alto torra tokens).
Para gastar menos: `onp-spec plano production-hardening --modelo gpt-5.6-luna --esforco baixo`
(tudo) ou por tarefa `onp-spec tarefa production-hardening T-xxx --modelo <m> --esforco <nível>` — e regenere o plano.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo production-hardening --tabela   # a tabela de andamento
onp-spec resumo production-hardening            # o resumo em texto
```
