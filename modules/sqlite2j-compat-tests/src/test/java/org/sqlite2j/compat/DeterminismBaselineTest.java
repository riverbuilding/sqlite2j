package org.sqlite2j.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DeterminismBaselineTest {

  @Test
  void runtimeDefaultsAreDeterministic() {
    assertEquals(ZoneId.of("UTC"), ZoneId.systemDefault());
    assertEquals(Locale.US, Locale.getDefault());
    assertEquals("UTF-8", Charset.defaultCharset().name());
  }
}
