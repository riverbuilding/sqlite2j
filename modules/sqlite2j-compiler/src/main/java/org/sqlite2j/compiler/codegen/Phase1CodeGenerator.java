package org.sqlite2j.compiler.codegen;

import java.util.ArrayList;
import java.util.List;
import org.sqlite2j.sql.ast.ColumnDef;
import org.sqlite2j.sql.ast.CreateTableStatement;
import org.sqlite2j.sql.ast.InsertStatement;
import org.sqlite2j.sql.ast.LiteralValue;
import org.sqlite2j.sql.ast.OrderByClause;
import org.sqlite2j.sql.ast.SelectAllStatement;
import org.sqlite2j.sql.ast.Statement;

public final class Phase1CodeGenerator {

  public Program lower(Statement statement) {
    Program program = new Program();
    if (statement instanceof CreateTableStatement) {
      emitCreateTable(program, (CreateTableStatement) statement);
    } else if (statement instanceof InsertStatement) {
      emitInsert(program, (InsertStatement) statement);
    } else if (statement instanceof SelectAllStatement) {
      emitSelect(program, (SelectAllStatement) statement);
    } else {
      throw new IllegalArgumentException("Unsupported statement type: " + statement.getClass().getName());
    }
    program.add(Opcode.HALT, null, null);
    return program;
  }

  private void emitCreateTable(Program program, CreateTableStatement stmt) {
    program.add(Opcode.CREATE_TABLE, stmt.getTableName(), encodeColumns(stmt.getColumns()));
  }

  private void emitInsert(Program program, InsertStatement stmt) {
    program.add(Opcode.OPEN_WRITE, stmt.getTableName(), null);
    program.add(Opcode.MAKE_RECORD, encodeValues(stmt.getValues()), null);
    program.add(Opcode.INSERT_ROW, stmt.getTableName(), null);
  }

  private void emitSelect(Program program, SelectAllStatement stmt) {
    program.add(Opcode.OPEN_READ, stmt.getTableName(), null);
    program.add(Opcode.SCAN_TABLE, stmt.getTableName(), null);
    program.add(Opcode.RESULT_ROW, "*", encodeOrderBy(stmt.getOrderByClause()));
  }

  private String encodeOrderBy(OrderByClause orderByClause) {
    if (orderByClause == null) return null;
    return orderByClause.getColumnName() + ":" + (orderByClause.isDescending() ? "DESC" : "ASC");
  }

  private String encodeColumns(List<ColumnDef> columns) {
    List<String> parts = new ArrayList<String>();
    for (ColumnDef c : columns) {
      parts.add(c.getName() + ":" + c.getType());
    }
    return String.join(",", parts);
  }

  private String encodeValues(List<LiteralValue> values) {
    List<String> parts = new ArrayList<String>();
    for (LiteralValue v : values) {
      Object o = v.getValue();
      if (o instanceof String) parts.add("'" + o + "'");
      else parts.add(String.valueOf(o));
    }
    return String.join(",", parts);
  }
}
