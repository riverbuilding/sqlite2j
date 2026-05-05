package org.sqlite2j.btree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sqlite2j.pager.Pager;

class BTreeTableTest {

  @Test
  void insertAndScanRows() throws Exception {
    Path path = Files.createTempFile("btree", ".db");
    try (Pager pager = new Pager(path, Pager.DEFAULT_PAGE_SIZE)) {
      BTreeTable table = new BTreeTable(pager);
      table.insert(Arrays.asList("1", "alice"));
      table.insert(Arrays.asList("2", "bob"));

      List<List<String>> rows = table.scanAll();
      assertEquals(2, rows.size());
      assertEquals(Arrays.asList("1", "alice"), rows.get(0));
      assertEquals(Arrays.asList("2", "bob"), rows.get(1));
    }
  }

  @Test
  void deterministicBinaryFixturePrefix() throws Exception {
    Path path = Files.createTempFile("btree-fixture", ".db");
    try (Pager pager = new Pager(path, Pager.DEFAULT_PAGE_SIZE)) {
      BTreeTable table = new BTreeTable(pager);
      table.insert(Arrays.asList("1", "alice"));
    }

    byte[] bytes = Files.readAllBytes(path);
    String headerHex = String.format("%02X%02X%02X%02X%02X%02X%02X%02X",
        bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7]);
    assertEquals("0D000100080FF400", headerHex);

    int cellOffset = ((bytes[8] & 0xFF) << 8) | (bytes[9] & 0xFF);
    StringBuilder cellHex = new StringBuilder();
    for (int i = cellOffset; i < cellOffset + 10; i++) {
      cellHex.append(String.format("%02X", bytes[i]));
    }
    assertEquals("00020001310005616C69", cellHex.toString());
  }
}
