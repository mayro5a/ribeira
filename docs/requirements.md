# Requisitos — Ribeira

Este documento consolida os requisitos do MVP. Itens marcados como
**Hipótese** ainda não foram validados com representantes comunitários e
devem ser revisados antes de uso real.

## 1. Problema

Organizações que recebem doações de alimentos e as distribuem para várias
comunidades ribeirinhas do Amazonas precisam saber o que receberam, onde
está cada lote, o que foi entregue a cada família e o que se perdeu — muitas
vezes sem internet nos locais de armazenamento e entrega.

## 2. Atores

| Ator | Responsabilidades principais |
|------|------------------------------|
| Administrador da organização | Organização, comunidades, usuários, permissões, indicadores consolidados |
| Coordenador comunitário | Acompanha estoque, doações, distribuições e perdas das comunidades autorizadas |
| Responsável pelo estoque | Recebimentos, lotes, movimentações, perdas |
| Distribuidor | Registra entregas às famílias |
| Visualizador | Consulta relatórios permitidos, sem alterações |

Os papéis são conjuntos de permissões. A organização pode criar papéis
próprios combinando permissões (ver [security.md](security.md)).

## 3. Requisitos funcionais

### RF01 — Autenticação e usuários
- Login com e-mail e senha; logout; renovação de sessão.
- Cadastro, bloqueio e desativação de usuários pela organização.
- Associação de usuários a comunidades com um papel por comunidade.
- Recuperação de acesso por e-mail **somente** quando houver serviço de
  e-mail configurado (fora do MVP; administrador redefine a senha).

### RF02 — Organizações e comunidades
- Uma organização administra várias comunidades.
- Comunidade: nome, descrição, município, localização aproximada opcional.
- Nunca exigir endereço residencial nem coordenadas de moradores.

### RF03 — Famílias beneficiadas
- Código comunitário da família (no lugar de nome), comunidade,
  quantidade de integrantes, situação de atendimento, histórico.
- **Hipótese:** o código comunitário é suficiente para identificar a
  família na entrega; nomes não são armazenados no MVP.

### RF04 — Doadores e doações
- Doadores individuais ou organizações.
- Doação com comunidade destinatária, data, itens, quantidades, unidade,
  observações e origem identificável (doador cadastrado ou descrição da
  origem).
- Cada item gera um ou mais lotes rastreáveis.

### RF05 — Alimentos
- Nome, categoria, unidade de medida, descrição, orientações de
  armazenamento, dias de antecedência para aviso de validade, ativo/inativo.
- Validade: `KNOWN` (data informada), `NOT_APPLICABLE` (validade
  indefinida) ou `UNKNOWN` (sem informação). Ausência de informação e
  validade indefinida são estados distintos.

### RF06 — Lotes e estoque
- Lote: alimento, comunidade, local, origem, quantidade inicial e
  disponível, unidade, recebimento, validade, observações, estado
  (`AVAILABLE`, `BLOCKED`, `DEPLETED`).
- Movimentações: `ENTRY`, `DISTRIBUTION`, `LOSS`, `ADJUSTMENT`.
  `TRANSFER` fica fora do MVP.
- FEFO como **sugestão** de ordenação, considerando apenas lotes com
  validade conhecida.

### RF07 — Distribuição
- Comunidade, famílias atendidas, lotes e quantidades, responsável, data,
  observações, histórico e comprovante (resumo imprimível).

### RF08 — Perdas
- Lote, quantidade, data, motivo, descrição, responsável e situação.
- Motivos: umidade, calor/exposição solar, embalagem danificada,
  vencimento, pragas, transporte, contaminação suspeita, outro, não
  identificado.
- O sistema nunca declara um alimento impróprio para consumo; apenas
  registra e recomenda inspeção por pessoa responsável.

### RF09 — Monitoramento e alertas
- Locais de armazenamento, leituras manuais de temperatura e umidade,
  registro de inspeção.
- Condições de armazenamento configuráveis por local ou por categoria de
  alimento. Não existe limite universal.
- Regras explícitas de alerta: validade próxima, estoque abaixo do
  mínimo, aumento de perdas, leitura fora do intervalo, inspeção atrasada.
- Resolução de alertas com registro de inspeção.
- **Hipótese:** os parâmetros padrão de alerta (janela de 30 dias para
  perdas, fator 2×) são pontos de partida e devem ser ajustados com
  especialistas em conservação.

### RF10 — Dashboards e relatórios
- Dashboard da organização (online) e da comunidade (online e offline,
  a partir dos dados locais).
- Relatórios de estoque, doações, distribuições e perdas sem dados
  pessoais.

### RF11 — Offline e sincronização
- Ver [synchronization.md](synchronization.md).

## 4. Regras de negócio obrigatórias

| # | Regra | Onde é garantida |
|---|-------|------------------|
| RN01 | Quantidade nunca negativa | Domínio + `CHECK` no banco |
| RN02 | Distribuição não excede saldo validado | `InventoryService` com bloqueio `PESSIMISTIC_WRITE` |
| RN03 | Toda entrada tem origem identificável | Domínio + `CHECK` (doação ou descrição de origem) |
| RN04 | Toda saída gera movimentação | Saldo só muda via `InventoryService` |
| RN05 | Toda perda vinculada a um lote | `NOT NULL` + FK |
| RN06 | Toda operação registra responsável e data | Colunas obrigatórias + auditoria |
| RN07 | Sem acesso a comunidades não autorizadas | `AccessControl` em todos os casos de uso |
| RN08 | Mesmo `operationId` não é processado duas vezes | PK de `sync_operations` + hash do payload |
| RN09 | Movimentações nunca são apagadas | Sem endpoint de exclusão + trigger no banco |
| RN10 | Ajuste exige justificativa | Domínio + `CHECK` |
| RN11 | Unidade e precisão validadas | `Quantity` / `UnitOfMeasure` |
| RN12 | Movimentação e saldo na mesma transação | Fronteira `@Transactional` na aplicação |
| RN13 | Operações rejeitadas/conflitantes preservadas | `sync_operations` + `sync_conflicts` |
| RN14 | FEFO só com validade conhecida | `FefoPolicy` |
| RN15 | Alertas ambientais com parâmetros configuráveis | `storage_conditions` |

## 5. Requisitos não funcionais

- Celulares Android de entrada: telas a partir de 360 px, pouca memória,
  conexão intermitente; interface em português do Brasil.
- Acessibilidade com objetivo WCAG 2.2 AA.
- Privacidade (LGPD): minimização de dados; famílias identificadas por
  código; nenhum dado pessoal em dashboards ou logs.
- Integridade: o servidor é a fonte de verdade de saldos e autorização.

## 6. Decisões que dependem de validação com usuários reais

1. Se o código comunitário da família é suficiente na entrega (RF03).
2. Quais unidades de medida são usadas em campo (`KILOGRAM`, `LITER`,
   `UNIT`, `PACKAGE` no MVP) e se há necessidade de conversões.
3. Se haverá um único aparelho por comunidade alterando estoque
   (estratégia conservadora do MVP) ou vários.
4. Por quanto tempo um aparelho pode operar offline sem revalidar a sessão
   (padrão: 7 dias, configurável).
5. Parâmetros de conservação por categoria de alimento — devem vir de
   orientação técnica adequada.
6. Prazos de retenção de dados de famílias e de auditoria.
