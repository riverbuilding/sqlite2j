package org.sqlite2j.vm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.sqlite2j.core.schema.SchemaRegistry;
import org.sqlite2j.core.schema.TableSchema;

public final class VmDatabase {
  private final SchemaRegistry schemaRegistry;
  private final Map<String, List<VmRow>> tableRows = new HashMap<String, List<VmRow>>();

  public VmDatabase(Path catalogPath) {
    this.schemaRegistry = new SchemaRegistry(catalogPath);
  }

  public SchemaRegistry getSchemaRegistry() { return schemaRegistry; }

  public void createTable(TableSchema schema) {
    schemaRegistry.registerTable(schema);
    tableRows.put(normalize(schema.getName()), new ArrayList<VmRow>());
  }

  public void insert(String tableName, VmRow row) {
    List<VmRow> rows = tableRows.get(normalize(tableName));
    if (rows == null) throw new IllegalStateException("Table not found: " + tableName);
    rows.add(row);
  }

  public VmCursor openReadCursor(String tableName) {
    List<VmRow> rows = tableRows.get(normalize(tableName));
    if (rows == null) throw new IllegalStateException("Table not found: " + tableName);
    return new VmCursor(rows);
  }

  private String normalize(String name) {
    return name.toLowerCase(Locale.ROOT);
  }
}
