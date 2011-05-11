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
package edu.vt.middleware.ldap.provider.jndi;

import java.io.IOException;
import java.util.Hashtable;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import edu.vt.middleware.ldap.Credential;
import edu.vt.middleware.ldap.LdapConnectionConfig;
import edu.vt.middleware.ldap.LdapException;
import edu.vt.middleware.ldap.ResultCode;
import edu.vt.middleware.ldap.auth.AuthenticationException;
import edu.vt.middleware.ldap.provider.ConnectionException;

/**
 * Creates ldap connections using the JNDI {@link InitialLdapContext} class with
 * the start tls extended operation.
 *
 * @author  Middleware Services
 * @version  $Revision$
 */
public class JndiTlsConnectionFactory extends AbstractJndiConnectionFactory
{
  /** ldap socket factory used for SSL and TLS. */
  protected SSLSocketFactory sslSocketFactory;

  /** hostname verifier for TLS connections. */
  protected HostnameVerifier hostnameVerifier;


  /**
   * Creates a new jndi tls connection factory.
   *
   * @param  url  of the ldap to connect to
   */
  protected JndiTlsConnectionFactory(final String url)
  {
    if (url == null) {
      throw new IllegalArgumentException("LDAP URL cannot be null");
    }
    this.ldapUrl = url;
  }


  /**
   * Returns the SSL socket factory to use for TLS connections.
   *
   * @return  SSL socket factory
   */
  public SSLSocketFactory getSslSocketFactory()
  {
    return this.sslSocketFactory;
  }


  /**
   * Sets the SSL socket factory to use for TLS connections.
   *
   * @param  sf  SSL socket factory
   */
  public void setSslSocketFactory(final SSLSocketFactory sf)
  {
    this.sslSocketFactory = sf;
  }


  /**
   * Returns the hostname verifier to use for TLS connections.
   *
   * @return  hostname verifier
   */
  public HostnameVerifier getHostnameVerifier()
  {
    return this.hostnameVerifier;
  }


  /**
   * Sets the hostname verifier to use for TLS connections.
   *
   * @param  verifier  for hostnames
   */
  public void setHostnameVerifier(final HostnameVerifier verifier)
  {
    this.hostnameVerifier = verifier;
  }


  /** {@inheritDoc} */
  protected JndiTlsConnection createInternal(
    final String url, final String dn, final Credential credential)
    throws LdapException
  {
    this.logger.debug(
      "Bind with the following parameters: url = {}, " +
      "authenticationType = {}, dn = {}, credential = {}, env = {}",
      new Object[] {
        url,
        this.authenticationType,
        dn,
        this.logCredentials ? credential : "<suppressed>",
        this.environment, });

    final Hashtable<String, Object> env = new Hashtable<String, Object>(
      this.environment);
    env.put(PROVIDER_URL, url);
    if (this.tracePackets != null) {
      env.put(TRACE, this.tracePackets);
    }

    JndiTlsConnection conn = null;
    env.put(VERSION, "3");
    boolean closeConn = false;
    try {
      conn = new JndiTlsConnection(new InitialLdapContext(env, null));
      conn.setStartTlsResponse(this.startTls(conn.getLdapContext()));
      // note that when using simple authentication (the default),
      // if the credential is null the provider will automatically revert the
      // authentication to none
      conn.getLdapContext().addToEnvironment(
        AUTHENTICATION, getAuthenticationType(this.authenticationType));
      if (dn != null) {
        conn.getLdapContext().addToEnvironment(PRINCIPAL, dn);
        if (credential != null) {
          conn.getLdapContext().addToEnvironment(
            CREDENTIALS, credential.getBytes());
        }
      }
      conn.getLdapContext().reconnect(null);
      conn.setRemoveDnUrls(this.removeDnUrls);
      conn.setOperationRetryExceptions(
        NamingExceptionUtil.getNamingExceptions(
          this.operationRetryResultCodes));
    } catch (javax.naming.AuthenticationException e) {
      closeConn = true;
      throw new AuthenticationException(e, ResultCode.INVALID_CREDENTIALS);
    } catch (NamingException e) {
      closeConn = true;
      throw new ConnectionException(
        e, NamingExceptionUtil.getResultCode(e.getClass()));
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
          this.logger.debug("Problem tearing down connection", e);
        }
      }
    }
    return conn;
  }


  /**
   * This will attempt the StartTLS extended operation on the supplied ldap
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
  public StartTlsResponse startTls(final LdapContext ctx)
    throws NamingException, IOException
  {
    final StartTlsResponse tls = (StartTlsResponse) ctx.extendedOperation(
      new StartTlsRequest());
    if (this.hostnameVerifier != null) {
      this.logger.trace("TLS hostnameVerifier = {}", this.hostnameVerifier);
      tls.setHostnameVerifier(this.hostnameVerifier);
    }
    if (this.sslSocketFactory != null) {
      this.logger.trace("TLS sslSocketFactory = {}", this.sslSocketFactory);
      tls.negotiate(this.sslSocketFactory);
    } else {
      tls.negotiate();
    }
    return tls;
  }


  /**
   * Creates a new instance of this connection factory.
   *
   * @param  lcc  ldap connection configuration to read connection properties
   * from
   * @return  jndi tls connection factory
   */
  public static JndiTlsConnectionFactory newInstance(
    final LdapConnectionConfig lcc)
  {
    final JndiTlsConnectionFactory cf = new JndiTlsConnectionFactory(
      lcc.getLdapUrl());
    cf.setAuthenticationType(lcc.getAuthenticationType());
    cf.setEnvironment(createEnvironment(lcc));
    cf.setLogCredentials(lcc.getLogCredentials());
    cf.setSslSocketFactory(lcc.getSslSocketFactory());
    cf.setHostnameVerifier(lcc.getHostnameVerifier());
    if (lcc.getConnectionStrategy() != null) {
      cf.setConnectionStrategy(lcc.getConnectionStrategy());
    }
    return cf;
  }
}
