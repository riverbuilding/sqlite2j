package org.sqlite2j.sql.ast;

public final class CreateIndexStatement implements Statement {
  private final String indexName;
  private final String tableName;
  private final String columnName;

  public CreateIndexStatement(String indexName, String tableName, String columnName) {
    this.indexName = indexName;
    this.tableName = tableName;
    this.columnName = columnName;
  }

  public String getIndexName() {
    return indexName;
  }

  public String getTableName() {
    return tableName;
  }

  public String getColumnName() {
    return columnName;
  }
}
