package org.sqlite2j.vm.api;

import java.util.Collections;
import java.util.List;
import org.sqlite2j.compiler.codegen.CompilerFacade;
import org.sqlite2j.compiler.codegen.Program;
import org.sqlite2j.vm.VirtualMachine;
import org.sqlite2j.vm.VmResult;
import org.sqlite2j.vm.VmRow;
import org.sqlite2j.vm.VmValue;

public final class Sqlite2jStatement implements AutoCloseable {
  private final CompilerFacade compiler;
  private final VirtualMachine vm;
  private final String sql;
  private boolean executed;
  private List<VmRow> rows = Collections.emptyList();
  private int rowIndex = -1;

  Sqlite2jStatement(CompilerFacade compiler, VirtualMachine vm, String sql) {
    this.compiler = compiler;
    this.vm = vm;
    this.sql = sql;
  }

  public StepResult step() {
    if (!executed) {
      Program p = compiler.compile(sql);
      VmResult result = vm.execute(p);
      rows = result.getRows();
      executed = true;
    }
    rowIndex++;
    return rowIndex < rows.size() ? StepResult.ROW : StepResult.DONE;
  }

  public int columnCount() {
    if (rowIndex < 0 || rowIndex >= rows.size()) return 0;
    return rows.get(rowIndex).getValues().size();
  }

  public String columnText(int index) {
    VmValue value = rows.get(rowIndex).getValues().get(index);
    return value.getValue() == null ? "null" : String.valueOf(value.getValue());
  }

  public void close() {
    if (!executed) {
      while (step() == StepResult.ROW) { }
    }
    rows = Collections.emptyList();
  }
}
