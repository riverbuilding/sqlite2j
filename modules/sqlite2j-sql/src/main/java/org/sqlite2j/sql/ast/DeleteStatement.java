package org.sqlite2j.sql.ast;

public final class DeleteStatement implements Statement {
  private final String tableName;
  private final Expression whereExpression;

  public DeleteStatement(String tableName, Expression whereExpression) {
    this.tableName = tableName;
    this.whereExpression = whereExpression;
  }

  public String getTableName() {
    return tableName;
  }

  public Expression getWhereExpression() {
    return whereExpression;
  }
}
