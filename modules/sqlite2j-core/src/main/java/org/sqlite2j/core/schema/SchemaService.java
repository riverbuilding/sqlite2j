package org.sqlite2j.core.schema;

import org.sqlite2j.sql.ast.CreateTableStatement;
import org.sqlite2j.sql.ast.Statement;
import org.sqlite2j.sql.parser.Parser;

public final class SchemaService {
  private final Parser parser;
  private final SchemaRegistry registry;

  public SchemaService(SchemaRegistry registry) {
    this.parser = new Parser();
    this.registry = registry;
  }

  public SchemaRegistry getRegistry() {
    return registry;
  }

  public void execute(String sql) {
    Statement statement = parser.parse(sql);
    if (statement instanceof CreateTableStatement) {
      CreateTableStatement create = (CreateTableStatement) statement;
      registry.registerTable(new TableSchema(create.getTableName(), create.getColumns()));
      return;
    }
    throw new SchemaException("Only CREATE TABLE is supported in SchemaService");
  }
}
