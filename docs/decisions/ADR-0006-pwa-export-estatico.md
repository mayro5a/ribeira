# ADR-0006 — PWA com export estático e Service Worker próprio

- **Status:** aceita
- **Data:** 2026-11-20

## Contexto
O app precisa abrir sem conexão em qualquer rota. Renderização no servidor
não ajuda offline e aumenta o custo de hospedagem. Plugins de PWA para
Next.js têm compatibilidade irregular entre versões.

## Decisão
- `output: "export"`: o frontend é um conjunto de arquivos estáticos.
  Rotas com parâmetro usam query string (`/distribuicoes/detalhe?id=`).
- Service Worker escrito à mão, gerado após o build por
  `scripts/generate-sw.mjs`, que lista os arquivos de `out/` e define a
  versão do cache pelo hash do conteúdo.
- Estratégia: **precache** de todo o app; navegação `network-first` com
  fallback para o cache; estáticos com hash `cache-first`.
  **Requisições `/api/` nunca passam pelo cache** do Service Worker — dados
  autenticados ficam apenas no IndexedDB, por usuário.
- Atualização: a nova versão instala em segundo plano; o app oferece
  "Atualizar agora" (`SKIP_WAITING`), e caches antigos são removidos no
  `activate`.

## Consequências
- Sem dependência de plugin; comportamento de cache explícito e testável.
- Não há rotas dinâmicas nem API routes do Next.js.
