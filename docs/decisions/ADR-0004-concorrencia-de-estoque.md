# ADR-0004 — Concorrência na baixa de estoque

- **Status:** aceita
- **Data:** 2026-11-20

## Contexto
Distribuições e perdas simultâneas (REST e sincronização) podem disputar o
mesmo lote. Saldo negativo é inaceitável (RN01, RN02).

## Alternativas
1. Controle otimista com `@Version` — exige retry em cada chamador; no push
   de sincronização geraria conflitos falsos.
2. `UPDATE ... SET available = available - q WHERE available >= q` — atômico,
   mas espalha a regra em SQL e dificulta registrar `balance_after`.
3. **Bloqueio pessimista** (`SELECT ... FOR UPDATE`, `PESSIMISTIC_WRITE`).

## Decisão
Opção 3, com lotes bloqueados em ordem crescente de ID para evitar
deadlocks em distribuições com vários lotes. `CHECK (available_quantity >= 0)`
e `@Version` permanecem como defesas adicionais. Comprovado por teste de
integração concorrente com PostgreSQL real (Testcontainers).

## Consequências
- Transações de baixa devem ser curtas (sem chamadas remotas).
- Operações concorrentes no mesmo lote serializam.
