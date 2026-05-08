package org.sqlite2j.sql.ast;

public final class OrderByClause {
  private final String columnName;
  private final boolean descending;

  public OrderByClause(String columnName, boolean descending) {
    this.columnName = columnName;
    this.descending = descending;
  }

  public String getColumnName() {
    return columnName;
  }

  public boolean isDescending() {
    return descending;
  }
}
