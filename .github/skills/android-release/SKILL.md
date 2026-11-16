---
name: android-release
description: Prepare e valide builds Android do Manga Tracker com Gradle, APK debug/release e assinatura segura, sem expor segredos nem declarar artefatos não verificados.
---

# Compilação e distribuição Android

## Objetivo

Orientar builds reproduzíveis e preparação de artefatos APK para desenvolvimento ou distribuição. Confirmar variantes, ambiente e assinatura reais do projeto; proteger chaves e distinguir um APK debug de um release pronto para entrega.

## Quando usar

- Ao verificar compilação após uma mudança.
- Ao gerar APK debug para instalação e validação manual.
- Ao preparar APK release ou revisar configuração de assinatura.
- Ao diagnosticar falhas Gradle, dependências, JDK ou Android SDK.

## Conhecimentos técnicos

- **Gradle Wrapper:** usa a versão do projeto; prefira `gradlew.bat` no Windows e confira permissões/arquivos antes de executar.
- **Build variants:** `debug`, `release`, flavors e nomes de tarefas dependem dos módulos e da configuração Gradle real.
- **APK debug:** em geral, usa configuração de debug do Android Gradle Plugin e serve a desenvolvimento/teste; não equivale a uma release assinada para distribuição.
- **APK release:** requer configuração e chave de assinatura adequadas para distribuição. Segredos devem vir de armazenamento/configuração protegidos.
- **Build outputs:** caminho e nome dos APKs podem variar por módulo, variante e versão do AGP; confirme o output produzido após o comando.
- **Validação:** build bem-sucedido não substitui testes funcionais, verificação do APK nem política de release.

## Procedimento

1. Inspecione Wrapper, `settings.gradle.kts`, plugins, catálogos de versão, módulos, flavors, `applicationId`, `minSdk`/`targetSdk` e configuração de assinatura.
2. Verifique disponibilidade e compatibilidade de JDK, Android SDK, licenças e dependências. Não altere versões para esconder um problema sem avaliar compatibilidade.
3. Determine a tarefa exata a partir dos módulos e variantes encontrados. Execute primeiro o build/check mais restrito que valida a mudança.
4. Para APK debug, execute a tarefa assemble debug da variante real via Wrapper e confirme seu código de saída e o artefato gerado.
5. Para APK release, verifique que a configuração de assinatura usa credenciais fornecidas fora do controle de versão. Nunca invente nem gere uma chave para o usuário sem solicitação.
6. Execute tarefas de validação configuradas, incluindo testes pertinentes. Analise o primeiro erro causal no log antes de alterar dependências ou caches.
7. Confirme existência, tamanho, variante e identidade do artefato no diretório esperado. Não renomeie para “release” um APK debug.
8. Se solicitada distribuição, descreva variante, assinatura, instalação/validação e limitações. Atualize status/changelog somente com fatos concluídos.

## Cuidados importantes

- Nunca coloque keystore, senha, token ou segredo em código, logs, argumentos compartilhados, documentação versionada ou commit.
- Não imprima conteúdo de variáveis secretas nem abra/compartilhe arquivos de assinatura.
- Não sobrescreva artefatos ou configurações existentes sem necessidade; verifique o estado antes de gerar.
- Não execute comandos destrutivos de limpeza ou remova outputs do usuário sem autorização.
- Não afirme que o app compilou sem executar build bem-sucedido; não afirme que release está pronto apenas porque `assemble` terminou.
- Builds podem falhar por ambiente, licenças, SDK ou rede; reporte causa observada e não altere configuração global sem autorização.

## Exemplo prático

Se o projeto contiver apenas o módulo `app` e a variante padrão `debug`, identifique pelo Gradle a tarefa correspondente antes de executá-la pelo `gradlew.bat`. Depois confira o APK realmente criado no diretório de outputs desse módulo. Para release, só prossiga com a configuração de assinatura existente e segura; se estiver ausente, reporte o pré-requisito em vez de criar credenciais.

## Critérios de conclusão

- Tarefa executada corresponde a módulo e variante verificados no repositório.
- Resultado do comando e disponibilidade do ambiente foram observados e reportados.
- APK informado existe e sua variante/assinatura não foi inferida pelo nome.
- Segredos permanecem fora do repositório e logs; nenhuma configuração de assinatura foi exposta.
- Testes e checks pertinentes são apresentados separadamente do build; limitações de distribuição permanecem explícitas.
