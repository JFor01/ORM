package sk.tuke.meta.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;


@Aspect
public class TransactionAspect {

    @Around("@annotation(sk.tuke.meta.persistence.annotations.AtomicPersistenceOperation)"
            + " && this(entity)" )
    public Object transactional(ProceedingJoinPoint pjp,
                                EntityDAO entity) throws Throwable {

        Connection conn = entity.getConnection();
        if (conn == null) {
            return pjp.proceed();
        }

        boolean origAuto = conn.getAutoCommit();
        try {
            if (origAuto) conn.setAutoCommit(false);
            System.out.println(conn.getAutoCommit());
            Object result = pjp.proceed();

            conn.commit();
            return result;

        } catch (Throwable ex) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
            throw ex;

        } finally {
            try {
                conn.setAutoCommit(origAuto);
            } catch (SQLException ignored) {
            }
        }
    }


    @Pointcut("execution(java.sql.Connection *(..))" )
    private void anyConn() {
    }


    @AfterReturning(pointcut = "anyConn()" , returning = "conn" )
    public void disableAutoCommit(Connection conn) {
        if (conn == null) return;
        try {
            conn.setAutoCommit(false);
            System.out.println("Commit disabled" );
        } catch (SQLException ignored) {
        }

    }
}

