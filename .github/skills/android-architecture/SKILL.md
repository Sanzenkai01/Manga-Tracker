---
name: android-architecture
description: Planeje ou altere a arquitetura Android do Manga Tracker usando MVVM, Repository Pattern, módulos e camadas coesas, sem perder o funcionamento local nem quebrar contratos existentes.
---

# Arquitetura Android

## Objetivo

Projetar mudanças Android que se encaixem na arquitetura real do Manga Tracker. Use MVVM e Repository Pattern para separar interface, estado de apresentação, regras de domínio e fontes de dados, mantendo o código simples, modular, testável e local-first.

## Quando usar

- Antes de criar uma nova funcionalidade ou módulo.
- Ao mover responsabilidades entre UI, ViewModel, domínio e dados.
- Ao integrar Room, rede, parsing HTML, notificações ou trabalho em segundo plano.
- Ao investigar dependências circulares, lógica duplicada ou classes difíceis de testar.

## Conhecimentos técnicos

- **Apresentação:** telas Compose renderizam estado e enviam ações; ViewModels coordenam operações de apresentação e publicam estado observável.
- **Domínio:** regras e tipos de negócio não devem depender de Android, Room, OkHttp ou elementos visuais quando isso não for necessário.
- **Dados:** repositórios oferecem operações orientadas ao domínio e coordenam fontes locais e, se expressamente solicitadas, remotas.
- **Injeção de dependência:** reutilize o mecanismo já configurado. Evite introduzir um framework somente para uma classe.
- **Modularização:** prefira limites de módulo que representem dependências e responsabilidades reais. Um módulo só é útil quando melhora isolamento, compilação ou propriedade do código.
- **Local-first:** Room é a fonte persistente local. Rede não pode ser requisito para consultar ou atualizar o acompanhamento já armazenado.

## Procedimento

1. Leia `AGENTS.md`, as instruções aplicáveis, a estrutura do projeto, as dependências e os testes. Localize o fluxo funcional relacionado antes de propor abstrações.
2. Desenhe o caminho da operação: ação da UI → ViewModel → caso de uso (se justificado) → repositório → fonte de dados. Diferencie fluxo de leitura, escrita e atualização em background.
3. Identifique quem possui cada regra e contrato. Não coloque consultas Room, requisições HTTP, parsing ou regras de negócio em composables.
4. Preserve APIs e comportamento existentes. Se for necessário mudar um contrato, liste chamadores, compatibilidade e impacto nos dados antes da alteração.
5. Implemente o menor recorte coeso. Reutilize padrões de navegação, estado, dispatchers e erros já adotados.
6. Adicione testes na camada onde a regra pode ser verificada com menor custo, sem depender de serviços externos.
7. Execute os testes pertinentes e os checks de build aplicáveis; atualize `PROJECT_STATUS.md` e `CHANGELOG.md` para mudanças concluídas, não planejadas.

## Cuidados importantes

- Não crie interfaces, módulos, casos de uso ou camadas sem uma necessidade concreta de isolamento ou reutilização.
- Evite dependências de UI na camada de dados e dependências Android na lógica de domínio quando forem dispensáveis.
- Não crie um `Repository` que apenas repasse métodos sem oferecer uma fronteira útil.
- Modele erros explicitamente; não os transforme em listas vazias ou sucesso aparente.
- Não troque ou apague dados locais por falhas de rede, migração destrutiva ou refatoração sem autorização.
- Não remova funcionalidade existente sem justificar e obter autorização do usuário.

## Exemplo prático

Para adicionar uma ação “marcar capítulo como lido”, procure primeiro o modelo de capítulo, o DAO, o método de repositório, o ViewModel e a tela que já exibem o progresso. Mantenha a alteração de estado em uma operação de repositório testável; a tela emite a ação e observa o novo estado. Não adicione uma segunda fonte de verdade de progresso na UI.

## Critérios de conclusão

- Cada responsabilidade da mudança tem uma camada proprietária clara e não duplica regras existentes.
- Contratos, fluxo offline e comportamento existente estão preservados ou as mudanças foram autorizadas.
- Testes pertinentes foram executados e resultados reportados com precisão.
- Build foi executado quando possível; limitações são explicitadas.
- Documentação de status e changelog reflete somente trabalho efetivamente concluído.
