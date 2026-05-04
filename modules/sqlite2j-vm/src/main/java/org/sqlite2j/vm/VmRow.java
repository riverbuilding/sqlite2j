package org.sqlite2j.vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VmRow {
  private final List<VmValue> values;

  public VmRow(List<VmValue> values) {
    this.values = Collections.unmodifiableList(new ArrayList<VmValue>(values));
  }

  public List<VmValue> getValues() { return values; }
}
