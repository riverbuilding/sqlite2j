package org.sqlite2j.sql.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.sqlite2j.sql.SqlErrorCode;
import org.sqlite2j.sql.SqlParseException;
import org.sqlite2j.sql.ast.CreateTableStatement;
import org.sqlite2j.sql.ast.InsertStatement;
import org.sqlite2j.sql.ast.SelectAllStatement;

class ParserTest {
  private final Parser parser = new Parser();

  @Test
  void parsesCreateTable() {
    var stmt = parser.parse("CREATE TABLE users (id INT, name TEXT);");
    var create = assertInstanceOf(CreateTableStatement.class, stmt);
    assertEquals("users", create.tableName());
    assertEquals(2, create.columns().size());
  }

  @Test
  void parsesInsert() {
    var stmt = parser.parse("INSERT INTO users VALUES (1, 'alice');");
    var insert = assertInstanceOf(InsertStatement.class, stmt);
    assertEquals("users", insert.tableName());
    assertEquals(2, insert.values().size());
  }

  @Test
  void parsesSelectAll() {
    var stmt = parser.parse("SELECT * FROM users;");
    var select = assertInstanceOf(SelectAllStatement.class, stmt);
    assertEquals("users", select.tableName());
  }

  @Test
  void rejectsUnsupportedWhereClause() {
    SqlParseException ex = assertThrows(SqlParseException.class, () -> parser.parse("SELECT * FROM users WHERE id = 1;"));
    assertEquals(SqlErrorCode.UNEXPECTED_TOKEN, ex.getCode());
  }

  @Test
  void rejectsUnsupportedStatement() {
    SqlParseException ex = assertThrows(SqlParseException.class, () -> parser.parse("DELETE FROM users;"));
    assertEquals(SqlErrorCode.UNSUPPORTED_STATEMENT, ex.getCode());
  }
}
