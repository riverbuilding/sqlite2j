package org.sqlite2j.sql.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sqlite2j.sql.SqlErrorCode;
import org.sqlite2j.sql.SqlParseException;

class TokenizerTest {
  private final Tokenizer tokenizer = new Tokenizer();

  @Test
  void tokenizesCreateStatementSubset() {
    List<Token> tokens = tokenizer.tokenize("CREATE TABLE t (id INT);");
    assertEquals(TokenType.CREATE, tokens.get(0).getType());
    assertEquals(TokenType.TABLE, tokens.get(1).getType());
    assertEquals(TokenType.IDENTIFIER, tokens.get(2).getType());
    assertEquals(TokenType.LPAREN, tokens.get(3).getType());
    assertEquals(TokenType.IDENTIFIER, tokens.get(4).getType());
    assertEquals(TokenType.IDENTIFIER, tokens.get(5).getType());
    assertEquals(TokenType.RPAREN, tokens.get(6).getType());
    assertEquals(TokenType.SEMICOLON, tokens.get(7).getType());
  }

  @Test
  void reportsUnterminatedStringDeterministically() {
    SqlParseException ex = assertThrows(SqlParseException.class, () -> tokenizer.tokenize("INSERT INTO t VALUES ('abc);"));
    assertEquals(SqlErrorCode.UNTERMINATED_STRING, ex.getCode());
    assertEquals(22, ex.getPosition());
  }

  @Test
  void tokenizesWhereAndOrderByKeywords() {
    List<Token> tokens = tokenizer.tokenize("SELECT * FROM t WHERE id >= 1 ORDER BY name DESC;");
    assertEquals(TokenType.WHERE, tokens.get(4).getType());
    assertEquals(TokenType.GTE, tokens.get(6).getType());
    assertEquals(TokenType.ORDER, tokens.get(8).getType());
    assertEquals(TokenType.BY, tokens.get(9).getType());
    assertEquals(TokenType.DESC, tokens.get(11).getType());
  }

  @Test
  void tokenizesUpdateSetKeywords() {
    List<Token> tokens = tokenizer.tokenize("UPDATE t SET name = 'bob' WHERE id = 1;");
    assertEquals(TokenType.UPDATE, tokens.get(0).getType());
    assertEquals(TokenType.SET, tokens.get(2).getType());
    assertEquals(TokenType.WHERE, tokens.get(6).getType());
  }

  @Test
  void tokenizesDeleteKeywords() {
    List<Token> tokens = tokenizer.tokenize("DELETE FROM t WHERE id = 1;");
    assertEquals(TokenType.DELETE, tokens.get(0).getType());
    assertEquals(TokenType.FROM, tokens.get(1).getType());
    assertEquals(TokenType.WHERE, tokens.get(3).getType());
  }

  @Test
  void tokenizesComparisonOperators() {
    List<Token> tokens = tokenizer.tokenize("a = 1, b != 2, c <> 3, d < 4, e <= 5, f > 6, g >= 7;");
    assertEquals(TokenType.EQUAL, tokens.get(1).getType());
    assertEquals(TokenType.BANG_EQUAL, tokens.get(5).getType());
    assertEquals(TokenType.BANG_EQUAL, tokens.get(9).getType());
    assertEquals(TokenType.LT, tokens.get(13).getType());
    assertEquals(TokenType.LTE, tokens.get(17).getType());
    assertEquals(TokenType.GT, tokens.get(21).getType());
    assertEquals(TokenType.GTE, tokens.get(25).getType());
  }

  @Test
  void tokenizesKeywordsWithMixedCasing() {
    List<Token> tokens = tokenizer.tokenize(
        "sElEcT * fRoM t wHeRe id >= 1 oRdEr bY name dEsC; uPdAtE t sEt name = 'x'; dElEtE fRoM t;");
    assertEquals(TokenType.SELECT, tokens.get(0).getType());
    assertEquals(TokenType.FROM, tokens.get(2).getType());
    assertEquals(TokenType.WHERE, tokens.get(4).getType());
    assertEquals(TokenType.ORDER, tokens.get(8).getType());
    assertEquals(TokenType.BY, tokens.get(9).getType());
    assertEquals(TokenType.DESC, tokens.get(11).getType());
    assertEquals(TokenType.UPDATE, tokens.get(13).getType());
    assertEquals(TokenType.SET, tokens.get(15).getType());
    assertEquals(TokenType.DELETE, tokens.get(20).getType());
  }
}
