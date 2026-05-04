package org.sqlite2j.sql;

public final class SqlParseException extends RuntimeException {
  private final SqlErrorCode code;
  private final int position;

  public SqlParseException(SqlErrorCode code, String message, int position) {
    super(message);
    this.code = code;
    this.position = position;
  }

  public SqlErrorCode getCode() {
    return code;
  }

  public int getPosition() {
    return position;
  }
}
