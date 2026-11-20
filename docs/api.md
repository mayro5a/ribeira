# API REST — Ribeira

- Prefixo: `/api/v1`. Documentação interativa (OpenAPI): `/swagger-ui.html`
  (desativada no perfil `prod`); especificação em `/v3/api-docs`.
- Autenticação: `Authorization: Bearer <token>` (exceto login, refresh e logout).
- Quantidades trafegam como **strings decimais** com 3 casas: `"12.500"`.
- Datas e horas em ISO 8601 UTC (`2026-11-20T13:45:00Z`); validade em `AAAA-MM-DD`.
- Listas paginadas: `?page=0&size=50&sort=campo,desc` (máximo 200 por página),
  resposta `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`.

## Erros (RFC 9457)

```json
{
  "type": "https://ribeira.app/problems/insufficient-stock",
  "title": "Conflict",
  "status": 409,
  "detail": "Saldo insuficiente no lote …: disponível 2.000, solicitado 3.000.",
  "code": "INSUFFICIENT_STOCK",
  "correlationId": "1f0c…"
}
```

Erros de validação incluem `errors: [{ "field", "message" }]`. O cabeçalho
`X-Correlation-Id` acompanha toda resposta.

| Status | Códigos frequentes |
|--------|--------------------|
| 400 | `VALIDATION_FAILED`, `BAD_REQUEST`, `INVALID_CURSOR` |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_ROTATED` |
| 403 | `ACCESS_DENIED`, `USER_INACTIVE`, `DEVICE_REVOKED`, `DEVICE_NOT_REGISTERED`, `CSRF_HEADER_MISSING` |
| 404 | `RESOURCE_NOT_FOUND` (inclui recursos de outra organização) |
| 409 | `INSUFFICIENT_STOCK`, `BATCH_BLOCKED`, `DUPLICATE_ID`, `*_IN_USE`, `CONCURRENT_MODIFICATION` |
| 422 | `ORIGIN_REQUIRED`, `UNIT_MISMATCH`, `INVALID_QUANTITY_PRECISION`, `BATCH_SPLIT_MISMATCH`, `FAMILY_INACTIVE`, `JUSTIFICATION_REQUIRED` |
| 429 | `TOO_MANY_LOGIN_ATTEMPTS` |

## Endpoints

| Área | Método e caminho | Permissão |
|------|------------------|-----------|
| Autenticação | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me` | — |
| Usuários | `GET/POST /users`, `GET/PUT /users/{id}`, `PUT /users/{id}/access`, `POST /users/{id}/password` | USER_MANAGE |
| Papéis | `GET /roles`, `POST /roles`, `PUT /roles/{id}` | USER_MANAGE (escrita) |
| Organização | `GET/PUT /organizations/{id}` | ORGANIZATION_MANAGE (escrita) |
| Comunidades | `GET/POST /communities`, `GET/PUT /communities/{id}`, `PUT /communities/{id}/primary-device` | COMMUNITY_READ / COMMUNITY_MANAGE / DEVICE_MANAGE |
| Famílias | `GET/POST /communities/{id}/families`, `GET/PUT /families/{id}` | FAMILY_READ / FAMILY_WRITE |
| Doadores | `GET/POST /donors`, `PUT /donors/{id}` | DONOR_MANAGE (escrita) |
| Alimentos | `GET/POST /foods`, `PUT /foods/{id}` | FOOD_MANAGE (escrita) |
| Doações | `POST /donations`, `GET /donations?communityId=`, `GET /donations/{id}` | DONATION_WRITE / STOCK_READ |
| Estoque | `GET /communities/{id}/inventory`, `GET/POST /inventory/batches`, `GET /inventory/batches/{id}`, `PUT /inventory/batches/{id}/status`, `POST/GET /inventory/movements`, `GET /inventory/low-stock`, `GET /inventory/expiring`, `GET /inventory/fefo`, `GET /inventory/reconciliation`, `PUT /communities/{id}/stock-minimums/{foodId}` | STOCK_READ / STOCK_WRITE / STOCK_ADJUST |
| Locais | `GET/POST /communities/{id}/storage-locations`, `PUT /storage-locations/{id}` | STOCK_WRITE (escrita) |
| Distribuições | `POST /distributions`, `GET /distributions?communityId=&familyId=`, `GET /distributions/{id}` | DISTRIBUTION_WRITE / STOCK_READ |
| Perdas | `POST /losses`, `GET /losses?communityId=`, `PUT /losses/{id}/status` | LOSS_WRITE / STOCK_READ |
| Monitoramento | `POST/GET /monitoring/readings`, `GET/POST /monitoring/conditions`, `PUT /monitoring/conditions/{id}` | MONITORING_WRITE / COMMUNITY_MANAGE |
| Alertas | `GET /alerts?communityId=&status=`, `POST /alerts/{id}/resolve`, `POST /alerts/evaluate?communityId=` | COMMUNITY_READ / ALERT_RESOLVE |
| Relatórios | `GET /reports/inventory`, `/reports/donations`, `/reports/distributions`, `/reports/losses`, `GET /dashboard/organization`, `GET /dashboard/communities/{id}` | REPORT_READ / STOCK_READ |
| Aparelhos | `POST /devices`, `GET /devices`, `POST /devices/{id}/revoke` | — / DEVICE_MANAGE |
| Sincronização | `POST /sync/push`, `GET /sync/pull?cursor=`, `GET /sync/status`, `GET /sync/conflicts`, `POST /sync/conflicts/{id}/resolution` | ver [synchronization.md](synchronization.md) |

## Exemplo: registrar doação

```http
POST /api/v1/donations
Authorization: Bearer eyJ…
Content-Type: application/json

{
  "communityId": "b1c2d3e4-0000-4000-8000-000000000001",
  "donationId": "6f2c0a7e-1d1b-4d0e-9b8f-0f6a1c2d3e4f",
  "originDescription": "Campanha fictícia de arrecadação",
  "receivedAt": "2026-11-20T13:00:00Z",
  "items": [{
    "itemId": "7a1b…", "foodId": "3c4d…", "quantity": "10.000", "unit": "KILOGRAM",
    "batches": [{ "batchId": "8b2c…", "quantity": "10.000",
                  "expirationStatus": "KNOWN", "expirationDate": "2027-01-31" }]
  }]
}
```

Resposta `201`: `{ "donation": { … }, "batches": [ { "id": "8b2c…", "availableQuantity": "10.000", … } ] }`.

## Exemplo: distribuição com saldo insuficiente

`POST /api/v1/distributions` com quantidade maior que o saldo retorna `409`
`INSUFFICIENT_STOCK` e **nada** é baixado de nenhum lote da distribuição.
