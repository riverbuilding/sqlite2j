package org.sqlite2j.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Sqlite2jShellTest {
  @Test
  void interactiveSelectPrintsDeterministicSnapshot() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    java.io.InputStream originalIn = System.in;
    Path db = Files.createTempFile("sqlite2j-cli", ".db");
    String script = "CREATE TABLE users (id INT, name TEXT);\n" +
        "INSERT INTO users VALUES (1, 'alice');\n" +
        "SELECT * FROM users;\n" +
        ".exit\n";
    System.setOut(new PrintStream(out));
    System.setIn(new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)));
    try {
      Sqlite2jShell.main(new String[] {"--db", db.toString()});
    } finally {
      System.setOut(originalOut);
      System.setIn(originalIn);
    }

    String text = out.toString(StandardCharsets.UTF_8);
    String expected = Files.readString(findRepoFile("testdata/expected/phase1/cli_smoke.out"), StandardCharsets.UTF_8);
    assertEquals(expected, text);
  }

  private Path findRepoFile(String relativePath) {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      Path candidate = current.resolve(relativePath);
      if (Files.exists(candidate)) return candidate;
      current = current.getParent();
    }
    throw new IllegalStateException("Unable to find " + relativePath);
  }
}
