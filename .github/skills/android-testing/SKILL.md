---
name: android-testing
description: Planeje e execute testes do Manga Tracker, incluindo unitários, instrumentados, parsing HTML com fixtures, Room e UI, mantendo validação reproduzível e independente de serviços externos.
---

# Testes Android

## Objetivo

Verificar comportamento observável e preservar funcionalidades do Manga Tracker com testes pequenos, determinísticos e adequados à camada. Relatar apenas comandos realmente executados e resultados observados.

## Quando usar

- Sempre que código ou comportamento do app for alterado.
- Ao adicionar parser, regra de domínio, ViewModel, DAO, migration, worker ou tela.
- Ao investigar regressão, instabilidade ou erro de compilação.
- Ao definir fixtures e testes para HTML e dados persistidos.

## Conhecimentos técnicos

- **Unitários:** validam regras puras, mapeamentos, reducers e ViewModels com dependências controladas.
- **Instrumentados:** executam no Android e são apropriados para componentes de plataforma, integração Room e testes UI que o ambiente exija.
- **Compose UI tests:** verificam interação, semântica e estados visíveis sem acoplamento excessivo a detalhes de implementação.
- **Room tests:** validam consultas, constraints, relações e migrations com banco temporário/in-memory ou infraestrutura existente.
- **Parsing HTML:** fixtures offline permitem testar títulos, links, imagens, datas, capítulos, campos ausentes e markup alternativo sem site ao vivo.
- **Workers:** testar resultados, restrições e integração com WorkManager de acordo com dependências já configuradas.

## Procedimento

1. Inspecione testes adjacentes, frameworks, versão Kotlin/Gradle, módulos e convenções existentes. Não adicione infraestrutura nova sem necessidade.
2. Identifique a regra ou regressão observável e escreva casos que falhariam antes da mudança, incluindo a borda relevante.
3. Isole dependências externas: use fakes ou fixtures para rede, horário, banco ou parser; não faça chamadas pagas ou instáveis.
4. Escolha o nível de teste mais baixo que valide o comportamento. Use instrumentação quando a plataforma fizer parte do requisito.
5. Para parsing, salve HTML mínimo representativo em fixture e valide valores normalizados e tratamento de campo ausente.
6. Para Room, teste migration da versão anterior suportada e confira preservação de registros, não apenas abertura do schema novo.
7. Execute a tarefa Gradle pertinente via Wrapper quando disponível; use o nome do módulo e tarefa encontrados no projeto.
8. Analise falhas. Diferencie regressão, teste instável, erro de ambiente e baseline anterior; corrija falhas causadas pela mudança e repita o teste.
9. Registre no relato o comando exato, resultado e checks que não puderam rodar. Nunca declare compilação com base apenas em teste unitário.

## Cuidados importantes

- Não remova, enfraqueça, pule ou condicione um teste somente para obter um resultado verde.
- Não use rede ao vivo para testes de unidade de parser; preserve fixtures e anonimização adequada.
- Não confunda teste compilado com executado, nem assemble debug com validação de release assinado.
- Não reporte falha ambiental como sucesso. Se SDK, JDK, emulador ou dependência estiver ausente, informe o bloqueio.
- Evite asserções que só repetem a implementação ou verificam constantes sem validar comportamento.
- Testes de migration devem partir de um esquema antigo e dados representativos, não apenas criar banco já na versão final.

## Exemplo prático

Para normalizar um capítulo, teste entradas como `Capítulo 12`, `12.5`, texto com data, string vazia e formato inválido conforme o requisito. Para o parser HTML, use uma fixture local e verifique link relativo resolvido contra a URL-base. Execute apenas a tarefa de teste do módulo identificado e relate seu código de saída.

## Critérios de conclusão

- Testes cobrem comportamento novo e ao menos os casos de borda diretamente relevantes.
- Fixtures e dependências são determinísticas e não exigem serviços externos.
- Testes pertinentes foram efetivamente executados após a mudança; falhas causadas foram corrigidas.
- Comandos e resultados são reportados precisamente, separando teste, build e inspeção manual.
- Limitações ambientais e verificações pendentes estão explícitas.
