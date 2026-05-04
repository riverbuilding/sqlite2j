package org.sqlite2j.sql.ast;

import java.util.List;

public record InsertStatement(String tableName, List<LiteralValue> values) implements Statement {}
