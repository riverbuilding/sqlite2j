package org.sqlite2j.compiler.codegen;

public final class Instruction {
  private final int pc;
  private final Opcode opcode;
  private final String p1;
  private final String p2;

  public Instruction(int pc, Opcode opcode, String p1, String p2) {
    this.pc = pc;
    this.opcode = opcode;
    this.p1 = p1;
    this.p2 = p2;
  }

  public int getPc() { return pc; }
  public Opcode getOpcode() { return opcode; }
  public String getP1() { return p1; }
  public String getP2() { return p2; }

  public String toDeterministicString() {
    return String.format("%03d %s %s %s", pc, opcode.name(), nz(p1), nz(p2));
  }

  private String nz(String v) { return v == null ? "-" : v; }
}
