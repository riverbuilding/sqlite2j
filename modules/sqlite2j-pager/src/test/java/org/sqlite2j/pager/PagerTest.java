package org.sqlite2j.pager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PagerTest {
  @Test
  void roundTripSinglePageDeterministic() throws Exception {
    Path tmp = Files.createTempFile("pager-test", ".db");
    byte[] payload = new byte[Pager.DEFAULT_PAGE_SIZE];
    payload[0] = 0x11;
    payload[1] = 0x22;
    payload[2] = 0x33;

    try (Pager pager = new Pager(tmp, Pager.DEFAULT_PAGE_SIZE)) {
      pager.writePage(new Page(1, payload));
      Page read = pager.readPage(1);
      assertArrayEquals(payload, read.getData());
    }
  }
}
