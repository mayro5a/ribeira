# ADR-0001 — Monólito modular

- **Status:** aceita
- **Data:** 2026-11-20

## Contexto
Equipe pequena, domínio ainda em validação, operação com recursos
limitados. Regras de estoque exigem transações que atravessam doação,
lote e movimentação.

## Alternativas
1. Microsserviços por domínio — consistência distribuída, custo operacional alto.
2. Monólito em camadas sem módulos — acoplamento crescente entre domínios.
3. **Monólito modular** com fronteiras explícitas por pacote.

## Decisão
Opção 3. Um único artefato Spring Boot, com módulos de negócio que se
comunicam por serviços de aplicação e referências por UUID. Fronteiras
verificadas com ArchUnit.

## Consequências
- Transações ACID locais para regras de estoque.
- Extração futura de um módulo é possível, mas não é objetivo.
- Exige disciplina: nenhum módulo acessa repositório de outro.
