package org.sqlite2j.sql.parser;

import java.util.ArrayList;
import java.util.List;
import org.sqlite2j.sql.SqlErrorCode;
import org.sqlite2j.sql.SqlParseException;
import org.sqlite2j.sql.ast.ColumnDef;
import org.sqlite2j.sql.ast.CreateTableStatement;
import org.sqlite2j.sql.ast.InsertStatement;
import org.sqlite2j.sql.ast.LiteralValue;
import org.sqlite2j.sql.ast.SelectAllStatement;
import org.sqlite2j.sql.ast.Statement;

public final class Parser {
  private List<Token> tokens;
  private int current;

  public Statement parse(String sql) {
    this.tokens = new Tokenizer().tokenize(sql);
    this.current = 0;
    Statement stmt;
    if (match(TokenType.CREATE)) stmt = parseCreateTable();
    else if (match(TokenType.INSERT)) stmt = parseInsert();
    else if (match(TokenType.SELECT)) stmt = parseSelect();
    else throw error(peek(), SqlErrorCode.UNSUPPORTED_STATEMENT, "Only CREATE TABLE, INSERT INTO, and SELECT * FROM are supported in Phase 1");

    if (!check(TokenType.SEMICOLON) && !check(TokenType.EOF)) {
      throw error(peek(), SqlErrorCode.UNSUPPORTED_STATEMENT, "Unsupported clause in Phase 1 near token: " + peek().getLexeme());
    }
    match(TokenType.SEMICOLON);
    consume(TokenType.EOF, SqlErrorCode.UNEXPECTED_TOKEN, "Unexpected trailing input");
    return stmt;
  }

  private Statement parseCreateTable() {
    consume(TokenType.TABLE, SqlErrorCode.UNEXPECTED_TOKEN, "Expected TABLE after CREATE");
    String tableName = identifier("Expected table name");
    consume(TokenType.LPAREN, SqlErrorCode.UNEXPECTED_TOKEN, "Expected '(' after table name");
    List<ColumnDef> cols = new ArrayList<>();
    do {
      String name = identifier("Expected column name");
      String type = identifier("Expected column type");
      cols.add(new ColumnDef(name, type));
    } while (match(TokenType.COMMA));
    consume(TokenType.RPAREN, SqlErrorCode.UNEXPECTED_TOKEN, "Expected ')' after column definitions");
    return new CreateTableStatement(tableName, cols);
  }

  private Statement parseInsert() {
    consume(TokenType.INTO, SqlErrorCode.UNEXPECTED_TOKEN, "Expected INTO after INSERT");
    String tableName = identifier("Expected table name after INTO");
    consume(TokenType.VALUES, SqlErrorCode.UNEXPECTED_TOKEN, "Expected VALUES after table name");
    consume(TokenType.LPAREN, SqlErrorCode.UNEXPECTED_TOKEN, "Expected '(' after VALUES");
    List<LiteralValue> values = new ArrayList<>();
    do { values.add(new LiteralValue(parseLiteral())); } while (match(TokenType.COMMA));
    consume(TokenType.RPAREN, SqlErrorCode.UNEXPECTED_TOKEN, "Expected ')' after values list");
    return new InsertStatement(tableName, values);
  }

  private Statement parseSelect() {
    consume(TokenType.STAR, SqlErrorCode.UNEXPECTED_TOKEN, "Expected '*' after SELECT");
    consume(TokenType.FROM, SqlErrorCode.UNEXPECTED_TOKEN, "Expected FROM after SELECT *");
    String tableName = identifier("Expected table name after FROM");
    return new SelectAllStatement(tableName);
  }

  private Object parseLiteral() {
    if (match(TokenType.STRING)) return previous().getLexeme();
    if (match(TokenType.NUMBER)) return Long.parseLong(previous().getLexeme());
    throw error(peek(), SqlErrorCode.UNEXPECTED_TOKEN, "Expected literal value (string or integer)");
  }

  private String identifier(String msg) {
    Token t = consume(TokenType.IDENTIFIER, SqlErrorCode.INVALID_IDENTIFIER, msg);
    return t.getLexeme();
  }

  private boolean match(TokenType t) { if (check(t)) { advance(); return true; } return false; }
  private boolean check(TokenType t) { return peek().getType() == t; }
  private Token advance() { if (!isAtEnd()) current++; return previous(); }
  private boolean isAtEnd() { return peek().getType() == TokenType.EOF; }
  private Token peek() { return tokens.get(current); }
  private Token previous() { return tokens.get(current - 1); }

  private Token consume(TokenType type, SqlErrorCode code, String message) {
    if (check(type)) return advance();
    throw error(peek(), code, message + " at position " + peek().getPosition());
  }

  private SqlParseException error(Token t, SqlErrorCode code, String msg) {
    return new SqlParseException(code, msg, t.getPosition());
  }
}
