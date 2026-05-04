package org.sqlite2j.compiler.codegen;

import org.sqlite2j.sql.ast.Statement;
import org.sqlite2j.sql.parser.Parser;

public final class CompilerFacade {
  private final Parser parser = new Parser();
  private final Phase1CodeGenerator codeGenerator = new Phase1CodeGenerator();

  public Program compile(String sql) {
    Statement statement = parser.parse(sql);
    return codeGenerator.lower(statement);
  }
}
