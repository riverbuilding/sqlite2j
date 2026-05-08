package org.sqlite2j.sql.ast;

public final class SelectAllStatement implements Statement {
  private final String tableName;
  private final Expression whereExpression;
  private final OrderByClause orderByClause;

  public SelectAllStatement(String tableName) {
    this(tableName, null, null);
  }

  public SelectAllStatement(String tableName, Expression whereExpression, OrderByClause orderByClause) {
    this.tableName = tableName;
    this.whereExpression = whereExpression;
    this.orderByClause = orderByClause;
  }

  public String getTableName() { return tableName; }

  public Expression getWhereExpression() {
    return whereExpression;
  }

  public OrderByClause getOrderByClause() {
    return orderByClause;
  }
}
