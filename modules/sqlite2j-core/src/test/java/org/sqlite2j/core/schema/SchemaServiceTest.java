package org.sqlite2j.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SchemaServiceTest {

  @Test
  void createTableRegistersMetadata() {
    SchemaRegistry registry = new SchemaRegistry(Path.of("catalog-placeholder.db"));
    SchemaService service = new SchemaService(registry);

    service.execute("CREATE TABLE users (id INT, name TEXT);");

    TableSchema users = registry.findTable("users").orElseThrow();
    assertEquals("users", users.getName());
    assertEquals(2, users.getColumns().size());
    assertEquals("id", users.getColumns().get(0).getName());
    assertEquals("INT", users.getColumns().get(0).getType());
    assertNotNull(registry.getCatalogPath());
    assertEquals(Path.of("catalog-placeholder.db"), registry.getCatalogPath());
  }

  @Test
  void duplicateCreateTableFailsDeterministically() {
    SchemaRegistry registry = new SchemaRegistry(Path.of("catalog-placeholder.db"));
    SchemaService service = new SchemaService(registry);

    service.execute("CREATE TABLE users (id INT);");

    SchemaException ex = assertThrows(SchemaException.class,
        () -> service.execute("CREATE TABLE users (id INT);"));
    assertEquals("Table already exists: users", ex.getMessage());
    assertTrue(registry.findTable("users").isPresent());
    assertEquals(1, registry.tablesView().size());
  }
}
