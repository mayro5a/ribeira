# ADR-0003 — Feed de alterações e cursor de sincronização

- **Status:** aceita
- **Data:** 2026-11-20

## Contexto
O `pull` precisa entregar tudo que mudou desde o último cursor, sem lacunas
entre páginas. Sequências do PostgreSQL (`bigserial`) não garantem que um
número menor seja confirmado antes de um maior: uma transação lenta pode
confirmar o `seq` 10 depois que o cliente já leu o 11.

## Alternativas
1. `updated_at` como cursor — sujeito a relógio e empates.
2. `bigserial` + filtro pelo `xmin` do snapshot — complexo e ainda exige
   cuidado entre páginas.
3. **Contador por organização com bloqueio de linha no fim da transação.**

## Decisão
Opção 3. Os módulos registram alterações via `ChangeTracker`; o módulo
`sync` acumula as alterações da transação e, no `beforeCommit`, executa
`UPDATE sync_counters SET last_seq = last_seq + n ... RETURNING last_seq`
e grava as linhas em `change_log`. O bloqueio da linha do contador é o
último bloqueio adquirido e dura até o commit, então a ordem do `seq` é a
ordem de commit dentro da organização.

## Consequências
- `pull` simples: `seq > cursor ORDER BY seq LIMIT n`.
- Escritas da mesma organização serializam apenas no instante final do
  commit. Aceitável para o volume de uma organização social.
- Como o contador é sempre o último bloqueio, não forma ciclos de deadlock
  com os bloqueios de lotes.
