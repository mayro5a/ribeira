# Segurança e privacidade — Ribeira

Referências de revisão: OWASP ASVS (nível 1–2 nos controles aplicáveis ao MVP)
e OWASP Top 10. Este documento **não** declara conformidade com a LGPD: os
pontos jurídicos estão listados para validação com responsável competente.

## 1. Autenticação e sessão

Ver [ADR-0005](decisions/ADR-0005-autenticacao.md).

| Controle | Implementação |
|----------|---------------|
| Senhas | BCrypt (`DelegatingPasswordEncoder`), mínimo de 10 caracteres |
| Token de acesso | JWT HS256, 15 min, só em memória no navegador; emissor e expiração validados |
| Renovação | Cookie `HttpOnly`, `SameSite=Strict`, `Secure` em produção, caminho `/api/v1/auth`; valor guardado só como SHA-256; rotação a cada uso; reuso revoga a cadeia |
| CSRF | APIs usam `Authorization: Bearer` (sem cookie); `refresh`/`logout` exigem `X-Requested-With` |
| Força bruta | 5 falhas por (IP, e-mail) em 15 min → `429` (limite em memória, por instância) |
| Enumeração de usuários | Mesma mensagem e mesmo custo de BCrypt para e-mail inexistente e senha errada |
| Bloqueio de usuário | Efeito imediato: permissões são carregadas a cada requisição; sessões de renovação revogadas |
| Segredo JWT | Somente por variável de ambiente; aplicação não sobe sem segredo de ≥ 32 bytes |

## 2. Autorização

Toda regra fica no backend (`AccessControl`). O `communityId` enviado pelo
cliente nunca é confiado: cada caso de uso usa a comunidade real do recurso.
Recursos de outra organização respondem `404` (indistinguíveis de inexistentes);
comunidades da própria organização sem permissão respondem `403`.

### 2.1 Matriz de permissões dos papéis padrão

Papéis organizacionais valem para todas as comunidades da organização; papéis
de comunidade valem só nela. A organização pode criar papéis próprios
(`POST /api/v1/roles`).

| Permissão | Admin | Coordenação | Estoque | Distribuição | Visualização |
|-----------|:-----:|:-----------:|:-------:|:------------:|:------------:|
| ORGANIZATION_MANAGE, COMMUNITY_MANAGE, USER_MANAGE, DEVICE_MANAGE, FOOD_MANAGE | ✔ | | | | |
| COMMUNITY_READ | ✔ | ✔ | ✔ | ✔ | ✔ |
| FAMILY_READ | ✔ | ✔ | | ✔ | |
| FAMILY_WRITE | ✔ | ✔ | | | |
| DONOR_MANAGE | ✔ | | ✔ | | |
| DONATION_WRITE, STOCK_WRITE, STOCK_ADJUST, LOSS_WRITE | ✔ | | ✔ | | |
| STOCK_READ | ✔ | ✔ | ✔ | ✔ | ✔ |
| DISTRIBUTION_WRITE | ✔ | | | ✔ | |
| MONITORING_WRITE | ✔ | ✔ | ✔ | | |
| ALERT_RESOLVE, SYNC_CONFLICT_RESOLVE | ✔ | ✔ | | | |
| REPORT_READ | ✔ | ✔ | | | ✔ |

Testes positivos e negativos: `CommunityAuthorizationIT`, `InventoryIT`,
`DistributionAndLossIT`, `SyncIT`.

## 3. Privacidade (LGPD — minimização)

- Famílias: apenas código comunitário, número de integrantes, situação e
  observação curta. **Não há campos de nome, documento, endereço ou contato.**
- Doadores: nome e tipo; sem contatos.
- Comunidades: localização aproximada opcional; nunca coordenadas de moradores.
- Relatórios e dashboards retornam apenas agregados (teste garante que códigos
  de famílias não aparecem).
- Auditoria registra quem, o quê e quando, com detalhes não pessoais; corpos de
  requisição não são registrados.
- No aparelho, famílias só são baixadas por quem tem `FAMILY_READ`.

### Pontos para validação jurídica

1. Base legal para o tratamento de dados de famílias beneficiadas.
2. Prazo de retenção de famílias inativas, auditoria e `sync_operations`.
3. Procedimento de atendimento a direitos dos titulares (acesso, correção, eliminação).
4. Encarregado (DPO) e registro das operações de tratamento.

## 4. Dados no aparelho

- IndexedDB **não é criptografado** pelo navegador. Por isso o aparelho guarda
  só o necessário: catálogos, lotes, histórico operacional e códigos de família.
- Sem senha nem token no IndexedDB ou `localStorage`.
- PIN local (PBKDF2-SHA256, 210 mil iterações, sal aleatório) protege a interface
  ao reabrir; 5 erros encerram a sessão local.
- Sessão offline expira 7 dias após o último contato com o servidor.
- Troca de usuário no aparelho apaga os snapshots do usuário anterior.
- Revogação: na próxima conexão, os snapshots são apagados e só a fila fica,
  exportável em arquivo.
- O Service Worker nunca armazena respostas de `/api/`.

## 5. Modelo de ameaças simplificado

| Ameaça | Impacto | Mitigação | Risco residual |
|--------|---------|-----------|----------------|
| Roubo/perda do aparelho | Acesso a dados baixados | PIN, sessão offline limitada, revogação, minimização de dados | Leitura do IndexedDB por quem extrair o armazenamento do navegador; revogação só ao conectar |
| Perda de dados locais (limpeza do navegador, aparelho quebrado) | Operações não enviadas perdidas | Sincronização frequente, `navigator.storage.persist()`, exportação da fila | Operações nunca sincronizadas nem exportadas |
| Vazamento entre organizações | Dados de outra ONG expostos | `organization_id` em todas as tabelas, filtro em todas as consultas, 404 para outros tenants | — |
| Manipulação de operações offline | Saldos incorretos, ações sem permissão | Servidor revalida formato, permissões e regras; aparelho principal opcional | Usuário autorizado registrando fatos falsos (mitigado por auditoria) |
| Replay de operações | Baixa duplicada | `operation_id` único + hash do payload | — |
| Roubo de cookie de renovação | Sessão sequestrada | `HttpOnly`, `SameSite=Strict`, rotação e detecção de reuso | Janela até a próxima rotação |
| XSS | Roubo do token em memória | React escapa conteúdo, CSP sem origens externas | `unsafe-inline` exigido pelo export do Next.js |

## 6. Operação segura

- CORS restrito a `RIBEIRA_CORS_ALLOWED_ORIGINS`; em produção a mesma origem via nginx dispensa CORS.
- Cabeçalhos: CSP, `X-Frame-Options: DENY`, `X-Content-Type-Options`, `Referrer-Policy`.
- Actuator (health/métricas) na porta interna 8081, não publicada pelo proxy.
- Swagger UI desativado no perfil `prod`.
- Logs estruturados (ECS) em produção com `correlationId`; sem senhas, tokens ou dados pessoais.

## 7. Backup e restauração (antes de produção)

```bash
# Backup
docker compose exec postgres pg_dump -U ribeira -Fc ribeira > ribeira-$(date +%F).dump
# Restauração em banco vazio
docker compose exec -T postgres pg_restore -U ribeira -d ribeira --clean < ribeira-AAAA-MM-DD.dump
```

Um teste de restauração documentado é pré-requisito para uso real.
