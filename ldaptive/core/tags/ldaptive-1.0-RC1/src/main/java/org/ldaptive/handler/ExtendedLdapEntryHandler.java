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
package org.ldaptive.handler;

import org.ldaptive.Connection;

/**
 * Provides an interface for entry handlers that require the use of the
 * connection that was used to perform the original search.
 *
 * @author  Middleware Services
 * @version  $Revision$
 */
public interface ExtendedLdapEntryHandler extends LdapEntryHandler
{


  /**
   * Gets the connection used by the search operation invoking this handler.
   *
   * @return  connection
   */
  Connection getResultConnection();


  /**
   * Sets the connection used by the search operation invoking this handler.
   *
   * @param  c  connection
   */
  void setResultConnection(final Connection c);
}
