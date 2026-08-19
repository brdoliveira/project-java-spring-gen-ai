# gen-ai-java-spring

Exemplos de GenAI com Java 25, Spring Boot e Spring AI. O repositório contém a aplicação principal na porta `8081` e o `posture-service` na porta `8082`.

## Requisitos

- JDK 25;
- Docker Engine com Docker Compose v2, para PostgreSQL com pgvector e a execução em containers;
- uma chave do provider que será usado (por exemplo, `OPENAI_API_KEY`) para chamar endpoints de IA. A suíte de testes não precisa de chaves nem faz chamadas pagas.

O Maven Wrapper está versionado: não é necessário instalar Maven globalmente. No Windows, troque `./mvnw` por `./mvnw.cmd` nos comandos abaixo.

## Verificação do clone

Na raiz do repositório, compile e teste os dois serviços com o Maven Wrapper:

```bash
./mvnw -B -ntp test
./mvnw -B -ntp -f posture-service/pom.xml test
node scripts/run-spec-tests.mjs
```

O último comando verifica os contratos versionados da feature. A CI executa a mesma suíte com `OFFLINE_EVALUATION=true`; não adicione chaves de providers à CI.

## Ambiente local

O profile `local` é explícito e permite os endpoints de demonstração sem JWT. Ele usa PostgreSQL em `localhost:5433` por padrão e não recria o índice RAG, a menos que `RAG_FORCE_REBUILD=true` seja definido intencionalmente.

Suba banco, aplicação principal e posture-service em containers:

```bash
docker compose -f docker/dev/compose.yml up --build
```

Ou inicie os serviços no host, depois de subir apenas o banco (`docker compose -f docker/dev/compose.yml up -d postgres`):

```bash
export SPRING_PROFILES_ACTIVE=local
export OPENAI_API_KEY=coloque-sua-chave-aqui
./mvnw spring-boot:run

# Em outro terminal
export SPRING_PROFILES_ACTIVE=local
./mvnw -f posture-service/pom.xml spring-boot:run
```

No PowerShell, use `$env:SPRING_PROFILES_ACTIVE='local'` e `$env:OPENAI_API_KEY='...'`. Confirme o estado dos serviços em `http://localhost:8081/actuator/health` e `http://localhost:8082/actuator/health`.

Para encerrar o ambiente Docker mantendo o volume de dados:

```bash
docker compose -f docker/dev/compose.yml down
```

Use `docker compose -f docker/dev/compose.yml down -v` somente quando quiser remover deliberadamente os dados locais.

## Produção e segurança

Produção deve ativar o profile `prod` e fornecer segredos exclusivamente pelo ambiente ou pelo gerenciador de segredos da plataforma. Os valores mínimos são:

```bash
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://db.example.internal:5432/genai
DB_USERNAME=genai
DB_PASSWORD=<segredo>
OPENAI_API_KEY=<segredo-do-provider-escolhido>
POSTURE_SERVICE_URL=http://posture-service:8082/api/posture/{id}
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://issuer.example.com/
```

Não versione valores reais para `DB_PASSWORD`, chaves de IA ou emissor JWT. No profile `prod`, chamadas de negócio exigem um bearer token JWT e o health check permanece público. Envie o token pelo cabeçalho `Authorization`:

```http
Authorization: Bearer <access-token>
```

A coleção [Postman](gen-ai-with-java-spring.postman_collection.json) usa a variável `accessToken`; importe-a, defina-a com um JWT de teste e execute as requisições contra a URL desejada. Deixe o token vazio apenas para testar o profile `local`.

O profile `prod` não inicializa schemas de forma implícita, mantém Flyway com `clean-disabled`, não inclui prompts/completions nas observações e usa amostragem de tracing inferior a 100%. Faça backup do banco e valide as migrações antes de alterar o ambiente.

## Avaliação RAG offline

O dataset dourado está em `src/test/resources/rag/evaluation/golden-dataset.json`. A avaliação de retrieval é determinística, offline e falha quando a métrica mínima não é atingida:

```bash
./mvnw -Dtest=RagEvaluationServiceTest,RagProductionThresholdTest test
```

Ela não avalia respostas com um LLM pago. Qualquer avaliação com provider externo deve ser acionada separadamente, com credenciais fornecidas no ambiente e nunca na CI padrão.

## Operação

- Métricas e health são expostos pelo Actuator; mantenha apenas os endpoints necessários acessíveis na borda.
- As migrações Flyway são idempotentes; não use operações destrutivas para recuperar um ambiente.
- Os uploads ficam em `APP_UPLOAD_DIR` (por padrão, diretório temporário). Em produção, forneça armazenamento persistente e permissões restritas.
- Para observabilidade local adicional, use `docker/observability/observability-compose.yml`.
