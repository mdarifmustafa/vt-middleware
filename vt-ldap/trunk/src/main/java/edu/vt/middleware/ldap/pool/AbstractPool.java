/*
  $Id$

  Copyright (C) 2003-2010 Virginia Tech.
  All rights reserved.

  SEE LICENSE FOR MORE INFORMATION

  Author:  Middleware Services
  Email:   middleware@vt.edu
  Version: $Revision$
  Updated: $Date$
*/
package edu.vt.middleware.ldap.pool;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import edu.vt.middleware.ldap.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains the base implementation for pooling ldap connections. The main
 * design objective for the supplied pooling implementations is to provide a
 * pool that does not block on connection creation or destruction. This is what
 * accounts for the multiple locks available on this class. The pool is backed
 * by two queues, one for available connections and one for active connections.
 * Connections that are available for {@link #checkOut()} exist in the available
 * queue. Connections that are actively in use exist in the active queue. Note
 * that depending on the implementation a connection can exist in both queues at
 * the same time.
 *
 * @param  <T>  type of ldap connection
 *
 * @author  Middleware Services
 * @version  $Revision$ $Date$
 */
public abstract class AbstractPool<T extends Connection>
  implements Pool<T>
{

  /** Lock for the entire pool. */
  protected final ReentrantLock poolLock = new ReentrantLock();

  /** Condition for notifying threads that a connection was returned. */
  protected final Condition poolNotEmpty = poolLock.newCondition();

  /** Lock for check ins. */
  protected final ReentrantLock checkInLock = new ReentrantLock();

  /** Lock for check outs. */
  protected final ReentrantLock checkOutLock = new ReentrantLock();

  /** Logger for this class. */
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  /** List of available ldap connections in the pool. */
  protected Queue<PooledConnection<T>> available =
    new LinkedList<PooledConnection<T>>();

  /** List of ldap connections in use. */
  protected Queue<PooledConnection<T>> active =
    new LinkedList<PooledConnection<T>>();

  /** Pool config. */
  protected PoolConfig config;

  /** Factory to create ldap connections. */
  protected ConnectionFactory<T> factory;

  /** Executor for scheduling pool tasks. */
  protected ScheduledExecutorService poolExecutor =
    Executors.newSingleThreadScheduledExecutor(
      new ThreadFactory() {
        public Thread newThread(final Runnable r)
        {
          final Thread t = new Thread(r);
          t.setDaemon(true);
          return t;
        }
      });


  /**
   * Creates a new pool with the supplied pool configuration and connection
   * factory. The pool configuration will be marked as immutable by this pool.
   *
   * @param  pc  pool config
   * @param  cf  connection factory
   */
  public AbstractPool(final PoolConfig pc, final ConnectionFactory<T> cf)
  {
    config = pc;
    config.makeImmutable();
    factory = cf;
  }


  /** {@inheritDoc} */
  @Override
  public PoolConfig getPoolConfig()
  {
    return config;
  }


  /** {@inheritDoc} */
  @Override
  public void initialize()
  {
    logger.debug("beginning pool initialization");

    final Runnable prune = new Runnable() {
      public void run()
      {
        logger.debug("Begin prune task for {}", this);
        prune();
        logger.debug("End prune task for {}", this);
      }
    };
    poolExecutor.scheduleAtFixedRate(
      prune,
      config.getPrunePeriod(),
      config.getPrunePeriod(),
      TimeUnit.SECONDS);
    logger.debug("prune pool task scheduled");

    final Runnable validate = new Runnable() {
      public void run()
      {
        logger.debug("Begin validate task for {}", this);
        validate();
        logger.debug("End validate task for {}", this);
      }
    };
    poolExecutor.scheduleAtFixedRate(
      validate,
      config.getValidatePeriod(),
      config.getValidatePeriod(),
      TimeUnit.SECONDS);
    logger.debug("validate pool task scheduled");

    initializePool();

    logger.debug("pool initialized to size " + available.size());
  }


  /** Attempts to fill the pool to its minimum size. */
  private void initializePool()
  {
    logger.debug(
      "checking ldap pool size >= {}", config.getMinPoolSize());

    int count = 0;
    poolLock.lock();
    try {
      while (
        available.size() < config.getMinPoolSize() &&
          count < config.getMinPoolSize() * 2) {
        final T t = createAvailable();
        if (config.isValidateOnCheckIn()) {
          if (factory.validate(t)) {
            logger.trace(
              "ldap connection passed initialize validation: {}", t);
          } else {
            logger.warn("ldap connection failed initialize validation: {}", t);
            removeAvailable(t);
          }
        }
        count++;
      }
    } finally {
      poolLock.unlock();
    }
  }


  /** {@inheritDoc} */
  @Override
  public void close()
  {
    poolLock.lock();
    try {
      while (available.size() > 0) {
        final PooledConnection<T> pl = available.remove();
        factory.destroy(pl.getConnection());
      }
      while (active.size() > 0) {
        final PooledConnection<T> pl = active.remove();
        factory.destroy(pl.getConnection());
      }
      logger.debug("pool closed");
    } finally {
      poolLock.unlock();
    }

    logger.debug("shutting down executor");
    poolExecutor.shutdown();
    logger.debug("executor shutdown");
  }


  /**
   * Create a new ldap connection and place it in the available pool.
   *
   * @return  ldap connection that was placed in the available pool
   */
  protected T createAvailable()
  {
    final T t = factory.create();
    if (t != null) {
      final PooledConnection<T> pl = new PooledConnection<T>(t);
      poolLock.lock();
      try {
        available.add(pl);
      } finally {
        poolLock.unlock();
      }
    } else {
      logger.warn("unable to create available ldap connection");
    }
    return t;
  }


  /**
   * Create a new ldap connection and place it in the active pool.
   *
   * @return  ldap connection that was placed in the active pool
   */
  protected T createActive()
  {
    final T t = factory.create();
    if (t != null) {
      final PooledConnection<T> pl = new PooledConnection<T>(t);
      poolLock.lock();
      try {
        active.add(pl);
      } finally {
        poolLock.unlock();
      }
    } else {
      logger.warn("unable to create active ldap connection");
    }
    return t;
  }


  /**
   * Create a new ldap connection and place it in both the available and active
   * pools.
   *
   * @return  ldap connection that was placed in the available and active pools
   */
  protected T createAvailableAndActive()
  {
    final T t = factory.create();
    if (t != null) {
      final PooledConnection<T> pl = new PooledConnection<T>(t);
      poolLock.lock();
      try {
        available.add(pl);
        active.add(pl);
      } finally {
        poolLock.unlock();
      }
    } else {
      logger.warn("unable to create available and active ldap connection");
    }
    return t;
  }


  /**
   * Remove an ldap connection from the available pool.
   *
   * @param  t  ldap connection that was in the available pool
   */
  protected void removeAvailable(final T t)
  {
    boolean destroy = false;
    final PooledConnection<T> pl = new PooledConnection<T>(t);
    poolLock.lock();
    try {
      if (available.remove(pl)) {
        destroy = true;
      } else {
        logger.warn(
          "attempt to remove unknown available ldap connection: {}", t);
      }
    } finally {
      poolLock.unlock();
    }
    if (destroy) {
      logger.trace("removing available ldap connection: {}", t);
      factory.destroy(t);
    }
  }


  /**
   * Remove an ldap connection from the active pool.
   *
   * @param  t  ldap connection that was in the active pool
   */
  protected void removeActive(final T t)
  {
    boolean destroy = false;
    final PooledConnection<T> pl = new PooledConnection<T>(t);
    poolLock.lock();
    try {
      if (active.remove(pl)) {
        destroy = true;
      } else {
        logger.warn("attempt to remove unknown active ldap connection: {}", t);
      }
    } finally {
      poolLock.unlock();
    }
    if (destroy) {
      logger.trace("removing active ldap connection: {}", t);
      factory.destroy(t);
    }
  }


  /**
   * Remove an ldap connection from both the available and active pools.
   *
   * @param  t  ldap connection that was in the both the available and active
   * pools
   */
  protected void removeAvailableAndActive(final T t)
  {
    boolean destroy = false;
    final PooledConnection<T> pl = new PooledConnection<T>(t);
    poolLock.lock();
    try {
      if (available.remove(pl)) {
        destroy = true;
      } else {
        logger.debug(
          "attempt to remove unknown available ldap connection: {}", t);
      }
      if (active.remove(pl)) {
        destroy = true;
      } else {
        logger.debug(
          "attempt to remove unknown active ldap connection: {}", t);
      }
    } finally {
      poolLock.unlock();
    }
    if (destroy) {
      logger.trace("removing active ldap connection: {}", t);
      factory.destroy(t);
    }
  }


  /**
   * Attempts to activate and validate an ldap connection. Performed before a
   * connection is returned from {@link Pool#checkOut()}.
   *
   * @param  t  ldap connection
   *
   * @throws  PoolException  if this method fais
   * @throws  ActivationException  if the ldap connection cannot be activated
   * @throws  ValidationException  if the ldap connection cannot be validated
   */
  protected void activateAndValidate(final T t)
    throws PoolException
  {
    if (!factory.activate(t)) {
      logger.warn("ldap connection failed activation: {}", t);
      removeAvailableAndActive(t);
      throw new ActivationException("Activation of ldap connection failed");
    }
    if (
      config.isValidateOnCheckOut() &&
        !factory.validate(t)) {
      logger.warn("ldap connection failed check out validation: {}", t);
      removeAvailableAndActive(t);
      throw new ValidationException("Validation of ldap connection failed");
    }
  }


  /**
   * Attempts to validate and passivate an ldap connection. Performed when a
   * connection is given to {@link Pool#checkIn}.
   *
   * @param  t  ldap connection
   *
   * @return  whether both validate and passivation succeeded
   */
  protected boolean validateAndPassivate(final T t)
  {
    boolean valid = false;
    if (config.isValidateOnCheckIn()) {
      if (!factory.validate(t)) {
        logger.warn("ldap connection failed check in validation: {}", t);
      } else {
        valid = true;
      }
    } else {
      valid = true;
    }
    if (valid && !factory.passivate(t)) {
      valid = false;
      logger.warn("ldap connection failed activation: {}", t);
    }
    return valid;
  }


  /** {@inheritDoc} */
  @Override
  public void prune()
  {
    logger.trace(
      "waiting for pool lock to prune {}", poolLock.getQueueLength());
    poolLock.lock();
    try {
      if (active.size() == 0) {
        logger.debug("pruning pool of size {}", available.size());
        while (available.size() > config.getMinPoolSize()) {
          PooledConnection<T> pl = available.peek();
          final long time = System.currentTimeMillis() - pl.getCreatedTime();
          if (time >
              TimeUnit.SECONDS.toMillis(config.getExpirationTime())) {
            pl = available.remove();
            logger.trace(
              "removing {} in the pool for {}ms", pl.getConnection(), time);
            factory.destroy(pl.getConnection());
          } else {
            break;
          }
        }
        logger.debug("pool size pruned to {}", available.size());
      } else {
        logger.debug("pool is currently active, no connections pruned");
      }
    } finally {
      poolLock.unlock();
    }
  }


  /** {@inheritDoc} */
  @Override
  public void validate()
  {
    poolLock.lock();
    try {
      if (active.size() == 0) {
        if (config.isValidatePeriodically()) {
          logger.debug(
            "validate for pool of size {}", available.size());

          final Queue<PooledConnection<T>> remove =
            new LinkedList<PooledConnection<T>>();
          for (PooledConnection<T> pl : available) {
            logger.trace("validating {}", pl.getConnection());
            if (factory.validate(pl.getConnection())) {
              logger.trace(
                "ldap connection passed validation: {}", pl.getConnection());
            } else {
              logger.warn(
                "ldap connection failed validation: {}", pl.getConnection());
              remove.add(pl);
            }
          }
          for (PooledConnection<T> pl : remove) {
            logger.trace(
              "removing {} from the pool", pl.getConnection());
            available.remove(pl);
            factory.destroy(pl.getConnection());
          }
        }
        initializePool();
        logger.debug(
          "pool size after validation is {}", available.size());
      } else {
        logger.debug("pool is currently active, no validation performed");
      }
    } finally {
      poolLock.unlock();
    }
  }


  /** {@inheritDoc} */
  @Override
  public int availableCount()
  {
    return available.size();
  }


  /** {@inheritDoc} */
  @Override
  public int activeCount()
  {
    return active.size();
  }


  /**
   * Called by the garbage collector on an object when garbage collection
   * determines that there are no more references to the object.
   *
   * @throws  Throwable  if an exception is thrown by this method
   */
  protected void finalize()
    throws Throwable
  {
    try {
      close();
    } finally {
      super.finalize();
    }
  }


  /**
   * Contains an ldap connection that is participating in this pool. Used to
   * track how long an ldap connection has been in either the available or
   * active queues.
   *
   * @param  <T>  type of ldap connection
   */
  static class PooledConnection<T extends Connection>
  {

    /** hash code seed. */
    protected static final int HASH_CODE_SEED = 89;

    /** Underlying connection object. */
    private T conn;

    /** Time this connection was created. */
    private long createdTime;


    /**
     * Creates a new pooled connection.
     *
     * @param  t  ldap connection
     */
    public PooledConnection(final T t)
    {
      conn = t;
      createdTime = System.currentTimeMillis();
    }


    /**
     * Returns the ldap connection.
     *
     * @return  underlying ldap connection
     */
    public T getConnection()
    {
      return conn;
    }


    /**
     * Returns the time this connection was created.
     *
     * @return  creation time
     */
    public long getCreatedTime()
    {
      return createdTime;
    }


    /**
     * Returns whether the supplied object contains the same data as this one.
     *
     * @param  o  to compare against
     *
     * @return  whether the supplied object contains the same data as this one
     */
    public boolean equals(final Object o)
    {
      if (o == null) {
        return false;
      }
      return
        o == this ||
          (getClass() == o.getClass() &&
            o.hashCode() == hashCode());
    }


    /**
     * Returns the hash code for this object.
     *
     * @return  hash code
     */
    public int hashCode()
    {
      int hc = HASH_CODE_SEED;
      if (conn != null) {
        hc += conn.hashCode();
      }
      return hc;
    }
  }
}