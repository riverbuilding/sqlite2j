package org.sqlite2j.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sqlite2jShellTest {
  @Test
  void oneShotSelectPrintsDeterministicRows() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    java.io.InputStream originalIn = System.in;
    String script = "CREATE TABLE users (id INT, name TEXT);\n" +
        "INSERT INTO users VALUES (1, 'alice');\n" +
        "SELECT * FROM users;\n" +
        ".exit\n";
    System.setOut(new PrintStream(out));
    System.setIn(new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)));
    try {
      Sqlite2jShell.main(new String[] {"--db", "cli-test.db"});
    } finally {
      System.setOut(originalOut);
      System.setIn(originalIn);
    }

    String text = out.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("1|alice"));
  }
}
