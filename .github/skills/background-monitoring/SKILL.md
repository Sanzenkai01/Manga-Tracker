---
name: background-monitoring
description: Modele monitoramento adiável do Manga Tracker com WorkManager, trabalho periódico e manual, restrições de bateria e execução individual, preservando limites e dados locais.
---

# Monitoramento em segundo plano

## Objetivo

Usar WorkManager para tarefas adiáveis e persistentes, como verificações periódicas de atualizações quando solicitadas. Equilibrar atualidade, bateria, conectividade, preferências do usuário e operação offline.

## Quando usar

- Ao agendar uma verificação periódica de capítulos solicitada pelo usuário.
- Ao implementar atualização manual que precise continuar em background.
- Ao permitir habilitar, pausar ou configurar monitoramento por mangá.
- Ao corrigir duplicação, retries ou constraints de workers.

## Conhecimentos técnicos

- **WorkManager:** execução persistente e adiável, sujeita a constraints e otimizações do sistema; não é execução em horário exato.
- **PeriodicWorkRequest:** intervalo mínimo aceito pelo WorkManager é de 15 minutos; a execução efetiva pode ocorrer mais tarde por bateria, Doze e constraints.
- **Constraints:** conectividade, bateria, charging e armazenamento devem refletir a tarefa, não ser escolhidos de forma arbitrária.
- **Unique work:** combina nome único e política de conflito para evitar agendamentos duplicados; política precisa refletir semântica de atualização/substituição.
- **Retry/backoff:** repetir somente falhas transitórias, respeitar backoff e manter execução idempotente.
- **Monitoramento individual:** identificadores estáveis permitem controlar trabalho associado ao mangá, sem criar carga ilimitada de workers.
- **Fonte de verdade:** configurações e resultados relevantes ficam no estado persistido local; status do worker não substitui estado de domínio.

## Procedimento

1. Confirme se a tarefa é adiável. Para atualização imediata enquanto a tela está aberta, prefira o fluxo assíncrono existente em vez de agendar trabalho periódico.
2. Inspecione dependência/versão WorkManager, `Worker` ou `CoroutineWorker`, DI, configurações e testes existentes.
3. Defina input mínimo e resultado observável. Use IDs locais, não objetos grandes ou dados mutáveis.
4. Escolha constraints alinhadas ao custo: uma checagem que depende de rede só executa com a constraint adequada; não agende rede para função local.
5. Decida nomes estáveis de unique work e política de conflito. Garanta que ativação repetida do monitoramento não duplica tarefas.
6. Para monitoramento por mangá, limite concorrência e permita cancelar/desabilitar o trabalho relacionado; prefira coordenação agregada quando o volume de tarefas individuais puder crescer.
7. Faça cada worker idempotente: valide ID, carregue o estado atual, persista resultados incrementalmente e não apague histórico por ausência temporária na fonte.
8. Trate sucesso, retryável, falha permanente e cancelamento com estados distintos; publique progresso somente se a tarefa exigir e o suporte existir.
9. Teste worker, constraints, agendamento único, cancelamento e resultados usando ferramentas de teste configuradas; verifique também o build.

## Cuidados importantes

- Não prometa horário exato: WorkManager respeita constraints e otimizações de bateria.
- Não tente contornar Doze, economia de bateria ou decisões do usuário por polling agressivo.
- A periodicidade não pode ser menor que o mínimo aceito pelo framework; consulte documentação e dependência realmente usadas.
- Evite retry infinito para erro permanente, parsing incompatível, URL inválida ou configuração ausente.
- Respeite taxa e termos da fonte; backoff e concorrência limitada são essenciais para tarefas de rede autorizadas.
- Falha de worker não pode apagar dados existentes nem ser convertida em sucesso vazio.
- Persistir monitoramento individual não significa criar um worker concorrente por mangá sem avaliar escala e limites.

## Exemplo prático

Ao ativar monitoramento de um mangá, grave a preferência local e enfileire trabalho único identificado pelo ID local. Desativá-lo cancela o trabalho associado e preserva histórico. Se a execução encontrar falha transitória de rede, retorna retry segundo backoff; se o HTML mudou, registra falha observável em vez de apagar capítulos.

## Critérios de conclusão

- Tipo de trabalho é apropriado ao WorkManager e limites temporais são comunicados corretamente.
- Reagendamentos não duplicam tarefas e desativação/cancelamento é funcional.
- Constraints, retry e idempotência têm justificativa e testes.
- Falhas de rede/parsing preservam estado local e são observáveis.
- Testes e build pertinentes foram executados ou limitações foram relatadas sem alegações não verificadas.
