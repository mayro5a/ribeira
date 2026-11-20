# ADR-0005 — Autenticação, renovação e sessão offline

- **Status:** aceita
- **Data:** 2026-11-20

## Contexto
O app precisa de autenticação segura no navegador e continuar operando
offline após o login inicial, sem guardar segredos de longa duração em
`localStorage`.

## Decisão
- **Token de acesso:** JWT HS256 de 15 minutos, mantido apenas em memória e
  enviado em `Authorization: Bearer`. Sem cookie de acesso, as APIs não são
  alvo de CSRF.
- **Token de renovação:** valor aleatório opaco (256 bits) em cookie
  `HttpOnly`, `SameSite=Strict`, `Secure` em produção, caminho
  `/api/v1/auth`. Armazenado no banco apenas como hash SHA-256, com rotação
  a cada uso e detecção de reuso (reuso revoga todos os tokens da cadeia).
  `POST /auth/refresh` exige o cabeçalho `X-Requested-With`, o que força
  preflight CORS e bloqueia CSRF entre origens.
- **Permissões** não vão no token: são carregadas por requisição, então
  bloqueio de usuário e mudança de papel valem imediatamente.
- **Sessão offline:** após o login, o app guarda no IndexedDB o perfil e as
  permissões (sem senha nem token) e exige um **PIN local** (hash PBKDF2 via
  WebCrypto) para desbloquear ao reabrir. A sessão offline expira 7 dias
  após o último contato bem-sucedido com o servidor.
- Senhas com BCrypt via `DelegatingPasswordEncoder`.

## Consequências
- Ao reabrir online, o app chama `/auth/refresh` para obter novo token.
- Perda do aparelho: o PIN protege a interface, mas IndexedDB **não é
  criptografado**; por isso famílias são armazenadas apenas por código.
  Ver [security.md](../security.md).
