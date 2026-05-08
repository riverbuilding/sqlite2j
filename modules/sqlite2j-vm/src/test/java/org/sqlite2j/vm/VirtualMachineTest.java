package org.sqlite2j.vm;

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
}
