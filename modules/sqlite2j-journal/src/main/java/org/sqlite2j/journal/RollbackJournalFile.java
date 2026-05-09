package org.sqlite2j.journal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RollbackJournalFile {
  private static final byte[] MAGIC = new byte[] {'S', '2', 'J', 'R', 'N', 'L', '0', '1'};
  private static final int VERSION = 1;

  public Path derivePath(Path databasePath) {
    return databasePath.resolveSibling(databasePath.getFileName().toString() + "-journal");
  }

  public void create(Path journalPath, int pageSize) throws IOException {
    validatePageSize(pageSize);
    ByteBuffer header = encodeHeader(new JournalHeader(VERSION, pageSize, 0, CommitMarker.INCOMPLETE));
    Files.write(journalPath, header.array());
  }

  public void appendPageRecord(Path journalPath, int pageNumber, byte[] preimageBytes, int pageSize) throws IOException {
    if (pageNumber <= 0) throw new IllegalArgumentException("Invalid page number: " + pageNumber);
    validatePageSize(pageSize);
    if (preimageBytes == null || preimageBytes.length != pageSize) {
      throw new IllegalArgumentException("Invalid preimage length: " + (preimageBytes == null ? -1 : preimageBytes.length));
    }

    ByteBuffer record = ByteBuffer.allocate(8 + preimageBytes.length);
    record.putInt(pageNumber);
    record.putInt(preimageBytes.length);
    record.put(preimageBytes);

    OpenOption[] options = new OpenOption[] {StandardOpenOption.APPEND};
    Files.write(journalPath, record.array(), options);
  }

  public ParsedJournal parse(Path journalPath) throws IOException {
    ByteBuffer input = ByteBuffer.wrap(Files.readAllBytes(journalPath));
    JournalHeader header = decodeHeader(input);

    List<JournalPageRecord> records = new ArrayList<JournalPageRecord>();
    while (input.hasRemaining()) {
      if (input.remaining() < 8) {
        throw new IllegalStateException("Truncated page payload");
      }

      int pageNumber = input.getInt();
      int payloadLength = input.getInt();
      if (pageNumber <= 0) throw new IllegalStateException("Invalid page number: " + pageNumber);
      if (payloadLength != header.getPageSize()) throw new IllegalStateException("Invalid payload length: " + payloadLength);
      if (input.remaining() < payloadLength) throw new IllegalStateException("Truncated page payload");

      byte[] payload = new byte[payloadLength];
      input.get(payload);
      records.add(new JournalPageRecord(pageNumber, payload));
    }
    return new ParsedJournal(header, records);
  }

  private ByteBuffer encodeHeader(JournalHeader header) {
    ByteBuffer out = ByteBuffer.allocate(MAGIC.length + 16);
    out.put(MAGIC);
    out.putInt(header.getVersion());
    out.putInt(header.getPageSize());
    out.putInt(header.getReservedFlags());
    out.putInt(header.getCommitMarker().code());
    return out;
  }

  private JournalHeader decodeHeader(ByteBuffer input) {
    if (input.remaining() < MAGIC.length + 16) throw new IllegalStateException("Invalid journal magic");

    byte[] magic = new byte[MAGIC.length];
    input.get(magic);
    if (!Arrays.equals(magic, MAGIC)) {
      throw new IllegalStateException("Invalid journal magic");
    }

    int version = input.getInt();
    if (version != VERSION) throw new IllegalStateException("Invalid journal version: " + version);

    int pageSize = input.getInt();
    validatePageSize(pageSize);

    int reservedFlags = input.getInt();
    int markerCode = input.getInt();
    CommitMarker marker = CommitMarker.fromCode(markerCode);
    return new JournalHeader(version, pageSize, reservedFlags, marker);
  }

  private void validatePageSize(int pageSize) {
    if (pageSize <= 0) throw new IllegalStateException("Invalid page size: " + pageSize);
  }
}
