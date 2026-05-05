package org.sqlite2j.sql.ast;

import java.util.List;

public final class CreateTableStatement implements Statement {
  private final String tableName;
  private final List<ColumnDef> columns;

  public CreateTableStatement(String tableName, List<ColumnDef> columns) {
    this.tableName = tableName;
    this.columns = columns;
  }

  public String getTableName() { return tableName; }
  public List<ColumnDef> getColumns() { return columns; }
}
