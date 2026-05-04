package org.sqlite2j.sql.ast;

import java.util.List;

public record CreateTableStatement(String tableName, List<ColumnDef> columns) implements Statement {}
