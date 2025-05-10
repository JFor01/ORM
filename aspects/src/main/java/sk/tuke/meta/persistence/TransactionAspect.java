package sk.tuke.meta.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;


@Aspect
public class TransactionAspect {

    @Around("@annotation(sk.tuke.meta.persistence.annotations.AtomicPersistenceOperation)"
            + " && this(entity)")
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
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw ex;

        }
    }
}

