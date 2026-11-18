# Manga Tracker — especificação funcional

**Estado:** requisitos registrados; esta especificação descreve o comportamento desejado, não comprova implementação. Consulte `PROJECT_STATUS.md` para o estado verificado de cada funcionalidade.  
**Plataforma mínima solicitada:** Android 8.0 (API 26).  
**Persistência:** inteiramente local no dispositivo.

Este documento é a referência funcional para o desenvolvimento. Os requisitos abaixo expressam o comportamento desejado, não funcionalidades já implementadas. Sites listados como exemplos não são considerados compatíveis até que testes reais sejam executados e registrados em [SUPPORTED_SITES.md](./SUPPORTED_SITES.md).

## Objetivo

Permitir que a pessoa acompanhe mangás a partir de links de páginas de capítulos, consulte capítulos e histórico de leitura, receba notificações de novas publicações e monitore fontes por mangá. O app deve armazenar seus dados no dispositivo, funcionar para o acompanhamento local sem serviços externos e oferecer descoberta/configuração de parsing para sites diversos.

## Requisitos funcionais

### Cadastro, descoberta e parsing

1. **Cadastro por link de capítulo.** A pessoa pode cadastrar um mangá fornecendo o link de uma página de capítulo. O campo de link deve oferecer uma ação para colar conteúdo da área de transferência. O app deve usar o link como ponto de partida para identificar a série e suas informações. Se o parser retornar uma janela recente validada e identificar nela o capítulo do link, esse capítulo será a linha de base e o app salvará ele e os capítulos mais novos da janela como não lidos, sem notificação retroativa. Se não identificar o capítulo, deverá usar o capítulo mais recente disponível e informar o fallback no cadastro; falhas de parsing não podem alterar dados locais.
2. **Identificação e metadados.** Tentar identificar automaticamente o título, a URL da capa original, a página da série, o capítulo associado ao link, a lista de capítulos, os links correspondentes e as datas disponíveis. Não inventar campos ausentes nem substituir a capa original por uma URL transformada sem preservar a original.
3. **Diversidade de fontes.** Suportar sites de tradução diversos sem limitar a descoberta a uma lista fixa e fechada de domínios. Compatibilidade continua sendo específica a cada site e precisa de validação; esta exigência não significa que qualquer site seja automaticamente suportado. Quando a página da série anunciar um feed RSS/Atom do mesmo host, ou houver indícios de WordPress que permitam tentar seu caminho `/feed/`, a descoberta pode usar os itens do feed como janela de capítulos mais recentes conhecidos. Feed vazio, malformado ou parcialmente interpretado nunca confirma ausência de capítulos novos nem substitui uma janela previamente válida.
4. **Exemplos iniciais para análise.** Considerar estes links como amostras de entrada e investigação futura, sem declará-los testados ou compatíveis:
   - `https://hivetoons.org/series/eleceed/chapter-426`
   - `https://imperiodabritannia.net/manga/o-exilado-cavaleiro-pesado-reencarnado-e-incomparavel-com-o-conhecimento-do-jogo/capitulo/185`
   - `https://mairimashitairuma-kun.com/manga/mairimashita-iruma-kun-chapter-465/`
   - `https://roliascan.com/read/shangri-la-frontier-kusoge-hunter-kamige-ni-idoman-to-su/ch285-342785/`
5. **Descoberta de sites desconhecidos.** Tentar reconhecer a estrutura de um site que ainda não tenha configuração. A identificação automática deve ser tratada como tentativa, não como prova de compatibilidade.
6. **Configuração manual assistida.** Quando a identificação automática falhar ou for insuficiente, oferecer um assistente simples para configurar o parsing manualmente, com seletores CSS e regex. Validar entradas e apresentar falhas/ambiguidade; regex serve para texto delimitado, não para substituir parsing de HTML.
7. **Configuração local por domínio.** Salvar e reutilizar as configurações de parsing associadas ao domínio correspondente. A configuração deve permanecer no dispositivo e poder ser corrigida se a estrutura do site mudar.

### Biblioteca, capítulos e leitura

8. **Tela principal.** Exibir para cada mangá nome, capa, capítulo mais recente conhecido, data da atualização, horário/data da última verificação, status e quantidade de capítulos não lidos. Falha de verificação deve ser distinguível de uma verificação bem-sucedida sem novidades.
9. **Lista ou grade.** Permitir alternar entre apresentação em lista e em grade e persistir localmente a preferência para uso posterior.
10. **Histórico de leitura.** Manter no banco de dados local o estado/histórico persistente de capítulos lidos; encerrar ou reiniciar o app não pode apagar esse histórico.
11. **Abrir não marca como lido.** Abrir um capítulo não altera automaticamente seu estado de leitura.
12. **Ação explícita por capítulo.** Disponibilizar um controle separado para marcar ou desmarcar cada capítulo como lido; a quantidade de não lidos deve refletir esse estado persistido.
- **Filtros e indicadores de leitura na biblioteca.** Permitir filtrar mangás com capítulos não lidos ou com todos os capítulos salvos lidos. Exibir uma badge por mangá: azul para lido, verde quando houver capítulo novo ainda não lido e vermelho para outros capítulos não lidos. Capítulos novos continuam incluídos no filtro de não lidos até serem marcados como lidos.
- **Ações em lote na biblioteca.** Um toque longo inicia a seleção de um ou mais mangás. Permitir marcar todos os capítulos dos itens selecionados como lidos ou não lidos. Remover itens exige confirmação explícita e apaga também os capítulos, o histórico de leitura e os dados de monitoramento associados, sem afetar outros mangás.
13. **Identificadores de capítulo.** Reconhecer capítulos inteiros, decimais, especiais e extras. Não reduzir especiais/extras a valores numéricos inventados; manter identificação suficiente para distinguir entradas e comparar novos resultados de maneira consistente.

### Notificações e monitoramento

14. **Conteúdo da notificação.** Ao detectar um novo capítulo, incluir a capa original, o nome do mangá, o capítulo, o site e o horário da detecção. Se a imagem estiver indisponível, a falha da capa não deve impedir a notificação textual.
15. **Ações separadas.** Disponibilizar ações distintas para abrir o capítulo e marcá-lo como lido. Abrir pela notificação também não marca como lido implicitamente.
16. **Monitoramento periódico.** Usar WorkManager para monitoramento adiável em segundo plano, com intervalo padrão de uma hora e opções de 1, 3, 6, 12 ou 24 horas nas Configurações. Persistir localmente a opção e aplicá-la ao agendamento único. Informar corretamente o horário da última verificação e respeitar constraints, economia de bateria, Doze, conectividade e demais limitações do Android: o intervalo escolhido é uma periodicidade desejada, não garantia de execução pontual.
17. **Atualização manual.** Disponibilizar atualização manual por mangá e uma ação "Atualizar tudo" na biblioteca para todos os mangás com monitoramento ativo. Executar sem bloquear a interface, distinguir sucesso sem novidades de falhas de conexão, acesso ou parsing e apresentar o resumo de resultados da atualização em lote.
18. **Controle individual.** Permitir pausar e reativar o monitoramento para cada mangá sem apagar seus dados, capítulos ou histórico.

### Persistência, compatibilidade e resiliência

19. **Banco inteiramente local.** Armazenar localmente mangás cadastrados, dados identificados, capítulos, histórico de leitura, configurações por domínio, preferências de apresentação, configuração de monitoramento e resultados/horários relevantes. Dados do núcleo do app não dependem de sincronização remota.
20. **Versão mínima Android.** Ser compatível com Android 8.0 ou superior (API 26 ou superior). Comportamentos de notificação, permissões e execução em background devem ser implementados de acordo com a versão e target SDK efetivamente configurados.
21. **Erros e conteúdo difícil.** Tratar explicitamente erros de conexão, bloqueios, páginas indisponíveis, alterações de HTML e conteúdo carregado dinamicamente. Comunicar estado/limitação em vez de apresentar dados incorretos como válidos. Não contornar autenticação ou mecanismos de segurança do site.
22. **Falha de parsing não é lista vazia.** Um erro de parsing, HTML inesperado ou resultado insuficiente jamais pode ser interpretado como confirmação de que não há capítulos novos. Só comparar/publicar resultado de novos capítulos quando a leitura foi concluída e validada com sucesso.
23. **Preservação em indisponibilidade.** Nunca apagar os dados antigos porque um site está indisponível, bloqueou a requisição, mudou de HTML ou não pôde ser analisado. Manter o último resultado válido e registrar a falha da tentativa separadamente.
24. **Acesso responsável.** Respeitar limites de requisição, autenticação e mecanismos de segurança dos sites. Não contornar login, paywall, CAPTCHA, controles de acesso ou medidas antiabuso; suportar apenas conteúdo cujo acesso e uso sejam permitidos.
25. **Sem serviços externos obrigatórios.** Não usar Firebase, servidor externo, APIs pagas ou armazenamento em nuvem. Acesso à internet só é utilizado para a funcionalidade de fontes explicitamente solicitada; a biblioteca e o histórico local devem continuar utilizáveis offline.

### Preferências e idioma

26. **Tema claro/escuro.** Oferecer as opções “Seguir tema do dispositivo”, “Claro” e “Escuro”. A opção deve ser persistida localmente e aplicada de forma consistente em todas as telas; o padrão deve acompanhar o sistema.
27. **Idioma conforme o sistema.** Por padrão, usar o idioma configurado no Android sem impor um idioma próprio ao aplicativo. Disponibilizar interface, mensagens e ajuda em português do Brasil e inglês, usando inglês como fallback quando não houver tradução para o idioma do dispositivo. Datas e horários devem seguir o formato local do sistema; títulos, nomes de sites, URLs e demais conteúdos de origem devem ser preservados como publicados.
28. **Backup da biblioteca.** Permitir exportar e importar um arquivo JSON versionado com mangás cadastrados, capítulos, estados e histórico de leitura, monitoramento e configurações locais dos sites. A pessoa escolhe onde salvar ou qual arquivo abrir pelo seletor de documentos do Android; não exigir permissão ampla de armazenamento nem serviço externo. A importação valida o arquivo antes de gravar e mescla os dados em uma transação, sem apagar nem substituir registros locais existentes.
29. **Organização das configurações.** Separar visualmente os grupos de preferências nas configurações, mantendo suas ações e opções atuais.
30. **Monitoramento com problema.** Derivar o estado das tentativas já registradas: pelo menos cinco falhas consecutivas elegíveis ou mais de três dias desde o último sucesso, desde que haja uma falha elegível. Contar respostas de servidor e falhas de parsing; falhas exclusivamente de rede do usuário não indicam que o site esteja quebrado. Mostrar um selo na biblioteca e uma explicação nos detalhes, e emitir no máximo uma notificação por episódio em canal próprio enquanto o monitoramento estiver ativo. Um sucesso encerra o episódio; preferir consulta derivada sem migração de schema.

### Abertura do app

- Em uma abertura a frio pelo launcher, exibir a imagem do ícone em uma animação de até aproximadamente 1,5 s, pulável por toque e desativada quando as animações do sistema estiverem desligadas.

## Regras invariantes

- O estado de leitura só muda por ação explícita de marcar/desmarcar; abrir um capítulo, abrir a notificação ou detectar um capítulo não altera esse estado por si só.
- Resultado vazio só é válido depois de uma leitura bem-sucedida. Falha deve permanecer um resultado distinto.
- Uma fonte externa nunca é a única cópia de capítulos, configurações ou histórico já persistidos.
- Detecção automática, metadados padronizados, assinatura WordPress ou existência de um seletor não bastam para declarar suporte a um site.
- A ausência de uma informação na página não autoriza fabricar seu valor.
- Permissões, conectividade e monitoramento devem respeitar as escolhas do usuário e as políticas do Android e da fonte.
- Solicitar a permissão de notificações em contexto, uma vez, após o primeiro cadastro bem-sucedido de mangá monitorado. Se o usuário não permitir, não repetir automaticamente a solicitação; enquanto houver mangá monitorado com notificações do app ou canal bloqueados, mostrar na biblioteca um aviso que leve às configurações do Android.

## Critérios de validação da especificação

- Cada um dos 30 requisitos está refletido acima sem tratá-lo como implementação concluída.
- Os três links de exemplo aparecem como amostras não verificadas em [SUPPORTED_SITES.md](./SUPPORTED_SITES.md).
- Testes futuros precisam cobrir, no mínimo, distinção entre lista vazia válida e erro de parsing; preservação de dados no erro; abertura sem alteração de leitura; alternância explícita de leitura; capítulos decimais/especiais; preferência de visualização; pausa individual; e tratamento/ações de notificação.
- A compatibilidade de cada site só poderá ser publicada com evidências de teste real e limitações registradas.

## Questões de implementação ainda em aberto

Estas escolhas não alteram os requisitos funcionais e devem ser decididas com base no projeto e nas limitações técnicas durante a implementação:

- Política detalhada para normalização de domínios (por exemplo, subdomínios e redirecionamentos).
- Formato interno e regras de ordenação que preservem capítulos inteiros, decimais, especiais e extras.
- Representação visual exata dos status, além dos dados de verificação e monitoramento requeridos.
- Estratégia técnica para fontes cujo conteúdo só existe após execução de JavaScript. Não contornar controles do site; quando a extração permitida não for possível, comunicar a limitação e preservar os dados anteriores.
- Target SDK, versões de dependências, estratégia de fixtures, esquema Room e estratégia de migrações compatíveis com o toolchain escolhido.
