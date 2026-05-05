package org.sqlite2j.compiler.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Program {
  private final List<Instruction> instructions = new ArrayList<>();

  public void add(Opcode opcode, String p1, String p2) {
    instructions.add(new Instruction(instructions.size(), opcode, p1, p2));
  }

  public List<Instruction> getInstructions() {
    return Collections.unmodifiableList(instructions);
  }

  public String toDeterministicString() {
    StringBuilder out = new StringBuilder();
    for (Instruction instruction : instructions) {
      if (out.length() > 0) out.append('\n');
      out.append(instruction.toDeterministicString());
    }
    return out.toString();
  }
}
