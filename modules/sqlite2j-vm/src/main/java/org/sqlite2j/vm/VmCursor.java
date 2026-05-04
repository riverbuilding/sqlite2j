package org.sqlite2j.vm;

import java.util.List;

public final class VmCursor {
  private final List<VmRow> rows;
  private int index = -1;

  public VmCursor(List<VmRow> rows) {
    this.rows = rows;
  }

  public boolean next() {
    index++;
    return index < rows.size();
  }

  public VmRow current() {
    if (index < 0 || index >= rows.size()) {
      throw new IllegalStateException("Cursor is not positioned on a row");
    }
    return rows.get(index);
  }
}
