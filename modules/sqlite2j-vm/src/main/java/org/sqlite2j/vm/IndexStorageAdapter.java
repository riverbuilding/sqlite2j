package org.sqlite2j.vm;

import java.util.ArrayList;
import java.util.List;

final class IndexStorageAdapter {
  List<Integer> lookupRowPositions(List<VmRow> rows, int columnIndex, VmValue value) {
    List<Integer> out = new ArrayList<Integer>();
    for (int i = 0; i < rows.size(); i++) {
      VmValue current = rows.get(i).getValues().get(columnIndex);
      if (current.getType() == value.getType() && String.valueOf(current.getValue()).equals(String.valueOf(value.getValue()))) {
        out.add(Integer.valueOf(i));
      }
    }
    return out;
  }
}
