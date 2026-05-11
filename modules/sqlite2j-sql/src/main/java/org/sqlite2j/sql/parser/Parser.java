package org.sqlite2j.sql.parser;

import java.util.ArrayList;
import java.util.List;
import org.sqlite2j.sql.SqlErrorCode;
import org.sqlite2j.sql.SqlParseException;
import org.sqlite2j.sql.ast.BeginTransactionStatement;
import org.sqlite2j.sql.ast.ColumnDef;
import org.sqlite2j.sql.ast.ColumnExpression;
import org.sqlite2j.sql.ast.ComparisonExpression;
import org.sqlite2j.sql.ast.CommitStatement;
import org.sqlite2j.sql.ast.ComparisonOperator;
import org.sqlite2j.sql.ast.CreateTableStatement;
import org.sqlite2j.sql.ast.CreateIndexStatement;
import org.sqlite2j.sql.ast.DeleteStatement;
import org.sqlite2j.sql.ast.Expression;
import org.sqlite2j.sql.ast.InsertStatement;
import org.sqlite2j.sql.ast.LiteralExpression;
import org.sqlite2j.sql.ast.LiteralValue;
import org.sqlite2j.sql.ast.OrderByClause;
import org.sqlite2j.sql.ast.RollbackStatement;
import org.sqlite2j.sql.ast.SelectAllStatement;
import org.sqlite2j.sql.ast.Statement;
import org.sqlite2j.sql.ast.UpdateAssignment;
import org.sqlite2j.sql.ast.UpdateStatement;

public final class Parser {
  private List<Token> tokens;
  private int current;

  public Statement parse(String sql) {
    this.tokens = new Tokenizer().tokenize(sql);
    this.current = 0;
    Statement stmt;
    if (match(TokenType.CREATE)) stmt = parseCreate();
    else if (match(TokenType.INSERT)) stmt = parseInsert();
    else if (match(TokenType.SELECT)) stmt = parseSelect();
    else if (match(TokenType.UPDATE)) stmt = parseUpdate();
    else if (match(TokenType.DELETE)) stmt = parseDelete();
    else if (match(TokenType.BEGIN)) stmt = parseBegin();
    else if (match(TokenType.COMMIT)) stmt = parseCommit();
    else if (match(TokenType.ROLLBACK)) stmt = parseRollback();
    else throw error(peek(), SqlErrorCode.UNSUPPORTED_STATEMENT, "Only CREATE TABLE, CREATE INDEX, INSERT INTO, SELECT * FROM, UPDATE, DELETE, BEGIN, COMMIT, and ROLLBACK are supported");

    if (!check(TokenType.SEMICOLON) && !check(TokenType.EOF)) {
      throw error(peek(), SqlErrorCode.UNSUPPORTED_STATEMENT, "Unsupported clause in Phase 1 near token: " + peek().getLexeme());
    }
    match(TokenType.SEMICOLON);
    consume(TokenType.EOF, SqlErrorCode.UNEXPECTED_TOKEN, "Unexpected trailing input");
    return stmt;
  }

  private Statement parseCreate() {
    if (match(TokenType.TABLE)) return parseCreateTable();
    if (match(TokenType.INDEX)) return parseCreateIndex();
    throw error(peek(), SqlErrorCode.UNSUPPORTED_STATEMENT, "Only CREATE TABLE and CREATE INDEX are supported");
  }


  private Statement parseBegin() {
    if (match(TokenType.TRANSACTION)) {
      return new BeginTransactionStatement();
    }
    if (check(TokenType.IDENTIFIER)) {
      String modifier = peek().getLexeme();
      if (modifier.equalsIgnoreCase("DEFERRED")
          || modifier.equalsIgnoreCase("IMMEDIATE")
          || modifier.equalsIgnoreCase("EXCLUSIVE")) {
        throw error(peek(), SqlErrorCode.UNSUPPORTED_STATEMENT, "BEGIN modifier is not supported in Phase 3: " + modifier);
      }
    }
    return new BeginTransactionStatement();
  }

  private Statement parseCommit() {
    return new CommitStatement();
  }

  private Statement parseRollback() {
    return new RollbackStatement();
  }

  private Statement parseDelete() {
    consume(TokenType.FROM, SqlErrorCode.UNEXPECTED_TOKEN, "Expected FROM after DELETE");
    String tableName = identifier("Expected table name after DELETE FROM");
    Expression whereExpression = null;
    if (match(TokenType.WHERE)) {
      whereExpression = parseComparisonExpression();
    }
    return new DeleteStatement(tableName, whereExpression);
  }

  private Statement parseUpdate() {
    String tableName = identifier("Expected table name after UPDATE");
    consume(TokenType.SET, SqlErrorCode.UNEXPECTED_TOKEN, "Expected SET after UPDATE table name");
    String columnName = identifier("Expected column name in UPDATE assignment");
    consume(TokenType.EQUAL, SqlErrorCode.UNEXPECTED_TOKEN, "Expected '=' in UPDATE assignment");
    LiteralValue literalValue = new LiteralValue(parseLiteral());
    List<UpdateAssignment> assignments = new ArrayList<UpdateAssignment>();
    assignments.add(new UpdateAssignment(columnName, literalValue));

    Expression whereExpression = null;
    if (match(TokenType.WHERE)) {
      whereExpression = parseComparisonExpression();
    }
    return new UpdateStatement(tableName, assignments, whereExpression);
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

  private Statement parseCreateIndex() {
    String indexName = identifier("Expected index name after CREATE INDEX");
    consume(TokenType.ON, SqlErrorCode.UNEXPECTED_TOKEN, "Expected ON after index name");
    String tableName = identifier("Expected table name after ON");
    consume(TokenType.LPAREN, SqlErrorCode.UNEXPECTED_TOKEN, "Expected '(' after table name");
    String columnName = identifier("Expected column name in index definition");
    consume(TokenType.RPAREN, SqlErrorCode.UNEXPECTED_TOKEN, "Expected ')' after index column");
    return new CreateIndexStatement(indexName, tableName, columnName);
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
