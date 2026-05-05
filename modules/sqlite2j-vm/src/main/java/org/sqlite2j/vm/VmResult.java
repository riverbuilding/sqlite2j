package org.sqlite2j.vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VmResult {
  private final List<VmRow> rows = new ArrayList<VmRow>();

  public void addRow(VmRow row) { rows.add(row); }

  public List<VmRow> getRows() { return Collections.unmodifiableList(rows); }
}
