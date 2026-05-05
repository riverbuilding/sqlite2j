package org.sqlite2j.pager;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

public final class Pager implements AutoCloseable {
  public static final int DEFAULT_PAGE_SIZE = 4096;
  private final RandomAccessFile file;
  private final int pageSize;

  public Pager(Path path, int pageSize) throws IOException {
    this.file = new RandomAccessFile(path.toFile(), "rw");
    this.pageSize = pageSize;
  }

  public int pageSize() { return pageSize; }

  public Page readPage(int pageNumber) throws IOException {
    byte[] data = new byte[pageSize];
    long offset = (long) (pageNumber - 1) * pageSize;
    if (file.length() >= offset + pageSize) {
      file.seek(offset);
      file.readFully(data);
    }
    return new Page(pageNumber, data);
  }

  public void writePage(Page page) throws IOException {
    long offset = (long) (page.getPageNumber() - 1) * pageSize;
    file.seek(offset);
    file.write(page.getData());
    file.getFD().sync();
  }

  @Override
  public void close() throws IOException {
    file.close();
  }
}
