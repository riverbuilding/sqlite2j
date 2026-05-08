package org.sqlite2j.vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.sqlite2j.compiler.codegen.Instruction;
import org.sqlite2j.compiler.codegen.Opcode;
import org.sqlite2j.compiler.codegen.Program;
import org.sqlite2j.core.schema.TableSchema;
import org.sqlite2j.sql.ast.ColumnDef;

public final class VirtualMachine {
  private final VmDatabase database;

  public VirtualMachine(VmDatabase database) {
    this.database = database;
  }

  public VmResult execute(Program program) {
    VmResult result = new VmResult();
    VmCursor cursor = null;
    VmRow registerRecord = null;
    String openReadTable = null;

    for (Instruction instruction : program.getInstructions()) {
      Opcode opcode = instruction.getOpcode();
      if (opcode == Opcode.CREATE_TABLE) {
        database.createTable(new TableSchema(instruction.getP1(), parseColumns(instruction.getP2())));
      } else if (opcode == Opcode.OPEN_WRITE) {
        // placeholder: writes target table via INSERT_ROW p1
      } else if (opcode == Opcode.OPEN_READ) {
        openReadTable = instruction.getP1();
        cursor = database.openReadCursor(instruction.getP1());
      } else if (opcode == Opcode.MAKE_RECORD) {
        registerRecord = new VmRow(parseValues(instruction.getP1()));
      } else if (opcode == Opcode.INSERT_ROW) {
        if (registerRecord == null) throw new IllegalStateException("No record available for INSERT_ROW");
        database.insert(instruction.getP1(), registerRecord);
      } else if (opcode == Opcode.SCAN_TABLE) {
        // scan is consumed by RESULT_ROW for phase 1
      } else if (opcode == Opcode.RESULT_ROW) {
        if (cursor == null) throw new IllegalStateException("No open cursor for RESULT_ROW");
        List<VmRow> rows = new ArrayList<VmRow>();
        while (cursor.next()) rows.add(cursor.current());
        sortRowsIfRequested(rows, openReadTable, instruction.getP2());
        for (VmRow row : rows) result.addRow(row);
      } else if (opcode == Opcode.HALT) {
        break;
      } else {
        throw new IllegalStateException("Unsupported opcode: " + opcode);
      }
    }
    return result;
  }

  private void sortRowsIfRequested(List<VmRow> rows, String tableName, String orderBy) {
    if (orderBy == null || orderBy.isEmpty()) return;
    if (tableName == null) throw new IllegalStateException("No open table for ORDER BY");
    String[] parts = orderBy.split(":", 2);
    if (parts.length != 2) throw new IllegalStateException("Invalid ORDER BY encoding: " + orderBy);
    int index = findColumnIndex(tableName, parts[0]);
    final boolean descending = "DESC".equalsIgnoreCase(parts[1]);
    Collections.sort(rows, new Comparator<VmRow>() {
      @Override
      public int compare(VmRow a, VmRow b) {
        VmValue left = a.getValues().get(index);
        VmValue right = b.getValues().get(index);
        int cmp = compareValues(left, right);
        return descending ? -cmp : cmp;
      }
    });
  }

  private int findColumnIndex(String tableName, String columnName) {
    TableSchema schema = database.getSchemaRegistry().findTable(tableName)
        .orElseThrow(() -> new IllegalStateException("Table not found: " + tableName));
    for (int i = 0; i < schema.getColumns().size(); i++) {
      if (schema.getColumns().get(i).getName().equalsIgnoreCase(columnName)) return i;
    }
    throw new IllegalStateException("Unknown ORDER BY column: " + columnName);
  }

  private int compareValues(VmValue left, VmValue right) {
    if (left.getType() == VmValue.Type.NULL && right.getType() == VmValue.Type.NULL) return 0;
    if (left.getType() == VmValue.Type.NULL) return -1;
    if (right.getType() == VmValue.Type.NULL) return 1;
    if (left.getType() == VmValue.Type.INT && right.getType() == VmValue.Type.INT) {
      return Long.compare((Long) left.getValue(), (Long) right.getValue());
    }
    if (left.getType() == VmValue.Type.TEXT && right.getType() == VmValue.Type.TEXT) {
      return ((String) left.getValue()).compareTo((String) right.getValue());
    }
    // deterministic mixed-type ordering for now: INT before TEXT
    return left.getType().ordinal() - right.getType().ordinal();
  }

  private List<ColumnDef> parseColumns(String encoded) {
    List<ColumnDef> columns = new ArrayList<ColumnDef>();
    String[] defs = encoded.split(",");
    for (String def : defs) {
      String[] parts = def.split(":");
      columns.add(new ColumnDef(parts[0], parts[1]));
    }
    return columns;
  }

  private List<VmValue> parseValues(String encoded) {
    List<VmValue> values = new ArrayList<VmValue>();
    if (encoded == null || encoded.isEmpty()) return values;

    String[] parts = encoded.split(",");
    for (String raw : parts) {
      String token = raw.trim();
      if (token.startsWith("'") && token.endsWith("'")) {
        values.add(VmValue.ofText(token.substring(1, token.length() - 1)));
      } else if (token.equalsIgnoreCase("null")) {
        values.add(VmValue.nullValue());
      } else {
        values.add(VmValue.ofInt(Long.parseLong(token)));
      }
    }
    return values;
  }
}
