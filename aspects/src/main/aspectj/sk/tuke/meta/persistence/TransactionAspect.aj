

import sk.tuke.meta.persistence.EntityDAO;

public aspect TransactionAspect {

    pointcut atomic(EntityDAO entity) :
        execution(@sk.tuke.meta.persistence.annotation.AtomicPersistenceOperation * *(..))
        && this(entity);

    Object around(EntityDAO entity) throws Throwable :atomic(entity) {

        java.sql.Connection conn = entity.getConnection();


        boolean origAuto = conn.getAutoCommit();
        try {
            if (origAuto) conn.setAutoCommit(false);

            Object result = proceed(entity);
            conn.commit();
            return result;

        } catch (Throwable ex) {
            try { conn.rollback(); } finally{}
            throw ex;

        } finally {
            conn.setAutoCommit(origAuto);
        }
    }
}
