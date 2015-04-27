package edu.berkeley.util.transaction

import org.hibernate.SessionFactory
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Methods for managing a transaction.
 */
class TransactionUtil {
    static transactional = false

    /**
     * Execute the closure using a new transaction.  The transaction will be
     * committed upon succesful closure completion.  The transactios will be
     * rolled back on any Exception thrown and the exception will propagate.
     *
     * @param sessionFactory A Hibernate sessionFactory.  sessionFactory can
     *        be injected into services and integration tsts.
     * @param closure The closure to execute within a new transaction.
     *
     * @return The optional return value from the closure.
     */
    static Object withTransaction(SessionFactory sessionFactory, Closure closure) {
        if (!sessionFactory)
            throw new RuntimeException("sessionFactory cannot be null")
        return new TransactionUtil().doTransaction(sessionFactory, closure)
    }

    /**
     * Execute the closure using a new transaction and clear the Hibernate
     * session after a successful commit of the transaction.  The
     * transaction will be committed upon succesful closure completion.  The
     * transactios will be rolled back on any Exception thrown and the
     * exception will propagate.  The Hibernate session is not cleared if
     * the closure threw an exception.
     *
     * @param sessionFactory A Hibernate sessionFactory.  sessionFactory can
     *        be injected into services and integration tsts.
     * @param closure The closure to execute within a new transaction.
     *
     * @return The optional return value from the closure.
     */
    static Object withClearingTransaction(SessionFactory sessionFactory, Closure closure) {
        if (!sessionFactory)
            throw new RuntimeException("sessionFactory cannot be null")
        Object result = withTransaction(sessionFactory, closure)
        if (!sessionFactory.currentSession) {
            throw new RuntimeException("Transaction has been committed but was unable to clear the Hibernate session because sessionFactory.getCurrentSession() returned null")
        }
        try {
            sessionFactory.currentSession.clear()
        }
        catch (Exception e) {
            throw new RuntimeException("Transaction has been committed but there was an exception clearing the Hibernate session", e)
        }
        return result
    }

    @Transactional(propagation = Propagation.NEVER)
    protected Object doTransaction(SessionFactory sessionFactory, Closure closure) {
      Object result = _doTransaction(sessionFactory, closure)
      sessionFactory.currentSession.transaction.commit()
      return result
    }

    /**
     * Execute the closure within a new transaction.  Rollback for any
     * Exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception)
    private Object _doTransaction(SessionFactory sessionFactory, Closure closure) {
        if (!closure)
            throw new RuntimeException("closure cannot be null")
        Object result = closure()
        return result
    }
}
