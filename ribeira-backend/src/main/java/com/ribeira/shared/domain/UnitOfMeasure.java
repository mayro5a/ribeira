package com.ribeira.shared.domain;

import com.ribeira.shared.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Units accepted in the MVP. There is deliberately no conversion between them: quantities keep the
 * unit of the food they refer to (docs/requirements.md, item to validate with communities).
 */
public enum UnitOfMeasure {
  KILOGRAM(false),
  LITER(false),
  UNIT(true),
  PACKAGE(true);

  /** Matches {@code numeric(14,3)}. */
  public static final int SCALE = 3;

  private static final BigDecimal MAX = new BigDecimal("99999999999.999");

  private final boolean wholeNumbersOnly;

  UnitOfMeasure(boolean wholeNumbersOnly) {
    this.wholeNumbersOnly = wholeNumbersOnly;
  }

  /**
   * Validates a positive quantity for this unit and returns it with the canonical scale. Values
   * with more than three decimal places are rejected rather than rounded (RN11).
   */
  public BigDecimal requirePositive(BigDecimal quantity) {
    if (quantity == null || quantity.signum() <= 0) {
      throw new BusinessRuleException("INVALID_QUANTITY", "A quantidade deve ser maior que zero.");
    }
    return requireValidMagnitude(quantity);
  }

  /** Same as {@link #requirePositive} but accepts negative values (adjustments). */
  public BigDecimal requireNonZero(BigDecimal quantity) {
    if (quantity == null || quantity.signum() == 0) {
      throw new BusinessRuleException("INVALID_QUANTITY", "A quantidade não pode ser zero.");
    }
    return requireValidMagnitude(quantity);
  }

  private BigDecimal requireValidMagnitude(BigDecimal quantity) {
    BigDecimal stripped = quantity.stripTrailingZeros();
    if (stripped.scale() > SCALE) {
      throw new BusinessRuleException(
          "INVALID_QUANTITY_PRECISION", "Use no máximo 3 casas decimais.");
    }
    if (wholeNumbersOnly && stripped.scale() > 0) {
      throw new BusinessRuleException(
          "INVALID_QUANTITY_PRECISION", "Esta unidade aceita apenas números inteiros.");
    }
    if (quantity.abs().compareTo(MAX) > 0) {
      throw new BusinessRuleException("INVALID_QUANTITY", "Quantidade grande demais.");
    }
    return quantity.setScale(SCALE, RoundingMode.UNNECESSARY);
  }

  public void requireSameAs(UnitOfMeasure expected) {
    if (this != expected) {
      throw new BusinessRuleException(
          "UNIT_MISMATCH",
          "Unidade "
              + name()
              + " diferente da unidade do alimento ("
              + expected.name()
              + "). Conversões não são feitas automaticamente.");
    }
  }
}
