---
name: kotlin-compose
description: Implemente ou mantenha Kotlin, Coroutines, Flow, ViewModels, navegação e telas Jetpack Compose com Material 3 no Manga Tracker, seguindo os padrões existentes e validando estados e testes.
---

# Kotlin, Coroutines e Compose

## Objetivo

Desenvolver a camada Kotlin de apresentação do Manga Tracker com estado previsível, coroutines estruturadas e Jetpack Compose/Material 3. Separar rendering, ações e acesso a dados, mantendo a interface reativa, acessível e testável.

## Quando usar

- Ao criar ou ajustar uma tela, componente, tema ou navegação Compose.
- Ao implementar estado ou eventos em ViewModels.
- Ao integrar `Flow`, `StateFlow`, coroutines ou cancelamento de operações.
- Ao corrigir recomposições, estados inconsistentes ou testes de UI.

## Conhecimentos técnicos

- **Kotlin:** tipos explícitos, null safety, imutabilidade por padrão e funções pequenas.
- **Coroutines:** escopo estruturado, cancelamento propagado e dispatcher apropriado a operações de I/O; não bloquear a thread principal.
- **Flow/StateFlow:** estado contínuo e observável; use os operadores e convenções já adotados no projeto.
- **ViewModel:** mantém estado de apresentação e coordena ações; não recebe referências de composables nem acessa diretamente DAO.
- **Compose:** UI declarativa, funções composable sem efeitos colaterais indevidos, estado elevado para o menor proprietário apropriado e efeitos controlados pelas APIs de efeito.
- **Material 3:** reutilize tema, tokens, componentes e padrões visuais já existentes.
- **Navigation:** siga a biblioteca, o padrão de rotas e o tratamento de argumentos já configurados; não introduza outro framework sem necessidade.
- **Lifecycle:** colete estado de maneira consciente do ciclo de vida, seguindo as dependências disponíveis no projeto.

## Procedimento

1. Examine telas, ViewModels, modelos de UI, rotas, tema, versões de dependências e testes existentes.
2. Defina o contrato de apresentação: estado imutável necessário à tela e ações explícitas que ela pode emitir.
3. Mantenha regras de domínio e acesso a Room/rede em seus respectivos limites; exponha à UI apenas o estado necessário.
4. Implemente loading, conteúdo, vazio e erro quando forem estados possíveis. Preserve o estado anterior e comunique falhas quando a operação não for concluída.
5. Garanta que efeitos (por exemplo, navegação, snackbar ou permissão) sejam associados à ação e ao ciclo de vida adequados, evitando disparos repetidos por recomposição.
6. Verifique acessibilidade, nomes semânticos, foco, tamanhos de toque e comportamento em telas estreitas.
7. Teste lógica do ViewModel e comportamento de UI usando os recursos presentes no projeto; rode as tarefas Gradle pertinentes.

## Cuidados importantes

- Não faça chamadas de rede, parsing HTML, consultas Room ou trabalho pesado dentro de composables.
- Não use `GlobalScope`, `runBlocking` na UI ou escopos sem proprietário/cancelamento claro.
- Não trate erros como estado vazio, nem ignore exceções silenciosamente.
- Não mantenha cópias mutáveis concorrentes do mesmo progresso em tela e ViewModel.
- Evite estado lembrado duplicado e efeitos keyed incorretamente que se repetem a cada recomposição.
- Preserve navegação e acessibilidade existentes. Evite mudanças visuais fora do escopo solicitado.

## Exemplo prático

Para uma lista de mangás, modele um `UiState` com estados explícitos de carregamento, itens, lista vazia e erro. O composable recebe esse estado e callbacks, exibe o conteúdo apropriado e não consulta o repositório por conta própria. O ViewModel coleta o `Flow` do repositório e fornece um estado lifecycle-aware à tela.

## Critérios de conclusão

- A UI renderiza estado observável e comunica ações sem lógica de persistência ou rede no composable.
- Coroutines são canceláveis, não bloqueiam a main thread e seguem os escopos do projeto.
- Estados de erro, vazio e carregamento relevantes têm comportamento visível e coerente.
- Componentes seguem Material 3, tema e acessibilidade do projeto.
- Testes pertinentes foram executados; build e limitações foram reportados sem alegações não verificadas.
