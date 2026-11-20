# Banco de dados — Ribeira

PostgreSQL 17, esquema versionado exclusivamente por Flyway
(`ribeira-backend/src/main/resources/db/migration`). `ddl-auto` fica em
`validate` em todos os perfis.

Diagrama ER: [diagrams/er-diagram.mmd](diagrams/er-diagram.mmd).

## 1. Convenções

- Chave primária `UUID` em todas as entidades que podem nascer offline
  (gerada no aparelho) e também nas demais, por uniformidade.
- Colunas em `snake_case`, nomes em inglês.
- Datas/horas em `timestamptz` (UTC no servidor; a interface exibe horário
  local). Datas sem horário (validade) em `date`.
- Quantidades em `numeric(14,3)` mapeadas para `BigDecimal`.
- `organization_id` em toda tabela de negócio para isolamento entre
  organizações (filtro obrigatório em consultas).
- Cadastros usam exclusão lógica (`active` ou `status`); movimentações de
  estoque são imutáveis (trigger bloqueia `UPDATE` e `DELETE`).
- `created_at`/`updated_at` e autor (`created_by`, `performed_by` …) onde
  aplicável; `version` para controle otimista nas entidades editáveis.

## 2. Relacionamentos e cardinalidades

### Organização e acesso

| Relação | Cardinalidade | Observação |
|---------|---------------|------------|
| Organização → Comunidade | 1:N | Comunidade pertence a uma única organização |
| Organização → Usuário | 1:N | Usuário pertence a uma organização |
| Papel → Permissão | 1:N | `role_permissions`; papéis de sistema têm `organization_id` nulo |
| Usuário ↔ Papel (organização) | N:M | `user_roles`: vale para todas as comunidades da organização |
| Usuário ↔ Comunidade | N:M | `community_users` com **um papel por comunidade** |
| Comunidade → Aparelho principal | 0..1 | `communities.primary_device_id` |

Permissões efetivas de um usuário em uma comunidade = permissões dos papéis
organizacionais ∪ permissões do papel naquela comunidade.

### Doações e estoque

| Relação | Cardinalidade | Observação |
|---------|---------------|------------|
| Doador → Doação | 0..1:N | Doação sem doador exige `origin_description` |
| Doação → Item | 1:N (mín. 1) | |
| Item → Lote | 1:N | Soma das quantidades dos lotes = quantidade do item |
| Lote → Movimentação | 1:N (mín. 1) | A primeira é sempre `ENTRY` |
| Lote sem item de doação | — | Exige `origin_description` (RN03) |
| Comunidade × Alimento → Estoque mínimo | 0..1 | `stock_minimums` |

**Saldo materializado:** `food_batches.available_quantity` é atualizado na
mesma transação da movimentação e deve sempre ser igual à soma de
`stock_movements.quantity_delta` do lote. A consulta de reconciliação
`GET /api/v1/inventory/reconciliation` lista divergências (esperado: vazio).

### Distribuição e perdas

| Relação | Cardinalidade |
|---------|---------------|
| Distribuição → Item (lote, quantidade) | 1:N (mín. 1) |
| Distribuição ↔ Família | N:M (`distribution_families`, mín. 1) |
| Lote → Perda | 1:N |

Cada item de distribuição e cada perda geram exatamente uma movimentação
(`source_type` + `source_id` apontam para a origem).

### Monitoramento

| Relação | Cardinalidade |
|---------|---------------|
| Local de armazenamento → Leitura | 1:N |
| Organização → Condição de armazenamento | 1:N; aplica-se a um local **ou** a uma categoria de alimento |
| Comunidade → Alerta | 1:N; no máximo um alerta `OPEN` por `dedup_key` (índice único parcial) |

### Sincronização e auditoria

| Tabela | Papel |
|--------|-------|
| `devices` | Aparelhos registrados; `status` `ACTIVE`/`REVOKED` |
| `sync_operations` | Uma linha por `operation_id` (PK): payload, hash, estado, `server_seq` |
| `sync_conflicts` | Revisão humana de operações `CONFLICT`/`REJECTED` (1:1 com a operação) |
| `sync_conflict_events` | Histórico de tentativas e decisões |
| `sync_counters` | Contador por organização para o feed (ver ADR-0003) |
| `change_log` | Feed de alterações `(organization_id, seq)` |
| `sync_checkpoints` | Último cursor confirmado por aparelho |
| `audit_logs` | Quem fez o quê e quando, sem dados pessoais |

## 3. Índices principais

- `food_batches (community_id, food_id)`, `(community_id, expiration_date)`
  parcial para `status = 'AVAILABLE'` (FEFO e vencimentos).
- `stock_movements (batch_id, occurred_at)`, `(community_id, occurred_at)`.
- `change_log (organization_id, seq)` (PK).
- `families (community_id, code)` único.
- `alerts (dedup_key) WHERE status = 'OPEN'` único.

## 4. Retenção (a validar)

| Dado | Proposta MVP |
|------|--------------|
| Movimentações, doações, distribuições, perdas | Indefinida (rastreabilidade) |
| Famílias inativas | Revisar com a organização; sem dados nominais no MVP |
| `audit_logs`, `sync_operations` | Indefinida no MVP; definir prazo com responsável jurídico |
| Tokens de renovação expirados | Removidos por rotina diária |
