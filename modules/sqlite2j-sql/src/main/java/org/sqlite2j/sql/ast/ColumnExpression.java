package org.sqlite2j.sql.ast;

public final class ColumnExpression implements Expression {
  private final String columnName;

  public ColumnExpression(String columnName) {
    this.columnName = columnName;
  }

  public String getColumnName() {
    return columnName;
  }
}
