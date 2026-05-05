package org.sqlite2j.sql.ast;

public final class ComparisonExpression implements Expression {
  private final Expression left;
  private final ComparisonOperator operator;
  private final Expression right;

  public ComparisonExpression(Expression left, ComparisonOperator operator, Expression right) {
    this.left = left;
    this.operator = operator;
    this.right = right;
  }

  public Expression getLeft() {
    return left;
  }

  public ComparisonOperator getOperator() {
    return operator;
  }

  public Expression getRight() {
    return right;
  }
}
