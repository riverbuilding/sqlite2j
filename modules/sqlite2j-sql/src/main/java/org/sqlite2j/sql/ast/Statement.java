package org.sqlite2j.sql.ast;

public sealed interface Statement permits CreateTableStatement, InsertStatement, SelectAllStatement {}
