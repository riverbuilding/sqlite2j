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

  @Test
  void selectOrderByGoldenProgram() {
    Program program = compiler.compile("SELECT * FROM users ORDER BY name DESC;");
    assertEquals(
        "000 OPEN_READ users -\n" +
        "001 SCAN_TABLE users -\n" +
        "002 RESULT_ROW * name:DESC\n" +
        "003 HALT - -",
        program.toDeterministicString());
  }

  @Test
  void selectWhereGoldenProgram() {
    Program program = compiler.compile("SELECT * FROM users WHERE id = 1;");
    assertEquals(
        "000 OPEN_READ users -\n" +
        "001 SCAN_TABLE users C(id):EQ:L(1)\n" +
        "002 RESULT_ROW * -\n" +
        "003 HALT - -",
        program.toDeterministicString());
  }

  @Test
  void updateWhereGoldenProgram() {
    Program program = compiler.compile("UPDATE users SET name = 'bob' WHERE id = 1;");
    assertEquals(
        "000 OPEN_WRITE users -\n" +
        "001 SCAN_TABLE users C(id):EQ:L(1)\n" +
        "002 UPDATE_ROWS name 'bob'\n" +
        "003 HALT - -",
        program.toDeterministicString());
  }

  @Test
  void deleteWhereGoldenProgram() {
    Program program = compiler.compile("DELETE FROM users WHERE id = 1;");
    assertEquals(
        "000 OPEN_WRITE users -\n" +
        "001 SCAN_TABLE users C(id):EQ:L(1)\n" +
        "002 DELETE_ROWS users -\n" +
        "003 HALT - -",
        program.toDeterministicString());
  }

  @Test
  void selectWhereOrderByGoldenProgram() {
    Program program = compiler.compile("SELECT * FROM users WHERE id = 1 ORDER BY name ASC;");
    assertEquals(
        "000 OPEN_READ users -\n" +
        "001 SCAN_TABLE users C(id):EQ:L(1)\n" +
        "002 RESULT_ROW * name:ASC\n" +
        "003 HALT - -",
        program.toDeterministicString());
  }
}
