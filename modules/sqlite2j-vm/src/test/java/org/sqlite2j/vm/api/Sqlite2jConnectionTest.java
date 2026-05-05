package org.sqlite2j.vm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class Sqlite2jConnectionTest {
  @Test
  void openPrepareStepCloseFlow() {
    Sqlite2jConnection conn = Sqlite2jConnection.open("catalog-placeholder.db");
    conn.prepare("CREATE TABLE users (id INT, name TEXT);").close();
    conn.prepare("INSERT INTO users VALUES (1, 'alice');").close();

    Sqlite2jStatement select = conn.prepare("SELECT * FROM users;");
    assertEquals(StepResult.ROW, select.step());
    assertEquals(2, select.columnCount());
    assertEquals("1", select.columnText(0));
    assertEquals("alice", select.columnText(1));
    assertEquals(StepResult.DONE, select.step());
    conn.close();
  }
}
