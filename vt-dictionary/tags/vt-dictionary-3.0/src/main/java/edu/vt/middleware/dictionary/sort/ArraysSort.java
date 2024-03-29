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
package edu.vt.middleware.dictionary.sort;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Delegates sorting to {@link java.util.Arrays#sort(Object[], Comparator)}.
 *
 * @author  Middleware Services
 * @version  $Revision: 1252 $ $Date: 2010-04-16 17:24:23 -0400 (Fri, 16 Apr 2010) $
 */
public class ArraysSort implements ArraySorter
{


  /** {@inheritDoc} */
  public void sort(final String[] array)
  {
    Arrays.sort(array);
  }


  /** {@inheritDoc} */
  public void sort(final String[] array, final Comparator<String> c)
  {
    Arrays.sort(array, c);
  }
}
