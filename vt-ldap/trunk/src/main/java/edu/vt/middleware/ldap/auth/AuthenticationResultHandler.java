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
package edu.vt.middleware.ldap.auth;

/**
 * Provides post processing of authentication results.
 *
 * @author  Middleware Services
 * @version  $Revision$
 */
public interface AuthenticationResultHandler
{


  /**
   * Process the results from an ldap authentication.
   *
   * @param  criteria  authentication criteria used to perform the
   * authentication
   * @param  success  whether the authentication succeeded
   */
  void process(AuthenticationCriteria criteria, boolean success);
}