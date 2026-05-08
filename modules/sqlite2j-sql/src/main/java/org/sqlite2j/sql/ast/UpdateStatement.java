package org.sqlite2j.sql.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UpdateStatement implements Statement {
  private final String tableName;
  private final List<UpdateAssignment> assignments;
  private final Expression whereExpression;

  public UpdateStatement(String tableName, List<UpdateAssignment> assignments, Expression whereExpression) {
    this.tableName = tableName;
    this.assignments = Collections.unmodifiableList(new ArrayList<UpdateAssignment>(assignments));
    this.whereExpression = whereExpression;
  }

  public String getTableName() {
    return tableName;
  }

  public List<UpdateAssignment> getAssignments() {
    return assignments;
  }

  public Expression getWhereExpression() {
    return whereExpression;
  }
}
