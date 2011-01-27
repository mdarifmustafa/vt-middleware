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
package edu.vt.middleware.ldap.provider;

import edu.vt.middleware.ldap.Credential;
import edu.vt.middleware.ldap.LdapException;

/**
 * Provides an interface for creating and closing provider connections.
 *
 * @author  Middleware Services
 * @version  $Revision$
 */
public interface ConnectionFactory
{


  /**
   * Returns the connection strategy.
   *
   * @return  strategy for making connections
   */
  ConnectionStrategy getConnectionStrategy();


  /**
   * Sets the connection strategy.
   *
   * @param  strategy  for making connections
   */
  void setConnectionStrategy(ConnectionStrategy strategy);


  /**
   * Create a connection to an LDAP.
   *
   * @param  dn  to attempt bind with
   * @param  credential  to attempt bind with
   *
   * @throws  AuthenticationException  if the supplied credentials are invalid
   * @throws  LdapException  if an LDAP error occurs
   */
  Connection create(String dn, Credential credential) throws LdapException;


  /**
   * Tear down a connection to an LDAP.
   *
   * @param  dn  to attempt bind with
   * @param  credential  to attempt bind with
   *
   * @throws  LdapException  if an LDAP error occurs
   */
  void destroy(Connection conn) throws LdapException;
}
