package org.sqlite2j.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.sqlite2j.vm.api.Sqlite2jConnection;
import org.sqlite2j.vm.api.Sqlite2jStatement;
import org.sqlite2j.vm.api.StepResult;

public final class Sqlite2jShell {
  private Sqlite2jShell() {}

  public static void main(String[] args) throws Exception {
    String db = "sqlite2j.db";
    String oneShotSql = null;
    for (int i = 0; i < args.length; i++) {
      if ("--db".equals(args[i]) && i + 1 < args.length) db = args[++i];
      if ("--sql".equals(args[i]) && i + 1 < args.length) oneShotSql = args[++i];
    }

    try (Sqlite2jConnection conn = Sqlite2jConnection.open(db)) {
      if (oneShotSql != null) {
        runSql(conn, oneShotSql);
        return;
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;
        if (".exit".equals(line) || ".quit".equals(line)) break;
        runSql(conn, line);
      }
    }
  }

  private static void runSql(Sqlite2jConnection conn, String sql) {
    try (Sqlite2jStatement statement = conn.prepare(sql)) {
      while (statement.step() == StepResult.ROW) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < statement.columnCount(); i++) {
          if (i > 0) row.append("|");
          row.append(statement.columnText(i));
        }
        System.out.println(row);
      }
    }
  }
}
