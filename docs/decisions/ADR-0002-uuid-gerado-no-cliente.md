# ADR-0002 — UUIDs gerados no aparelho

- **Status:** aceita
- **Data:** 2026-11-20

## Contexto
Doações, lotes, distribuições e perdas são criados offline e referenciados
por operações seguintes antes de qualquer contato com o servidor.

## Alternativas
1. IDs sequenciais do servidor com mapeamento de IDs temporários — complexo
   e frágil em reenvios.
2. **UUID v4 gerado no aparelho** (`crypto.randomUUID()`).

## Decisão
Opção 2 para todas as entidades criadas pelo aparelho. O servidor aceita o
ID enviado e rejeita colisão com outra entidade (`409` via REST, `REJECTED`
via sincronização). Cadastros administrativos online usam UUID gerado pelo
servidor.

## Consequências
- Operações posteriores referenciam IDs locais sem tradução.
- Índices B-tree com UUID aleatório são um pouco maiores; aceitável no volume previsto.
