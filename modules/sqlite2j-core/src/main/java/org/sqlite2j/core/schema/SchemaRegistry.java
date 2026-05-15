package org.sqlite2j.core.schema;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SchemaRegistry {
  private final Map<String, TableSchema> tables = new LinkedHashMap<>();
  private final Map<String, IndexSchema> indexes = new LinkedHashMap<String, IndexSchema>();
  private final Path catalogPath;

  public SchemaRegistry(Path catalogPath) {
    this.catalogPath = catalogPath;
  }

  public Path getCatalogPath() {
    return catalogPath;
  }

  public void registerTable(TableSchema schema) {
    String key = normalize(schema.getName());
    if (tables.containsKey(key)) {
      throw new SchemaException("Table already exists: " + schema.getName());
    }
    tables.put(key, schema);
  }

  public Optional<TableSchema> findTable(String tableName) {
    return Optional.ofNullable(tables.get(normalize(tableName)));
  }

  public Map<String, TableSchema> tablesView() {
    return Collections.unmodifiableMap(tables);
  }

  public void registerIndex(IndexSchema schema) {
    String key = normalize(schema.getName());
    if (indexes.containsKey(key)) {
      throw new SchemaException("Index already exists: " + schema.getName());
    }
    indexes.put(key, schema);
  }

  public Map<String, IndexSchema> indexesView() {
    return Collections.unmodifiableMap(indexes);
  }

  public void reset() {
    tables.clear();
    indexes.clear();
  }

  private String normalize(String name) {
    return name.toLowerCase();
  }
}
