package org.sqlite2j.vm;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.sqlite2j.journal.CommitMarker;
import org.sqlite2j.journal.JournalPageRecord;
import org.sqlite2j.journal.ParsedJournal;
import org.sqlite2j.journal.RollbackJournalFile;
import org.sqlite2j.core.schema.SchemaRegistry;
import org.sqlite2j.core.schema.TableSchema;
import org.sqlite2j.core.schema.IndexSchema;
import org.sqlite2j.sql.ast.ColumnDef;

public final class VmDatabase {
  private static final String FORMAT_HEADER = "sqlite2j-vm-v1";

  private final SchemaRegistry schemaRegistry;
  private final Map<String, List<VmRow>> tableRows = new LinkedHashMap<String, List<VmRow>>();
  private final Map<String, Map<String, List<VmRow>>> indexEntries = new LinkedHashMap<String, Map<String, List<VmRow>>>();
  private final Path catalogPath;
  private final RollbackJournalFile rollbackJournalFile = new RollbackJournalFile();

  public VmDatabase(Path catalogPath) {
    this.catalogPath = catalogPath;
    this.schemaRegistry = new SchemaRegistry(catalogPath);
    recoverJournalIfPresent();
    load();
    rebuildAllIndexes();
  }

  public SchemaRegistry getSchemaRegistry() { return schemaRegistry; }

  Path catalogPath() { return catalogPath; }

  public void createTable(TableSchema schema) {
    schemaRegistry.registerTable(schema);
    tableRows.put(normalize(schema.getName()), new ArrayList<VmRow>());
    save();
  }

  public void insert(String tableName, VmRow row) {
    List<VmRow> rows = tableRows.get(normalize(tableName));
    if (rows == null) throw new IllegalStateException("Table not found: " + tableName);
    rows.add(row);
    onInsertRow(tableName, row);
    save();
  }

  public void createIndex(IndexSchema indexSchema) {
    TableSchema tableSchema = schemaRegistry.findTable(indexSchema.getTableName())
        .orElseThrow(() -> new IllegalStateException("Table not found: " + indexSchema.getTableName()));
    int columnIndex = findColumnIndex(tableSchema, indexSchema.getColumnName());
    List<VmRow> rows = tableRows.get(normalize(indexSchema.getTableName()));
    for (VmRow row : rows) {
      if (columnIndex >= row.getValues().size()) {
        throw new IllegalStateException("Unable to build index; row column missing for " + indexSchema.getColumnName());
      }
      row.getValues().get(columnIndex);
    }
    schemaRegistry.registerIndex(indexSchema);
    rebuildIndex(indexSchema);
    save();
  }

  public VmCursor openReadCursor(String tableName) {
    List<VmRow> rows = tableRows.get(normalize(tableName));
    if (rows == null) throw new IllegalStateException("Table not found: " + tableName);
    return new VmCursor(rows);
  }

  public List<VmRow> rowsView(String tableName) {
    List<VmRow> rows = tableRows.get(normalize(tableName));
    if (rows == null) throw new IllegalStateException("Table not found: " + tableName);
    return rows;
  }

  public void replaceRows(String tableName, List<VmRow> rows) {
    if (!tableRows.containsKey(normalize(tableName))) {
      throw new IllegalStateException("Table not found: " + tableName);
    }
    tableRows.put(normalize(tableName), new ArrayList<VmRow>(rows));
    save();
  }

  public Optional<String> findIndexColumnForTable(String tableName) {
    for (IndexSchema indexSchema : schemaRegistry.indexesView().values()) {
      if (indexSchema.getTableName().equalsIgnoreCase(tableName)) {
        return Optional.of(indexSchema.getColumnName());
      }
    }
    return Optional.empty();
  }

  public List<VmRow> lookupRowsByIndex(String tableName, String columnName, VmValue value) {
    IndexSchema indexSchema = findIndexSchema(tableName, columnName);
    Map<String, List<VmRow>> entries = indexEntries.get(normalize(indexSchema.getName()));
    if (entries == null) throw new IllegalStateException("Missing index entries for index " + indexSchema.getName());
    List<VmRow> rows = entries.get(encodeIndexKey(value));
    return rows == null ? new ArrayList<VmRow>() : new ArrayList<VmRow>(rows);
  }

  public void onInsertRow(String tableName, VmRow row) {
    for (IndexSchema indexSchema : indexesForTable(tableName)) {
      addRowToIndex(indexSchema, row);
    }
  }

  public void onRowUpdated(String tableName, VmRow oldRow, VmRow newRow) {
    TableSchema schema = schemaRegistry.findTable(tableName)
        .orElseThrow(() -> new IllegalStateException("Table not found: " + tableName));
    for (IndexSchema indexSchema : indexesForTable(tableName)) {
      int columnIndex = findColumnIndex(schema, indexSchema.getColumnName());
      VmValue oldValue = oldRow.getValues().get(columnIndex);
      VmValue newValue = newRow.getValues().get(columnIndex);
      if (compareValues(oldValue, newValue) != 0) {
        removeRowFromIndex(indexSchema, oldRow);
        addRowToIndex(indexSchema, newRow);
      }
    }
  }

  public void onRowDeleted(String tableName, VmRow row) {
    for (IndexSchema indexSchema : indexesForTable(tableName)) {
      removeRowFromIndex(indexSchema, row);
    }
  }


  void reloadFromDisk() {
    tableRows.clear();
    indexEntries.clear();
    schemaRegistry.reset();
    load();
    rebuildAllIndexes();
  }


  private void recoverJournalIfPresent() {
    Path journalPath = rollbackJournalFile.derivePath(catalogPath);
    if (!Files.exists(journalPath)) return;

    final ParsedJournal parsed;
    try {
      parsed = rollbackJournalFile.parse(journalPath);
    } catch (RuntimeException | IOException ex) {
      throw new IllegalStateException("Unsafe journal state; recovery aborted", ex);
    }

    try {
      if (parsed.getHeader().getCommitMarker() == CommitMarker.COMMITTED) {
        Files.deleteIfExists(journalPath);
        return;
      }

      for (JournalPageRecord record : parsed.getRecords()) {
        if (record.getPageNumber() != 1) {
          throw new IllegalStateException("Unsupported recovery page number: " + record.getPageNumber());
        }
        Files.write(catalogPath, record.getPreimage());
      }
      forceCatalogSync();
      Files.deleteIfExists(journalPath);
    } catch (IOException ex) {
      throw new IllegalStateException("Journal recovery failed", ex);
    }
  }

  private void forceCatalogSync() throws IOException {
    if (!Files.exists(catalogPath)) return;
    FileChannel channel = FileChannel.open(catalogPath, java.nio.file.StandardOpenOption.WRITE);
    try {
      channel.force(true);
    } finally {
      channel.close();
    }
  }

  private void load() {
    try {
      if (!Files.exists(catalogPath) || Files.size(catalogPath) == 0) return;
      List<String> lines = Files.readAllLines(catalogPath, StandardCharsets.UTF_8);
      if (lines.isEmpty() || !FORMAT_HEADER.equals(lines.get(0))) return;
      for (int i = 1; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line.startsWith("TABLE\t")) loadTable(line);
        else if (line.startsWith("INDEX\t")) loadIndex(line);
        else if (line.startsWith("ROW\t")) loadRow(line);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load database: " + catalogPath, e);
    }
  }

  private void loadTable(String line) {
    String[] parts = line.split("\t", -1);
    String tableName = parts[1];
    List<ColumnDef> columns = new ArrayList<ColumnDef>();
    if (parts.length > 2 && !parts[2].isEmpty()) {
      String[] defs = parts[2].split(",");
      for (String def : defs) {
        String[] column = def.split(":", 2);
        columns.add(new ColumnDef(column[0], column[1]));
      }
    }
    schemaRegistry.registerTable(new TableSchema(tableName, columns));
    tableRows.put(normalize(tableName), new ArrayList<VmRow>());
  }

  private void loadRow(String line) {
    String[] parts = line.split("\t", -1);
    String tableName = parts[1];
    List<VmValue> values = new ArrayList<VmValue>();
    for (int i = 2; i < parts.length; i++) {
      values.add(decodeValue(parts[i]));
    }
    List<VmRow> rows = tableRows.get(normalize(tableName));
    if (rows == null) throw new IllegalStateException("Table not found while loading: " + tableName);
    rows.add(new VmRow(values));
  }

  private void loadIndex(String line) {
    String[] parts = line.split("\t", -1);
    if (parts.length < 4) throw new IllegalStateException("Invalid index line: " + line);
    schemaRegistry.registerIndex(new IndexSchema(parts[1], parts[2], parts[3]));
  }

  private void save() {
    List<String> lines = new ArrayList<String>();
    lines.add(FORMAT_HEADER);
    for (TableSchema table : schemaRegistry.tablesView().values()) {
      lines.add("TABLE\t" + table.getName() + "\t" + encodeColumns(table.getColumns()));
      List<VmRow> rows = tableRows.get(normalize(table.getName()));
      if (rows != null) {
        for (VmRow row : rows) {
          lines.add(encodeRow(table.getName(), row));
        }
      }
    }
    for (IndexSchema index : schemaRegistry.indexesView().values()) {
      lines.add("INDEX\t" + index.getName() + "\t" + index.getTableName() + "\t" + index.getColumnName());
    }
    try {
      Path parent = catalogPath.toAbsolutePath().getParent();
      if (parent != null) Files.createDirectories(parent);
      Files.write(catalogPath, lines, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to save database: " + catalogPath, e);
    }
  }

  private String encodeColumns(List<ColumnDef> columns) {
    List<String> parts = new ArrayList<String>();
    for (ColumnDef column : columns) {
      parts.add(column.getName() + ":" + column.getType());
    }
    return String.join(",", parts);
  }

  private String encodeRow(String tableName, VmRow row) {
    StringBuilder out = new StringBuilder("ROW\t").append(tableName);
    for (VmValue value : row.getValues()) {
      out.append('\t').append(encodeValue(value));
    }
    return out.toString();
  }

  private String encodeValue(VmValue value) {
    if (value.getType() == VmValue.Type.NULL) return "N:";
    if (value.getType() == VmValue.Type.INT) return "I:" + value.getValue();
    String text = String.valueOf(value.getValue());
    return "T:" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }

  private VmValue decodeValue(String encoded) {
    if (encoded.startsWith("N:")) return VmValue.nullValue();
    if (encoded.startsWith("I:")) return VmValue.ofInt(Long.parseLong(encoded.substring(2)));
    if (encoded.startsWith("T:")) {
      byte[] bytes = Base64.getDecoder().decode(encoded.substring(2));
      return VmValue.ofText(new String(bytes, StandardCharsets.UTF_8));
    }
    throw new IllegalStateException("Unknown value encoding: " + encoded);
  }

  private String normalize(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  private int findColumnIndex(TableSchema tableSchema, String columnName) {
    for (int i = 0; i < tableSchema.getColumns().size(); i++) {
      if (tableSchema.getColumns().get(i).getName().equalsIgnoreCase(columnName)) {
        return i;
      }
    }
    throw new IllegalStateException("Unknown column for index: " + columnName);
  }

  private int compareValues(VmValue left, VmValue right) {
    if (left.getType() != right.getType()) {
      return left.getType().ordinal() - right.getType().ordinal();
    }
    if (left.getType() == VmValue.Type.INT) {
      return Long.compare((Long) left.getValue(), (Long) right.getValue());
    }
    if (left.getType() == VmValue.Type.TEXT) {
      return ((String) left.getValue()).compareTo((String) right.getValue());
    }
    return 0;
  }

  private void rebuildAllIndexes() {
    indexEntries.clear();
    for (IndexSchema indexSchema : schemaRegistry.indexesView().values()) {
      rebuildIndex(indexSchema);
    }
  }

  private void rebuildIndex(IndexSchema indexSchema) {
    TableSchema tableSchema = schemaRegistry.findTable(indexSchema.getTableName())
        .orElseThrow(() -> new IllegalStateException("Table not found: " + indexSchema.getTableName()));
    int columnIndex = findColumnIndex(tableSchema, indexSchema.getColumnName());
    Map<String, List<VmRow>> entries = new LinkedHashMap<String, List<VmRow>>();
    for (VmRow row : rowsView(indexSchema.getTableName())) {
      if (columnIndex >= row.getValues().size()) {
        throw new IllegalStateException("Invalid index metadata for table " + indexSchema.getTableName() + ": " + indexSchema.getColumnName());
      }
      String key = encodeIndexKey(row.getValues().get(columnIndex));
      entries.computeIfAbsent(key, k -> new ArrayList<VmRow>()).add(row);
    }
    indexEntries.put(normalize(indexSchema.getName()), entries);
  }

  private List<IndexSchema> indexesForTable(String tableName) {
    List<IndexSchema> out = new ArrayList<IndexSchema>();
    for (IndexSchema indexSchema : schemaRegistry.indexesView().values()) {
      if (indexSchema.getTableName().equalsIgnoreCase(tableName)) out.add(indexSchema);
    }
    return out;
  }

  private IndexSchema findIndexSchema(String tableName, String columnName) {
    for (IndexSchema indexSchema : indexesForTable(tableName)) {
      if (indexSchema.getColumnName().equalsIgnoreCase(columnName)) return indexSchema;
    }
    throw new IllegalStateException("Index not found for table " + tableName + " and column " + columnName);
  }

  private void addRowToIndex(IndexSchema indexSchema, VmRow row) {
    TableSchema tableSchema = schemaRegistry.findTable(indexSchema.getTableName()).orElseThrow();
    int columnIndex = findColumnIndex(tableSchema, indexSchema.getColumnName());
    String key = encodeIndexKey(row.getValues().get(columnIndex));
    Map<String, List<VmRow>> entries = indexEntries.computeIfAbsent(normalize(indexSchema.getName()),
        k -> new LinkedHashMap<String, List<VmRow>>());
    entries.computeIfAbsent(key, k -> new ArrayList<VmRow>()).add(row);
  }

  private void removeRowFromIndex(IndexSchema indexSchema, VmRow row) {
    TableSchema tableSchema = schemaRegistry.findTable(indexSchema.getTableName()).orElseThrow();
    int columnIndex = findColumnIndex(tableSchema, indexSchema.getColumnName());
    String key = encodeIndexKey(row.getValues().get(columnIndex));
    Map<String, List<VmRow>> entries = indexEntries.get(normalize(indexSchema.getName()));
    if (entries == null) return;
    List<VmRow> rows = entries.get(key);
    if (rows == null) return;
    rows.remove(row);
    if (rows.isEmpty()) entries.remove(key);
  }

  private String encodeIndexKey(VmValue value) {
    if (value.getType() == VmValue.Type.NULL) return "N:";
    if (value.getType() == VmValue.Type.INT) return "I:" + value.getValue();
    return "T:" + value.getValue();
  }
}
