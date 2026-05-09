package org.sqlite2j.journal;

public final class JournalHeader {
  private final int version;
  private final int pageSize;
  private final int reservedFlags;
  private final CommitMarker commitMarker;

  public JournalHeader(int version, int pageSize, int reservedFlags, CommitMarker commitMarker) {
    this.version = version;
    this.pageSize = pageSize;
    this.reservedFlags = reservedFlags;
    this.commitMarker = commitMarker;
  }

  public int getVersion() { return version; }
  public int getPageSize() { return pageSize; }
  public int getReservedFlags() { return reservedFlags; }
  public CommitMarker getCommitMarker() { return commitMarker; }
}
