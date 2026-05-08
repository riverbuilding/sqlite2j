package org.sqlite2j.sql.parser;

import java.util.ArrayList;
import java.util.List;
import org.sqlite2j.sql.SqlErrorCode;
import org.sqlite2j.sql.SqlParseException;
import org.sqlite2j.sql.ast.ColumnDef;
import org.sqlite2j.sql.ast.ColumnExpression;
import org.sqlite2j.sql.ast.ComparisonExpression;
import org.sqlite2j.sql.ast.ComparisonOperator;
import org.sqlite2j.sql.ast.CreateTableStatement;
import org.sqlite2j.sql.ast.Expression;
import org.sqlite2j.sql.ast.InsertStatement;
import org.sqlite2j.sql.ast.LiteralExpression;
import org.sqlite2j.sql.ast.LiteralValue;
import org.sqlite2j.sql.ast.OrderByClause;
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

  public Expression parseExpression(String sql) {
    this.tokens = new Tokenizer().tokenize(sql);
    this.current = 0;
    Expression expression = parseComparisonExpression();
    if (match(TokenType.AND) || match(TokenType.OR)) {
      throw error(previous(), SqlErrorCode.UNSUPPORTED_STATEMENT, "Boolean conjunction is not supported yet in Phase 2");
    }
    consume(TokenType.EOF, SqlErrorCode.UNEXPECTED_TOKEN, "Unexpected trailing input in expression");
    return expression;
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
    Expression whereExpression = null;
    OrderByClause orderByClause = null;
    if (match(TokenType.WHERE)) {
      whereExpression = parseComparisonExpression();
    }
    if (match(TokenType.ORDER)) {
      consume(TokenType.BY, SqlErrorCode.UNEXPECTED_TOKEN, "Expected BY after ORDER");
      String orderColumn = identifier("Expected column name after ORDER BY");
      boolean descending = false;
      if (match(TokenType.DESC)) descending = true;
      else match(TokenType.ASC);
      orderByClause = new OrderByClause(orderColumn, descending);
    }
    return new SelectAllStatement(tableName, whereExpression, orderByClause);
  }

  private Object parseLiteral() {
    if (match(TokenType.STRING)) return previous().getLexeme();
    if (match(TokenType.NUMBER)) return Long.parseLong(previous().getLexeme());
    throw error(peek(), SqlErrorCode.UNEXPECTED_TOKEN, "Expected literal value (string or integer)");
  }

  private Expression parseComparisonExpression() {
    Expression left = parseOperand();
    ComparisonOperator operator = parseComparisonOperator();
    Expression right = parseOperand();
    return new ComparisonExpression(left, operator, right);
  }

  private Expression parseOperand() {
    if (match(TokenType.IDENTIFIER)) {
      return new ColumnExpression(previous().getLexeme());
    }
    if (match(TokenType.NULL)) {
      throw error(previous(), SqlErrorCode.UNSUPPORTED_STATEMENT, "NULL comparisons are not supported yet in Phase 2");
    }
    if (check(TokenType.STRING) || check(TokenType.NUMBER)) {
      return new LiteralExpression(new LiteralValue(parseLiteral()));
    }
    throw error(peek(), SqlErrorCode.UNEXPECTED_TOKEN, "Expected column reference or literal");
  }

  private ComparisonOperator parseComparisonOperator() {
    if (match(TokenType.EQUAL)) return ComparisonOperator.EQ;
    if (match(TokenType.BANG_EQUAL)) return ComparisonOperator.NE;
    if (match(TokenType.LT)) return ComparisonOperator.LT;
    if (match(TokenType.LTE)) return ComparisonOperator.LTE;
    if (match(TokenType.GT)) return ComparisonOperator.GT;
    if (match(TokenType.GTE)) return ComparisonOperator.GTE;
    throw error(peek(), SqlErrorCode.UNEXPECTED_TOKEN, "Expected comparison operator");
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
