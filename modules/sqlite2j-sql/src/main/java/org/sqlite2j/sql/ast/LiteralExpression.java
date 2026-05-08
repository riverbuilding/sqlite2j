package org.sqlite2j.sql.ast;

public final class LiteralExpression implements Expression {
  private final LiteralValue literalValue;

  public LiteralExpression(LiteralValue literalValue) {
    this.literalValue = literalValue;
  }

  public LiteralValue getLiteralValue() {
    return literalValue;
  }
}
