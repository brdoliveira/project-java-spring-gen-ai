# Constituição — v1.1.0

## P-001 [DEVE] Todo requisito tem prova executável

Nenhuma feature é declarada pronta sem o audit em modo CI sair limpo.

- verificação(gate): intrínseca ao audit

## P-002 [DEVE] Configuração versionada não contém segredo literal

Credenciais de runtime são fornecidas por variável de ambiente ou secret
externo; arquivos de configuração aceitam apenas placeholders.

- verificação(proibido): `(api-key|password):\s*(?!\$\{)[^\s#]+` em `**/src/main/resources/application*.yml`

## P-003 [DEVE] Startup não executa SQL destrutivo

Scripts carregados automaticamente nunca truncam ou removem tabelas.

- verificação(proibido): `\b(TRUNCATE|DROP\s+TABLE)\b` em `**/src/main/resources/**/*.sql`

## P-004 [DEVE] Endpoints de produção exigem identidade

Operações de negócio e dados de revisão são acessíveis somente por uma
identidade autenticada e autorizada.

- verificação(teste): @principle:P-004

## P-005 [DEVE] Conteúdo sensível não é registrado em log

Perguntas, prompts, respostas de modelos e notas de aprovação não aparecem
integralmente nos logs de runtime.

- verificação(teste): @principle:P-005

## P-006 [DEVE] CI padrão não chama APIs pagas

O gate normal usa doubles determinísticos; avaliações externas exigem ativação
explícita e credenciais fora do repositório.

- verificação(teste): @principle:P-006
