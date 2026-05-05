package org.sqlite2j.vm.api;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.sqlite2j.compiler.codegen.CompilerFacade;
import org.sqlite2j.vm.VirtualMachine;
import org.sqlite2j.vm.VmDatabase;

public final class Sqlite2jConnection implements AutoCloseable {
  private static final Map<String, VmDatabase> DATABASES = new HashMap<String, VmDatabase>();

  private final CompilerFacade compiler;
  private final VirtualMachine vm;
  private boolean closed;

  private Sqlite2jConnection(Path catalogPath) {
    this.compiler = new CompilerFacade();
    this.vm = new VirtualMachine(openDatabase(catalogPath));
  }

  public static Sqlite2jConnection open(String path) { return new Sqlite2jConnection(Path.of(path)); }

  private static synchronized VmDatabase openDatabase(Path catalogPath) {
    String key = catalogPath.toAbsolutePath().normalize().toString();
    VmDatabase database = DATABASES.get(key);
    if (database == null) {
      database = new VmDatabase(catalogPath);
      DATABASES.put(key, database);
    }
    return database;
  }

  public Sqlite2jStatement prepare(String sql) {
    if (closed) throw new IllegalStateException("Connection closed");
    return new Sqlite2jStatement(compiler, vm, sql);
  }

  public void close() { closed = true; }
}
