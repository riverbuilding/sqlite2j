package org.sqlite2j.sql.ast;

public final class SelectAllStatement implements Statement {
  private final String tableName;

  public SelectAllStatement(String tableName) {
    this.tableName = tableName;
  }

  public String getTableName() { return tableName; }
}
