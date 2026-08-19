# Tasks: Endurecimento para produção

> feature: production-hardening

## T-001 — Preparar build reproduzível e gate de testes [concluida]
- Refs: US-005, AC-015, AC-016, AC-017
- Arquivos: pom.xml, posture-service/pom.xml, .mvn/wrapper/maven-wrapper.properties, mvnw, mvnw.cmd, scripts/run-spec-tests.mjs, onpspec.config.json, .github/workflows/ci.yml, src/test/java/com/genai/java/spring/build/BuildContractTest.java
- Modelo: gpt-5.6-terra
- Esforço: alto
- Notas: Adicionar Maven Wrapper, escopos corretos, dependências comuns de segurança/migração/testes e relatório Surefire granular para o gate.

## T-002 — Tornar configuração e migrações não destrutivas [concluida]
- Refs: US-001, US-002, AC-001, AC-002, AC-003, AC-004, AC-006
- Arquivos: src/main/resources/application.yml, src/main/resources/application-local.yml, src/main/resources/application-prod.yml, src/main/resources/db/data.sql, src/main/resources/db/schema.sql, src/main/resources/db/migration/V1__create_review_state_snapshot.sql, posture-service/src/main/resources/application.yml, posture-service/src/main/resources/application-local.yml, posture-service/src/main/resources/application-prod.yml, posture-service/src/main/resources/db/data.sql, posture-service/src/main/resources/db/schema.sql, posture-service/src/main/resources/db/migration/V1__create_security_posture.sql, posture-service/src/main/resources/db/local-data.sql, src/test/java/com/genai/java/spring/config/ConfigurationSafetyTest.java, src/test/java/com/genai/java/spring/config/DatabaseMigrationIT.java, posture-service/src/test/java/com/genai/posture/config/PostureMigrationIT.java
- Modelo: gpt-5.6-sol
- Esforço: alto
- Notas: Depende de T-001 para Flyway e Testcontainers. Remover SQL destrutivo, separar profiles e provar reinicialização idempotente.

## T-003 — Proteger APIs e garantir posse/idempotência de revisão [concluida]
- Refs: US-003, US-004, AC-007, AC-008, AC-009, AC-010, AC-011, AC-014
- Arquivos: src/main/java/com/genai/java/spring/security/SecurityConfiguration.java, src/main/java/com/genai/java/spring/security/CurrentSubject.java, src/main/java/com/genai/java/spring/aiagent/controller/SecurityReviewController.java, src/main/java/com/genai/java/spring/aiagent/service/SecurityReviewService.java, src/main/java/com/genai/java/spring/aiagent/service/impl/SecurityReviewServiceImpl.java, src/main/java/com/genai/java/spring/aiagent/dataaccess/helper/ReviewStateRepositoryHelper.java, src/main/java/com/genai/java/spring/aiagent/dataaccess/repository/ReviewStateRepository.java, src/main/java/com/genai/java/spring/aiagent/dataaccess/repository/impl/ReviewStateRepositoryImpl.java, src/main/java/com/genai/java/spring/aiagent/dto/ReviewState.java, src/main/java/com/genai/java/spring/aiagent/dto/ApprovalRequest.java, src/main/java/com/genai/java/spring/aiagent/dto/FollowUpRequestDto.java, src/main/resources/db/migration/V2__add_review_owner_and_version.sql, src/test/java/com/genai/java/spring/security/ApiSecurityTest.java, src/test/java/com/genai/java/spring/aiagent/SecurityReviewAuthorizationTest.java, src/test/java/com/genai/java/spring/aiagent/ApprovalIdempotencyTest.java, src/test/java/com/genai/java/spring/aiagent/SensitiveLoggingTest.java
- Modelo: gpt-5.6-sol
- Esforço: alto
- Notas: Depende de T-001 e da decisão Q-001. Usar JWT em produção, subject como dono e compare-and-set para a aprovação.

## T-004 — Endurecer uploads e armazenamento de imagens [concluida]
- Refs: US-003, US-004, AC-010, AC-013
- Arquivos: src/main/java/com/genai/java/spring/aiagent/service/impl/FileStorageServiceImpl.java, src/main/java/com/genai/java/spring/multimodality/texttoimage/GeneratedImageStorage.java, src/main/java/com/genai/java/spring/multimodality/texttoimage/MarketingAssetController.java, src/test/java/com/genai/java/spring/aiagent/FileStorageServiceTest.java, src/test/java/com/genai/java/spring/multimodality/GeneratedImageStorageTest.java
- Modelo: gpt-5.6-terra
- Esforço: alto
- Notas: Depende de T-001. Validar assinatura real, XML seguro, normalização e nomes UUID sem colisão.

## T-005 — Isolar providers e limitar chamadas externas [concluida]
- Refs: US-002, US-004, AC-005, AC-012
- Arquivos: src/main/java/com/genai/java/spring/config/AIProviderConfig.java, src/main/java/com/genai/java/spring/config/ProviderProperties.java, src/main/java/com/genai/java/spring/aiagent/tools/web/WebTools.java, src/main/java/com/genai/java/spring/aiagent/tools/posture/PostureTools.java, src/main/java/com/genai/java/spring/rag/rerank/processor/RerankPostProcessor.java, src/test/java/com/genai/java/spring/config/ProviderIsolationTest.java, src/test/java/com/genai/java/spring/aiagent/OutboundToolResilienceTest.java
- Modelo: gpt-5.6-sol
- Esforço: alto
- Notas: Depende de T-001. Ativar integrações por propriedade, usar timeout/retry finitos e não devolver mensagens internas de exceção.

## T-006 — Criar avaliação determinística do RAG [concluida]
- Refs: US-006, AC-018, AC-019
- Arquivos: src/main/java/com/genai/java/spring/rag/evaluation/RagEvaluationService.java, src/main/java/com/genai/java/spring/rag/evaluation/RagEvaluationReport.java, src/test/resources/rag/evaluation/golden-dataset.json, src/test/java/com/genai/java/spring/rag/RagProductionThresholdTest.java, src/test/java/com/genai/java/spring/rag/RagEvaluationServiceTest.java
- Modelo: gpt-5.6-terra
- Esforço: médio
- Notas: Depende de T-001 e da decisão Q-003. Gate offline; avaliação com LLM real fica opt-in e fora do CI padrão.

## T-007 — Documentar execução, segurança e operação [concluida]
- Refs: US-007, AC-020
- Arquivos: README.md, gen-ai-with-java-spring.postman_collection.json, docker/dev/compose.yml, docker/dev/Dockerfile, docker/dev/posture-service.Dockerfile, src/test/java/com/genai/java/spring/docs/DocumentationContractTest.java
- Modelo: gpt-5.6-terra
- Esforço: médio
- Notas: Executar após T-001 a T-006 para documentar os comandos e contratos finais sem divergência.
