/*
  $Id$

  Copyright (C) 2003-2014 Virginia Tech.
  All rights reserved.

  SEE LICENSE FOR MORE INFORMATION

  Author:  Middleware Services
  Email:   middleware@vt.edu
  Version: $Revision$
  Updated: $Date$
*/
package org.ldaptive.provider.opends;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.ldaptive.AttributeModification;
import org.ldaptive.AttributeModificationType;
import org.ldaptive.LdapAttribute;
import org.ldaptive.LdapEntry;
import org.ldaptive.SearchEntry;
import org.ldaptive.SortBehavior;
import org.ldaptive.control.ResponseControl;
import org.opends.sdk.Attribute;
import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.DN;
import org.opends.sdk.Entry;
import org.opends.sdk.LinkedAttribute;
import org.opends.sdk.LinkedHashMapEntry;
import org.opends.sdk.Modification;
import org.opends.sdk.ModificationType;
import org.opends.sdk.SortKey;

/**
 * Provides methods for converting between OpenDS specific objects and ldaptive
 * specific objects.
 *
 * @author  Middleware Services
 * @version  $Revision$ $Date$
 */
public class OpenDSUtils
{

  /** Ldap result sort behavior. */
  private final SortBehavior sortBehavior;

  /** Attributes that should be treated as binary. */
  private List<String> binaryAttrs;


  /** Default constructor. */
  public OpenDSUtils()
  {
    sortBehavior = SortBehavior.getDefaultSortBehavior();
  }


  /**
   * Creates a new opends util.
   *
   * @param  sb  sort behavior
   */
  public OpenDSUtils(final SortBehavior sb)
  {
    sortBehavior = sb;
  }


  /**
   * Returns the list of binary attributes.
   *
   * @return  list of binary attributes
   */
  public List<String> getBinaryAttributes()
  {
    return binaryAttrs;
  }


  /**
   * Sets the list of binary attributes.
   *
   * @param  s  binary attributes
   */
  public void setBinaryAttributes(final String[] s)
  {
    if (s != null) {
      binaryAttrs = Arrays.asList(s);
    }
  }


  /**
   * Returns an opends byte string that represents the values in the supplied
   * collection.
   *
   * @param  values  to convert to byte strings
   *
   * @return  byte strings
   */
  public ByteString[] fromStringValues(final Collection<String> values)
  {
    final ByteString[] bstrings = new ByteString[values.size()];
    int i = 0;
    for (String s : values) {
      final ByteStringBuilder builder = new ByteStringBuilder(s.length());
      builder.append(s);
      bstrings[i++] = builder.toByteString();
    }
    return bstrings;
  }


  /**
   * Returns an opends byte string that represents the values in the supplied
   * collection.
   *
   * @param  values  to convert to byte strings
   *
   * @return  byte strings
   */
  public ByteString[] fromBinaryValues(final Collection<byte[]> values)
  {
    final ByteString[] bstrings = new ByteString[values.size()];
    int i = 0;
    for (byte[] b : values) {
      final ByteStringBuilder builder = new ByteStringBuilder(b.length);
      builder.append(b);
      bstrings[i++] = builder.toByteString();
    }
    return bstrings;
  }


  /**
   * Returns string values for the supplied byte strings.
   *
   * @param  values  to convert to strings
   *
   * @return  string values
   */
  public String[] toStringValues(final ByteString[] values)
  {
    final String[] strings = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      strings[i] = values[i].toString();
    }
    return strings;
  }


  /**
   * Returns byte array values for the supplied byte strings.
   *
   * @param  values  to convert to byte arrays
   *
   * @return  byte array values
   */
  public byte[][] toBinaryValues(final ByteString[] values)
  {
    final byte[][] bytes = new byte[values.length][];
    for (int i = 0; i < values.length; i++) {
      bytes[i] = values[i].toByteArray();
    }
    return bytes;
  }


  /**
   * Returns an opends attribute that represents the values in the supplied ldap
   * attribute.
   *
   * @param  la  ldap attribute
   *
   * @return  opends attribute
   */
  public Attribute fromLdapAttribute(final LdapAttribute la)
  {
    Attribute attribute;
    if (la.isBinary()) {
      attribute = new LinkedAttribute(
        la.getName(),
        (Object[]) fromBinaryValues(la.getBinaryValues()));
    } else {
      attribute = new LinkedAttribute(
        la.getName(),
        (Object[]) fromStringValues(la.getStringValues()));
    }
    return attribute;
  }


  /**
   * Returns an ldap attribute using the supplied opends attribute.
   *
   * @param  a  opends attribute
   *
   * @return  ldap attribute
   */
  public LdapAttribute toLdapAttribute(final Attribute a)
  {
    boolean isBinary = false;
    if (a.getAttributeDescriptionAsString().contains(";binary")) {
      isBinary = true;
    } else if (binaryAttrs != null &&
               binaryAttrs.contains(a.getAttributeDescriptionAsString())) {
      isBinary = true;
    }

    final LdapAttribute la = new LdapAttribute(sortBehavior, isBinary);
    la.setName(a.getAttributeDescriptionAsString());
    if (isBinary) {
      la.addBinaryValue(toBinaryValues(a.toArray()));
    } else {
      la.addStringValue(toStringValues(a.toArray()));
    }
    return la;
  }


  /**
   * Returns a list of opends attribute that represents the values in the
   * supplied ldap attributes.
   *
   * @param  c  ldap attributes
   *
   * @return  opends attributes
   */
  public Attribute[] fromLdapAttributes(final Collection<LdapAttribute> c)
  {
    final List<Attribute> attributes = new ArrayList<Attribute>();
    for (LdapAttribute a : c) {
      attributes.add(fromLdapAttribute(a));
    }
    return attributes.toArray(new Attribute[attributes.size()]);
  }


  /**
   * Returns an opends entry that represents the values in the supplied entry.
   *
   * @param  le  ldap entry
   *
   * @return  opends entry
   */
  public Entry fromLdapEntry(final LdapEntry le)
  {
    final Entry entry = new LinkedHashMapEntry();
    entry.setName(DN.valueOf(le.getDn()));
    for (LdapAttribute la : le.getAttributes()) {
      entry.addAttribute(fromLdapAttribute(la), null);
    }
    return entry;
  }


  /**
   * Returns a search entry using the supplied opends entry.
   *
   * @param  e  opends entry
   * @param  c  response controls
   * @param  id  message id
   *
   * @return  search entry
   */
  public SearchEntry toSearchEntry(
    final Entry e,
    final ResponseControl[] c,
    final int id)
  {
    final SearchEntry se = new SearchEntry(id, c, sortBehavior);
    se.setDn(e.getName().toString());
    for (Attribute a : e.getAllAttributes()) {
      se.addAttribute(toLdapAttribute(a));
    }
    return se;
  }


  /**
   * Returns opends modifications using the supplied attribute modifications.
   *
   * @param  am  attribute modifications
   *
   * @return  opends modifications
   */
  public Modification[] fromAttributeModification(
    final AttributeModification[] am)
  {
    final Modification[] mods = new Modification[am.length];
    for (int i = 0; i < am.length; i++) {
      final Attribute a = fromLdapAttribute(am[i].getAttribute());
      if (am[i].getAttribute().isBinary()) {
        mods[i] = new Modification(
          getModificationType(am[i].getAttributeModificationType()),
          a);
      } else {
        mods[i] = new Modification(
          getModificationType(am[i].getAttributeModificationType()),
          a);
      }
    }
    return mods;
  }


  /**
   * Returns opends sort keys using the supplied sort keys.
   *
   * @param  sk  sort keys
   *
   * @return  opends sort keys
   */
  public static SortKey[] fromSortKey(final org.ldaptive.control.SortKey[] sk)
  {
    SortKey[] keys = null;
    if (sk != null) {
      keys = new SortKey[sk.length];
      for (int i = 0; i < sk.length; i++) {
        keys[i] = new SortKey(
          sk[i].getAttributeDescription(),
          sk[i].getReverseOrder(),
          sk[i].getMatchingRuleId());
      }
    }
    return keys;
  }


  /**
   * Returns the opends modification type for the supplied attribute
   * modification type.
   *
   * @param  am  attribute modification type
   *
   * @return  modification type
   */
  protected static ModificationType getModificationType(
    final AttributeModificationType am)
  {
    ModificationType type = null;
    if (am == AttributeModificationType.ADD) {
      type = ModificationType.ADD;
    } else if (am == AttributeModificationType.REMOVE) {
      type = ModificationType.DELETE;
    } else if (am == AttributeModificationType.REPLACE) {
      type = ModificationType.REPLACE;
    }
    return type;
  }
}
