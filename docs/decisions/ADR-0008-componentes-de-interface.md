# ADR-0008 — Biblioteca de componentes de interface

- **Status:** aceita
- **Data:** 2026-11-20

## Contexto
O público usa celulares Android de entrada. O prompt pede uma biblioteca
de componentes acessível e consistente.

## Alternativas
1. Biblioteca completa (MUI, Chakra) — bundle grande para aparelhos modestos.
2. Primitivas headless (Radix) — boas, mas vários pacotes para componentes
   que o HTML já entrega acessíveis.
3. **Biblioteca interna** em `src/components/ui` sobre elementos HTML
   nativos (`button`, `select`, `input`, `dialog`) + Tailwind CSS.

## Decisão
Opção 3. Os componentes padronizam rótulos, mensagens de erro ligadas por
`aria-describedby`, foco visível, alvos de toque de pelo menos 48 px e
contraste AA.

## Consequências
- Bundle pequeno e sem dependências de UI de terceiros.
- A acessibilidade fica sob nossa responsabilidade: os testes com Testing
  Library usam consultas por papel e rótulo.
