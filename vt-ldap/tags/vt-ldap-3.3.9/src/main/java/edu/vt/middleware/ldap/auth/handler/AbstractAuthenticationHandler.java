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
package edu.vt.middleware.ldap.auth.handler;

import javax.naming.NamingException;
import edu.vt.middleware.ldap.auth.AuthenticatorConfig;
import edu.vt.middleware.ldap.handler.ConnectionHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * AbstractAuthenticationHandler provides a base implementation for
 * authentication handlers.
 *
 * @author  Middleware Services
 * @version  $Revision$
 */
public abstract class AbstractAuthenticationHandler
  implements AuthenticationHandler
{

  /** Log for this class. */
  protected final Log logger = LogFactory.getLog(this.getClass());

  /** Authenticator configuration. */
  protected AuthenticatorConfig config;


  /** {@inheritDoc} */
  public void setAuthenticatorConfig(final AuthenticatorConfig ac)
  {
    this.config = ac;
  }


  /** {@inheritDoc} */
  public abstract void authenticate(
    final ConnectionHandler ch,
    final AuthenticationCriteria ac)
    throws NamingException;


  /** {@inheritDoc} */
  public abstract AuthenticationHandler newInstance();
}
