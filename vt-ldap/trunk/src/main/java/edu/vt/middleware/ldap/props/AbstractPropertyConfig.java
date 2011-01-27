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
package edu.vt.middleware.ldap.props;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <code>AbstractPropertyConfig</code> provides a base implementation of <code>
 * PropertyConfig</code>.
 *
 * @author  Middleware Services
 * @version  $Revision$ $Date$
 */
public abstract class AbstractPropertyConfig implements PropertyConfig
{

  /** Log for this class. */
  protected final Log logger = LogFactory.getLog(this.getClass());

  /** Whether this config has been marked immutable. */
  private boolean immutable;


  /** Make this property config immutable. */
  public void makeImmutable()
  {
    this.immutable = true;
  }


  /**
   * Verifies if this property config is immutable.
   *
   * @throws  IllegalStateException  if this property config is immutable
   */
  public void checkImmutable()
  {
    if (this.immutable) {
      throw new IllegalStateException("Cannot modify immutable object");
    }
  }


  /** {@inheritDoc} */
  public abstract String getPropertiesDomain();


  /** {@inheritDoc} */
  public abstract void setProviderProperty(
    final String name,
    final String value);


  /** {@inheritDoc} */
  public void setProviderProperties(final Properties properties)
  {
    if (properties != null) {
      final Map<String, String> props = new HashMap<String, String>();
      final Enumeration<?> en = properties.keys();
      if (en != null) {
        while (en.hasMoreElements()) {
          final String name = (String) en.nextElement();
          final String value = (String) properties.get(name);
          if (this.hasProviderProperty(name)) {
            props.put(name, value);
          } else {
            this.setProviderProperty(name, value);
          }
        }
        for (Map.Entry<String, String> e : props.entrySet()) {
          this.setProviderProperty(e.getKey(), e.getValue());
        }
      }
    }
  }


  /**
   * See {@link #setProviderProperty(String,String)}.
   *
   * @param  properties  map of provider properties
   */
  public void setProviderProperties(final Map<String, String> properties)
  {
    if (properties != null) {
      final Map<String, String> props = new HashMap<String, String>();
      for (Map.Entry<String, String> e : properties.entrySet()) {
        if (this.hasProviderProperty(e.getKey())) {
          props.put(e.getKey(), e.getValue());
        } else {
          this.setProviderProperty(e.getKey(), e.getValue());
        }
      }
      for (Map.Entry<String, String> e : props.entrySet()) {
        this.setProviderProperty(e.getKey(), e.getValue());
      }
    }
  }


  /** {@inheritDoc} */
  public abstract boolean hasProviderProperty(final String name);


  /**
   * Verifies that a string is not null or empty.
   *
   * @param  s  to verify
   * @param  allowNull  whether null strings are valid
   *
   * @throws  IllegalArgumentException  if the string is null or empty
   */
  protected void checkStringInput(final String s, final boolean allowNull)
  {
    if (allowNull) {
      if (s != null && "".equals(s)) {
        throw new IllegalArgumentException("Input cannot be empty");
      }
    } else {
      if (s == null || "".equals(s)) {
        throw new IllegalArgumentException("Input cannot be null or empty");
      }
    }
  }
}
