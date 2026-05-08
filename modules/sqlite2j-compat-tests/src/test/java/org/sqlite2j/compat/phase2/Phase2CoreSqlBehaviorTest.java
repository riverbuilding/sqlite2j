package org.sqlite2j.compat.phase2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sqlite2j.vm.api.Sqlite2jConnection;
import org.sqlite2j.vm.api.Sqlite2jStatement;
import org.sqlite2j.vm.api.StepResult;

class Phase2CoreSqlBehaviorTest {

  @Test
  void selectWhereOrderUpdateDeleteFlow() throws Exception {
    Path db = Files.createTempFile("sqlite2j-phase2", ".db");
    try (Sqlite2jConnection conn = Sqlite2jConnection.open(db.toString())) {
      executeDone(conn, "CREATE TABLE users (id INT, name TEXT);");
      executeDone(conn, "INSERT INTO users VALUES (1, 'carl');");
      executeDone(conn, "INSERT INTO users VALUES (2, 'alice');");
      executeDone(conn, "INSERT INTO users VALUES (3, 'bob');");

      assertEquals(Arrays.asList("2|alice", "3|bob"),
          collectRows(conn, "SELECT * FROM users WHERE id >= 2 ORDER BY name ASC;"));

      executeDone(conn, "UPDATE users SET name = 'zed' WHERE id = 3;");
      assertEquals(Arrays.asList("1|carl", "2|alice", "3|zed"),
          collectRows(conn, "SELECT * FROM users ORDER BY id ASC;"));

      executeDone(conn, "DELETE FROM users WHERE id = 2;");
      assertEquals(Arrays.asList("1|carl", "3|zed"),
          collectRows(conn, "SELECT * FROM users ORDER BY id ASC;"));
    }
  }

  private void executeDone(Sqlite2jConnection conn, String sql) {
    try (Sqlite2jStatement statement = conn.prepare(sql)) {
      while (statement.step() == StepResult.ROW) { }
    }
  }

  private List<String> collectRows(Sqlite2jConnection conn, String sql) {
    List<String> rows = new ArrayList<String>();
    try (Sqlite2jStatement statement = conn.prepare(sql)) {
      while (statement.step() == StepResult.ROW) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < statement.columnCount(); i++) {
          if (i > 0) row.append('|');
          row.append(statement.columnText(i));
        }
        rows.add(row.toString());
      }
    }
    return rows;
  }
}
