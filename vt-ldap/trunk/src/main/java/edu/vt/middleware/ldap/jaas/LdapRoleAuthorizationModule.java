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
package edu.vt.middleware.ldap.jaas;

import java.security.Principal;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import com.sun.security.auth.callback.TextCallbackHandler;
import edu.vt.middleware.ldap.LdapConnection;
import edu.vt.middleware.ldap.LdapEntry;
import edu.vt.middleware.ldap.LdapException;
import edu.vt.middleware.ldap.LdapResult;
import edu.vt.middleware.ldap.SearchFilter;
import edu.vt.middleware.ldap.SearchOperation;
import edu.vt.middleware.ldap.SearchRequest;

/**
 * <code>LdapRoleAuthorizationModule</code> provides a JAAS authentication hook
 * into LDAP roles. No authentication is performed in this module. Role data is
 * set for the login name in the shared state or for the name returned by the
 * CallbackHandler.
 *
 * @author  Middleware Services
 * @version  $Revision$ $Date$
 */
public class LdapRoleAuthorizationModule extends AbstractLoginModule
  implements LoginModule
{

  /** Ldap filter for role searches. */
  private String roleFilter;

  /** Role attribute to add to role data. */
  private String[] roleAttribute = new String[0];

  /** Whether failing to find any roles should raise an exception. */
  private boolean noResultsIsError;

  /** Ldap to use for searching roles against the LDAP. */
  private LdapConnection ldapConn;


  /** {@inheritDoc} */
  public void initialize(
    final Subject subject,
    final CallbackHandler callbackHandler,
    final Map<String, ?> sharedState,
    final Map<String, ?> options)
  {
    super.initialize(subject, callbackHandler, sharedState, options);

    final Iterator<String> i = options.keySet().iterator();
    while (i.hasNext()) {
      final String key = i.next();
      final String value = (String) options.get(key);
      if (key.equalsIgnoreCase("roleFilter")) {
        this.roleFilter = value;
      } else if (key.equalsIgnoreCase("roleAttribute")) {
        if ("*".equals(value)) {
          this.roleAttribute = null;
        } else {
          this.roleAttribute = value.split(",");
        }
      } else if (key.equalsIgnoreCase("noResultsIsError")) {
        this.noResultsIsError = Boolean.valueOf(value);
      }
    }

    if (this.logger.isDebugEnabled()) {
      this.logger.debug("roleFilter = " + this.roleFilter);
      this.logger.debug(
        "roleAttribute = " + Arrays.toString(this.roleAttribute));
      this.logger.debug("noResultsIsError = " + this.noResultsIsError);
    }

    this.ldapConn = createLdapConnection(options);
    if (this.logger.isDebugEnabled()) {
      this.logger.debug(
        "Created ldap connection: " + this.ldapConn.getLdapConfig());
    }
  }


  /** {@inheritDoc} */
  public boolean login()
    throws LoginException
  {
    try {
      final NameCallback nameCb = new NameCallback("Enter user: ");
      final PasswordCallback passCb = new PasswordCallback(
        "Enter user password: ",
        false);
      this.getCredentials(nameCb, passCb, false);

      if (nameCb.getName() == null && this.tryFirstPass) {
        this.getCredentials(nameCb, passCb, true);
      }

      final String loginName = nameCb.getName();
      if (loginName != null && this.setLdapPrincipal) {
        this.principals.add(new LdapPrincipal(loginName));
        this.success = true;
      }

      final String loginDn = (String) this.sharedState.get(LOGIN_DN);
      if (loginDn != null && this.setLdapDnPrincipal) {
        this.principals.add(new LdapDnPrincipal(loginDn));
        this.success = true;
      }

      if (this.roleFilter != null) {
        final Object[] filterArgs = new Object[] {loginDn, loginName, };
        this.ldapConn.open();
        final SearchOperation search = new SearchOperation(this.ldapConn);
        final LdapResult result = search.execute(new SearchRequest(
          new SearchFilter(
            this.roleFilter, filterArgs), this.roleAttribute)).getResult();
        if (result.size() == 0 && this.noResultsIsError) {
          this.success = false;
          throw new LoginException(
            "Could not find roles using " + this.roleFilter);
        }
        for (LdapEntry le : result.getEntries()) {
          this.roles.addAll(this.attributesToRoles(le.getLdapAttributes()));
        }
      }
      if (this.defaultRole != null && !this.defaultRole.isEmpty()) {
        this.roles.addAll(this.defaultRole);
      }
      if (!this.roles.isEmpty()) {
        this.success = true;
      }
      this.storeCredentials(nameCb, passCb, null);
    } catch (LdapException e) {
      if (this.logger.isDebugEnabled()) {
        this.logger.debug("Error occured attempting role lookup", e);
      }
      this.success = false;
      throw new LoginException(e.getMessage());
    } finally {
      this.ldapConn.close();
    }
    return true;
  }


  /**
   * This provides command line access to a <code>LdapRoleLoginModule</code>.
   *
   * @param  args  <code>String[]</code>
   *
   * @throws  Exception  if an error occurs
   */
  public static void main(final String[] args)
    throws Exception
  {
    String name = "vt-ldap-role";
    if (args.length > 0) {
      name = args[0];
    }

    final LoginContext lc = new LoginContext(name, new TextCallbackHandler());
    lc.login();
    System.out.println("Authorization succeeded");

    final Set<Principal> principals = lc.getSubject().getPrincipals();
    System.out.println("Subject Principal(s): ");

    final Iterator<Principal> i = principals.iterator();
    while (i.hasNext()) {
      final Principal p = i.next();
      System.out.println("  " + p.getName());
    }
    lc.logout();
  }
}
