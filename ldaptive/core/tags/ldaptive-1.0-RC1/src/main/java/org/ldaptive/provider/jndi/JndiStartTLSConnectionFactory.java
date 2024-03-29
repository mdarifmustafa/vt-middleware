/*
  $Id$

  Copyright (C) 2003-2012 Virginia Tech.
  All rights reserved.

  SEE LICENSE FOR MORE INFORMATION

  Author:  Middleware Services
  Email:   middleware@vt.edu
  Version: $Revision$
  Updated: $Date$
*/
package org.ldaptive.provider.jndi;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import org.ldaptive.LdapException;
import org.ldaptive.provider.AbstractConnectionFactory;
import org.ldaptive.provider.ConnectionException;

/**
 * Creates connections using the JNDI {@link InitialLdapContext} class with the
 * startTLS extended operation.
 *
 * @author  Middleware Services
 * @version  $Revision$
 */
public class JndiStartTLSConnectionFactory
  extends AbstractConnectionFactory<JndiProviderConfig>
{

  /** Environment properties. */
  private Map<String, Object> environment;

  /** SSL socket factory to use for startTLS negotiation. */
  private SSLSocketFactory sslSocketFactory;

  /** Hostname verifier to use for startTLS negotiation. */
  private HostnameVerifier hostnameVerifier;


  /**
   * Creates a new jndi startTLS connection factory.
   *
   * @param  url  of the ldap to connect to
   * @param  env  jndi context environment
   * @param  factory  SSL socket factory
   * @param  verifier  hostname verifier
   */
  public JndiStartTLSConnectionFactory(
    final String url,
    final Map<String, Object> env,
    final SSLSocketFactory factory,
    final HostnameVerifier verifier)
  {
    super(url);
    environment = env;
    sslSocketFactory = factory;
    hostnameVerifier = verifier;
  }


  /** {@inheritDoc} */
  @Override
  protected JndiStartTLSConnection createInternal(final String url)
    throws LdapException
  {
    // CheckStyle:IllegalType OFF
    // the JNDI API requires the Hashtable type
    final Hashtable<String, Object> env = new Hashtable<String, Object>(
      environment);
    // CheckStyle:IllegalType ON
    env.put(JndiProvider.PROVIDER_URL, url);

    JndiStartTLSConnection conn = null;
    boolean closeConn = false;
    try {
      conn = new JndiStartTLSConnection(new InitialLdapContext(env, null));
      conn.setStartTlsResponse(startTLS(conn.getLdapContext()));
      conn.setRemoveDnUrls(getProviderConfig().getRemoveDnUrls());
      conn.setOperationRetryResultCodes(
        getProviderConfig().getOperationRetryResultCodes());
      conn.setSearchIgnoreResultCodes(
        getProviderConfig().getSearchIgnoreResultCodes());
      conn.setControlProcessor(getProviderConfig().getControlProcessor());
    } catch (NamingException e) {
      closeConn = true;
      throw new ConnectionException(
        e,
        NamingExceptionUtil.getResultCode(e.getClass()));
    } catch (IOException e) {
      closeConn = true;
      throw new ConnectionException(e);
    } catch (RuntimeException e) {
      closeConn = true;
      throw e;
    } finally {
      if (closeConn) {
        try {
          if (conn != null) {
            conn.close();
          }
        } catch (LdapException e) {
          logger.debug("Problem tearing down connection", e);
        }
      }
    }
    return conn;
  }


  /**
   * This will attempt the startTLS extended operation on the supplied ldap
   * context.
   *
   * @param  ctx  ldap context
   *
   * @return  start tls response
   *
   * @throws  NamingException  if an error occurs while requesting an extended
   * operation
   * @throws  IOException  if an error occurs while negotiating TLS
   */
  protected StartTlsResponse startTLS(final LdapContext ctx)
    throws NamingException, IOException
  {
    final StartTlsResponse tls = (StartTlsResponse) ctx.extendedOperation(
      new StartTlsRequest());
    if (hostnameVerifier != null) {
      logger.trace("startTLS hostnameVerifier = {}", hostnameVerifier);
      tls.setHostnameVerifier(hostnameVerifier);
    }
    if (sslSocketFactory != null) {
      logger.trace("startTLS sslSocketFactory = {}", sslSocketFactory);
      tls.negotiate(sslSocketFactory);
    } else {
      tls.negotiate();
    }
    return tls;
  }


  /**
   * Provides a descriptive string representation of this instance.
   *
   * @return  string representation
   */
  @Override
  public String toString()
  {
    return
      String.format(
        "[%s@%d::env=%s, config=%s, sslSocketFactory=%s, hostnameVerifier=%s]",
        getClass().getName(),
        hashCode(),
        environment,
        getProviderConfig(),
        sslSocketFactory,
        hostnameVerifier);
  }
}
