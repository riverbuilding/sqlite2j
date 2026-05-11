package org.sqlite2j.journal;

public enum CommitMarker {
  INCOMPLETE(0),
  COMMITTED(1);

  private final int code;

  CommitMarker(int code) {
    this.code = code;
  }

  int code() {
    return code;
  }

  static CommitMarker fromCode(int code) {
    if (code == 0) return INCOMPLETE;
    if (code == 1) return COMMITTED;
    throw new IllegalArgumentException("Invalid commit marker code: " + code);
  }
}
