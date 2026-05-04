package org.sqlite2j.sql.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.sqlite2j.sql.SqlErrorCode;
import org.sqlite2j.sql.SqlParseException;

public final class Tokenizer {
  public List<Token> tokenize(String sql) {
    List<Token> out = new ArrayList<>();
    int i = 0;
    while (i < sql.length()) {
      char c = sql.charAt(i);
      if (Character.isWhitespace(c)) { i++; continue; }
      int pos = i;
      switch (c) {
        case '(': out.add(new Token(TokenType.LPAREN, "(", pos)); i++; continue;
        case ')': out.add(new Token(TokenType.RPAREN, ")", pos)); i++; continue;
        case ',': out.add(new Token(TokenType.COMMA, ",", pos)); i++; continue;
        case '*': out.add(new Token(TokenType.STAR, "*", pos)); i++; continue;
        case ';': out.add(new Token(TokenType.SEMICOLON, ";", pos)); i++; continue;
        case '\'': {
          int start = i + 1;
          i++;
          StringBuilder sb = new StringBuilder();
          while (i < sql.length() && sql.charAt(i) != '\'') sb.append(sql.charAt(i++));
          if (i >= sql.length()) throw new SqlParseException(SqlErrorCode.UNTERMINATED_STRING, "Unterminated string literal", pos);
          i++;
          out.add(new Token(TokenType.STRING, sb.toString(), start));
          continue;
        }
        default:
      }
      if (Character.isDigit(c)) {
        int s = i;
        while (i < sql.length() && Character.isDigit(sql.charAt(i))) i++;
        out.add(new Token(TokenType.NUMBER, sql.substring(s, i), s));
        continue;
      }
      if (Character.isLetter(c) || c == '_') {
        int s = i;
        while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) i++;
        String lex = sql.substring(s, i);
        out.add(new Token(keywordOf(lex), lex, s));
        continue;
      }
      throw new SqlParseException(SqlErrorCode.SYNTAX_ERROR, "Unexpected character '" + c + "'", pos);
    }
    out.add(new Token(TokenType.EOF, "", sql.length()));
    return out;
  }

  private TokenType keywordOf(String word) {
    return switch (word.toUpperCase(Locale.ROOT)) {
      case "CREATE" -> TokenType.CREATE;
      case "TABLE" -> TokenType.TABLE;
      case "INSERT" -> TokenType.INSERT;
      case "INTO" -> TokenType.INTO;
      case "VALUES" -> TokenType.VALUES;
      case "SELECT" -> TokenType.SELECT;
      case "FROM" -> TokenType.FROM;
      default -> TokenType.IDENTIFIER;
    };
  }
}
