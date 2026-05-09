package org.sqlite2j.journal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
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
    try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(journalPath))) {
      writeHeader(out, new JournalHeader(VERSION, pageSize, 0, CommitMarker.INCOMPLETE));
    }
  }

  public void appendPageRecord(Path journalPath, int pageNumber, byte[] preimageBytes, int pageSize) throws IOException {
    if (pageNumber <= 0) throw new IllegalArgumentException("Invalid page number: " + pageNumber);
    validatePageSize(pageSize);
    if (preimageBytes == null || preimageBytes.length != pageSize) {
      throw new IllegalArgumentException("Invalid preimage length: " + (preimageBytes == null ? -1 : preimageBytes.length));
    }

    OpenOption[] options = new OpenOption[] {StandardOpenOption.APPEND};
    try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(journalPath, options))) {
      out.writeInt(pageNumber);
      out.writeInt(preimageBytes.length);
      out.write(preimageBytes);
    }
  }

  public ParsedJournal parse(Path journalPath) throws IOException {
    try (DataInputStream in = new DataInputStream(Files.newInputStream(journalPath))) {
      JournalHeader header = readHeader(in);
      List<JournalPageRecord> records = new ArrayList<JournalPageRecord>();
      while (true) {
        try {
          int pageNumber = in.readInt();
          int payloadLength = in.readInt();
          if (pageNumber <= 0) throw new IllegalStateException("Invalid page number: " + pageNumber);
          if (payloadLength != header.getPageSize()) throw new IllegalStateException("Invalid payload length: " + payloadLength);

          byte[] payload = new byte[payloadLength];
          int read = in.read(payload);
          if (read != payloadLength) throw new IllegalStateException("Truncated page payload");
          records.add(new JournalPageRecord(pageNumber, payload));
        } catch (EOFException eof) {
          break;
        }
      }
      return new ParsedJournal(header, records);
    }
  }

  private void writeHeader(DataOutputStream out, JournalHeader header) throws IOException {
    out.write(MAGIC);
    out.writeInt(header.getVersion());
    out.writeInt(header.getPageSize());
    out.writeInt(header.getReservedFlags());
    out.writeInt(header.getCommitMarker().code());
  }

  private JournalHeader readHeader(DataInputStream in) throws IOException {
    byte[] magic = new byte[MAGIC.length];
    int readMagic = in.read(magic);
    if (readMagic != MAGIC.length || !Arrays.equals(magic, MAGIC)) {
      throw new IllegalStateException("Invalid journal magic");
    }

    int version = in.readInt();
    if (version != VERSION) throw new IllegalStateException("Invalid journal version: " + version);

    int pageSize = in.readInt();
    validatePageSize(pageSize);

    int reservedFlags = in.readInt();
    int markerCode = in.readInt();
    CommitMarker marker = CommitMarker.fromCode(markerCode);
    return new JournalHeader(version, pageSize, reservedFlags, marker);
  }

  private void validatePageSize(int pageSize) {
    if (pageSize <= 0) throw new IllegalStateException("Invalid page size: " + pageSize);
  }
}
