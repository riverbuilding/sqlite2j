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
}
