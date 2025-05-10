package sk.tuke.meta.persistence;

import java.sql.Statement;

public aspect DBMonitoring {

    pointcut statementExecution(Statement stmt):
            call(* Statement+.execute*(..)) && target(stmt);

    before(Statement stmt): statementExecution(stmt) {
        System.out.println("Executing SQL: " + stmt);
    }
}