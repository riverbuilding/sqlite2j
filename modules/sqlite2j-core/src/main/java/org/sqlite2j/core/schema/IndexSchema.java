package org.sqlite2j.core.schema;

public final class IndexSchema {
  private final String name;
  private final String tableName;
  private final String columnName;

  public IndexSchema(String name, String tableName, String columnName) {
    this.name = name;
    this.tableName = tableName;
    this.columnName = columnName;
  }

  public String getName() { return name; }
  public String getTableName() { return tableName; }
  public String getColumnName() { return columnName; }
}
