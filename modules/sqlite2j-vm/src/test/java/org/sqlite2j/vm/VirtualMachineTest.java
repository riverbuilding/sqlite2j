package org.sqlite2j.vm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.sqlite2j.compiler.codegen.CompilerFacade;
import org.sqlite2j.compiler.codegen.Program;

class VirtualMachineTest {
  private final CompilerFacade compiler = new CompilerFacade();

  @Test
  void createInsertSelectRoundTrip() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);

    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'alice');"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (2, 'bob');"));

    VmResult result = vm.execute(compiler.compile("SELECT * FROM users;"));

    assertEquals(2, result.getRows().size());
    assertEquals(1L, result.getRows().get(0).getValues().get(0).getValue());
    assertEquals("alice", result.getRows().get(0).getValues().get(1).getValue());
    assertEquals(2L, result.getRows().get(1).getValues().get(0).getValue());
    assertEquals("bob", result.getRows().get(1).getValues().get(1).getValue());
  }

  @Test
  void createTableMetadataRegistered() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);

    Program create = compiler.compile("CREATE TABLE events (id INT, label TEXT);");
    vm.execute(create);

    assertEquals(1, db.getSchemaRegistry().tablesView().size());
    assertEquals(2, db.getSchemaRegistry().findTable("events").orElseThrow().getColumns().size());
  }

  @Test
  void duplicateTableCreateFails() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);

    vm.execute(compiler.compile("CREATE TABLE t (id INT);"));

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> vm.execute(compiler.compile("CREATE TABLE t (id INT);")));
    assertEquals("Table already exists: t", ex.getMessage());
  }

  @Test
  void selectOrderByUsesDeterministicSortAndStableTies() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);

    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);") );
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'bob');"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (2, 'alice');"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (3, 'bob');"));

    VmResult asc = vm.execute(compiler.compile("SELECT * FROM users ORDER BY name ASC;"));
    assertEquals(2L, asc.getRows().get(0).getValues().get(0).getValue());
    assertEquals(1L, asc.getRows().get(1).getValues().get(0).getValue());
    assertEquals(3L, asc.getRows().get(2).getValues().get(0).getValue());

    VmResult desc = vm.execute(compiler.compile("SELECT * FROM users ORDER BY name DESC;"));
    assertEquals(1L, desc.getRows().get(0).getValues().get(0).getValue());
    assertEquals(3L, desc.getRows().get(1).getValues().get(0).getValue());
    assertEquals(2L, desc.getRows().get(2).getValues().get(0).getValue());
  }

  @Test
  void selectWhereFiltersDuringScanBeforeSorting() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);

    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (2, 'bob');"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'alice');"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (3, 'carl');"));

    VmResult filtered = vm.execute(compiler.compile("SELECT * FROM users WHERE id >= 2 ORDER BY name ASC;"));
    assertEquals(2, filtered.getRows().size());
    assertEquals(2L, filtered.getRows().get(0).getValues().get(0).getValue());
    assertEquals(3L, filtered.getRows().get(1).getValues().get(0).getValue());
  }

  @Test
  void updateRowsSupportsFilteredAndUnfilteredForms() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);

    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'alice');"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (2, 'bob');"));

    vm.execute(compiler.compile("UPDATE users SET name = 'x' WHERE id = 1;"));
    VmResult first = vm.execute(compiler.compile("SELECT * FROM users ORDER BY id ASC;"));
    assertEquals("x", first.getRows().get(0).getValues().get(1).getValue());
    assertEquals("bob", first.getRows().get(1).getValues().get(1).getValue());

    vm.execute(compiler.compile("UPDATE users SET name = 'z';"));
    VmResult second = vm.execute(compiler.compile("SELECT * FROM users ORDER BY id ASC;"));
    assertEquals("z", second.getRows().get(0).getValues().get(1).getValue());
    assertEquals("z", second.getRows().get(1).getValues().get(1).getValue());
  }

  @Test
  void deleteRowsSupportsFilteredAndUnfilteredForms() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);

    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'alice');"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (2, 'bob');"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (3, 'carl');"));

    vm.execute(compiler.compile("DELETE FROM users WHERE id = 2;"));
    VmResult first = vm.execute(compiler.compile("SELECT * FROM users ORDER BY id ASC;"));
    assertEquals(2, first.getRows().size());
    assertEquals(1L, first.getRows().get(0).getValues().get(0).getValue());
    assertEquals(3L, first.getRows().get(1).getValues().get(0).getValue());

    vm.execute(compiler.compile("DELETE FROM users;"));
    VmResult second = vm.execute(compiler.compile("SELECT * FROM users ORDER BY id ASC;"));
    assertEquals(0, second.getRows().size());
  }

  @Test
  void whereWithUnknownColumnFailsDeterministically() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);
    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'alice');"));

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> vm.execute(compiler.compile("SELECT * FROM users WHERE missing = 1;")));
    assertEquals("Unknown column: missing", ex.getMessage());
  }

  @Test
  void orderByWithUnknownColumnFailsDeterministically() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);
    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'alice');"));

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> vm.execute(compiler.compile("SELECT * FROM users ORDER BY missing;")));
    assertEquals("Unknown column: missing", ex.getMessage());
  }

  @Test
  void firstMutationInTransactionCreatesJournalBeforeOverwrite() throws Exception {
    java.nio.file.Path dbPath = Files.createTempFile("sqlite2j-vm", ".db");
    VmDatabase db = new VmDatabase(dbPath);
    VirtualMachine vm = new VirtualMachine(db);

    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'alice');"));

    byte[] before = Files.readAllBytes(dbPath);
    vm.execute(compiler.compile("BEGIN;"));
    vm.execute(compiler.compile("UPDATE users SET name = 'bob' WHERE id = 1;"));

    java.nio.file.Path journalPath = dbPath.resolveSibling(dbPath.getFileName().toString() + "-journal");
    org.sqlite2j.journal.RollbackJournalFile journal = new org.sqlite2j.journal.RollbackJournalFile();
    org.sqlite2j.journal.ParsedJournal parsed = journal.parse(journalPath);

    assertEquals(1, parsed.getRecords().size());
    assertEquals(Math.max(1, before.length), parsed.getHeader().getPageSize());
    byte[] expected = new byte[Math.max(1, before.length)];
    System.arraycopy(before, 0, expected, 0, Math.min(before.length, expected.length));
    assertArrayEquals(expected, parsed.getRecords().get(0).getPreimage());
  }

  @Test
  void repeatedMutationsInSameTransactionDoNotDuplicatePreimage() throws Exception {
    java.nio.file.Path dbPath = Files.createTempFile("sqlite2j-vm", ".db");
    VmDatabase db = new VmDatabase(dbPath);
    VirtualMachine vm = new VirtualMachine(db);

    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'alice');"));

    vm.execute(compiler.compile("BEGIN;"));
    vm.execute(compiler.compile("UPDATE users SET name = 'bob' WHERE id = 1;"));
    vm.execute(compiler.compile("UPDATE users SET name = 'carl' WHERE id = 1;"));

    java.nio.file.Path journalPath = dbPath.resolveSibling(dbPath.getFileName().toString() + "-journal");
    org.sqlite2j.journal.RollbackJournalFile journal = new org.sqlite2j.journal.RollbackJournalFile();
    org.sqlite2j.journal.ParsedJournal parsed = journal.parse(journalPath);
    assertEquals(1, parsed.getRecords().size());
  }


  @Test
  void transactionStateTransitionsAreDeterministic() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);

    assertEquals(TransactionState.IDLE, vm.transactionState());
    vm.execute(compiler.compile("BEGIN;"));
    assertEquals(TransactionState.IN_TXN, vm.transactionState());
    vm.execute(compiler.compile("COMMIT;"));
    assertEquals(TransactionState.IDLE, vm.transactionState());

    vm.execute(compiler.compile("BEGIN;"));
    assertEquals(TransactionState.IN_TXN, vm.transactionState());
    vm.execute(compiler.compile("ROLLBACK;"));
    assertEquals(TransactionState.IDLE, vm.transactionState());
  }

  @Test
  void transactionStateMisuseDoesNotMutateState() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);

    RuntimeException commitNoTxn = assertThrows(RuntimeException.class, () -> vm.execute(compiler.compile("COMMIT;")));
    assertEquals("No active transaction", commitNoTxn.getMessage());
    assertEquals(TransactionState.IDLE, vm.transactionState());

    RuntimeException rollbackNoTxn = assertThrows(RuntimeException.class, () -> vm.execute(compiler.compile("ROLLBACK;")));
    assertEquals("No active transaction", rollbackNoTxn.getMessage());
    assertEquals(TransactionState.IDLE, vm.transactionState());

    vm.execute(compiler.compile("BEGIN;"));
    assertEquals(TransactionState.IN_TXN, vm.transactionState());

    RuntimeException nestedBegin = assertThrows(RuntimeException.class, () -> vm.execute(compiler.compile("BEGIN;")));
    assertEquals("Transaction already active", nestedBegin.getMessage());
    assertEquals(TransactionState.IN_TXN, vm.transactionState());
  }


  @Test
  void updateWhereNoMatchesLeavesRowsUnchanged() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);
    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'alice');"));

    vm.execute(compiler.compile("UPDATE users SET name = 'bob' WHERE id = 2;"));
    VmResult result = vm.execute(compiler.compile("SELECT * FROM users;"));
    assertEquals("alice", result.getRows().get(0).getValues().get(1).getValue());
  }

  @Test
  void deleteWhereNoMatchesLeavesRowsUnchanged() throws Exception {
    VmDatabase db = new VmDatabase(Files.createTempFile("sqlite2j-vm", ".db"));
    VirtualMachine vm = new VirtualMachine(db);
    vm.execute(compiler.compile("CREATE TABLE users (id INT, name TEXT);"));
    vm.execute(compiler.compile("INSERT INTO users VALUES (1, 'alice');"));

    vm.execute(compiler.compile("DELETE FROM users WHERE id = 2;"));
    VmResult result = vm.execute(compiler.compile("SELECT * FROM users;"));
    assertEquals(1, result.getRows().size());
    assertEquals("alice", result.getRows().get(0).getValues().get(1).getValue());
  }
}
