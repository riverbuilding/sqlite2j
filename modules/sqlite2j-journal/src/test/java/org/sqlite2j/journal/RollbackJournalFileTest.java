package org.sqlite2j.journal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RollbackJournalFileTest {
  private final RollbackJournalFile journal = new RollbackJournalFile();

  @Test
  void derivesJournalPathAsDatabaseSidecar() {
    Path db = Path.of("/tmp/main.db");
    assertEquals(Path.of("/tmp/main.db-journal"), journal.derivePath(db));
  }

  @Test
  void createsAndParsesWellFormedJournal() throws Exception {
    Path dir = Files.createTempDirectory("sqlite2j-journal");
    Path file = dir.resolve("test.db-journal");

    journal.create(file, 8);
    journal.appendPageRecord(file, 1, new byte[] {1,2,3,4,5,6,7,8}, 8);

    ParsedJournal parsed = journal.parse(file);
    assertEquals(1, parsed.getHeader().getVersion());
    assertEquals(8, parsed.getHeader().getPageSize());
    assertEquals(CommitMarker.INCOMPLETE, parsed.getHeader().getCommitMarker());
    assertEquals(1, parsed.getRecords().size());
    assertEquals(1, parsed.getRecords().get(0).getPageNumber());
    assertArrayEquals(new byte[] {1,2,3,4,5,6,7,8}, parsed.getRecords().get(0).getPreimage());
  }

  @Test
  void rejectsInvalidMagic() throws Exception {
    Path file = Files.createTempFile("sqlite2j-journal", ".bin");
    Files.write(file, new byte[] {0,1,2,3,4,5,6,7});
    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> journal.parse(file));
    assertEquals("Invalid journal magic", ex.getMessage());
  }

  @Test
  void rejectsInvalidPayloadLength() throws Exception {
    Path file = Files.createTempFile("sqlite2j-journal", ".bin");
    journal.create(file, 8);
    // invalid payload length 4 for page size 8
    Files.write(file, new byte[] {0,0,0,1, 0,0,0,4, 1,2,3,4}, java.nio.file.StandardOpenOption.APPEND);

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> journal.parse(file));
    assertEquals("Invalid payload length: 4", ex.getMessage());
  }

  @Test
  void rejectsTruncatedPayload() throws Exception {
    Path file = Files.createTempFile("sqlite2j-journal", ".bin");
    journal.create(file, 8);
    Files.write(file, new byte[] {0,0,0,1, 0,0,0,8, 1,2,3}, java.nio.file.StandardOpenOption.APPEND);

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> journal.parse(file));
    assertEquals("Truncated page payload", ex.getMessage());
  }
}
