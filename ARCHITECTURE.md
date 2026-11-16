# Planejamento técnico e arquitetura

**Estado:** auditoria estática, testes JVM disponíveis e build completo foram executados após as correções. O build Gradle completo passou; não existem testes instrumentados nem dispositivo conectado para validação de execução. Nenhum site está plenamente compatível. Este documento distingue código presente do plano para atender à [especificação funcional](./PRODUCT_SPEC.md).

## Decisões e princípios

- Plataforma mínima: Android 8.0 (API 26); `compileSdk`/`targetSdk` 35; Kotlin 2.1.21; AGP 8.10.1; Gradle Wrapper 8.11.1; JDK 17. O build completo forçado passou após a auditoria; os APKs debug e release foram gerados, mas o release é unsigned.
- Namespace e `applicationId` configurados como `com.sanzenkai.mangatracker`, provisórios até confirmação antes de distribuição.
- Kotlin, Gradle Kotlin DSL, Compose/Material 3, MVVM, Room e WorkManager conforme os padrões do projeto.
- O acompanhamento, as configurações, preferências e histórico têm fonte de verdade local. OkHttp/Jsoup são usados para obter e interpretar fontes solicitadas, sem tornar a biblioteca local dependente de rede.
- Cada parser é isolado por contrato. O fluxo de descoberta não contém `when(host)` nem uma allowlist fechada de domínios; adicionar parser de site não requer alterar telas, ViewModels ou repositórios.
- Um resultado sem capítulos novos só pode vir de uma leitura bem-sucedida e suficientemente completa. Erros e resultados parciais nunca substituem o último snapshot válido nem apagam capítulos existentes.
- Novos módulos Gradle são limites de dependência intencionais; manter cada parser como módulo separado só quando isso trouxer isolamento ou escala real.

## 1. Estrutura de pacotes

Namespace configurado: `com.sanzenkai.mangatracker`, derivado da identidade do repositório e provisório até confirmação antes do primeiro release.

```text
com.sanzenkai.mangatracker
├── app/                         # Application, Activity e composição de dependências
├── navigation/                  # Grafo, rotas e argumentos de navegação
├── data/                        # Container e composição manual de dependências
├── monitoring/                  # Scheduler, workers e coordenação de checagem
├── notifications/               # Canal, ações, capa e agrupamento
└── ui/
    ├── discovery/               # Tela de descoberta e ViewModel
    ├── library/                 # Tela inicial, estado e ViewModel
    ├── details/                 # Detalhes, capítulos e ações de leitura/monitoramento
    ├── settings/                # Preferências e permissões de notificação
    └── theme/                   # Tema Material 3

com.sanzenkai.mangatracker.core.domain
├── model/                       # Manga, Chapter, CheckOutcome, ReadingEvent
├── repository/                  # Contratos consumidos pelo domínio/UI
└── usecase/                     # Fluxos de negócio com regra/orquestração real

com.sanzenkai.mangatracker.core.data
├── local/                       # Database, entities e DAOs Room
├── network/                     # Fetchers HTTP limitado para HTML e capas
├── preferences/                 # Preferências DataStore
└── repository/                  # Implementações dos contratos de domínio

com.sanzenkai.mangatracker.core.parser.api
├── ParserDocument.kt            # Documento, prévia, metadados e resultado
└── SiteParser.kt                # Contratos de parser e registry

com.sanzenkai.mangatracker.core.parser.engine
├── PageMetadataExtractor.kt     # HTML, Open Graph, JSON-LD/Schema.org
├── ManualCssRegexParser.kt      # Regras CSS/regex fornecidas e testadas pelo usuário
├── ManualSiteConfiguration.kt   # Validação e codec JSON por host
└── SiteDiscoveryEngine.kt       # Estratégias independentes e ordenadas

com.sanzenkai.mangatracker.parsers.site.<adapter>
├── HiveToonsArticleParser.kt    # Metadados observados na página de capítulo
└── HiveToonsSeriesParser.kt     # Lista observada e validação da contagem da série
```

O cadastro por URL está integrado ao `ui.discovery`; não há um ViewModel separado em `addmanga`. `ui.details`, `ui.settings`, `monitoring` e `notifications` estão implementados e cobertos pelos testes JVM disponíveis. A validação visual e de comportamento em dispositivo permanece pendente.

## 2. Módulos Gradle

Os módulos abaixo foram criados para permitir evolução sem acoplar o app a cada HTML. O scaffold contém somente as fundações indicadas:

| Módulo | Responsabilidade | Dependências permitidas |
| --- | --- | --- |
| `:app` | **Implementado parcialmente:** Application, Activity, tema, biblioteca lista/grade, cadastro validado, detalhe/capítulos, ações explícitas de leitura, refresh manual, descoberta, WorkManager e notifications. Testes JVM e build passam; validação em dispositivo está pendente. | APIs de `:core:domain`, `:core:data`, `:core:parser-api`, `:core:parser-engine` e adapters. |
| `:core:domain` | **Implementado parcialmente:** modelos locais de manga/capítulo, estado/histórico de leitura, configuração, monitoramento e tentativas; contratos de repositório, preferência `LibraryLayout` e fronteira de snapshot validado. Casos de uso e fluxos de UI pendentes. | Kotlin; não depende de Android, Room, Compose, Jsoup ou OkHttp. |
| `:core:data` | **Implementado parcialmente:** schema Room v3, repository, DataStore, `SafeHtmlFetcher`, `SafeCoverImageFetcher` e `SafePublicImageHttpClient` com limites, validação de host público/DNS pinning e política de redirecionamento. | `:core:domain`, Room, DataStore, Coroutines e OkHttp. |
| `:core:parser-api` | **Implementado:** documento, metadados, capítulo, completude, issues e resultado tipado; sem dependência de Jsoup ou Android. | Kotlin. |
| `:core:parser-engine` | **Implementado parcialmente:** registry, descoberta genérica, JSON-LD/HTML, configuração manual CSS/regex, parsing de janelas recentes RSS/Atom e limites para listas incompletas. | `:core:parser-api`, Jsoup, JSON e RE2/J. |
| `:parsers:site-adapters` | **Implementado parcialmente:** adapter HiveToons baseado em campos observados; fixture dinâmica de Imperio usada para validar detecção genérica. Nenhum site é declarado completo. | `:core:parser-api` e utilitários públicos do engine. |

Direção: `:app` compõe os módulos; `:core:data` implementa contratos do domínio e fornece a aquisição HTTP; engine e adapters dependem da API de parsing, nunca do app. O engine não importa implementações de sites. O ponto de composição registra um conjunto de `SiteParser`s (por injeção de dependência ou registro explícito), para que incluir um adapter não exija alterar o orquestrador.

As telas e ViewModels começam organizados por feature dentro de `:app`, não como um Gradle module por tela. Se o custo de build, ownership ou isolamento justificar mais divisão, promover uma feature depois, sem mudar os contratos públicos. O container de dependências inicial é manual; não foi adicionado framework de DI.

## 3. Entidades Room

Room é a fonte de verdade do catálogo, capítulos, estados de leitura, configurações de site e resultados de monitoramento. O schema atual é v3. As migrations v1→v2 e v2→v3 preservam os registros existentes; a primeira renomeia as tabelas de configuração e monitoramento e adiciona o índice de ordenação dos capítulos, e a segunda cria a fila de notificações pendentes.

| Entidade | Campos/responsabilidade principais | Relações e índices |
| --- | --- | --- |
| `MangaEntity` | ID estável; título; URL original da capa; URL da série; URL do capítulo inicial; domínio normalizado; datas de criação/atualização. | Índice único `(normalizedDomain, seriesUrl)`. O cadastro exige título, série, capítulo atual e resultado completo; canonicalização de URLs ainda não é feita. |
| `ChapterEntity` | ID; manga; chave estável de origem; rótulo original; tipo/número normalizado quando seguro; URL; data publicada se disponível; primeira detecção; estado/instante de notificação. | FK para manga; unique `(mangaId, sourceChapterKey)`; índice `(mangaId, firstDetectedAtEpochMillis)`. Preservar texto original e especiais/extras. |
| `ReadingStateEntity` | Estado atual (`isRead`) e instante da última mudança, por capítulo. | Uma linha por `chapterId`; índice para consulta de lidos/não lidos. |
| `ReadingHistoryEntity` | Evento append-only (`READ`/`UNREAD`), capítulo, instante e origem da ação (tela/notificação). | FK para capítulo; índice `(chapterId, occurredAtEpochMillis)`. |
| `SiteConfigurationEntity` | Host normalizado, parser/estratégia, versão do formato e configuração CSS/regex, timestamps e validade da última validação. | Tabela `site_configuration`, chave por host; nunca persistir credenciais/sessões. |
| `MonitoringSettingsEntity` | Manga, habilitado/pausado, último resultado resumido e timestamps de tentativa/sucesso. | Tabela `monitoring_settings`, uma linha por manga e FK para manga. |
| `CheckAttemptEntity` | ID da tentativa, manga, tipo manual/periódico, início/fim, outcome tipado e código diagnóstico seguro. | FK e índice `(mangaId, startedAt)`; guardar diagnóstico, não HTML bruto completo. |
| Preferência DataStore | Preferência lista/grade e modo de tema (`SYSTEM`, `LIGHT`, `DARK`). | Repositório conectado à biblioteca e à tela de configurações; o tema segue o sistema por padrão. |

O estado de “monitoramento com problema” é derivado das tentativas, sem coluna nem migração Room: cinco falhas consecutivas com resposta do servidor ou parsing inválido, ou mais de três dias desde o último sucesso quando existe uma falha elegível. Falhas de rede e de configuração não contam como falhas do site. A consulta considera apenas falhas posteriores ao sucesso mais recente. Um marcador DataStore por mangá/episódio evita repetir a notificação; sucesso encerra o aviso ativo e permite um episódio futuro.

Índices iniciais estão declarados nas entidades e devem ser reavaliados com as consultas reais. Números decimais não devem depender de `REAL` com arredondamento; armazenar o rótulo de origem e a representação decimal normalizada sem perda. Para especiais/extras, manter chave/tipo próprios; ordem incerta não deve ser inferida.

Não excluir capítulos antigos por ausência em uma resposta. Migrações Room devem ser explícitas, registradas e testadas; nenhuma entidade de histórico pode ser apagada em cascata por conveniência. Política de remoção de mangá ainda requer decisão de produto e preservação do histórico.

## 4. DAOs

- **Implementados:** DAOs de manga, capítulo, estado/histórico de leitura, configuração por host, configurações individuais de monitoramento e tentativas. Incluem consultas observáveis, contagem de não lidos, inserção idempotente e atualização de metadados de capítulo sem tocar no estado de leitura.
- **Implementado e testado:** a consulta Room projeta sinais de saúde por manga a partir de `check_attempt`, ignorando falhas de rede e delimitando a sequência pelo sucesso mais recente.
- **Implementado:** estado atual e evento de histórico são gravados na mesma transação; inserir capítulos novos grava o estado inicial como não lido. Consultas de capítulos não alteram leitura.
- **Implementado e testado:** migrations Room v1→v2 e v2→v3 registradas; os testes verificam preservação dos dados existentes e criação da fila de notificações pendentes.
- As preferências de lista/grade e tema são fornecidas pelo repositório DataStore, sem `AppSettingsDao`. A ausência de valor de tema resolve para seguir o sistema.

Queries observáveis usam `Flow`; escritas usam `suspend`. Somente um snapshot de capítulos já validado deve chegar à reconciliação local; uma falha de parsing/rede não pode ser representada por uma lista vazia bem-sucedida.

## 5. Repositórios e fluxos de domínio

- **Implementado e testado:** `RoomMangaRepository` persiste/observa mangas, reconcilia capítulos idempotentemente, calcula não lidos e estado derivado do monitoramento, salva configurações por host, gerencia estado individual de monitoramento e registra tentativas.
- **Implementado:** `setChapterReadState` é a única operação de repositório que altera o estado de leitura; apenas transições reais registram histórico, junto ao novo estado na mesma transação. Abrir ou observar capítulos não marca como lido.
- **Implementado:** `ValidatedChapterSnapshot` rejeita resultado de parser incompleto antes de persistência; snapshot vazio completo é permitido e não remove capítulos anteriormente salvos.
- **Implementado:** `registerManga` cadastra o manga e um snapshot completo em transação, evita duplicar série e inicializa monitoramento ativo para iniciar as verificações automáticas.
- **`ChapterRepository`:** expor capítulos observáveis e aceitar apenas reconciliação validada; preserva capítulos e campos quando a checagem falha.
- **`ReadingHistoryRepository`:** executar marcação/desmarcação explícita, registrar evento e calcular estado/count por consulta.
- **Implementado:** `MangaRepository` persiste configuração por hostname; `SiteDiscoveryViewModel` fornece resolução automática, teste manual e salvamento somente após validação.
- **Implementado parcialmente:** `SiteDiscoveryViewModel` conecta URL, discovery e registro após parse completo; `MangaDetailViewModel` coordena atualização manual e persiste apenas snapshots completos.
- **Implementado:** `MangaCheckCoordinator` coordena fetch → discovery/parser → validação/completude → persistência atômica do snapshot/tentativa → resultado tipado; somente sucesso completo pode confirmar “sem capítulos novos”.
- **Implementado:** `MonitoringScheduler` abstrai agendamento; `WorkManagerMonitoringScheduler` cria despacho periódico único, checks únicos por manga, constraints e cancelamento individual. A preferência de monitoramento continua persistida no Room.
- **`LibraryPreferencesRepository` (implementado):** fornece e persiste as preferências de lista/grade e tema claro/escuro via DataStore.

Casos de uso centrais: cadastrar por link, descobrir/configurar fonte, verificar atualizações, marcar/desmarcar capítulo, alternar monitoramento. Não criar uma classe use case para cada getter trivial.

## 6. ViewModels

- **`LibraryViewModel` (implementado):** coleta a projeção da biblioteca, altera a preferência persistida de lista/grade e publica estados de loading/erro/vazio.
- **Cadastro por URL (`SiteDiscoveryViewModel`):** analisa o link e mostra a prévia. Quando o título, a série e o capítulo do link forem identificados, permite cadastrar esse capítulo mesmo que a lista da série esteja incompleta. Somente resultados completos são aceitos nas verificações e reconciliações posteriores.
- **`MangaDetailViewModel` (implementado):** observa manga/capítulos/status, apresenta estado de problema do monitoramento, marca leitura apenas por ação explícita, atualiza manualmente e sincroniza pausa/retomada com o scheduler individual.
- **`SettingsViewModel` (implementado):** observa e altera as preferências persistidas de lista/grade e tema.
- **`SiteDiscoveryViewModel` (implementado):** controla fetch, parser, prévia, regras CSS/regex, validação e salvamento por hostname; falhas e listas incompletas não chegam à reconciliação.

Os ViewModels usam repositórios e não acessam Room diretamente. A atualização manual e a automática compartilham o coordenador de checagem; somente snapshots completos são persistidos. WorkManager não é acessado pelos ViewModels diretamente: a integração usa o contrato `MonitoringScheduler`. ViewModels devem usar estado imutável e não receber `Context`/View para consultas Room ou scraping.

## 7. Telas Compose

1. **Biblioteca (implementada):** lista ou grade adaptável, preferência DataStore, capas via Coil, capítulo mais recente, não lidos, última verificação e selo quando o monitoramento de um mangá apresenta falhas de site/parsing.
2. **Cadastro por URL (implementado no assistente):** campo do capítulo, loading/erro/prévia e confirmação quando o parse identifica título, série e capítulo do link. Se a lista completa não for identificada, cadastra somente o capítulo conhecido; verificações futuras ainda exigem uma lista completa ou configuração testada.
3. **Detalhe da série (implementado):** capa/título/série, capítulos, estado de leitura, contagem não lida, última verificação/sucesso, atualização manual e preferência individual de monitoramento. Abrir URL e marcar leitura são ações independentes.
4. **Assistente de site (implementado):** campo URL, prévia, regras CSS/regex, teste e salvamento por hostname. Edição/listagem dedicada das configurações segue pendente.
5. **Configurações (implementada parcialmente):** preferência lista/grade em controle segmentado, escolha entre tema do sistema/claro/escuro em linhas agrupadas e selecionáveis, explicação do armazenamento local, aviso de que o agendamento WorkManager pode ser adiado pelo Android e atalho para análise de site. O conteúdo é rolável e organizado em grupos Material 3. Tema e idioma seguem o sistema por padrão; há recursos em português do Brasil e inglês como fallback, e datas seguem o locale do dispositivo.

Todos os estados de conexão, bloqueio, indisponibilidade, HTML inválido e conteúdo dinâmico não disponível são apresentados como falha/limitação, nunca como lista vazia bem-sucedida. Componentes devem manter Material 3, semântica e acessibilidade.

## 8. Navegação

Navigation Compose configura `library`, `site-discovery`, `manga/{mangaId}` e `settings`. Cadastro é uma ação do assistente, não uma rota separada. Edição/listagem dedicada de configurações ainda é futura. Passar IDs/argumentos pequenos e tipados, não entidades Room ou objetos de tela.

Notificações usam intents explícitos e identificadores estáveis. “Abrir capítulo” abre a URL validada no navegador (ou destino interno se isso for escolhido) e não grava leitura; “marcar como lido” chama a ação de domínio explicitamente. Problemas de monitoramento usam canal próprio, respeitam permissão/canal bloqueados e abrem o app sem alterar leitura. A navegação preserva back stack válido e restaura argumentos após recriação quando necessário.

## 9. Descoberta de sites

Pipeline implementado para uma URL informada pelo usuário, sem allowlist fixa:

1. Validar URL HTTP/HTTPS, rejeitar credenciais embutidas, portas não padrão e hosts que resolvam para endereços não públicos.
2. `SafeHtmlFetcher` limita timeout e resposta a 2 MiB, aceita HTML/XHTML e segue apenas redirects same-host sem downgrade HTTPS. Endereços públicos retornados pelo resolvedor são fixados no `Dns` do OkHttp por requisição, evitando segunda resolução não validada durante a conexão.
3. Buscar configuração manual pelo hostname normalizado; então avaliar adapters registrados e estratégias genéricas.
4. Extrair HTML/title, canonical, Open Graph/Twitter, metadados HTML e tipos JSON-LD/Schema.org selecionados. WordPress é somente uma pista; shell com root/script e corpo vazio é identificado como dinâmico.
5. Exigir validação da completude antes de afirmar lista integral. Quando a lista não for demonstrada, retornar prévia incompleta e solicitar configuração manual; não converter falha em lista vazia.
6. Salvar configuração local por hostname apenas após teste bem-sucedido contra o HTML baixado. O cadastro exige resultado `Success` completo e confirmação explícita, então inicia o manga/capítulos na transação local.

Suporte a domínios novos significa tentar reconhecimento genérico e permitir configuração, não garantir parsing em qualquer site. Resultados e evidências dos três exemplos iniciais estão em [SUPPORTED_SITES.md](./SUPPORTED_SITES.md); nenhum está plenamente compatível.

## 10. Arquitetura dos parsers

Contrato independente em `:core:parser-api`: cada parser declara identidade/condições de aplicabilidade e transforma `ParserDocument` delimitado em resultado tipado (`Success`, `RequiresConfiguration` ou `Failure`). O contrato não expõe `Document` do Jsoup à UI/domínio. Parser não faz HTTP nem escreve Room.

Registro e prioridade implementados:

1. Configuração manual válida do host.
2. Adapter de site/layout registrado com evidência observada (`HiveToonsArticleParser`).
3. Estratégias genéricas independentes (Open Graph/JSON-LD, Schema.org, metadados HTML e RSS/Atom).
4. Descoberta ambígua/insuficiente → assistente CSS/regex; não fingir sucesso.

Uma lista só é completa quando `ItemList` Schema.org ou as entradas de uma lista manual são positivamente validadas. Metadados parciais e shells dinâmicos são prévias/configuração necessária. A tela de descoberta não chama a persistência de snapshots, portanto não pode apagar capítulos antigos nem afirmar “sem novidades”.

`SyndicationFeedParser` reconhece links `rel="alternate"` do mesmo host com tipo RSS/Atom e usa o caminho WordPress `/feed/` apenas como pista. O parser é puro: lê o XML fornecido em `ParserDocument.supplementalData` pela chave exposta em `SyndicationFeedParser.supplementalDataKey`, sem iniciar requisições HTTP. Só devolve uma janela `isLatestChapterKnown` quando há pelo menos dois capítulos associados à série e a ordem é comprovada por números ou datas; conteúdo ausente, vazio, truncado, parcialmente interpretado ou ambíguo resulta em `RequiresConfiguration`, nunca em uma janela vazia válida. A obtenção segura do feed e sua passagem em `supplementalData` permanecem responsabilidade do fluxo de fetch, ainda não integrada nesta etapa.

Parsers de site vivem em adapters isolados e recebem fixtures próprias. Registrar um novo parser adiciona implementação ao conjunto, sem `if/when` por domínio no repositório ou tela. Regras CSS são validadas pelo Jsoup; regex usa RE2/J com limite de tamanho e grupo de captura. Links relativos são resolvidos contra a URL de origem e somente URLs HTTP/HTTPS sem credenciais são aceitas. Configuração manual não salva antes de um teste bem-sucedido.

Snapshots só são confirmados em uma transação após fetch e parsing completos. Em qualquer falha, registrar tentativa/erro e manter dados, último sucesso e contagens anteriores. Uma falha nunca vira `emptyList()`.

### Limites de OkHttp, Jsoup e JavaScript

- **OkHttp** faz requisições HTTP; não executa JavaScript nem constrói o DOM renderizado por um navegador.
- **Jsoup** analisa o HTML que recebeu; não executa scripts, não aguarda AJAX e não revela elementos que só são criados no cliente.
- Primeira tentativa: HTML de resposta, metadados embutidos e JSON-LD públicos. Usar endpoint JSON documentado ou claramente permitido apenas se o site autorizar; não descobrir/contornar endpoints protegidos, autenticação, CAPTCHA, paywalls ou limites.
- Se conteúdo necessário só existir após JavaScript e não houver interface pública permitida, reportar `UnsupportedDynamicContent` e oferecer configuração manual somente para dados que a amostra acessível realmente contém. Não inferir ausência de capítulos.
- **Monitoramento em background não deve iniciar WebView/headless browser:** WorkManager é para trabalho adiável, não renderizador de navegador, e não fornece um DOM JavaScript seguro/reproduzível.
- WebView é uma opção futura, somente se houver necessidade aprovada, para prévia assistida em primeiro plano; não é fallback automático nem solução para worker. Revisar ameaça e APIs compatíveis com API 26; HTTPS apenas, consentimento por origem, sem `addJavascriptInterface`, acesso a arquivo/conteúdo desativado, validação de navegação/redirecionamentos e requisições de subrecursos, bloqueio de destinos locais/hosts não aprovados, Safe Browsing, timeout/limite de recursos, sem downloads/intents arbitrários e sem reutilizar sessão autenticada. Se os limites de origem/rede não puderem ser garantidos, não habilitar execução de JS.
- Não usar serviço remoto/headless na nuvem, pois contradiz armazenamento local e ausência de servidores externos.

## 11. Monitoramento com WorkManager

- **Implementado:** `MonitoringDispatchWorker` é único e periódico, com intervalo de uma hora e flex de 15 minutos. Ele consulta mangas ativos e agenda checks separados por manga; WorkManager não garante execução exatamente a cada 60 minutos e pode adiar por Doze, bateria, constraints e políticas do sistema.
- **Implementado:** dispatch e checagens automáticas exigem rede conectada e bateria não baixa. `MangaUpdateWorker` usa trabalho único por manga; política `KEEP` impede duplicatas ainda pendentes.
- **Implementado:** estado `enabled/paused` é persistido no Room; pausar cancela o trabalho automático individual. A checagem revalida o estado antes da requisição e antes de salvar o resultado. Reativar agenda uma checagem individual.
- **Implementado:** cadastro agenda uma primeira checagem automática; refresh manual continua no foreground e compartilha `MangaCheckCoordinator`, mesmo quando o monitoramento está pausado.
- **Implementado:** falhas transitórias de rede, HTTP 429 e servidor resultam em retry com backoff exponencial iniciado em 30 minutos. Erros de acesso, configuração ou parsing são registrados sem retry imediato; o próximo ciclo pode tentar novamente.
- **Implementado:** o coordenador serializa checagens por manga e aplica intervalo mínimo em memória de dois segundos entre requisições ao mesmo host. Falhas e sucessos geram tentativas persistidas; apenas parse completo altera o snapshot. Não agenda navegador WebView.
- **Implementado e testado:** sinais de problema usam a consulta derivada do DAO, contabilizam apenas respostas de servidor e erros de parsing desde o último sucesso, e ativam após cinco falhas elegíveis ou mais de três dias sem sucesso quando existe falha elegível. Falhas de rede não aumentam a contagem. O coordenador registra no DataStore o episódio já notificado e remove o aviso ativo após sucesso.
- A limitação de execução aproximada é exposta na interface e deve permanecer clara na documentação. A limitação do WorkManager significa que “uma hora” é a cadência desejada, não SLA de execução.

Outcomes distinguem sucesso com atualizações, sucesso sem atualizações, rede, bloqueio/acesso, página indisponível, HTML/parsing inválido, conteúdo dinâmico não suportado e cancelamento. Apenas sucesso completo atualiza o snapshot.

## 12. Notificações

- **Implementado; testes JVM e build completo aprovados; validação em dispositivo pendente:** `ChapterNotificationManager` cria um canal estável no startup e publica notificação individual para cada capítulo novo descoberto por check manual ou automático. Título, label, domínio e horário da primeira detecção aparecem no conteúdo; usar a URL original da capa.
- **Imagem segura e opcional:** notificações usam `SafeCoverImageFetcher`, que limita resposta a 2 MiB, valida host/IP público, fixa endereços DNS, restringe redirects ao mesmo host sem downgrade HTTPS e exige `image/*`. A UI configura Coil com `SafePublicImageHttpClient`, que valida hosts públicos, bloqueia credenciais/portas não permitidas e limita respostas. A decodificação de capas de notificação é reduzida fora da main thread; falhas mantêm o conteúdo textual.
- **Agrupamento:** filhos usam group key estável e tag por ID local do capítulo; atualizar resumo InboxStyle quando existem avisos ativos de ao menos dois mangás distintos. A ação de marcar lido remove apenas a notificação daquele capítulo e recalcula o resumo.
- **Ações independentes:** “Abrir capítulo” usa PendingIntent imutável `ACTION_VIEW` para URL HTTP(S) validada, sem chamar repositório. “Marcar como lido” usa PendingIntent imutável para `MarkChapterReadReceiver`, explicitamente não exportado, e persiste `READ` com origem `NOTIFICATION_ACTION`; ação repetida não gera evento de histórico duplicado.
- **Entrega idempotente:** o capítulo é marcado com `notificationPostedAtEpochMillis` apenas depois que `NotificationManagerCompat.notify` retorna sem erro; DAO atualiza somente campo ainda nulo. A coluna já existia no schema v2 e foi preservada nas migrations seguintes.
- **Permissão:** canal configurado para API 26+; manifest declara `POST_NOTIFICATIONS` para target 35. A solicitação runtime em API 33+ só ocorre após ação explícita “Enable chapter notifications” em Settings. Testes Robolectric cobrem canal em API 26 e permissão negada em API 33; permissão negada ou canal bloqueado não é contornado. A validação em dispositivo continua pendente.

## 13. Histórico de leitura

`ReadingStateEntity` é a projeção atual usada na UI; `ReadingHistoryEntity` é o log append-only de cada transição efetiva `READ`/`UNREAD`. Atualizar ambos em uma transação para evitar divergência. A contagem de não lidos é derivada dos capítulos, não duplicada em contador armazenado.

Abrir capítulo (na tela ou notificação) apenas abre o link. Marcar/desmarcar por botão é a única ação normal que grava leitura, com instante; a ação de notificação “marcar como lido” é explícita. Não limpar histórico por pausa/reativação, erro do site ou refresh. Capítulos encontrados no primeiro cadastro começam não lidos; ainda requer decisão se esse cadastro inicial deve estabelecer baseline sem enviar notificações.

## 14. Estratégia de testes

- **Unitários (`:core:domain`):** identidade e comparação de capítulos inteiros/decimais/especiais, leitura explícita, unread count, estados de check e regras de reconciliação.
- **Parsing (`:core:parser-engine`, `:parsers:site-adapters`):** fixtures sanitizadas offline por layout; sucesso, campo ausente, links relativos, HTML vazio/inválido, conteúdo dinâmico indisponível, CSS/regex inválidos e resultado parcial. Provar que falha não produz lista vazia nem apaga snapshot.
- **Sites:** teste real só em páginas públicas e conforme permitido; resultado reproduzível/fixture e data/escopo registrado em `SUPPORTED_SITES.md`. Não depender de rede ao vivo na suíte normal.
- **Room (`:core:data`):** DAO/índices, transação leitura+histórico, count e migrations desde schemas anteriores, verificando preservação.
- **WorkManager:** testes JVM/Robolectric validam agendamento único, intervalo configurado, constraints de rede/bateria, cancelamento, classificação de retry transitório, preservação após parse incompleto e pausa antes da persistência. A execução real e a cadência continuam sujeitas ao sistema Android; não houve teste em dispositivo.
- **Compose/navigation:** alternância lista/grade persiste; abrir não marca lido; toggles e ações da notificação têm efeitos distintos; loading/vazio/erro e links de navegação funcionam.
- **Notificações:** executados testes para cover/BigPicture, fallback textual, ações e payload/flags PendingIntent, permissão negada em API 33, canal em API 26, agrupamento por mangá e fluxo de persistência.
- Revisar adaptação de WebView isoladamente se vier a ser aprovada; não declarar suporte dinâmico com apenas teste de DOM estático.

### Validação já executada

- `gradlew.bat --no-daemon --rerun-tasks build` passou, executando 230 tarefas acionáveis, incluindo `check`, lint, testes e variantes debug/release.
- Testes JVM: `:app` 16 por variante debug/release, `:core:data` 25 por variante debug/release, `:core:parser-engine` 12 e `:parsers:site-adapters` 2. Zero falhas, erros ou skips; 55 casos únicos e 96 execuções considerando ambas as variantes Android.
- Após a limpeza de uma chamada deprecated em teste, `:app:testDebugUnitTest` e `:app:testReleaseUnitTest` foram executados novamente e passaram (16 testes cada).
- Lint do app concluiu sem erros; restaram 18 avisos informativos de versões mais novas de Gradle/AGP/dependências. `:core:data` não reportou issues. O build também avisou que duas bibliotecas nativas não puderam ser stripadas e foram empacotadas sem stripping.
- APKs gerados: `app/build/outputs/apk/debug/app-debug.apk` e `app/build/outputs/apk/release/app-release-unsigned.apk`. O artefato release não está assinado e não é pronto para distribuição.
- Não há arquivos em `src/androidTest` em nenhum módulo; `adb devices` não listou dispositivos. Não houve execução instrumentada, em emulador/dispositivo ou integração live end-to-end.

## 15. Estratégia de compilação

1. Configuração inicial selecionada: `applicationId` provisório `com.sanzenkai.mangatracker`, `compileSdk`/`targetSdk` 35, `minSdk` 26, Kotlin 2.1.21, AGP 8.10.1, Gradle 8.11.1 e JDK 17. A combinação passou no build completo e nos testes JVM indicados acima.
2. Compilar cada módulo novo e seus testes antes de adicionar funcionalidades dependentes. Usar version catalog se adotado no scaffold, sem atualizar toolchain incidentalmente.
3. Passou: `gradlew.bat --no-daemon --rerun-tasks build`. No Windows, usar o Wrapper `gradlew.bat`; testes instrumentados e validação em emulador/dispositivo continuam pendentes por indisponibilidade de testes/dispositivo.
4. Instalar/testar debug em emulador Android API 26 (mínimo) e numa API moderna para permissões/notificações, quando SDK/emuladores estiverem disponíveis.
5. Verificar APK produzido, variante e identidade; assemble não substitui testes funcionais nem real-site validation.
6. Release/assinatura somente quando solicitada, com keystore/segredos externos ao Git e verificação de variante/assinatura. Não gerar segredos ou afirmar compilação sem comando executado com sucesso.

## Decisões ainda necessárias antes ou durante a implementação

- Confirmar o namespace/applicationId provisório antes de distribuição; instalar Android Studio, JDK 17 e SDK 35 no ambiente de desenvolvimento permanente.
- Decidido e implementado: `allowBackup=false`, regras `fullBackupContent` para versões anteriores ao Android 12 e `dataExtractionRules` excluem os dados locais de backup em nuvem e transferência entre dispositivos. O backup JSON manual do app permanece disponível.
- Definir se configurações pertencem exatamente ao hostname ou a um escopo registrable/subdomínio; nunca herdar configuração usando substring.
- Definir tratamento de identidade duplicada quando a página de série não puder ser detectada.
- Decidido e implementado: no cadastro por link com janela recente validada, usar o capítulo informado como baseline quando ele for identificado; salvar esse capítulo e os mais novos da janela como não lidos, sem notificação retroativa. Se não for identificado, manter o capítulo mais recente disponível como baseline e informar o fallback. Falhas de parsing não alteram dados locais; não foi necessária migração de schema.
- Definir regra de ordenação entre capítulos numéricos e especiais/extra com amostras reais.
- Decidido e implementado: a atualização manual continua disponível no detalhe de cada mangá e a biblioteca oferece "Atualizar tudo" para todos os mangás com monitoramento ativo, usando o coordenador existente e exibindo o resumo de sucessos e falhas.
- Decidido e implementado: explicar uma vez a permissão de notificações após o primeiro cadastro monitorado e, se ela ou o canal estiver bloqueado, mostrar um aviso na biblioteca com atalho às configurações do Android. Ainda falta decidir se haverá ação explícita para reprocessar aviso pendente.
- Avaliar, após exemplos reais e consulta de permissões/termos, se há fonte dinâmica que justifique WebView assistida; sem tal validação, manter como não suportada para monitoramento automático.
- Selecionar páginas/tipos de conteúdo e autorizações para testar os três domínios candidatos. Nenhum está validado hoje.

## Estado do repositório

O projeto Gradle, o Wrapper, Room v3, descoberta estática, fluxos iniciais, monitoramento WorkManager e pipeline de notificações estão implementados. A auditoria mais recente passou no build completo e nos testes JVM disponíveis; confirmou a separação entre abrir capítulo e marcar leitura e a preservação de dados em resultados incompletos. Nenhum site é plenamente suportado e não houve teste em emulador/dispositivo. O coordenador mantém mapas de locks e timestamps por manga/host apenas em memória, sem política de remoção; não foi medido impacto de memória. Alterações à arquitetura devem ser refletidas em `PROJECT_STATUS.md` e somente decisões verificadas devem ser descritas como concluídas.
