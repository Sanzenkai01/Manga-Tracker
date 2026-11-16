---
name: site-discovery
description: Projete descoberta e configuração de fontes de mangá por domínio, Open Graph, JSON-LD/Schema.org, WordPress e seletores CSS ou regex manuais, com validação e controle do usuário.
---

# Descoberta e configuração de sites

## Objetivo

Orientar a identificação de estrutura e metadados de sites de mangá e a criação de configurações de extração robustas. A descoberta deve ser transparente, validável e limitada a conteúdo público autorizado; não deve presumir que um formato funcione em todos os domínios.

## Quando usar

- Ao reconhecer um site informado pelo usuário ou identificar uma fonte nova.
- Ao avaliar metadados Open Graph, JSON-LD, Schema.org ou estruturas WordPress.
- Ao permitir seletores CSS ou expressões regex configuráveis manualmente.
- Ao diagnosticar uma fonte existente que mudou de estrutura.

## Conhecimentos técnicos

- **Domínio e URL:** canonicalização cuidadosa de host, esquema, portas e subdomínios; comparação por limites de domínio, nunca por simples substring.
- **Open Graph:** metatags comuns como `og:title`, `og:image` e `og:url`; indicam metadados candidatos, não prova da semântica de mangá.
- **JSON-LD / Schema.org:** blocos `application/ld+json`, estruturas `@graph` e tipos potencialmente úteis (`Book`, `CreativeWork`, `ComicSeries` quando presentes); campos podem faltar ou ser aninhados.
- **WordPress:** reconhecimento de sinais públicos de plataforma apenas como hipótese de suporte; temas e plugins produzem DOMs distintos.
- **Seletores CSS:** caminhos explícitos para título, capa, capítulo, data e link, com validação de cardinalidade e pré-visualização.
- **Regex:** adequada para normalizar texto delimitado, por exemplo número ou data, não para substituir parser DOM.
- **Configuração:** domínio e seletores versionáveis, validação antes de persistir e opção de revisar ou corrigir manualmente.

## Procedimento

1. Confirme a URL inicial e normalize-a sem descartar distinções relevantes de host. Não siga automaticamente para domínios diferentes sem autorização.
2. Verifique se a página é acessível publicamente e se a análise é compatível com os termos e limites da fonte. Não contorne autenticação, paywall, CAPTCHA ou bloqueios.
3. Analise primeiro os metadados disponíveis (canonical, Open Graph e JSON-LD); mantenha procedência de cada campo e não trate metadados incompletos como resultado definitivo.
4. Inspecione o DOM visível e identifique seletores por atributos, classes ou estrutura relativamente estável. Verifique se títulos, capas e links pertencem de fato a mangás/capítulos.
5. Detecte padrões conhecidos (inclusive WordPress) somente para sugerir uma estratégia; confirme a estrutura real antes de marcar a fonte como compatível.
6. Se a detecção automática for insuficiente, permita configuração manual de seletores CSS e, para texto já isolado, regex de extração/normalização.
7. Valide seletores em uma amostra local: quantidade de correspondências, campos vazios, links relativos e exemplos de erro.
8. Mostre resultados e ambiguidades para revisão. Persista a configuração apenas depois de validação e siga o modelo de armazenamento existente.
9. Crie fixtures e testes por formato; reavalie compatibilidade quando markup ou configuração mudar.

## Cuidados importantes

- Não classifique domínio por `host.contains("example.com")`; isso aceita domínios enganosos. Use parsing de URI e limites de domínio/subdomínio esperados.
- Open Graph e Schema.org são indícios; valide tipos, conteúdo e origem antes de mapear campos.
- JSON-LD pode ser JSON válido mas ter tipos e formas diferentes; trate arrays, `@graph`, objetos nulos e JSON malformado com erros explícitos.
- Regex em entrada externa deve ser limitada e validada; evite padrões suscetíveis a backtracking catastrófico. Não execute expressões arbitrárias sem limites.
- CSS e regex configuráveis devem produzir erros de validação compreensíveis, nunca exceções silenciosas ou dados inventados.
- Evite requisições repetidas, varredura de domínios e scraping não autorizado. Não faça descoberta em massa sem solicitação explícita.

## Exemplo prático

Uma página pode conter `og:title` confiável e não ter dados de capítulos em JSON-LD. Use o título como candidato documentando sua origem, inspecione o DOM para capítulos e peça seleção manual de CSS se não houver padrão consistente. Teste uma regra regex apenas sobre o texto de capítulo já isolado, não sobre o documento HTML inteiro.

## Critérios de conclusão

- A URL e o domínio são validados sem confusão entre host e substring.
- Cada campo detectado tem uma fonte identificável e é validado quanto a conteúdo e formato.
- Seletores e regex manuais têm pré-validação, erros explícitos e testes com fixtures.
- Detecção é apresentada como hipótese até sua confirmação; casos ambíguos podem ser corrigidos pelo usuário.
- Uso respeita autorização, termos e limites; não altera dados locais sem validação.
