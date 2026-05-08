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
    String openWriteTable = null;
    String currentWhere = null;

    for (Instruction instruction : program.getInstructions()) {
      Opcode opcode = instruction.getOpcode();
      if (opcode == Opcode.CREATE_TABLE) {
        database.createTable(new TableSchema(instruction.getP1(), parseColumns(instruction.getP2())));
      } else if (opcode == Opcode.OPEN_WRITE) {
        openWriteTable = instruction.getP1();
      } else if (opcode == Opcode.OPEN_READ) {
        openReadTable = instruction.getP1();
        cursor = database.openReadCursor(instruction.getP1());
      } else if (opcode == Opcode.MAKE_RECORD) {
        registerRecord = new VmRow(parseValues(instruction.getP1()));
      } else if (opcode == Opcode.INSERT_ROW) {
        if (registerRecord == null) throw new IllegalStateException("No record available for INSERT_ROW");
        database.insert(instruction.getP1(), registerRecord);
      } else if (opcode == Opcode.SCAN_TABLE) {
        currentWhere = instruction.getP2();
      } else if (opcode == Opcode.RESULT_ROW) {
        if (cursor == null) throw new IllegalStateException("No open cursor for RESULT_ROW");
        List<VmRow> rows = new ArrayList<VmRow>();
        while (cursor.next()) {
          VmRow row = cursor.current();
          if (matchesWhere(openReadTable, row, currentWhere)) {
            rows.add(row);
          }
        }
        sortRowsIfRequested(rows, openReadTable, instruction.getP2());
        for (VmRow row : rows) result.addRow(row);
      } else if (opcode == Opcode.UPDATE_ROWS) {
        applyUpdateRows(openWriteTable, currentWhere, instruction.getP1(), instruction.getP2());
      } else if (opcode == Opcode.DELETE_ROWS) {
        applyDeleteRows(openWriteTable, currentWhere);
      } else if (opcode == Opcode.HALT) {
        break;
      } else {
        throw new IllegalStateException("Unsupported opcode: " + opcode);
      }
    }
    return result;
  }

  private void applyUpdateRows(String tableName, String where, String columnName, String encodedLiteral) {
    if (tableName == null) throw new IllegalStateException("No open table for UPDATE_ROWS");
    int targetColumnIndex = findColumnIndex(tableName, columnName);
    VmValue newValue = parseEncodedLiteral(encodedLiteral);

    List<VmRow> source = database.rowsView(tableName);
    List<VmRow> updated = new ArrayList<VmRow>(source.size());
    for (VmRow row : source) {
      if (matchesWhere(tableName, row, where)) {
        List<VmValue> values = new ArrayList<VmValue>(row.getValues());
        values.set(targetColumnIndex, newValue);
        updated.add(new VmRow(values));
      } else {
        updated.add(row);
      }
    }
    database.replaceRows(tableName, updated);
  }

  private void applyDeleteRows(String tableName, String where) {
    if (tableName == null) throw new IllegalStateException("No open table for DELETE_ROWS");
    List<VmRow> source = database.rowsView(tableName);
    List<VmRow> kept = new ArrayList<VmRow>();
    for (VmRow row : source) {
      if (!matchesWhere(tableName, row, where)) {
        kept.add(row);
      }
    }
    database.replaceRows(tableName, kept);
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

  private boolean matchesWhere(String tableName, VmRow row, String where) {
    if (where == null || where.isEmpty()) return true;
    int first = where.indexOf(':');
    int second = where.indexOf(':', first + 1);
    if (first <= 0 || second <= first) throw new IllegalStateException("Invalid WHERE encoding: " + where);

    String left = where.substring(0, first);
    String op = where.substring(first + 1, second);
    String right = where.substring(second + 1);
    if (!left.startsWith("C(") || !left.endsWith(")")) throw new IllegalStateException("Invalid WHERE column encoding: " + left);
    if (!right.startsWith("L(") || !right.endsWith(")")) throw new IllegalStateException("Invalid WHERE literal encoding: " + right);

    String columnName = left.substring(2, left.length() - 1);
    String literal = right.substring(2, right.length() - 1);
    int columnIndex = findColumnIndex(tableName, columnName);
    VmValue leftValue = row.getValues().get(columnIndex);
    VmValue rightValue = parseEncodedLiteral(literal);
    return compareByOperator(compareValuesForWhere(leftValue, rightValue), op);
  }

  private VmValue parseEncodedLiteral(String literal) {
    if (literal.startsWith("'") && literal.endsWith("'")) {
      return VmValue.ofText(literal.substring(1, literal.length() - 1));
    }
    return VmValue.ofInt(Long.parseLong(literal));
  }

  private boolean compareByOperator(int cmp, String operator) {
    if ("EQ".equals(operator)) return cmp == 0;
    if ("NE".equals(operator)) return cmp != 0;
    if ("LT".equals(operator)) return cmp < 0;
    if ("LTE".equals(operator)) return cmp <= 0;
    if ("GT".equals(operator)) return cmp > 0;
    if ("GTE".equals(operator)) return cmp >= 0;
    throw new IllegalStateException("Unsupported WHERE operator: " + operator);
  }

  private int compareValuesForWhere(VmValue left, VmValue right) {
    if (left.getType() != right.getType()) {
      throw new IllegalStateException("Type mismatch in WHERE comparison: " + left.getType() + " vs " + right.getType());
    }
    return compareValues(left, right);
  }

  private int findColumnIndex(String tableName, String columnName) {
    TableSchema schema = database.getSchemaRegistry().findTable(tableName)
        .orElseThrow(() -> new IllegalStateException("Table not found: " + tableName));
    for (int i = 0; i < schema.getColumns().size(); i++) {
      if (schema.getColumns().get(i).getName().equalsIgnoreCase(columnName)) return i;
    }
    throw new IllegalStateException("Unknown column: " + columnName);
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
