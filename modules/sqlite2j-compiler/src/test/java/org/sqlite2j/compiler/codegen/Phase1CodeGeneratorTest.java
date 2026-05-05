package org.sqlite2j.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class Phase1CodeGeneratorTest {
  private final CompilerFacade compiler = new CompilerFacade();

  @Test
  void createTableGoldenProgram() {
    Program program = compiler.compile("CREATE TABLE users (id INT, name TEXT);");
    assertEquals(
        "000 CREATE_TABLE users id:INT,name:TEXT\n" +
        "001 HALT - -",
        program.toDeterministicString());
  }

  @Test
  void insertGoldenProgram() {
    Program program = compiler.compile("INSERT INTO users VALUES (1, 'alice');");
    assertEquals(
        "000 OPEN_WRITE users -\n" +
        "001 MAKE_RECORD 1,'alice' -\n" +
        "002 INSERT_ROW users -\n" +
        "003 HALT - -",
        program.toDeterministicString());
  }

  @Test
  void selectGoldenProgram() {
    Program program = compiler.compile("SELECT * FROM users;");
    assertEquals(
        "000 OPEN_READ users -\n" +
        "001 SCAN_TABLE users -\n" +
        "002 RESULT_ROW * -\n" +
        "003 HALT - -",
        program.toDeterministicString());
  }
}
