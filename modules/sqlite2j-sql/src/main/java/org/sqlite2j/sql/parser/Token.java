package org.sqlite2j.sql.parser;

public record Token(TokenType type, String lexeme, int position) {}
