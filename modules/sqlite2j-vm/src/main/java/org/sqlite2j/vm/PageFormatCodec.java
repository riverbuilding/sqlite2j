package org.sqlite2j.vm;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.sqlite2j.core.schema.IndexSchema;
import org.sqlite2j.core.schema.TableSchema;
import org.sqlite2j.sql.ast.ColumnDef;

final class PageFormatCodec {
  static final byte[] MAGIC = new byte[] { 'S', '2', 'J', 'D', 'B', 0 };
  static final short VERSION = 1;
  static final short PAGE_SIZE = 4096;

  byte[] encode(Map<String, TableSchema> tables, Map<String, List<VmRow>> rowsByTable, Map<String, IndexSchema> indexes) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeBytes(out, MAGIC);
    writeU16(out, VERSION);
    writeU16(out, PAGE_SIZE);
    writeU32(out, 1);
    writeU32(out, 2);
    writeU32(out, 0); // page count reserved for now
    writeBytes(out, new byte[10]);

    writeU32(out, tables.size());
    for (TableSchema table : tables.values()) {
      writeString(out, table.getName());
      writeU16(out, table.getColumns().size());
      for (ColumnDef column : table.getColumns()) {
        writeString(out, column.getName());
        writeString(out, column.getType());
      }
      List<VmRow> rows = rowsByTable.get(table.getName().toLowerCase());
      writeU32(out, rows == null ? 0 : rows.size());
      if (rows != null) {
        for (VmRow row : rows) writeRow(out, row);
      }
    }

    writeU32(out, indexes.size());
    for (IndexSchema index : indexes.values()) {
      writeString(out, index.getName());
      writeString(out, index.getTableName());
      writeString(out, index.getColumnName());
    }
    return out.toByteArray();
  }

  Decoded decode(byte[] bytes) {
    ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    byte[] magic = new byte[MAGIC.length];
    in.get(magic);
    for (int i = 0; i < MAGIC.length; i++) if (magic[i] != MAGIC[i]) throw new IllegalStateException("STORAGE_FORMAT_UNSUPPORTED");
    short version = in.getShort();
    if (version != VERSION) throw new IllegalStateException("STORAGE_FORMAT_VERSION_UNSUPPORTED");
    in.getShort(); // page size
    in.getInt(); // root table page
    in.getInt(); // root index page
    in.getInt(); // page count
    in.position(in.position() + 10); // reserved

    List<TableSchema> tables = new ArrayList<TableSchema>();
    java.util.LinkedHashMap<String, List<VmRow>> tableRows = new java.util.LinkedHashMap<String, List<VmRow>>();
    int tableCount = in.getInt();
    for (int t = 0; t < tableCount; t++) {
      String tableName = readString(in);
      int colCount = in.getShort() & 0xFFFF;
      List<ColumnDef> columns = new ArrayList<ColumnDef>();
      for (int c = 0; c < colCount; c++) columns.add(new ColumnDef(readString(in), readString(in)));
      tables.add(new TableSchema(tableName, columns));
      int rowCount = in.getInt();
      List<VmRow> rows = new ArrayList<VmRow>();
      for (int r = 0; r < rowCount; r++) rows.add(readRow(in));
      tableRows.put(tableName.toLowerCase(), rows);
    }
    int indexCount = in.getInt();
    List<IndexSchema> indexes = new ArrayList<IndexSchema>();
    for (int i = 0; i < indexCount; i++) {
      indexes.add(new IndexSchema(readString(in), readString(in), readString(in)));
    }
    return new Decoded(tables, tableRows, indexes);
  }

  static final class Decoded {
    final List<TableSchema> tables;
    final Map<String, List<VmRow>> rowsByTable;
    final List<IndexSchema> indexes;
    Decoded(List<TableSchema> tables, Map<String, List<VmRow>> rowsByTable, List<IndexSchema> indexes) {
      this.tables = tables; this.rowsByTable = rowsByTable; this.indexes = indexes;
    }
  }

  private void writeRow(ByteArrayOutputStream out, VmRow row) {
    writeU16(out, row.getValues().size());
    for (VmValue value : row.getValues()) {
      if (value.getType() == VmValue.Type.NULL) { out.write(0); writeU32(out, 0); }
      else if (value.getType() == VmValue.Type.INT) { out.write(1); writeU32(out, 8); writeI64(out, (Long) value.getValue()); }
      else { byte[] b = String.valueOf(value.getValue()).getBytes(StandardCharsets.UTF_8); out.write(2); writeU32(out, b.length); writeBytes(out, b); }
    }
  }
  private VmRow readRow(ByteBuffer in) {
    int count = in.getShort() & 0xFFFF;
    List<VmValue> values = new ArrayList<VmValue>();
    for (int i = 0; i < count; i++) {
      int tag = in.get() & 0xFF;
      int len = in.getInt();
      if (tag == 0) values.add(VmValue.nullValue());
      else if (tag == 1) values.add(VmValue.ofInt(in.getLong()));
      else if (tag == 2) { byte[] b = new byte[len]; in.get(b); values.add(VmValue.ofText(new String(b, StandardCharsets.UTF_8))); }
      else throw new IllegalStateException("STORAGE_FORMAT_UNSUPPORTED");
    }
    return new VmRow(values);
  }
  private void writeString(ByteArrayOutputStream out, String v) { byte[] b = v.getBytes(StandardCharsets.UTF_8); writeU16(out, b.length); writeBytes(out, b); }
  private String readString(ByteBuffer in) { int l = in.getShort() & 0xFFFF; byte[] b = new byte[l]; in.get(b); return new String(b, StandardCharsets.UTF_8); }
  private void writeU16(ByteArrayOutputStream out, int v) { out.write((v >>> 8) & 0xFF); out.write(v & 0xFF); }
  private void writeU32(ByteArrayOutputStream out, int v) { out.write((v >>> 24) & 0xFF); out.write((v >>> 16) & 0xFF); out.write((v >>> 8) & 0xFF); out.write(v & 0xFF); }
  private void writeI64(ByteArrayOutputStream out, long v) { for (int i = 7; i >= 0; i--) out.write((int) ((v >>> (8 * i)) & 0xFF)); }
  private void writeBytes(ByteArrayOutputStream out, byte[] b) { out.write(b, 0, b.length); }
}
