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
package edu.vt.middleware.ldap.servlets;

import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import edu.vt.middleware.ldap.LdapAttribute;
import edu.vt.middleware.ldap.LdapConfig;
import edu.vt.middleware.ldap.LdapConnection;
import edu.vt.middleware.ldap.LdapEntry;
import edu.vt.middleware.ldap.LdapResult;
import edu.vt.middleware.ldap.SearchFilter;
import edu.vt.middleware.ldap.SearchOperation;
import edu.vt.middleware.ldap.SearchRequest;
import edu.vt.middleware.ldap.pool.BlockingLdapPool;
import edu.vt.middleware.ldap.pool.DefaultLdapFactory;
import edu.vt.middleware.ldap.pool.LdapPool;
import edu.vt.middleware.ldap.pool.LdapPoolConfig;
import edu.vt.middleware.ldap.pool.SharedLdapPool;
import edu.vt.middleware.ldap.pool.SoftLimitLdapPool;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <code>AttributeServlet</code> is a servlet which queries an LDAP and returns
 * the value of a single attribute. Example:
 * http://www.server.com/Attribute?query=uid=dfisher&attr=givenName If you need
 * to pass complex queries, such as (&(cn=daniel*)(surname=fisher)), then the
 * query must be form encoded. The content returned by the servlet is of type
 * text/plain, if you want to receive the content as application/octet-stream
 * that can be specified by passing the content-type=octet param. The following
 * init params can be set for this servlet:
 * edu.vt.middleware.ldap.servlets.propertiesFile - to load ldap properties from
 *
 * @author  Middleware Services
 * @version  $Revision$ $Date$
 */
public final class AttributeServlet extends HttpServlet
{

  /** serial version uid. */
  private static final long serialVersionUID = -5918353780927139315L;

  /** Types of available pools. */
  private enum PoolType {

    /** blocking. */
    BLOCKING,

    /** soft limit. */
    SOFTLIMIT,

    /** shared. */
    SHARED
  }

  /** Log for this class. */
  private final Log logger = LogFactory.getLog(AttributeServlet.class);

  /** Pool to use for searching. */
  private LdapPool<LdapConnection> pool;


  /**
   * Initialize this servlet.
   *
   * @param  config  <code>ServletConfig</code>
   *
   * @throws  ServletException  if an error occurs
   */
  public void init(final ServletConfig config)
    throws ServletException
  {
    super.init(config);

    final String propertiesFile = getInitParameter(
      ServletConstants.PROPERTIES_FILE);
    if (this.logger.isDebugEnabled()) {
      this.logger.debug(
        ServletConstants.PROPERTIES_FILE + " = " + propertiesFile);
    }

    final LdapConfig ldapConfig = LdapConfig.createFromProperties(
      AttributeServlet.class.getResourceAsStream(propertiesFile));

    final String poolPropertiesFile = getInitParameter(
      ServletConstants.POOL_PROPERTIES_FILE);
    if (this.logger.isDebugEnabled()) {
      this.logger.debug(
        ServletConstants.POOL_PROPERTIES_FILE + " = " + poolPropertiesFile);
    }

    final LdapPoolConfig ldapPoolConfig = LdapPoolConfig.createFromProperties(
      AttributeServlet.class.getResourceAsStream(poolPropertiesFile));

    final String poolType = getInitParameter(ServletConstants.POOL_TYPE);
    if (this.logger.isDebugEnabled()) {
      this.logger.debug(ServletConstants.POOL_TYPE + " = " + poolType);
    }
    if (PoolType.BLOCKING == PoolType.valueOf(poolType)) {
      this.pool = new BlockingLdapPool(
        ldapPoolConfig,
        new DefaultLdapFactory(ldapConfig));
    } else if (PoolType.SOFTLIMIT == PoolType.valueOf(poolType)) {
      this.pool = new SoftLimitLdapPool(
        ldapPoolConfig,
        new DefaultLdapFactory(ldapConfig));
    } else if (PoolType.SHARED == PoolType.valueOf(poolType)) {
      this.pool = new SharedLdapPool(
        ldapPoolConfig,
        new DefaultLdapFactory(ldapConfig));
    } else {
      throw new ServletException("Unknown pool type: " + poolType);
    }
    this.pool.initialize();
  }


  /**
   * Handle all requests sent to this servlet.
   *
   * @param  request  <code>HttpServletRequest</code>
   * @param  response  <code>HttpServletResponse</code>
   *
   * @throws  ServletException  if an error occurs
   * @throws  IOException  if an error occurs
   */
  public void service(
    final HttpServletRequest request,
    final HttpServletResponse response)
    throws ServletException, IOException
  {
    final String attribute = request.getParameter("attr");
    byte[] value = null;
    final String content = request.getParameter("content-type");

    if (content != null && content.equalsIgnoreCase("octet")) {
      response.setContentType("application/octet-stream");
      response.setHeader(
        "Content-Disposition",
        "attachment; filename=\"" + attribute + ".bin\"");
    } else {
      response.setContentType("text/plain");
    }

    try {
      LdapConnection conn = null;
      try {
        conn = this.pool.checkOut();

        final SearchOperation search = new SearchOperation(conn);
        final LdapResult result = search.execute(
          new SearchRequest(
            new SearchFilter(request.getParameter("query")),
            request.getParameterValues("attr"))).getResult();
        for (LdapEntry e : result.getEntries()) {
          final LdapAttribute a = e.getLdapAttributes().getAttribute(attribute);
          if (a != null && a.getValues().size() > 0) {
            final Object rawValue = a.getValues().iterator().next();
            if (rawValue instanceof String) {
              final String stringValue = (String) rawValue;
              value = stringValue.getBytes();
            } else {
              value = (byte[]) rawValue;
            }
          }
        }
      } finally {
        this.pool.checkIn(conn);
      }

      if (value != null) {
        final OutputStream out = response.getOutputStream();
        out.write(value);
        out.flush();
        out.close();
      }

    } catch (Exception e) {
      if (this.logger.isErrorEnabled()) {
        this.logger.error("Error performing search", e);
      }
      throw new ServletException(e.getMessage());
    }
  }


  /**
   * Called by the servlet container to indicate to a servlet that the servlet
   * is being taken out of service.
   */
  public void destroy()
  {
    try {
      this.pool.close();
    } finally {
      super.destroy();
    }
  }
}
