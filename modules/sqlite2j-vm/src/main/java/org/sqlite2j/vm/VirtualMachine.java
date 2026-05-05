package org.sqlite2j.vm;

import java.util.ArrayList;
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

    for (Instruction instruction : program.getInstructions()) {
      Opcode opcode = instruction.getOpcode();
      if (opcode == Opcode.CREATE_TABLE) {
        database.createTable(new TableSchema(instruction.getP1(), parseColumns(instruction.getP2())));
      } else if (opcode == Opcode.OPEN_WRITE) {
        // placeholder: writes target table via INSERT_ROW p1
      } else if (opcode == Opcode.OPEN_READ) {
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
        while (cursor.next()) result.addRow(cursor.current());
      } else if (opcode == Opcode.HALT) {
        break;
      } else {
        throw new IllegalStateException("Unsupported opcode: " + opcode);
      }
    }
    return result;
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
