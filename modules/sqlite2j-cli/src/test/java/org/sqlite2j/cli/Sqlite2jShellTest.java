package org.sqlite2j.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Sqlite2jShellTest {
  @Test
  void interactiveSelectPrintsDeterministicSnapshot() throws Exception {
    ByteBufferOutput out = new ByteBufferOutput(8192);
    PrintStream originalOut = System.out;
    java.io.InputStream originalIn = System.in;
    Path db = Files.createTempFile("sqlite2j-cli", ".db");
    String script = "CREATE TABLE users (id INT, name TEXT);\n" +
        "INSERT INTO users VALUES (1, 'alice');\n" +
        "SELECT * FROM users;\n" +
        ".exit\n";
    System.setOut(new PrintStream(out));
    System.setIn(new ByteBufferInput(ByteBuffer.wrap(script.getBytes(StandardCharsets.UTF_8))));
    try {
      Sqlite2jShell.main(new String[] {"--db", db.toString()});
    } finally {
      System.setOut(originalOut);
      System.setIn(originalIn);
    }

    String text = out.asString();
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

  private static final class ByteBufferInput extends InputStream {
    private final ByteBuffer buffer;
    ByteBufferInput(ByteBuffer buffer) { this.buffer = buffer; }
    @Override
    public int read() {
      if (!buffer.hasRemaining()) return -1;
      return buffer.get() & 0xFF;
    }
  }

  private static final class ByteBufferOutput extends OutputStream {
    private ByteBuffer buffer;
    ByteBufferOutput(int initialCapacity) { this.buffer = ByteBuffer.allocate(initialCapacity); }
    @Override
    public void write(int b) {
      ensureCapacity(1);
      buffer.put((byte) b);
    }
    private void ensureCapacity(int extra) {
      if (buffer.remaining() >= extra) return;
      ByteBuffer next = ByteBuffer.allocate(Math.max(buffer.capacity() * 2, buffer.capacity() + extra));
      buffer.flip();
      next.put(buffer);
      buffer = next;
    }
    String asString() {
      ByteBuffer copy = buffer.duplicate();
      copy.flip();
      byte[] bytes = new byte[copy.remaining()];
      copy.get(bytes);
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }
}
