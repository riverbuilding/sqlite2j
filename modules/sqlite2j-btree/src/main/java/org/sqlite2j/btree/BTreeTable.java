package org.sqlite2j.btree;

import java.io.IOException;
import java.util.List;
import org.sqlite2j.pager.Page;
import org.sqlite2j.pager.Pager;

public final class BTreeTable {
  private final Pager pager;
  private final TableLeafPage leaf = new TableLeafPage();

  public BTreeTable(Pager pager) {
    this.pager = pager;
  }

  public void insert(List<String> values) throws IOException {
    Page page = pager.readPage(1);
    byte[] data = page.getData();
    if (data[0] == 0) {
      data = leaf.empty(pager.pageSize());
    }
    byte[] updated = leaf.insert(data, values);
    pager.writePage(new Page(1, updated));
  }

  public List<List<String>> scanAll() throws IOException {
    Page page = pager.readPage(1);
    if (page.getData()[0] == 0) {
      return java.util.Collections.emptyList();
    }
    return leaf.scan(page.getData());
  }
}
