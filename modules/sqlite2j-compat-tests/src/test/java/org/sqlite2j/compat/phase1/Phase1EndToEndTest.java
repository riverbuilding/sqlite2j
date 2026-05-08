package org.sqlite2j.compat.phase1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sqlite2j.sql.SqlErrorCode;
import org.sqlite2j.vm.api.Sqlite2jConnection;
import org.sqlite2j.vm.api.Sqlite2jStatement;
import org.sqlite2j.vm.api.StepResult;

class Phase1EndToEndTest {

  @Test
  void canonicalCreateInsertSelectScriptProducesStableRows() throws Exception {
    List<String> rows = runScript("testdata/sql/phase1/01_create_insert_select.sql");
    assertEquals(Arrays.asList("1|alice", "2|bob"), rows);
  }

  @Test
  void canonicalSingleTableSmokeScriptProducesStableRows() throws Exception {
    List<String> rows = runScript("testdata/sql/phase1/02_single_table_smoke.sql");
    assertEquals(Arrays.asList("10|start", "11|stop"), rows);
  }

  @Test
  void closeReopenKeepsSelectResultStableWithinProcess() throws Exception {
    Path db = Files.createTempFile("sqlite2j-persistence", ".db");
    try (Sqlite2jConnection conn = Sqlite2jConnection.open(db.toString())) {
      executeDone(conn, "CREATE TABLE users (id INT, name TEXT);");
      executeDone(conn, "INSERT INTO users VALUES (1, 'alice');");
    }

    try (Sqlite2jConnection reopened = Sqlite2jConnection.open(db.toString())) {
      assertEquals(Arrays.asList("1|alice"), collectRows(reopened, "SELECT * FROM users;"));
    }
  }

  @Test
  void unsupportedStatementsReturnClearErrors() throws Exception {
    Path db = Files.createTempFile("sqlite2j-negative", ".db");
    try (Sqlite2jConnection conn = Sqlite2jConnection.open(db.toString())) {
      executeDone(conn, "CREATE TABLE users (id INT, name TEXT);");
      executeDone(conn, "INSERT INTO users VALUES (1, 'alice');");

      // WHERE syntax is now accepted by the parser as part of Phase 2 expression work.
      assertEquals(Arrays.asList("1|alice"), collectRows(conn, "SELECT * FROM users WHERE id = 1;"));

      RuntimeException update = assertThrows(RuntimeException.class,
          () -> executeDone(conn, "UPDATE users SET name = 'bob';"));
      assertTrue(update.getMessage().contains("Unsupported statement type"));
    }
  }

  private List<String> runScript(String scriptPath) throws Exception {
    Path db = Files.createTempFile("sqlite2j-phase1-script", ".db");
    List<String> rows = new ArrayList<String>();
    try (Sqlite2jConnection conn = Sqlite2jConnection.open(db.toString())) {
      List<String> statements = readStatements(findRepoFile(scriptPath));
      for (String statement : statements) {
        rows.addAll(collectRows(conn, statement));
      }
    }
    return rows;
  }

  private List<String> readStatements(Path script) throws IOException {
    List<String> statements = new ArrayList<String>();
    List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
      statements.add(trimmed);
    }
    return statements;
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

  private Path findRepoFile(String relativePath) {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      Path candidate = current.resolve(relativePath);
      if (Files.exists(candidate)) return candidate;
      current = current.getParent();
    }
    throw new IllegalStateException("Unable to find " + relativePath);
  }
}
