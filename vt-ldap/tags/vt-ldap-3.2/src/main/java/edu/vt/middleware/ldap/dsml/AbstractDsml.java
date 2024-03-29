/*
  $Id$

  Copyright (C) 2003-2009 Virginia Tech.
  All rights reserved.

  SEE LICENSE FOR MORE INFORMATION

  Author:  Middleware Services
  Email:   middleware@vt.edu
  Version: $Revision$
  Updated: $Date$
*/
package edu.vt.middleware.ldap.dsml;

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import javax.naming.NamingException;
import javax.naming.directory.SearchResult;
import edu.vt.middleware.ldap.LdapUtil;
import edu.vt.middleware.ldap.bean.LdapAttribute;
import edu.vt.middleware.ldap.bean.LdapEntry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

/**
 * <code>AbstractDsml</code> contains functions for converting LDAP search
 * result sets into DSML.
 *
 * @author  Middleware Services
 * @version  $Revision$ $Date$
 */

public abstract class AbstractDsml implements Serializable
{

  /** serial version uid. */
  private static final long serialVersionUID = -5626050181955100494L;

  /** Log for this class. */
  protected final Log logger = LogFactory.getLog(this.getClass());


  /**
   * This will take the results of a prior LDAP query and convert it to a DSML
   * <code>Document</code>.
   *
   * @param  results  <code>Iterator</code> of LDAP search results
   *
   * @return  <code>Document</code>
   */
  public abstract Document createDsml(final Iterator<SearchResult> results);


  /**
   * This will take an LDAP search result and convert it to a DSML entry
   * element.
   *
   * @param  entryName  <code>QName</code> name of element to create
   * @param  result  <code>SearchResult</code> to convert
   * @param  ns  <code>Namespace</code> of DSML
   *
   * @return  <code>Document</code>
   *
   * @throws  NamingException  if an error occurs while reading the search
   * result
   */
  protected Element createDsmlEntry(
    final QName entryName,
    final SearchResult result,
    final Namespace ns)
    throws NamingException
  {
    // create Element to hold result content
    final Element entryElement = DocumentHelper.createElement(entryName);

    if (result != null) {

      final LdapEntry entry = new LdapEntry(result);
      final String dn = entry.getDn();
      if (dn != null) {
        entryElement.addAttribute("dn", dn);
      }

      for (LdapAttribute attr : entry.getLdapAttributes().getAttributes()) {
        final String attrName = attr.getName();
        final List<?> attrValues = attr.getValues();
        final Element attrElement = createDsmlAttribute(
          attrName,
          attrValues,
          ns);
        if (attrElement.hasContent()) {
          entryElement.add(attrElement);
        }
      }
    }

    return entryElement;
  }


  /**
   * This will take an attribute name and it's values and return a DSML
   * attribute element.
   *
   * @param  attrName  <code>String</code>
   * @param  attrValues  <code>List</code>
   * @param  ns  <code>Namespace</code> of DSML
   *
   * @return  <code>Element</code>
   */
  protected Element createDsmlAttribute(
    final String attrName,
    final List<?> attrValues,
    final Namespace ns)
  {
    final Element attrElement = DocumentHelper.createElement("");

    if (attrName != null) {

      attrElement.setQName(new QName("attr", ns));
      attrElement.addAttribute("name", attrName);
      if (attrValues != null) {
        final Iterator<?> i = attrValues.iterator();
        while (i.hasNext()) {
          final Object rawValue = i.next();
          String value = null;
          boolean isBase64 = false;
          if (rawValue instanceof String) {
            value = (String) rawValue;
          } else if (rawValue instanceof byte[]) {
            value = LdapUtil.base64Encode((byte[]) rawValue);
            isBase64 = true;
          } else {
            if (this.logger.isWarnEnabled()) {
              this.logger.warn(
                "Could not cast attribute value as a byte[]" +
                " or a String");
            }
          }
          if (value != null) {
            final Element valueElement = attrElement.addElement(
              new QName("value", ns));
            valueElement.addText(value);
            if (isBase64) {
              valueElement.addAttribute("encoding", "base64");
            }
          }
        }
      }
    }

    return attrElement;
  }


  /**
   * This will write the supplied LDAP search results to the supplied writer in
   * the form of DSML.
   *
   * @param  results  <code>Iterator</code> of LDAP search results
   * @param  writer  <code>Writer</code> to write to
   *
   * @throws  IOException  if an error occurs while writing
   */
  public void outputDsml(
    final Iterator<SearchResult> results,
    final Writer writer)
    throws IOException
  {
    final XMLWriter xmlWriter = new XMLWriter(
      writer,
      OutputFormat.createPrettyPrint());
    xmlWriter.write(createDsml(results));
    writer.flush();
  }


  /**
   * This will take a Reader containing a DSML <code>Document</code> and convert
   * it to an Iterator of LDAP search results.
   *
   * @param  reader  <code>Reader</code> containing DSML content
   *
   * @return  <code>Iterator</code> - of LDAP search results
   *
   * @throws  DocumentException  if an error occurs building a document from the
   * reader
   * @throws  IOException  if an I/O error occurs
   */
  public Iterator<SearchResult> importDsml(final Reader reader)
    throws DocumentException, IOException
  {
    final Document dsml = new SAXReader().read(reader);
    return createSearchResults(dsml);
  }


  /**
   * This will take a DSML <code>Document</code> and convert it to an Iterator
   * of LDAP search results.
   *
   * @param  doc  <code>Document</code> of DSML
   *
   * @return  <code>Iterator</code> - of LDAP search results
   */
  protected abstract Iterator<SearchResult> createSearchResults(
    final Document doc);


  /**
   * This will take a DSML <code>Element</code> containing an entry of type
   * <entry/> and convert it to an LDAP entry.
   *
   * @param  entryElement  <code>Element</code> of DSML content
   *
   * @return  <code>LdapEntry</code>
   */
  protected LdapEntry createSearchResult(final Element entryElement)
  {
    final LdapEntry ldapEntry = new LdapEntry();
    ldapEntry.setDn("");

    if (entryElement != null) {

      final String name = entryElement.attributeValue("dn");
      if (name != null) {
        ldapEntry.setDn(name);
      }

      if (entryElement.hasContent()) {

        // load the attr elements
        final Iterator<?> attrIterator = entryElement.elementIterator("attr");
        while (attrIterator.hasNext()) {
          final Element attrElement = (Element) attrIterator.next();
          final String attrName = attrElement.attributeValue("name");
          if (attrName != null && attrElement.hasContent()) {
            final LdapAttribute ldapAttribute = new LdapAttribute(attrName);
            final Iterator<?> valueIterator = attrElement.elementIterator(
              "value");
            while (valueIterator.hasNext()) {
              final Element valueElement = (Element) valueIterator.next();
              final String value = valueElement.getText();
              if (value != null) {
                final String encoding = valueElement.attributeValue("encoding");
                if (encoding != null && encoding.equals("base64")) {
                  ldapAttribute.getValues().add(LdapUtil.base64Decode(value));
                } else {
                  ldapAttribute.getValues().add(value);
                }
              }
            }
            ldapEntry.getLdapAttributes().addAttribute(ldapAttribute);
          }
        }
      }
    }

    return ldapEntry;
  }
}
