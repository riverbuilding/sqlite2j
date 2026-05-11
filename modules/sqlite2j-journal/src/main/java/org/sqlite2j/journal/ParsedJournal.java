package org.sqlite2j.journal;

import java.util.List;

public final class ParsedJournal {
  private final JournalHeader header;
  private final List<JournalPageRecord> records;

  public ParsedJournal(JournalHeader header, List<JournalPageRecord> records) {
    this.header = header;
    this.records = records;
  }

  public JournalHeader getHeader() { return header; }
  public List<JournalPageRecord> getRecords() { return records; }
}
