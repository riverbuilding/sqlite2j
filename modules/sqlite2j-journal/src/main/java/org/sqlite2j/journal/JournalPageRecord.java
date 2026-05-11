package org.sqlite2j.journal;

public final class JournalPageRecord {
  private final int pageNumber;
  private final byte[] preimage;

  public JournalPageRecord(int pageNumber, byte[] preimage) {
    this.pageNumber = pageNumber;
    this.preimage = preimage;
  }

  public int getPageNumber() { return pageNumber; }
  public byte[] getPreimage() { return preimage; }
}
