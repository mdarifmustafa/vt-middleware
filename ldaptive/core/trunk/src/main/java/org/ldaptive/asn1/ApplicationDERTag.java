/*
  $Id: $

  Copyright (C) 2012 Virginia Tech.
  All rights reserved.

  SEE LICENSE FOR MORE INFORMATION

  Author:  Middleware Services
  Email:   middleware@vt.edu
  Version: $Revision: $
  Updated: $Date: $
*/
package org.ldaptive.asn1;

/**
 * Generic application-specific tag.
 *
 * @author Middleware Services
 * @version $Revision: $
 */
public class ApplicationDERTag extends AbstractDERTag
{
  /** Generic tag name for a application-specific type. */
  public static final String TAG_NAME = "APP";


  /**
   * Creates a new application-specific tag with given tag number.
   *
   * @param number        Tag number.
   * @param isConstructed True for constructed tag, false otherwise.
   */
  public ApplicationDERTag(final int number, final boolean isConstructed)
  {
    super(number, isConstructed);
  }


  /** {@inheritDoc} */
  @Override
  public String name()
  {
    return String.format("%s(%s)", TAG_NAME, getTagNo());
  }


  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return name();
  }
}
