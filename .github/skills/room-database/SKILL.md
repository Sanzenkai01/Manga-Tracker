---
name: room-database
description: Modele, consulte ou migre o banco Room local do Manga Tracker, incluindo entidades, DAOs, relações, índices e histórico de leitura, preservando registros existentes e testando migrações.
---

# Room e persistência local

## Objetivo

Manter dados de mangás e histórico de leitura persistentes, íntegros e disponíveis offline com Room. Projetar esquema e consultas claros, evolutivos e cobertos por testes, sem apagar dados em mudanças de versão.

## Quando usar

- Ao criar ou ajustar entidade, DAO, banco, repositório ou consulta.
- Ao adicionar relações, índices ou regras de integridade.
- Ao alterar número de capítulo, progresso ou histórico.
- Ao atualizar esquema e registrar uma migração Room.

## Conhecimentos técnicos

- **Entidades:** campos persistidos com identidade estável, nulabilidade e conversores explícitos quando necessários.
- **DAOs:** consultas SQL/Room focadas, observáveis por `Flow` e operações de escrita assíncronas.
- **Relações:** chaves estrangeiras e tabelas de junção para relações reais; evite duplicar listas em colunas textuais sem justificativa.
- **Índices:** aceleram filtros/uniqueness à custa de espaço e escrita; escolha após analisar padrões de consulta e cardinalidade.
- **Migrations:** sequência explícita de versões, registrada no builder e compatível com todos os esquemas suportados pelo app.
- **Histórico de leitura:** distinguir estado atual do capítulo e eventos históricos quando o produto exige ambos; usar timestamp e identificadores com semântica documentada.
- **Repositórios:** interface de acesso usada pelas camadas superiores; UI não consulta DAO diretamente.

## Procedimento

1. Leia entidades, DAOs, configuração do `RoomDatabase`, repositórios, versão/exportações do esquema e testes. Identifique dados e consultas atuais antes de propor alteração.
2. Descreva o caso de uso e as consultas necessárias. Defina identidade, nulabilidade, integridade e cardinalidade das relações.
3. Projete a alteração compatível com dados já gravados. Avalie índices conforme filtros, ordenações, joins e unicidade realmente usados.
4. Atualize os modelos e DAOs; mantenha consultas observáveis quando a UI precisa refletir mudanças e use transações para operações que devem ser atômicas.
5. Incremente a versão do banco e escreva a migração que transforma cada versão de origem suportada para o novo esquema. Registre-a no builder.
6. Use testes instrumentados/in-memory conforme ferramentas existentes para validar entidades, consultas, relações, constraints e migrações.
7. Execute testes pertinentes e build. Inspecione o esquema final e atualize documentação apenas para comportamento implementado.

## Cuidados importantes

- Nunca habilite fallback de migração destrutiva para contornar falha; isso pode apagar dados do usuário.
- Não remova coluna, tabela ou histórico sem autorização explícita e plano de preservação/transformação aprovado.
- Atualize e revise arquivos de esquema exportados se o projeto os usa.
- Defina política de chaves estrangeiras e exclusão com cuidado; cascade delete pode apagar histórico associado.
- Evite índices especulativos ou redundantes. Verifique se a ordem de colunas do índice corresponde aos filtros/ordenação da consulta.
- Use tipos e conversores previsíveis para data/hora e número de capítulo; não confunda representação textual com ordenação numérica.
- Não execute consultas bloqueantes na main thread, nem exponha DAO diretamente à UI.

## Exemplo prático

Para registrar progresso por capítulo, determine primeiro se um registro atual por mangá/capítulo é suficiente ou se o requisito pede eventos históricos de leitura. Defina chave estável, restrição de unicidade e consulta do último evento. Ao migrar de um campo de progresso único, converta os valores existentes explicitamente e teste que nenhum mangá perde progresso.

## Critérios de conclusão

- Modelo representa o requisito sem duplicação desnecessária e mantém consultas pela camada de repositório.
- Relações, índices e comportamento de exclusão são intencionais e testados.
- Migração é registrada, transforma dados existentes e não usa fallback destrutivo.
- Testes cobrem caminho novo, restrições importantes e migrações relevantes.
- Testes e build foram executados ou o bloqueio foi reportado com clareza.
