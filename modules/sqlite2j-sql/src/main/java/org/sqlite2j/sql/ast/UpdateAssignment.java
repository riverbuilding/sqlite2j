package org.sqlite2j.sql.ast;

public final class UpdateAssignment {
  private final String columnName;
  private final LiteralValue value;

  public UpdateAssignment(String columnName, LiteralValue value) {
    this.columnName = columnName;
    this.value = value;
  }

  public String getColumnName() {
    return columnName;
  }

  public LiteralValue getValue() {
    return value;
  }
}
