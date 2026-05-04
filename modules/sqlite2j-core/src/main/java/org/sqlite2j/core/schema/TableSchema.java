package org.sqlite2j.core.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sqlite2j.sql.ast.ColumnDef;

public final class TableSchema {
  private final String name;
  private final List<ColumnDef> columns;

  public TableSchema(String name, List<ColumnDef> columns) {
    this.name = name;
    this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
  }

  public String getName() {
    return name;
  }

  public List<ColumnDef> getColumns() {
    return columns;
  }
}
