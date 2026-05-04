package org.sqlite2j.compiler.codegen;

public enum Opcode {
  CREATE_TABLE,
  OPEN_WRITE,
  OPEN_READ,
  MAKE_RECORD,
  INSERT_ROW,
  SCAN_TABLE,
  RESULT_ROW,
  HALT
}
