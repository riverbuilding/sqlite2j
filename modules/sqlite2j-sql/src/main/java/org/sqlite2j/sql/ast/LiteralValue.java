package org.sqlite2j.sql.ast;

public final class LiteralValue {
  private final Object value;

  public LiteralValue(Object value) {
    this.value = value;
  }

  public Object getValue() { return value; }
}
