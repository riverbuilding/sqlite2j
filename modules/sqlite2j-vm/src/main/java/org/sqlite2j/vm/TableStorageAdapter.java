package org.sqlite2j.vm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.sqlite2j.btree.BTreeTable;
import org.sqlite2j.pager.Pager;

final class TableStorageAdapter {
  private final BTreeTable bTreeTable;

  TableStorageAdapter(Path path) throws IOException {
    this.bTreeTable = new BTreeTable(new Pager(path, Pager.DEFAULT_PAGE_SIZE));
  }

  void insert(VmRow row) throws IOException {
    List<String> encoded = new ArrayList<String>();
    for (VmValue value : row.getValues()) encoded.add(encode(value));
    bTreeTable.insert(encoded);
  }

  List<VmRow> scanAll() throws IOException {
    List<List<String>> raw = bTreeTable.scanAll();
    List<VmRow> out = new ArrayList<VmRow>();
    for (List<String> row : raw) {
      List<VmValue> values = new ArrayList<VmValue>();
      for (String token : row) values.add(decode(token));
      out.add(new VmRow(values));
    }
    return out;
  }

  private String encode(VmValue value) {
    if (value.getType() == VmValue.Type.NULL) return "N:";
    if (value.getType() == VmValue.Type.INT) return "I:" + value.getValue();
    return "T:" + Base64.getEncoder().encodeToString(String.valueOf(value.getValue()).getBytes(StandardCharsets.UTF_8));
  }

  private VmValue decode(String token) {
    if (token.startsWith("N:")) return VmValue.nullValue();
    if (token.startsWith("I:")) return VmValue.ofInt(Long.parseLong(token.substring(2)));
    if (token.startsWith("T:")) {
      byte[] bytes = Base64.getDecoder().decode(token.substring(2));
      return VmValue.ofText(new String(bytes, StandardCharsets.UTF_8));
    }
    throw new IllegalStateException("Invalid table storage token: " + token);
  }
}
