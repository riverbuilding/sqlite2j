package org.sqlite2j.sql.ast;

public final class ColumnDef {
  private final String name;
  private final String type;

  public ColumnDef(String name, String type) {
    this.name = name;
    this.type = type;
  }

  public String getName() { return name; }
  public String getType() { return type; }
}
