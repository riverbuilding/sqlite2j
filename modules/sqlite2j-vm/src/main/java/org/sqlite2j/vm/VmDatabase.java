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
import org.sqlite2j.journal.CommitMarker;
import org.sqlite2j.journal.JournalPageRecord;
import org.sqlite2j.journal.ParsedJournal;
import org.sqlite2j.journal.RollbackJournalFile;
import org.sqlite2j.core.schema.SchemaRegistry;
import org.sqlite2j.core.schema.TableSchema;
import org.sqlite2j.sql.ast.ColumnDef;

public final class VmDatabase {
  private static final String FORMAT_HEADER = "sqlite2j-vm-v1";

  private final SchemaRegistry schemaRegistry;
  private final Map<String, List<VmRow>> tableRows = new LinkedHashMap<String, List<VmRow>>();
  private final Path catalogPath;
  private final RollbackJournalFile rollbackJournalFile = new RollbackJournalFile();

  public VmDatabase(Path catalogPath) {
    this.catalogPath = catalogPath;
    this.schemaRegistry = new SchemaRegistry(catalogPath);
    recoverJournalIfPresent();
    load();
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


  void reloadFromDisk() {
    tableRows.clear();
    schemaRegistry.reset();
    load();
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
}
