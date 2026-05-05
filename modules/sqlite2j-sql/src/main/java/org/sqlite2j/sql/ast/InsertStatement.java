package org.sqlite2j.sql.ast;

import java.util.List;

public final class InsertStatement implements Statement {
  private final String tableName;
  private final List<LiteralValue> values;

  public InsertStatement(String tableName, List<LiteralValue> values) {
    this.tableName = tableName;
    this.values = values;
  }

  public String getTableName() { return tableName; }
  public List<LiteralValue> getValues() { return values; }
}
