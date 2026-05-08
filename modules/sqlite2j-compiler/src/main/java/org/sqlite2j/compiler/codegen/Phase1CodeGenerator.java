package org.sqlite2j.compiler.codegen;

import java.util.ArrayList;
import java.util.List;
import org.sqlite2j.sql.ast.ColumnDef;
import org.sqlite2j.sql.ast.CreateTableStatement;
import org.sqlite2j.sql.ast.ColumnExpression;
import org.sqlite2j.sql.ast.ComparisonExpression;
import org.sqlite2j.sql.ast.DeleteStatement;
import org.sqlite2j.sql.ast.Expression;
import org.sqlite2j.sql.ast.InsertStatement;
import org.sqlite2j.sql.ast.LiteralExpression;
import org.sqlite2j.sql.ast.LiteralValue;
import org.sqlite2j.sql.ast.OrderByClause;
import org.sqlite2j.sql.ast.UpdateStatement;
import org.sqlite2j.sql.ast.SelectAllStatement;
import org.sqlite2j.sql.ast.Statement;
import org.sqlite2j.sql.ast.UpdateAssignment;

public final class Phase1CodeGenerator {

  public Program lower(Statement statement) {
    Program program = new Program();
    if (statement instanceof CreateTableStatement) {
      emitCreateTable(program, (CreateTableStatement) statement);
    } else if (statement instanceof InsertStatement) {
      emitInsert(program, (InsertStatement) statement);
    } else if (statement instanceof SelectAllStatement) {
      emitSelect(program, (SelectAllStatement) statement);
    } else if (statement instanceof UpdateStatement) {
      emitUpdate(program, (UpdateStatement) statement);
    } else if (statement instanceof DeleteStatement) {
      emitDelete(program, (DeleteStatement) statement);
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
    program.add(Opcode.SCAN_TABLE, stmt.getTableName(), encodeWhere(stmt.getWhereExpression()));
    program.add(Opcode.RESULT_ROW, "*", encodeOrderBy(stmt.getOrderByClause()));
  }

  private void emitUpdate(Program program, UpdateStatement stmt) {
    UpdateAssignment assignment = stmt.getAssignments().get(0);
    program.add(Opcode.OPEN_WRITE, stmt.getTableName(), null);
    program.add(Opcode.SCAN_TABLE, stmt.getTableName(), encodeWhere(stmt.getWhereExpression()));
    program.add(Opcode.UPDATE_ROWS, assignment.getColumnName(), encodeLiteral(assignment.getValue()));
  }

  private void emitDelete(Program program, DeleteStatement stmt) {
    program.add(Opcode.OPEN_WRITE, stmt.getTableName(), null);
    program.add(Opcode.SCAN_TABLE, stmt.getTableName(), encodeWhere(stmt.getWhereExpression()));
    program.add(Opcode.DELETE_ROWS, stmt.getTableName(), null);
  }

  private String encodeOrderBy(OrderByClause orderByClause) {
    if (orderByClause == null) return null;
    return orderByClause.getColumnName() + ":" + (orderByClause.isDescending() ? "DESC" : "ASC");
  }

  private String encodeWhere(Expression expression) {
    if (expression == null) return null;
    if (expression instanceof ComparisonExpression) {
      ComparisonExpression comparison = (ComparisonExpression) expression;
      return encodeOperand(comparison.getLeft()) + ":" + comparison.getOperator().name() + ":" + encodeOperand(comparison.getRight());
    }
    throw new IllegalArgumentException("Unsupported WHERE expression type: " + expression.getClass().getName());
  }

  private String encodeOperand(Expression expression) {
    if (expression instanceof ColumnExpression) {
      return "C(" + ((ColumnExpression) expression).getColumnName() + ")";
    }
    if (expression instanceof LiteralExpression) {
      return "L(" + encodeLiteral(((LiteralExpression) expression).getLiteralValue()) + ")";
    }
    throw new IllegalArgumentException("Unsupported expression operand: " + expression.getClass().getName());
  }

  private String encodeLiteral(LiteralValue value) {
    Object raw = value.getValue();
    if (raw instanceof String) return "'" + raw + "'";
    return String.valueOf(raw);
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
