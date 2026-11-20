# Testes — Ribeira

Pirâmide orientada a risco: regras de estoque, isolamento e sincronização têm a
maior cobertura; metas artificiais de cobertura não são usadas.

## Como executar

```bash
# Backend: Spotless, compilação com -Werror, unitários (Surefire) e integração
# com PostgreSQL real via Testcontainers (Failsafe). Requer Docker.
cd ribeira-backend && ./mvnw verify

# Frontend: formatação, lint, tipos, unitários/componentes (Vitest + fake-indexeddb)
cd ribeira-frontend && npm run check && npm run build

# Ponta a ponta (navegador real, perfil Android) contra a stack completa
cp .env.example .env   # preencha os segredos e RIBEIRA_DEMO_DATA_ENABLED=true
docker compose up -d --build --wait
cd ribeira-frontend && npx playwright install chromium
E2E_DEMO_PASSWORD=<senha-demo> npx playwright test
```

## O que cada suíte cobre

| Suíte | Foco |
|-------|------|
| `FoodBatchTest`, `FefoPolicyTest` | Saldo nunca negativo, bloqueio, ajustes justificados, precisão, origem, validade, FEFO |
| `GlobalExceptionHandlerTest` | Problem Details, códigos estáveis, sem vazamento de detalhes internos |
| `AccessGrantsTest` | Composição de permissões organizacionais e por comunidade |
| `ArchitectureTest` (ArchUnit) | Sem ciclos entre módulos, `shared` independente, controllers sem repositórios, repositórios só no próprio módulo |
| `FoundationIT` | Migrações Flyway em base limpa, health público, correlation id |
| `AuthenticationIT` | Login, cookie seguro, rate limit, rotação e reuso do refresh, CSRF, expiração |
| `CommunityAuthorizationIT` | Isolamento entre comunidades e organizações, bloqueio imediato, papéis próprios |
| `InventoryIT` | Doação → lotes → livro-razão, reconciliação, duplicidade, trigger append-only, **concorrência real** (duas baixas de 4 sobre saldo 5) e baixa multi-lote atômica |
| `DistributionAndLossIT` | Critérios Dado/Quando/Então de distribuição e saldo insuficiente, famílias, perdas, alertas, relatórios sem dados pessoais |
| `SyncIT` | Reenvio idempotente, ID reutilizado, conflito preservado e resolvido, retry falho, rejeição, aparelho principal, revogação, cursor e permissões no pull |
| `quantity.test`, `projection.test` | Aritmética decimal sem ponto flutuante, projeção local de saldos |
| `state-machine.test`, `queue.test` | Máquina de estados, backoff, recuperação de `SENDING`, persistência após reabrir, transação IndexedDB |
| `engine.test` | Push/pull com servidor falso: resposta perdida após commit, falha de rede, conflito resolvido depois, 401, revogação, multiusuário |
| `DonationForm.test`, `connection-state.test`, `validation.test`, `pin.test` | Formulários acessíveis, indicador de conexão, validação de distribuição, PIN |
| `e2e/offline.spec.ts` | Cenários 1, 2 e 7: doação e distribuição offline, reabertura com PIN, saldo provisório, sincronização ao reconectar |

## Cenários críticos de aceitação (prompt §13)

| # | Cenário | Onde |
|---|---------|------|
| 1 | Doação offline, fechar/reabrir, sincronizar depois | `e2e/offline.spec.ts`, `queue.test` |
| 2 | Distribuição offline atualiza estoque local | `e2e/offline.spec.ts`, `projection.test` |
| 3 | Mesma operação duas vezes não duplica | `SyncIT.sameOperationTwiceIsAppliedOnce`, `engine.test` |
| 4 | Falha durante envio mantém a operação pendente | `engine.test` (rede e resposta perdida) |
| 5 | Operações concorrentes que excedem o saldo viram conflito | `SyncIT`, `InventoryIT` (concorrência) |
| 6 | Comunidade não autorizada → acesso negado | `CommunityAuthorizationIT`, `SyncIT` |
| 7 | Reconexão recebe alterações após o cursor | `SyncIT.pullDeliversChanges…`, `e2e/offline.spec.ts` |

## Limitações conhecidas dos testes

- O E2E cobre Chromium (perfil Pixel 5); outros navegadores não são testados.
- Não há teste automatizado de acessibilidade com axe; as consultas por papel e
  rótulo nos testes de componente servem como verificação básica.
- Limpeza do armazenamento pelo navegador é tratada por design (exportação,
  `storage.persist`), sem teste automatizado.
