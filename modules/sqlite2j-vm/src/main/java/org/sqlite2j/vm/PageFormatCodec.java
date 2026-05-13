package org.sqlite2j.vm;

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
    int size = 6 + 2 + 2 + 4 + 4 + 4 + 10 + 4;
    for (TableSchema table : tables.values()) {
      size += stringSize(table.getName()) + 2;
      for (ColumnDef column : table.getColumns()) size += stringSize(column.getName()) + stringSize(column.getType());
      List<VmRow> rows = rowsByTable.get(table.getName().toLowerCase());
      size += 4;
      if (rows != null) for (VmRow row : rows) size += rowSize(row);
    }
    size += 4;
    for (IndexSchema index : indexes.values()) size += stringSize(index.getName()) + stringSize(index.getTableName()) + stringSize(index.getColumnName());

    ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
    out.put(MAGIC).putShort(VERSION).putShort(PAGE_SIZE).putInt(1).putInt(2).putInt(0).put(new byte[10]);
    out.putInt(tables.size());
    for (TableSchema table : tables.values()) {
      putString(out, table.getName());
      out.putShort((short) table.getColumns().size());
      for (ColumnDef column : table.getColumns()) {
        putString(out, column.getName());
        putString(out, column.getType());
      }
      List<VmRow> rows = rowsByTable.get(table.getName().toLowerCase());
      out.putInt(rows == null ? 0 : rows.size());
      if (rows != null) for (VmRow row : rows) putRow(out, row);
    }
    out.putInt(indexes.size());
    for (IndexSchema index : indexes.values()) {
      putString(out, index.getName());
      putString(out, index.getTableName());
      putString(out, index.getColumnName());
    }
    return out.array();
  }

  Decoded decode(byte[] bytes) {
    ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    byte[] magic = new byte[MAGIC.length];
    in.get(magic);
    for (int i = 0; i < MAGIC.length; i++) if (magic[i] != MAGIC[i]) throw new IllegalStateException("STORAGE_FORMAT_UNSUPPORTED");
    if (in.getShort() != VERSION) throw new IllegalStateException("STORAGE_FORMAT_VERSION_UNSUPPORTED");
    in.getShort(); in.getInt(); in.getInt(); in.getInt(); in.position(in.position() + 10);

    List<TableSchema> tables = new ArrayList<TableSchema>();
    java.util.LinkedHashMap<String, List<VmRow>> tableRows = new java.util.LinkedHashMap<String, List<VmRow>>();
    int tableCount = in.getInt();
    for (int t = 0; t < tableCount; t++) {
      String tableName = readString(in);
      int colCount = in.getShort() & 0xFFFF;
      List<ColumnDef> cols = new ArrayList<ColumnDef>();
      for (int c = 0; c < colCount; c++) cols.add(new ColumnDef(readString(in), readString(in)));
      tables.add(new TableSchema(tableName, cols));
      int rowCount = in.getInt();
      List<VmRow> rows = new ArrayList<VmRow>();
      for (int r = 0; r < rowCount; r++) rows.add(readRow(in));
      tableRows.put(tableName.toLowerCase(), rows);
    }

    int indexCount = in.getInt();
    List<IndexSchema> idx = new ArrayList<IndexSchema>();
    for (int i = 0; i < indexCount; i++) idx.add(new IndexSchema(readString(in), readString(in), readString(in)));
    return new Decoded(tables, tableRows, idx);
  }

  static final class Decoded {
    final List<TableSchema> tables;
    final Map<String, List<VmRow>> rowsByTable;
    final List<IndexSchema> indexes;
    Decoded(List<TableSchema> tables, Map<String, List<VmRow>> rowsByTable, List<IndexSchema> indexes) {
      this.tables = tables; this.rowsByTable = rowsByTable; this.indexes = indexes;
    }
  }

  private int stringSize(String v) { return 2 + v.getBytes(StandardCharsets.UTF_8).length; }
  private int rowSize(VmRow row) {
    int size = 2;
    for (VmValue v : row.getValues()) {
      size += 1 + 4;
      if (v.getType() == VmValue.Type.INT) size += 8;
      else if (v.getType() == VmValue.Type.TEXT) size += String.valueOf(v.getValue()).getBytes(StandardCharsets.UTF_8).length;
    }
    return size;
  }
  private void putString(ByteBuffer out, String v) { byte[] b = v.getBytes(StandardCharsets.UTF_8); out.putShort((short) b.length).put(b); }
  private String readString(ByteBuffer in) { int l = in.getShort() & 0xFFFF; byte[] b = new byte[l]; in.get(b); return new String(b, StandardCharsets.UTF_8); }
  private void putRow(ByteBuffer out, VmRow row) {
    out.putShort((short) row.getValues().size());
    for (VmValue v : row.getValues()) {
      if (v.getType() == VmValue.Type.NULL) out.put((byte) 0).putInt(0);
      else if (v.getType() == VmValue.Type.INT) out.put((byte) 1).putInt(8).putLong((Long) v.getValue());
      else { byte[] b = String.valueOf(v.getValue()).getBytes(StandardCharsets.UTF_8); out.put((byte) 2).putInt(b.length).put(b); }
    }
  }
  private VmRow readRow(ByteBuffer in) {
    int n = in.getShort() & 0xFFFF;
    List<VmValue> values = new ArrayList<VmValue>();
    for (int i = 0; i < n; i++) {
      int tag = in.get() & 0xFF;
      int len = in.getInt();
      if (tag == 0) values.add(VmValue.nullValue());
      else if (tag == 1) values.add(VmValue.ofInt(in.getLong()));
      else if (tag == 2) { byte[] b = new byte[len]; in.get(b); values.add(VmValue.ofText(new String(b, StandardCharsets.UTF_8))); }
      else throw new IllegalStateException("STORAGE_FORMAT_UNSUPPORTED");
    }
    return new VmRow(values);
  }
}
