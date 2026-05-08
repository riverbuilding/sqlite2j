package org.sqlite2j.sql.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.sqlite2j.sql.SqlErrorCode;
import org.sqlite2j.sql.SqlParseException;
import org.sqlite2j.sql.ast.CreateTableStatement;
import org.sqlite2j.sql.ast.Expression;
import org.sqlite2j.sql.ast.InsertStatement;
import org.sqlite2j.sql.ast.SelectAllStatement;
import org.sqlite2j.sql.ast.ComparisonExpression;
import org.sqlite2j.sql.ast.ComparisonOperator;
import org.sqlite2j.sql.ast.OrderByClause;

class ParserTest {
  private final Parser parser = new Parser();

  @Test
  void parsesCreateTable() {
    var stmt = parser.parse("CREATE TABLE users (id INT, name TEXT);");
    var create = assertInstanceOf(CreateTableStatement.class, stmt);
    assertEquals("users", create.getTableName());
    assertEquals(2, create.getColumns().size());
  }

  @Test
  void parsesInsert() {
    var stmt = parser.parse("INSERT INTO users VALUES (1, 'alice');");
    var insert = assertInstanceOf(InsertStatement.class, stmt);
    assertEquals("users", insert.getTableName());
    assertEquals(2, insert.getValues().size());
  }

  @Test
  void parsesSelectAll() {
    var stmt = parser.parse("SELECT * FROM users;");
    var select = assertInstanceOf(SelectAllStatement.class, stmt);
    assertEquals("users", select.getTableName());
  }

  @Test
  void parsesSelectWithWhereClause() {
    var stmt = parser.parse("SELECT * FROM users WHERE id = 1;");
    var select = assertInstanceOf(SelectAllStatement.class, stmt);
    assertEquals("users", select.getTableName());
    assertInstanceOf(ComparisonExpression.class, select.getWhereExpression());
  }

  @Test
  void parsesSelectWithWhereAndOrderBy() {
    var stmt = parser.parse("SELECT * FROM users WHERE id >= 1 ORDER BY name DESC;");
    var select = assertInstanceOf(SelectAllStatement.class, stmt);
    assertInstanceOf(ComparisonExpression.class, select.getWhereExpression());
    OrderByClause orderBy = select.getOrderByClause();
    assertEquals("name", orderBy.getColumnName());
    assertEquals(true, orderBy.isDescending());
  }

  @Test
  void rejectsUnsupportedStatement() {
    SqlParseException ex = assertThrows(SqlParseException.class, () -> parser.parse("DELETE FROM users;"));
    assertEquals(SqlErrorCode.UNSUPPORTED_STATEMENT, ex.getCode());
  }

  @Test
  void parsesCreateTableWithoutSemicolon() {
    var stmt = parser.parse("CREATE TABLE users (id INT, name TEXT)");
    var create = assertInstanceOf(CreateTableStatement.class, stmt);
    assertEquals("users", create.getTableName());
    assertEquals(2, create.getColumns().size());
  }

  @Test
  void reportsInvalidIdentifierForQuotedTableName() {
    SqlParseException ex = assertThrows(SqlParseException.class, () -> parser.parse("CREATE TABLE 'users' (id INT);"));
    assertEquals(SqlErrorCode.INVALID_IDENTIFIER, ex.getCode());
  }

  @Test
  void reportsUnexpectedTokenForMissingColumnType() {
    SqlParseException ex = assertThrows(SqlParseException.class, () -> parser.parse("CREATE TABLE users (id);"));
    assertEquals(SqlErrorCode.INVALID_IDENTIFIER, ex.getCode());
  }

  @Test
  void parsesComparisonExpression() {
    Expression expr = parser.parseExpression("id >= 10");
    ComparisonExpression comparison = assertInstanceOf(ComparisonExpression.class, expr);
    assertEquals(ComparisonOperator.GTE, comparison.getOperator());
  }

  @Test
  void rejectsBooleanConjunctionForNow() {
    SqlParseException ex = assertThrows(SqlParseException.class, () -> parser.parseExpression("id = 1 AND name = 'a'"));
    assertEquals(SqlErrorCode.UNSUPPORTED_STATEMENT, ex.getCode());
  }

  @Test
  void rejectsNullComparisonsForNow() {
    SqlParseException ex = assertThrows(SqlParseException.class, () -> parser.parseExpression("id = NULL"));
    assertEquals(SqlErrorCode.UNSUPPORTED_STATEMENT, ex.getCode());
  }

}
