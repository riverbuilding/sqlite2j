package org.sqlite2j.pager;

public final class Page {
  private final int pageNumber;
  private final byte[] data;

  public Page(int pageNumber, byte[] data) {
    this.pageNumber = pageNumber;
    this.data = data;
  }

  public int getPageNumber() { return pageNumber; }
  public byte[] getData() { return data; }
}
