---
name: android-notifications
description: Planeje ou implemente notificações Android do Manga Tracker, incluindo canais, imagens, PendingIntent e ações rápidas, respeitando versões do Android, permissões e privacidade.
---

# Notificações Android

## Objetivo

Criar notificações úteis e seguras para o Manga Tracker sem surpreender o usuário, perder contexto de navegação ou solicitar permissões desnecessárias. Integrar notificações com o estado local e o fluxo de trabalho existente.

## Quando usar

- Ao notificar novo capítulo ou evento de acompanhamento solicitado pelo usuário.
- Ao criar canais, ações rápidas ou navegação de uma notificação.
- Ao exibir capa usando `BigPictureStyle`.
- Ao revisar comportamento em versões distintas do Android.

## Conhecimentos técnicos

- **Notification channels:** necessários em versões modernas do Android; cada canal expressa uma categoria e importância configurável pelo usuário.
- **Permissão de notificação:** requisitos dependem da versão do sistema, target SDK e fluxo do app. Confirme configuração real e orientação Android vigente antes de alterar.
- **PendingIntent:** deve ter identidade e flags deliberadas; prefira intents explícitos e `FLAG_IMMUTABLE` quando a aplicação não precisa que outro componente os modifique. Use mutabilidade apenas com justificativa de API.
- **BigPictureStyle:** adequada a imagem de capa opcional; carregar imagem fora da main thread, limitar tamanho e oferecer apresentação degradada sem imagem.
- **Ações rápidas:** cada ação deve executar uma intenção específica, segura, idempotente quando possível, e refletir o estado persistido.
- **Privacidade:** texto da notificação pode aparecer na tela bloqueada; evite conteúdo sensível e forneça controles claros ao usuário.

## Procedimento

1. Confirme o caso de uso, quem optou por receber a notificação e qual evento a aciona. Não implemente notificações proativas não solicitadas.
2. Inspecione `minSdk`, `targetSdk`, manifest, canais, dependências e arquitetura existentes.
3. Defina um canal estável com nome e finalidade compreensíveis. Não crie canal novo a cada notificação ou atualização.
4. Trate solicitação de permissão no contexto de valor ao usuário e verifique seu estado antes de postar; não peça permissão no início sem contexto.
5. Construa notificação com ícone pequeno, título e texto concisos, categoria/priority apropriadas e `BigPictureStyle` opcional quando houver capa válida.
6. Crie intents explícitos com extras mínimos e flags corretas. Garanta request codes/identificadores distintos quando intents diferentes não devam colidir.
7. Para ações rápidas, valide a entrada, atualize o repositório de modo seguro e remova/atualize a notificação conforme o resultado; trate repetição.
8. Teste construção de conteúdo e ação, além de comportamento por API/ambiente disponível. Revise logs sem expor dados privados.

## Cuidados importantes

- Comportamento e permissões variam conforme API/target SDK; verifique docs oficiais e configuração do projeto antes de afirmar requisitos específicos.
- Não contorne preferências do usuário, canal bloqueado ou permissão negada.
- Não faça download de imagem na main thread. Não permita que falha de imagem impeça notificação textual válida.
- Evite intents implícitos que possam ser interceptados ou encaminhar a ação a outro app inesperado.
- Use flags de mutabilidade compatíveis com o alvo e com a API; não torne PendingIntent mutável por conveniência.
- Ação “marcar lido” deve atualizar estado persistido, não só a aparência da notificação.
- Não exiba títulos ou links não validados provenientes de fontes externas sem sanitização e limites.

## Exemplo prático

Uma notificação de capítulo pode abrir explicitamente a tela do mangá usando seu ID local. A capa pode ser anexada como `BigPictureStyle` se já estiver disponível em cache; caso contrário, publique apenas o texto. Uma ação “marcar lido” chama uma operação do repositório com o ID e o capítulo, e a notificação só reflete sucesso após a atualização ser confirmada.

## Critérios de conclusão

- Canal e permissão são coerentes com a versão Android e escolhas do usuário.
- PendingIntents são explícitos, distinguíveis e têm flags seguras.
- Notificação continua utilizável sem imagem e não bloqueia a UI.
- Ações atualizam dados reais e lidam com repetição/erro sem sucesso aparente.
- Testes e validações pertinentes foram executados; alterações de permissões e comportamento são explicadas.
