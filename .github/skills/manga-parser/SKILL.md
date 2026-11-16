---
name: manga-parser
description: Projete, implemente ou teste a extração de metadados de mangás a partir de HTML autorizado usando Jsoup, resultados tipados e tratamento seguro de dados ausentes e inconsistentes.
---

# Parsing de páginas de mangá

## Objetivo

Definir extração previsível e testável de títulos, capas, capítulos, datas, links e metadados de páginas HTML. Tratar cada site como uma fonte sujeita a variações, sem permitir que falha de scraping comprometa o histórico local.

## Quando usar

- Ao implementar ou ajustar um parser HTML para uma fonte autorizada pelo usuário.
- Ao normalizar títulos, URLs, números de capítulo ou datas obtidos de HTML.
- Ao investigar mudanças de markup que causaram regressão.
- Ao criar fixtures e testes offline de parsing.

## Conhecimentos técnicos

- **Jsoup:** parse de HTML, seleção CSS, navegação pelo DOM e leitura de atributos/texto sem carregar uma UI.
- **Metadados de mangá:** título, imagem de capa, link canônico, capítulos, data, número/ordem, estado e descrição quando presentes.
- **Modelagem:** resultado tipado, campos opcionais explícitos e lista de avisos/erros compreensíveis, conforme os padrões do projeto.
- **URLs:** resolução de links relativos contra a URL-base, preservação de esquema seguro esperado e validação de host/origem quando relevante.
- **Datas e números:** parse localizado e normalização sem supor que toda fonte usa o mesmo formato, fuso, idioma ou numeração.
- **Testabilidade:** HTML representativo versionado como fixture; testes não dependem de sites ao vivo.

## Procedimento

1. Confirme que a fonte é pública e autorizada para o uso solicitado; verifique termos e limites aplicáveis.
2. Colete exemplos reais e permitidos de HTML para casos de sucesso, campos ausentes, listas vazias e markup alternativo. Remova dados pessoais desnecessários.
3. Identifique elementos estáveis por estrutura semântica, atributos ou seletores CSS específicos. Evite seletores frágeis dependentes de posição ou estilo visual.
4. Faça o parsing em uma camada de dados dedicada. Separe aquisição HTTP do parser puro que recebe HTML e URL-base.
5. Extraia e normalize cada campo individualmente. Trate capa lazy-loaded (`src`, `data-src` ou variantes observadas), links relativos e campos opcionais sem fabricar valores.
6. Normalize números e datas com regras explícitas por fonte. Se ambíguos, preserve o texto original ou sinalize a incerteza em vez de inferir silenciosamente.
7. Detecte HTML inesperado ou página de erro e retorne falha explícita, não uma ficha de mangá parcialmente inventada.
8. Crie testes com fixtures para cada variação identificada, valide URLs normalizadas e execute os testes offline.
9. Integre os resultados ao repositório preservando dados locais; uma falha de parsing não deve apagar capítulos ou histórico existentes.

## Cuidados importantes

- Não contorne login, paywall, CAPTCHA, controles de acesso ou medidas antiabuso. Não faça scraping de conteúdo não autorizado.
- Respeite termos do site, `robots.txt` quando aplicável e limites de requisição; use rede somente se a funcionalidade foi solicitada.
- Não confie cegamente em HTML externo; limite tamanhos e valide valores antes de persistir.
- Evite regex para percorrer a estrutura de HTML. Use CSS/DOM para elementos e regex apenas para campos textuais bem delimitados.
- Não faça `catch` amplo que transforme erro em sucesso, nem use título vazio/URL inválida como fallback silencioso.
- Não acople parser a composable, Activity ou banco. Parsers puros são mais fáceis de testar e manter.

## Exemplo prático

Um parser pode receber `html` e `pageUrl`, selecionar o título e a lista de capítulos com seletores definidos para aquela fonte, resolver cada `href` relativo usando `pageUrl` e retornar campos ausentes como opcionais. Um teste fornece um fixture salvo e confere título, quantidade de capítulos e URLs absolutas, sem abrir conexão de rede.

## Critérios de conclusão

- A fonte e o uso são autorizados, e extração está isolada de UI e persistência.
- Todos os campos implementados têm regra de normalização e comportamento definido para ausência/inconsistência.
- URLs e datas foram verificadas contra fixtures representativas.
- Testes offline cobrem sucesso e casos de markup ausente ou alterado.
- Erros não apagam dados existentes e foram reportados; nenhum resultado foi declarado funcional sem verificação.
