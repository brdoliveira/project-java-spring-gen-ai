# Design: Endurecimento para produção

## Direção arquitetural

O projeto continuará com duas aplicações Spring Boot no mesmo repositório. Um
Maven Wrapper na raiz executará a aplicação principal e o `posture-service`
por comandos explícitos, evitando uma reorganização invasiva de diretórios.

## Decisões propostas

1. **Profiles:** `application.yml` contém somente defaults neutros;
   `application-local.yml` habilita facilidades didáticas; `application-prod.yml`
   exige configuração externa e defaults seguros.
2. **Banco:** Flyway passa a ser a única fonte de evolução de schema. Seeds
   locais usam UPSERT e nunca `TRUNCATE`.
3. **Segurança:** produção usa OAuth2 Resource Server/JWT. O sujeito do token é
   persistido como dono da revisão. Health permanece público. `local` pode
   liberar endpoints apenas quando explicitamente ativado.
4. **Validação:** Bean Validation cobre DTOs; uploads são verificados por
   assinatura/conteúdo e gravados somente após validação.
5. **Concorrência:** revisão usa versão/compare-and-set para aceitar uma única
   transição de aprovação. Imagens usam nomes UUID e diretório configurável.
6. **Integrações:** providers opcionais e reranker são condicionais. Chamadas
   HTTP têm timeout e retry limitados; mensagens externas são sanitizadas.
7. **Testes:** testes unitários e MVC não chamam provedores reais. PostgreSQL e
   migrações usam Testcontainers. Um adaptador Node converte resultados
   Surefire em relatório granular consumido pelo gate da especificação.
8. **RAG:** dataset dourado offline mede presença de fontes esperadas e precisão
   de retrieval. Avaliação com LLM fica em profile Maven separado e opt-in.
9. **CI:** GitHub Actions prepara JDK 25, usa Maven Wrapper, cacheia dependências
   e valida as duas aplicações sem secrets.

## Fluxo de segurança

1. JWT autenticado cria a revisão; `sub` vira `owner_subject`.
2. Consulta, pergunta e aprovação comparam o sujeito atual ao dono persistido.
3. A transição de aprovação atualiza estado e versão de forma atômica.
4. Apenas quem venceu a transição agenda a continuação do agente.
5. Logs registram IDs, status e latência, nunca prompt, pergunta ou nota.

## Estratégia de compatibilidade

- O Postman e o README terão exemplos para `local` e para Bearer JWT.
- Variáveis existentes continuam aceitas, mas sem valores secretos no Git.
- O profile local conserva dados de demonstração por UPSERT.
- Nenhuma versão maior de framework será atualizada nesta feature.

## Riscos e mitigação

- **JDK 25 indisponível localmente:** Maven Wrapper resolve Maven, não o JDK;
  README e CI fixam a distribuição/versão exigida.
- **Testcontainers sem Docker:** testes de integração são separados, mas o gate
  completo e a CI exigem Docker disponível.
- **Mudança de autenticação quebra exemplos:** profile `local` e coleção
  Postman preservam uma rota didática explícita.
- **Providers condicionais alteram injeção:** testes de contexto cobrem cada
  combinação suportada antes da refatoração ser aceita.

## Perguntas que bloqueiam execução

As perguntas Q-001, Q-002 e Q-003 da especificação precisam ser respondidas
antes de mudar o status para `pronta` e iniciar a implementação.
