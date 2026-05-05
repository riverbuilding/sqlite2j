package org.sqlite2j.btree;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TableLeafPage {
  private static final byte PAGE_TYPE_LEAF = 0x0D;
  private static final int HEADER_SIZE = 8;
  private static final int MAX_CELLS = 128;

  public byte[] empty(int pageSize) {
    byte[] page = new byte[pageSize];
    page[0] = PAGE_TYPE_LEAF;
    putShort(page, 1, 0);
    putShort(page, 3, HEADER_SIZE);
    putShort(page, 5, pageSize);
    page[7] = 0;
    return page;
  }

  public byte[] insert(byte[] page, List<String> values) {
    short cellCount = getShort(page, 1);
    if (cellCount >= MAX_CELLS) {
      throw new IllegalStateException("Leaf page full; split strategy deferred in phase 1");
    }
    byte[] cell = encodeCell(values);
    short contentStart = getShort(page, 5);
    short newStart = (short) (contentStart - cell.length);
    int ptrOffset = HEADER_SIZE + cellCount * 2;
    if (newStart <= ptrOffset + 2) {
      throw new IllegalStateException("Leaf page space exhausted; split strategy deferred in phase 1");
    }
    System.arraycopy(cell, 0, page, newStart, cell.length);
    putShort(page, ptrOffset, newStart);
    putShort(page, 1, cellCount + 1);
    putShort(page, 5, newStart);
    return page;
  }

  public List<List<String>> scan(byte[] page) {
    short cellCount = getShort(page, 1);
    List<List<String>> rows = new ArrayList<List<String>>();
    for (int i = 0; i < cellCount; i++) {
      short offset = getShort(page, HEADER_SIZE + i * 2);
      rows.add(decodeCell(page, offset));
    }
    return rows;
  }

  private byte[] encodeCell(List<String> values) {
    List<byte[]> encoded = new ArrayList<byte[]>();
    int total = 2;
    for (String v : values) {
      byte[] b = v.getBytes(StandardCharsets.UTF_8);
      encoded.add(b);
      total += 2 + b.length;
    }
    ByteBuffer buffer = ByteBuffer.allocate(total);
    buffer.putShort((short) values.size());
    for (byte[] b : encoded) {
      buffer.putShort((short) b.length);
      buffer.put(b);
    }
    return buffer.array();
  }

  private List<String> decodeCell(byte[] page, int offset) {
    ByteBuffer buffer = ByteBuffer.wrap(page, offset, page.length - offset);
    int cols = Short.toUnsignedInt(buffer.getShort());
    List<String> row = new ArrayList<String>();
    for (int i = 0; i < cols; i++) {
      int len = Short.toUnsignedInt(buffer.getShort());
      byte[] value = new byte[len];
      buffer.get(value);
      row.add(new String(value, StandardCharsets.UTF_8));
    }
    return row;
  }

  private void putShort(byte[] page, int offset, int value) {
    page[offset] = (byte) ((value >>> 8) & 0xFF);
    page[offset + 1] = (byte) (value & 0xFF);
  }

  private short getShort(byte[] page, int offset) {
    return (short) (((page[offset] & 0xFF) << 8) | (page[offset + 1] & 0xFF));
  }
}
