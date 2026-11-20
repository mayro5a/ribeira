# ADR-0007 — Estrutura interna dos módulos do backend

- **Status:** aceita
- **Data:** 2026-11-20

## Contexto
O prompt sugere `api/application/domain/infrastructure` como referência,
sem exigir quatro camadas para todo cadastro simples.

## Decisão
- `api`, `application` e `domain` nos módulos de negócio.
- Repositórios Spring Data ficam em `domain`: são interfaces declarativas,
  e criar portas + adaptadores para cada uma seria abstração sem variação real.
- `infrastructure` apenas quando houver integração técnica (ex.: tokens JWT).
- Regras de domínio relevantes (quantidades, FEFO, estados do lote) ficam
  em classes de domínio sem dependência de HTTP.

## Consequências
- Menos arquivos e indireções; dependência de Spring Data no domínio é aceita.
- ArchUnit garante que `api` não usa repositórios e que módulos não usam
  repositórios uns dos outros.
