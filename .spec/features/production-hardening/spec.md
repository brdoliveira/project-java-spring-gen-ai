# Spec: Endurecimento para produção

> feature: production-hardening
> status: em-implementacao

## Contexto

O repositório demonstra recursos de Spring AI, RAG, multimodalidade e agentes,
mas os defaults atuais podem apagar dados no startup, dependem de caminhos de
uma máquina específica, expõem operações caras sem autenticação e não possuem
testes automatizados ou CI. Esta entrega torna o projeto reproduzível, seguro
por padrão e verificável sem remover os exemplos didáticos.

## Histórias

### US-001 — Dados sobrevivem a reinicializações

Como pessoa desenvolvedora, quero inicialização e migração de banco não
destrutivas, para que reiniciar os serviços não apague memória, postura ou
vetores existentes.

#### AC-001 — Startup não executa SQL destrutivo

- **Dado** o conjunto de scripts carregados automaticamente pelos dois serviços
- **Quando** a aplicação é inicializada ou reinicializada
- **Então** nenhum `TRUNCATE` ou `DROP TABLE` é executado automaticamente

#### AC-002 — Reindexação RAG é opt-in

- **Dado** o profile padrão ou de produção
- **Quando** o serviço de ingestão RAG inicia
- **Então** o vector store existente não é truncado nem reconstruído sem uma opção explícita

#### AC-003 — Migrações preservam registros existentes

- **Dado** um PostgreSQL com as migrações aplicadas e registros já gravados
- **Quando** as migrações são executadas novamente
- **Então** o schema permanece atualizado e os registros anteriores continuam presentes

### US-002 — Configuração portátil e segura

Como pessoa desenvolvedora, quero profiles claros e configuração externa, para
que o projeto rode em qualquer sistema operacional e chegue à produção sem
segredos ou defaults perigosos.

#### AC-004 — Defaults não dependem de uma máquina específica

- **Dado** um clone novo do repositório
- **Quando** os arquivos de configuração padrão são avaliados
- **Então** não existem caminhos absolutos de usuário, project IDs reais ou senhas literais de banco

#### AC-005 — Provedores opcionais não impedem o startup local

- **Dado** o profile local sem credenciais de Hugging Face, Vertex AI ou Cohere
- **Quando** o contexto Spring é criado
- **Então** os recursos não habilitados ficam ausentes e a aplicação pode iniciar com o provider escolhido

#### AC-006 — Produção usa observabilidade segura

- **Dado** o profile de produção
- **Quando** logs e tracing são configurados
- **Então** conteúdo de prompts permanece desabilitado e a amostragem de traces é menor que 100%

### US-003 — APIs protegidas e entradas validadas

Como responsável pelo serviço, quero autenticação, autorização e validação de
entrada, para impedir uso indevido dos modelos e acesso cruzado a relatórios.

#### AC-007 — API de produção exige JWT

- **Dado** o profile de produção sem token de acesso
- **Quando** alguém chama um endpoint de negócio
- **Então** a chamada é recusada com 401 enquanto o health check continua público

#### AC-008 — Relatório pertence ao usuário autenticado

- **Dado** um relatório criado por um sujeito JWT
- **Quando** outro sujeito tenta consultar, perguntar ou aprovar esse relatório
- **Então** o acesso é recusado com 403 e o agente não é retomado

#### AC-009 — Modo local permanece utilizável

- **Dado** o profile local explicitamente ativo
- **Quando** uma pessoa usa os endpoints de demonstração sem JWT
- **Então** a chamada é permitida para fins de estudo

#### AC-010 — Payload e upload inválidos são rejeitados

- **Dado** uma requisição vazia, acima do limite ou com extensão incompatível com o conteúdo
- **Quando** ela chega a um endpoint validado
- **Então** a API responde 400 ou 413 e não persiste o arquivo

#### AC-011 — Conteúdo sensível não aparece em logs

- **Dado** uma pergunta ou nota de aprovação contendo um marcador sensível
- **Quando** a requisição é processada
- **Então** o texto integral não aparece nos logs da aplicação

### US-004 — Chamadas externas e jobs são resilientes

Como pessoa operadora, quero limites e idempotência nas integrações, para que
falhas externas ou requisições duplicadas não prendam threads nem dupliquem
execuções caras.

#### AC-012 — Chamadas externas possuem limite de tempo

- **Dado** um serviço externo lento ou indisponível
- **Quando** uma ferramenta HTTP é executada
- **Então** ela termina dentro do timeout configurado, aplica tentativas limitadas e devolve erro sanitizado

#### AC-013 — Imagens concorrentes não se sobrescrevem

- **Dado** duas gerações de imagem simultâneas
- **Quando** os resultados são armazenados
- **Então** cada chamada recebe um identificador e arquivo distintos

#### AC-014 — Aprovação repetida não duplica o agente

- **Dado** uma revisão pendente de aprovação
- **Quando** duas aprovações concorrentes chegam para o mesmo checkpoint
- **Então** apenas uma transição de estado é aceita e apenas uma retomada do agente é agendada

### US-005 — Build e qualidade são reproduzíveis

Como pessoa colaboradora, quero um comando único e CI, para detectar regressões
nos dois serviços antes de integrar mudanças.

#### AC-015 — Maven Wrapper valida os dois serviços

- **Dado** uma máquina com o JDK definido pelo projeto e sem Maven global
- **Quando** o comando documentado de verificação é executado
- **Então** aplicação principal e posture-service são compilados e testados pelo Maven Wrapper

#### AC-016 — Dependências de teste não vão para produção

- **Dado** os POMs dos serviços
- **Quando** o classpath de produção é resolvido
- **Então** bibliotecas de teste possuem escopo `test` e não compõem o artefato de runtime

#### AC-017 — CI não consome APIs pagas

- **Dado** um push ou pull request sem chaves de provedores de IA
- **Quando** o workflow de CI executa
- **Então** testes determinísticos passam sem chamadas a APIs pagas e os dois módulos são verificados

### US-006 — Qualidade do RAG é mensurável

Como pessoa responsável por IA, quero uma suíte de avaliação repetível, para
detectar perda de relevância ou fundamentação ao alterar chunking e retrieval.

#### AC-018 — Produção filtra contexto irrelevante

- **Dado** uma busca vetorial no profile de produção
- **Quando** documentos são recuperados
- **Então** o threshold configurado é maior que zero e resultados abaixo dele não entram no contexto

#### AC-019 — Dataset dourado produz relatório de avaliação

- **Dado** o dataset versionado de perguntas, respostas e fontes esperadas
- **Quando** a avaliação offline é executada
- **Então** ela gera métricas determinísticas de recuperação e falha quando o mínimo definido não é alcançado

### US-007 — Operação está documentada

Como pessoa que clonou o projeto, quero instruções completas, para executar o
ambiente local e entender as diferenças de segurança para produção.

#### AC-020 — README descreve o caminho completo

- **Dado** um clone novo
- **Quando** a pessoa segue o README
- **Então** encontra requisitos, comando de build, profile local, variáveis de produção, autenticação, testes e avaliação RAG

## Fora de escopo

- Contratar ou configurar um provedor externo de identidade.
- Fazer deploy em uma nuvem específica.
- Garantir certificação formal de segurança ou conformidade.
- Executar avaliações de LLM pagas no CI padrão.
- Atualizar versões maiores de Spring Boot ou Spring AI nesta entrega.

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-001 | O projeto continuará em Java 25 para preservar a intenção do código original. | confirmada | Confirmado pelo usuário em 2026-08-18. |
| ASM-002 | PostgreSQL com pgvector continuará sendo a persistência local e de integração. | confirmada | O projeto e os assets Docker já adotam PostgreSQL/pgvector. |
| ASM-003 | O profile local poderá liberar autenticação apenas quando ativado explicitamente. | confirmada | Produção com JWT; `local` permit-all explícito. |
| ASM-004 | A suíte padrão será totalmente offline; avaliação com LLM será opt-in. | confirmada | CI sem APIs pagas; avaliação real somente opt-in. |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-001 | Em produção usamos OAuth2 Resource Server com JWT e deixamos `local` como permit-all explícito? | respondida | Sim. |
| Q-002 | Mantemos Java 25 ou reduzimos o baseline para Java 21? | respondida | Manter Java 25. |
| Q-003 | Avaliações com um LLM real ficam somente em profile/comando opt-in? | respondida | Sim; CI padrão offline. |
