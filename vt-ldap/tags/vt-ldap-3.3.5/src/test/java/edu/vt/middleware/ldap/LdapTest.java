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
package edu.vt.middleware.ldap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.LimitExceededException;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.SizeLimitExceededException;
import javax.naming.TimeLimitExceededException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidSearchFilterException;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchResult;
import edu.vt.middleware.ldap.Ldap.AttributeModification;
import edu.vt.middleware.ldap.bean.LdapAttribute;
import edu.vt.middleware.ldap.bean.LdapAttributes;
import edu.vt.middleware.ldap.bean.LdapEntry;
import edu.vt.middleware.ldap.bean.LdapResult;
import edu.vt.middleware.ldap.handler.AttributeHandler;
import edu.vt.middleware.ldap.handler.BinaryAttributeHandler;
import edu.vt.middleware.ldap.handler.BinarySearchResultHandler;
import edu.vt.middleware.ldap.handler.CaseChangeSearchResultHandler;
import edu.vt.middleware.ldap.handler.CaseChangeSearchResultHandler.CaseChange;
import edu.vt.middleware.ldap.handler.EntryDnSearchResultHandler;
import edu.vt.middleware.ldap.handler.FqdnSearchResultHandler;
import edu.vt.middleware.ldap.handler.MergeSearchResultHandler;
import edu.vt.middleware.ldap.handler.RecursiveAttributeHandler;
import edu.vt.middleware.ldap.handler.RecursiveSearchResultHandler;
import edu.vt.middleware.ldap.handler.SearchResultHandler;
import edu.vt.middleware.ldap.ldif.Ldif;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

/**
 * Unit test for {@link Ldap}.
 *
 * @author  Middleware Services
 * @version  $Revision$
 */
public class LdapTest
{

  /** Invalid search filter. */
  public static final String INVALID_FILTER = "(cn=not-a-name)";

  /** Entry created for ldap tests. */
  private static LdapEntry testLdapEntry;

  /** Entry created for ldap tests. */
  private static LdapEntry specialCharsLdapEntry;

  /** Entries for group tests. */
  private static Map<String, LdapEntry[]> groupEntries =
    new HashMap<String, LdapEntry[]>();

  /**
   * Initialize the map of group entries.
   */
  static {
    for (int i = 2; i <= 5; i++) {
      groupEntries.put(String.valueOf(i), new LdapEntry[2]);
    }
  }

  /** Ldap instance for concurrency testing. */
  private Ldap singleLdap;


  /**
   * Default constructor.
   *
   * @throws  Exception  if ldap cannot be constructed
   */
  public LdapTest()
    throws Exception
  {
    this.singleLdap = TestUtil.createLdap();
  }


  /**
   * @param  ldifFile  to create.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "createEntry2" })
  @BeforeClass(groups = {"ldaptest"})
  public void createLdapEntry(final String ldifFile)
    throws Exception
  {
    final String ldif = TestUtil.readFileIntoString(ldifFile);
    testLdapEntry = TestUtil.convertLdifToEntry(ldif);

    Ldap ldap = TestUtil.createSetupLdap();
    ldap.create(
      testLdapEntry.getDn(),
      testLdapEntry.getLdapAttributes().toAttributes());
    ldap.close();
    ldap = TestUtil.createLdap();
    while (
      !ldap.compare(
          testLdapEntry.getDn(),
          new SearchFilter(testLdapEntry.getDn().split(",")[0]))) {
      Thread.sleep(100);
    }
    ldap.close();
  }


  /**
   * @param  ldifFile2  to create.
   * @param  ldifFile3  to create.
   * @param  ldifFile4  to create.
   * @param  ldifFile5  to create.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "createGroup2",
      "createGroup3",
      "createGroup4",
      "createGroup5"
    }
  )
  @BeforeClass(groups = {"ldaptest"})
  public void createGroupEntry(
    final String ldifFile2,
    final String ldifFile3,
    final String ldifFile4,
    final String ldifFile5)
    throws Exception
  {
    groupEntries.get("2")[0] = TestUtil.convertLdifToEntry(
      TestUtil.readFileIntoString(ldifFile2));
    groupEntries.get("3")[0] = TestUtil.convertLdifToEntry(
      TestUtil.readFileIntoString(ldifFile3));
    groupEntries.get("4")[0] = TestUtil.convertLdifToEntry(
      TestUtil.readFileIntoString(ldifFile4));
    groupEntries.get("5")[0] = TestUtil.convertLdifToEntry(
      TestUtil.readFileIntoString(ldifFile5));

    Ldap ldap = TestUtil.createSetupLdap();
    for (Map.Entry<String, LdapEntry[]> e : groupEntries.entrySet()) {
      ldap.create(
        e.getValue()[0].getDn(),
        e.getValue()[0].getLdapAttributes().toAttributes());
    }
    ldap.close();

    ldap = TestUtil.createLdap();
    for (Map.Entry<String, LdapEntry[]> e : groupEntries.entrySet()) {
      while (
        !ldap.compare(
            e.getValue()[0].getDn(),
            new SearchFilter(e.getValue()[0].getDn().split(",")[0]))) {
        Thread.sleep(100);
      }
    }

    // setup group relationships
    ldap.modifyAttributes(
      groupEntries.get("2")[0].getDn(),
      AttributeModification.ADD,
      AttributesFactory.createAttributes(
        "member",
        "uugid=group3,ou=test,dc=vt,dc=edu"));
    ldap.modifyAttributes(
      groupEntries.get("3")[0].getDn(),
      AttributeModification.ADD,
      AttributesFactory.createAttributes(
        "member",
        new String[] {
          "uugid=group4,ou=test,dc=vt,dc=edu",
          "uugid=group5,ou=test,dc=vt,dc=edu",
        }));
    ldap.modifyAttributes(
      groupEntries.get("4")[0].getDn(),
      AttributeModification.ADD,
      AttributesFactory.createAttributes(
        "member",
        "uugid=group3,ou=test,dc=vt,dc=edu"));
    ldap.close();
  }


  /**
   * @param  ldifFile  to create.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "createSpecialCharsEntry" })
  @BeforeClass(groups = {"ldaptest"})
  public void createSpecialCharsEntry(final String ldifFile)
    throws Exception
  {
    final String ldif = TestUtil.readFileIntoString(ldifFile);
    specialCharsLdapEntry = TestUtil.convertLdifToEntry(ldif);

    Ldap ldap = TestUtil.createSetupLdap();
    ldap.create(
      specialCharsLdapEntry.getDn(),
      specialCharsLdapEntry.getLdapAttributes().toAttributes());
    ldap.close();
    ldap = TestUtil.createLdap();
    while (
      !ldap.compare(
        specialCharsLdapEntry.getDn(),
          new SearchFilter(specialCharsLdapEntry.getDn().split(",")[0]))) {
      Thread.sleep(100);
    }
    ldap.close();
  }


  /**
   * @param  oldDn  to rename.
   * @param  newDn  to rename to.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "renameOldDn", "renameNewDn" })
  @AfterClass(groups = {"ldaptest"})
  public void renameLdapEntry(final String oldDn, final String newDn)
    throws Exception
  {
    final Ldap ldap = this.createLdap(true);
    AssertJUnit.assertNotNull(ldap.getAttributes(oldDn));
    ldap.rename(oldDn, newDn);
    AssertJUnit.assertNotNull(ldap.getAttributes(newDn));
    try {
      ldap.getAttributes(oldDn);
      AssertJUnit.fail(
        "Should have thrown NameNotFoundException, no exception thrown");
    } catch (NameNotFoundException e) {
      AssertJUnit.assertEquals(NameNotFoundException.class, e.getClass());
    } catch (Exception e) {
      AssertJUnit.fail("Should have thrown NameNotFoundException, threw " + e);
    }
    ldap.rename(newDn, oldDn);
    AssertJUnit.assertNotNull(ldap.getAttributes(oldDn));
    try {
      ldap.getAttributes(newDn);
      AssertJUnit.fail(
        "Should have thrown NameNotFoundException, no exception thrown");
    } catch (NameNotFoundException e) {
      AssertJUnit.assertEquals(NameNotFoundException.class, e.getClass());
    } catch (Exception e) {
      AssertJUnit.fail("Should have thrown NameNotFoundException, threw " + e);
    }
    ldap.close();
  }


  /** @throws  Exception  On test failure. */
  @AfterClass(
    groups = {"ldaptest"},
    dependsOnMethods = {"renameLdapEntry"}
  )
  public void deleteLdapEntry()
    throws Exception
  {
    final Ldap ldap = TestUtil.createSetupLdap();
    ldap.delete(testLdapEntry.getDn());
    ldap.delete(specialCharsLdapEntry.getDn());
    ldap.delete(groupEntries.get("2")[0].getDn());
    ldap.delete(groupEntries.get("3")[0].getDn());
    ldap.delete(groupEntries.get("4")[0].getDn());
    ldap.delete(groupEntries.get("5")[0].getDn());
    ldap.close();
  }


  /**
   * @param  createNew  whether to construct a new ldap instance.
   *
   * @return  <code>Ldap</code>
   *
   * @throws  Exception  On ldap construction failure.
   */
  public Ldap createLdap(final boolean createNew)
    throws Exception
  {
    if (createNew) {
      return TestUtil.createLdap();
    }
    return singleLdap;
  }


  /**
   * @param  dn  to compare.
   * @param  filter  to compare with.
   * @param  filterArgs  to replace args in filter with.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "compareDn", "compareFilter", "compareFilterArgs" })
  @Test(
    groups = {"ldaptest"},
    threadPoolSize = 10,
    invocationCount = 100,
    timeOut = 60000
  )
  public void compare(
    final String dn,
    final String filter,
    final String filterArgs)
    throws Exception
  {
    final Ldap ldap = this.createLdap(false);
    AssertJUnit.assertFalse(
      ldap.compare(dn, INVALID_FILTER, filterArgs.split("\\|")));
    AssertJUnit.assertTrue(ldap.compare(dn, filter, filterArgs.split("\\|")));
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  filterArgs  to replace args in filter with.
   * @param  returnAttrs  to return from search.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "searchDn",
      "searchFilter",
      "searchFilterArgs",
      "searchReturnAttrs",
      "searchResults"
    }
  )
  @Test(
    groups = {"ldaptest"},
    threadPoolSize = 10,
    invocationCount = 100,
    timeOut = 60000
  )
  public void search(
    final String dn,
    final String filter,
    final String filterArgs,
    final String returnAttrs,
    final String ldifFile)
    throws Exception
  {
    final Ldap ldap = this.createLdap(false);

    final String expected = TestUtil.readFileIntoString(ldifFile);
    final LdapEntry entry = TestUtil.convertLdifToEntry(expected);
    final LdapEntry shortDnEntry = TestUtil.convertLdifToEntry(expected);
    shortDnEntry.setDn(
      shortDnEntry.getDn().substring(0, shortDnEntry.getDn().indexOf(",")));

    final LdapEntry entryDnEntry = TestUtil.convertLdifToEntry(expected);
    entryDnEntry.getLdapAttributes().addAttribute(
      "entryDN",
      entryDnEntry.getDn());

    // test searching
    Iterator<SearchResult> iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      returnAttrs.split("\\|"));
    AssertJUnit.assertEquals(
      entry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));

    // test searching without handler
    iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      returnAttrs.split("\\|"),
      new SearchResultHandler[0]);
    AssertJUnit.assertEquals(
      shortDnEntry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));

    // test searching with multiple handlers
    final EntryDnSearchResultHandler srh = new EntryDnSearchResultHandler();
    iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      returnAttrs.split("\\|"),
      new FqdnSearchResultHandler(),
      srh);
    AssertJUnit.assertEquals(
      entryDnEntry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));

    // test that entry dn handler is no-op if attribute name conflicts
    srh.setDnAttributeName("givenName");
    iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      returnAttrs.split("\\|"),
      new FqdnSearchResultHandler(),
      srh);
    AssertJUnit.assertEquals(
      entry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({
      "pagedSearchDn",
      "pagedSearchFilter",
      "pagedSearchResults"
    })
  @Test(groups = {"ldaptest"})
  public void pagedSearch(
    final String dn,
    final String filter,
    final String ldifFile)
    throws Exception
  {
    final Ldap ldap = this.createLdap(true);
    ldap.getLdapConfig().setPagedResultsSize(1);

    final String expected = TestUtil.readFileIntoString(ldifFile);
    final LdapResult result = TestUtil.convertLdifToResult(expected);

    // test searching
    final Iterator<SearchResult> iter = ldap.search(
      dn,
      new SearchFilter(filter));
    AssertJUnit.assertEquals(
      result,
      TestUtil.convertLdifToResult((new Ldif()).createLdif(iter)));

    ldap.close();
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  filterArgs  to replace args in filter with.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "recursiveSearchDn",
      "recursiveSearchFilter",
      "recursiveSearchFilterArgs",
      "recursiveAttributeHandlerResults"
    }
  )
  @Test(groups = {"ldaptest"})
  public void recursiveAttributeHandlerSearch(
    final String dn,
    final String filter,
    final String filterArgs,
    final String ldifFile)
    throws Exception
  {
    final Ldap ldap = this.createLdap(false);

    final String expected = TestUtil.readFileIntoString(ldifFile);
    final LdapEntry entry = TestUtil.convertLdifToEntry(expected);

    // test recursive searching
    final FqdnSearchResultHandler handler = new FqdnSearchResultHandler();
    handler.setAttributeHandler(
      new AttributeHandler[] {new RecursiveAttributeHandler("member")});

    final Iterator<SearchResult> iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      (String[]) null,
      handler);
    AssertJUnit.assertEquals(
      entry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  filterArgs  to replace args in filter with.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "recursiveSearchDn",
      "recursiveSearchFilter",
      "recursiveSearchFilterArgs",
      "recursiveSearchResultHandlerResults"
    }
  )
  @Test(groups = {"ldaptest"})
  public void recursiveSearchResultHandlerSearch(
    final String dn,
    final String filter,
    final String filterArgs,
    final String ldifFile)
    throws Exception
  {
    final Ldap ldap = this.createLdap(false);

    final String expected = TestUtil.readFileIntoString(ldifFile);
    final LdapEntry entry = TestUtil.convertLdifToEntry(expected);

    // test recursive searching
    final FqdnSearchResultHandler fsrh = new FqdnSearchResultHandler();
    final RecursiveSearchResultHandler rsrh = new RecursiveSearchResultHandler(
      "member",
      new String[] {"uugid", "uid"});

    final Iterator<SearchResult> iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      (String[]) null,
      fsrh,
      rsrh);
    AssertJUnit.assertEquals(
      entry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({
      "mergeSearchDn",
      "mergeSearchFilter",
      "mergeSearchResults"
    })
  @Test(groups = {"ldaptest"})
  public void mergeSearch(
    final String dn,
    final String filter,
    final String ldifFile)
    throws Exception
  {
    final Ldap ldap = this.createLdap(false);

    final String expected = TestUtil.readFileIntoString(ldifFile);
    final LdapEntry entry = TestUtil.convertLdifToEntry(expected);

    // test merge searching
    final MergeSearchResultHandler handler = new MergeSearchResultHandler();

    final Iterator<SearchResult> iter = ldap.search(
      dn,
      new SearchFilter(filter),
      (String[]) null,
      new FqdnSearchResultHandler(),
      handler);
    AssertJUnit.assertEquals(
      entry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "mergeDuplicateSearchDn",
      "mergeDuplicateSearchFilter",
      "mergeDuplicateSearchResults"
    }
  )
  @Test(groups = {"ldaptest"})
  public void mergeDuplicateSearch(
    final String dn,
    final String filter,
    final String ldifFile)
    throws Exception
  {
    final Ldap ldap = this.createLdap(false);

    final String expected = TestUtil.readFileIntoString(ldifFile);
    final LdapEntry entry = TestUtil.convertLdifToEntry(expected);

    // test merge searching
    final MergeSearchResultHandler handler = new MergeSearchResultHandler();
    handler.setAllowDuplicates(true);

    final Iterator<SearchResult> iter = ldap.search(
      dn,
      new SearchFilter(filter),
      (String[]) null,
      new FqdnSearchResultHandler(),
      handler);
    AssertJUnit.assertEquals(
      entry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  returnAttr  to return from search.
   * @param  base64Value  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "binarySearchDn",
      "binarySearchFilter",
      "binarySearchReturnAttr",
      "binarySearchResult"
    }
  )
  @Test(groups = {"ldaptest"})
  public void binarySearch(
    final String dn,
    final String filter,
    final String returnAttr,
    final String base64Value)
    throws Exception
  {
    final Ldap ldap = this.createLdap(false);

    // test binary searching
    Iterator<SearchResult> iter = ldap.search(
      dn,
      new SearchFilter(filter),
      new String[] {returnAttr},
      new FqdnSearchResultHandler());
    AssertJUnit.assertNotSame(
      base64Value,
      iter.next().getAttributes().get(returnAttr).get());

    iter = ldap.search(
      dn,
      new SearchFilter(filter),
      new String[] {returnAttr},
      new FqdnSearchResultHandler(),
      new BinarySearchResultHandler());
    AssertJUnit.assertEquals(
      base64Value,
      iter.next().getAttributes().get(returnAttr).get());
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  filterArgs  to replace args in filter with.
   * @param  returnAttrs  to return from search.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
      {
        "searchDn",
        "searchFilter",
        "searchFilterArgs",
        "searchReturnAttrs",
        "searchResults"
      }
    )
  @Test(groups = {"ldaptest"})
  public void caseChangeSearch(
    final String dn,
    final String filter,
    final String filterArgs,
    final String returnAttrs,
    final String ldifFile)
    throws Exception
  {
    final CaseChangeSearchResultHandler srh =
      new CaseChangeSearchResultHandler();
    final String expected = TestUtil.readFileIntoString(ldifFile);
    final Ldap ldap = this.createLdap(true);

    // test no case change
    final LdapEntry noChangeEntry = TestUtil.convertLdifToEntry(expected);
    Iterator<SearchResult> iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      returnAttrs.split("\\|"),
      new FqdnSearchResultHandler(),
      srh);
    AssertJUnit.assertEquals(
      noChangeEntry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));

    // test lower case attribute values
    srh.setAttributeValueCaseChange(CaseChange.LOWER);
    final LdapEntry lcValuesChangeEntry = TestUtil.convertLdifToEntry(expected);
    for (LdapAttribute la :
         lcValuesChangeEntry.getLdapAttributes().getAttributes()) {
      final Set<Object> s = new HashSet<Object>();
      for (Object o : la.getValues()) {
        if (o instanceof String) {
          s.add(((String) o).toLowerCase());
        }
      }
      la.getValues().clear();
      la.getValues().addAll(s);
    }
    iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      returnAttrs.split("\\|"),
      new FqdnSearchResultHandler(),
      srh);
    AssertJUnit.assertEquals(
      lcValuesChangeEntry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));

    // test upper case attribute names
    srh.setAttributeValueCaseChange(CaseChange.NONE);
    srh.setAttributeNameCaseChange(CaseChange.UPPER);
    final LdapEntry ucNamesChangeEntry = TestUtil.convertLdifToEntry(expected);
    for (LdapAttribute la :
         ucNamesChangeEntry.getLdapAttributes().getAttributes()) {
      la.setName(la.getName().toUpperCase());
    }
    iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      returnAttrs.split("\\|"),
      new FqdnSearchResultHandler(),
      srh);
    AssertJUnit.assertEquals(
      ucNamesChangeEntry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));

    // test lower case everything
    srh.setAttributeValueCaseChange(CaseChange.LOWER);
    srh.setAttributeNameCaseChange(CaseChange.LOWER);
    srh.setDnCaseChange(CaseChange.LOWER);
    final LdapEntry lcAllChangeEntry = TestUtil.convertLdifToEntry(expected);
    for (LdapAttribute la :
         ucNamesChangeEntry.getLdapAttributes().getAttributes()) {
      lcAllChangeEntry.setDn(lcAllChangeEntry.getDn().toLowerCase());
      la.setName(la.getName().toLowerCase());
      final Set<Object> s = new HashSet<Object>();
      for (Object o : la.getValues()) {
        if (o instanceof String) {
          s.add(((String) o).toLowerCase());
        }
      }
      la.getValues().clear();
      la.getValues().addAll(s);
    }
    iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      returnAttrs.split("\\|"),
      new FqdnSearchResultHandler(),
      srh);
    AssertJUnit.assertEquals(
      ucNamesChangeEntry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));

    ldap.close();
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  resultsSize  of search results.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "searchExceptionDn",
      "searchExceptionFilter",
      "searchExceptionResultsSize"
    }
  )
  @Test(groups = {"ldaptest"})
  public void searchWithException(
    final String dn,
    final String filter,
    final int resultsSize)
    throws Exception
  {
    final Ldap ldap = this.createLdap(true);

    // test exception searching
    ldap.getLdapConfig().setCountLimit(resultsSize);
    ldap.getLdapConfig().setHandlerIgnoreExceptions(null);

    try {
      ldap.search(dn, new SearchFilter("(uugid=*)"));
      AssertJUnit.fail("Should have thrown SizeLimitExceededException");
    } catch (NamingException e) {
      AssertJUnit.assertEquals(SizeLimitExceededException.class, e.getClass());
    }

    ldap.getLdapConfig().setHandlerIgnoreExceptions(
      new Class[] {TimeLimitExceededException.class});
    try {
      ldap.search(dn, new SearchFilter("(uugid=*)"));
      AssertJUnit.fail("Should have thrown SizeLimitExceededException");
    } catch (NamingException e) {
      AssertJUnit.assertEquals(SizeLimitExceededException.class, e.getClass());
    }

    ldap.getLdapConfig().setHandlerIgnoreExceptions(
      new Class[] {LimitExceededException.class});

    final Iterator<SearchResult> iter = ldap.search(
      dn,
      new SearchFilter(filter));
    AssertJUnit.assertEquals(resultsSize, TestUtil.newLdapResult(iter).size());

    ldap.close();
  }


  /** @throws  Exception  On test failure. */
  @Test(groups = {"ldaptest"})
  public void searchWithRetry()
    throws Exception
  {
    final RetryLdap ldap = new RetryLdap();
    ldap.setLdapConfig(this.createLdap(true).getLdapConfig());
    ldap.getLdapConfig().setOperationRetryExceptions(
      new Class[] {InvalidSearchFilterException.class});

    // test defaults
    try {
      ldap.search(new SearchFilter("(("));
    } catch (InvalidSearchFilterException e) {
      AssertJUnit.assertEquals(
        InvalidSearchFilterException.class,
        e.getClass());
    }
    AssertJUnit.assertEquals(1, ldap.getRetryCount());
    AssertJUnit.assertEquals(
      ldap.getRunTime(),
      Math.min(ldap.getRunTime(), 50));

    // test no retry
    ldap.reset();
    ldap.getLdapConfig().setOperationRetry(0);

    try {
      ldap.search(new SearchFilter("(("));
    } catch (InvalidSearchFilterException e) {
      AssertJUnit.assertEquals(
        InvalidSearchFilterException.class,
        e.getClass());
    }
    AssertJUnit.assertEquals(0, ldap.getRetryCount());
    AssertJUnit.assertEquals(0, ldap.getRunTime());

    // test no exception
    ldap.reset();
    ldap.getLdapConfig().setOperationRetry(1);
    ldap.getLdapConfig().setOperationRetryExceptions(null);

    try {
      ldap.search(new SearchFilter("(("));
    } catch (InvalidSearchFilterException e) {
      AssertJUnit.assertEquals(
        InvalidSearchFilterException.class,
        e.getClass());
    }
    AssertJUnit.assertEquals(0, ldap.getRetryCount());
    AssertJUnit.assertEquals(0, ldap.getRunTime());

    // test retry count and wait time
    ldap.reset();
    ldap.getLdapConfig().setOperationRetry(3);
    ldap.getLdapConfig().setOperationRetryWait(1000);
    ldap.getLdapConfig().setOperationRetryExceptions(
      new Class[] {InvalidSearchFilterException.class});

    try {
      ldap.search(new SearchFilter("(("));
    } catch (InvalidSearchFilterException e) {
      AssertJUnit.assertEquals(
        InvalidSearchFilterException.class,
        e.getClass());
    }
    AssertJUnit.assertEquals(3, ldap.getRetryCount());
    AssertJUnit.assertTrue(ldap.getRunTime() % 3000 < 30);

    // test backoff interval
    ldap.reset();
    ldap.getLdapConfig().setOperationRetryBackoff(2);
    try {
      ldap.search(new SearchFilter("(("));
    } catch (InvalidSearchFilterException e) {
      AssertJUnit.assertEquals(
        InvalidSearchFilterException.class,
        e.getClass());
    }
    AssertJUnit.assertEquals(3, ldap.getRetryCount());
    AssertJUnit.assertTrue(ldap.getRunTime() % 7000 < 70);

    // test infinite retries
    ldap.reset();
    ldap.setStopCount(10);
    ldap.getLdapConfig().setOperationRetry(-1);
    try {
      ldap.search(new SearchFilter("(("));
    } catch (InvalidSearchFilterException e) {
      AssertJUnit.assertEquals(
        InvalidSearchFilterException.class,
        e.getClass());
    }
    AssertJUnit.assertEquals(10, ldap.getRetryCount());
    AssertJUnit.assertTrue(ldap.getRunTime() % 111000 < 111);

    ldap.close();
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  returnAttrs  to return from search.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "searchAttributesDn",
      "searchAttributesFilter",
      "searchAttributesReturnAttrs",
      "searchAttributesResults"
    }
  )
  @Test(
    groups = {"ldaptest"},
    threadPoolSize = 10,
    invocationCount = 100,
    timeOut = 60000
  )
  public void searchAttributes(
    final String dn,
    final String filter,
    final String returnAttrs,
    final String ldifFile)
    throws Exception
  {
    final String[] matchAttrs = filter.split("=");
    final Ldap ldap = this.createLdap(false);
    // test searching
    Iterator<SearchResult> iter = ldap.searchAttributes(
      dn,
      AttributesFactory.createAttributes(matchAttrs[0], matchAttrs[1]),
      returnAttrs.split("\\|"));
    final String expected = TestUtil.readFileIntoString(ldifFile);
    AssertJUnit.assertEquals(
      TestUtil.convertLdifToEntry(expected),
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));
    // test searching without handler
    iter = ldap.searchAttributes(
      dn,
      AttributesFactory.createAttributes(matchAttrs[0], matchAttrs[1]),
      returnAttrs.split("\\|"),
      new SearchResultHandler[0]);

    final LdapEntry entry = TestUtil.convertLdifToEntry(expected);
    entry.setDn(entry.getDn().substring(0, entry.getDn().indexOf(",")));
    AssertJUnit.assertEquals(
      entry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "specialCharSearchDn",
      "specialCharSearchFilter",
      "specialCharSearchResults"
    }
  )
  @Test(groups = {"ldaptest"})
  public void searchSpecialChars(
    final String dn,
    final String filter,
    final String ldifFile)
    throws Exception
  {
    final String expected = TestUtil.readFileIntoString(ldifFile);
    final LdapEntry entry = TestUtil.convertLdifToEntry(expected);
    // only remove escaped '/'
    entry.setDn(entry.getDn().replaceAll("\\\\/", "/"));

    final Ldap ldap = this.createLdap(false);

    final Iterator<SearchResult> iter = ldap.search(
      dn, new SearchFilter(filter));
    AssertJUnit.assertEquals(
      entry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));
  }


  /**
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "rewriteSearchDn",
      "rewriteSearchFilter",
      "rewriteSearchResults"
    }
  )
  @Test(groups = {"ldaptest"})
  public void searchRewrite(
    final String dn,
    final String filter,
    final String ldifFile)
    throws Exception
  {
    final String expected = TestUtil.readFileIntoString(ldifFile);
    final LdapEntry entry = TestUtil.convertLdifToEntry(expected);
    // remove all escaped characters
    entry.setDn(entry.getDn().replaceAll("\\\\", ""));

    final Ldap ldap = this.createLdap(false);

    final Iterator<SearchResult> iter = ldap.search(
      dn, new SearchFilter(filter));
    AssertJUnit.assertEquals(
      entry,
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));
  }


  /**
   * @param  dn  to search on.
   * @param  results  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "listDn", "listResults" })
  @Test(groups = {"ldaptest"})
  public void list(final String dn, final String results)
    throws Exception
  {
    final Ldap ldap = this.createLdap(true);
    final Iterator<NameClassPair> iter = ldap.list(dn);
    final List<String> l = new ArrayList<String>();
    while (iter.hasNext()) {
      final NameClassPair ncp = iter.next();
      l.add(ncp.getName());
    }

    final List<String> expected = Arrays.asList(results.split("\\|"));
    AssertJUnit.assertTrue(l.containsAll(expected));
    ldap.close();
  }


  /**
   * @param  dn  to search on.
   * @param  results  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "listBindingsDn", "listBindingsResults" })
  @Test(groups = {"ldaptest"})
  public void listBindings(final String dn, final String results)
    throws Exception
  {
    final Ldap ldap = this.createLdap(true);
    final Iterator<Binding> iter = ldap.listBindings(dn);
    final List<String> l = new ArrayList<String>();
    while (iter.hasNext()) {
      final Binding b = iter.next();
      if (Context.class.isAssignableFrom(b.getObject().getClass())) {
        ((Context) b.getObject()).close();
      }
      l.add(b.getName());
    }

    final List<String> expected = Arrays.asList(results.split("\\|"));
    AssertJUnit.assertTrue(l.containsAll(expected));
    ldap.close();
  }


  /**
   * @param  dn  to search on.
   * @param  returnAttrs  to return from search.
   * @param  results  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "getAttributesDn",
      "getAttributesReturnAttrs",
      "getAttributesResults"
    }
  )
  @Test(
    groups = {"ldaptest"},
    threadPoolSize = 10,
    invocationCount = 100,
    timeOut = 60000
  )
  public void getAttributes(
    final String dn,
    final String returnAttrs,
    final String results)
    throws Exception
  {
    final Ldap ldap = this.createLdap(false);
    final Attributes attrs = ldap.getAttributes(dn, returnAttrs.split("\\|"));
    final LdapAttributes expected = TestUtil.convertStringToAttributes(results);
    AssertJUnit.assertEquals(expected, TestUtil.newLdapAttributes(attrs));
  }


  /**
   * @param  dn  to search on.
   * @param  returnAttrs  to return from search.
   * @param  results  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "getAttributesBase64Dn",
      "getAttributesBase64ReturnAttrs",
      "getAttributesBase64Results"
    }
  )
  @Test(groups = {"ldaptest"})
  public void getAttributesBase64(
    final String dn,
    final String returnAttrs,
    final String results)
    throws Exception
  {
    final Ldap ldap = this.createLdap(true);
    final Attributes attrs = ldap.getAttributes(
      dn,
      returnAttrs.split("\\|"),
      new BinaryAttributeHandler());
    final LdapAttributes expected = TestUtil.convertStringToAttributes(results);
    AssertJUnit.assertEquals(expected, TestUtil.newLdapAttributes(attrs));
    ldap.close();
  }


  /**
   * @param  dn  to search on.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "getSchemaDn", "getSchemaResults" })
  @Test(groups = {"ldaptest"})
  public void getSchema(final String dn, final String ldifFile)
    throws Exception
  {
    final Ldap ldap = this.createLdap(true);
    final Iterator<SearchResult> iter = ldap.getSchema(dn);
    final String expected = TestUtil.readFileIntoString(ldifFile);
    AssertJUnit.assertEquals(
      TestUtil.convertLdifToResult(expected),
      TestUtil.convertLdifToResult((new Ldif()).createLdif(iter)));
    ldap.close();
  }


  /** @throws  Exception  On test failure. */
  @Test(
    groups = {"ldaptest"},
    enabled = false
  )
  public void getSaslMechanisms()
    throws Exception
  {
    final Ldap ldap = this.createLdap(true);
    ldap.getSaslMechanisms();
    ldap.close();
  }


  /** @throws  Exception  On test failure. */
  @Test(
    groups = {"ldaptest"},
    enabled = false
  )
  public void getSupportedControls()
    throws Exception
  {
    final Ldap ldap = this.createLdap(true);
    ldap.getSupportedControls();
    ldap.close();
  }


  /**
   * @param  dn  to modify.
   * @param  attrs  to add.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "addAttributeDn", "addAttributeAttribute" })
  @Test(groups = {"ldaptest"})
  public void addAttribute(final String dn, final String attrs)
    throws Exception
  {
    final LdapAttribute expected = TestUtil.convertStringToAttributes(attrs)
        .getAttributes().iterator().next();
    final Ldap ldap = this.createLdap(true);
    ldap.modifyAttributes(
      dn,
      AttributeModification.ADD,
      AttributesFactory.createAttributes(
        expected.getName(),
        expected.getValues().toArray()));

    final Attributes a = ldap.getAttributes(
      dn,
      new String[] {expected.getName()});
    AssertJUnit.assertEquals(
      expected,
      TestUtil.newLdapAttributes(a).getAttribute(expected.getName()));
    ldap.close();
  }


  /**
   * @param  dn  to modify.
   * @param  attrs  to add.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "addAttributesDn", "addAttributesAttributes" })
  @Test(groups = {"ldaptest"})
  public void addAttributes(final String dn, final String attrs)
    throws Exception
  {
    final LdapAttributes expected = TestUtil.convertStringToAttributes(attrs);
    final Ldap ldap = this.createLdap(true);
    final ModificationItem[] mods = new ModificationItem[expected.size()];
    int i = 0;
    for (LdapAttribute la : expected.getAttributes()) {
      mods[i] = new ModificationItem(
        DirContext.ADD_ATTRIBUTE,
        la.toAttribute());
      i++;
    }
    ldap.modifyAttributes(dn, mods);

    final Attributes a = ldap.getAttributes(dn, expected.getAttributeNames());
    AssertJUnit.assertEquals(expected, TestUtil.newLdapAttributes(a));
    ldap.close();
  }


  /**
   * @param  dn  to modify.
   * @param  attrs  to replace.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "replaceAttributeDn", "replaceAttributeAttribute" })
  @Test(
    groups = {"ldaptest"},
    dependsOnMethods = {"addAttribute"}
  )
  public void replaceAttribute(final String dn, final String attrs)
    throws Exception
  {
    final LdapAttribute expected = TestUtil.convertStringToAttributes(attrs)
        .getAttributes().iterator().next();
    final Ldap ldap = this.createLdap(true);
    ldap.modifyAttributes(
      dn,
      AttributeModification.REPLACE,
      AttributesFactory.createAttributes(
        expected.getName(),
        expected.getValues().toArray()));

    final Attributes a = ldap.getAttributes(
      dn,
      new String[] {expected.getName()});
    AssertJUnit.assertEquals(
      expected,
      TestUtil.newLdapAttributes(a).getAttribute(expected.getName()));
    ldap.close();
  }


  /**
   * @param  dn  to modify.
   * @param  attrs  to replace.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "replaceAttributesDn", "replaceAttributesAttributes" })
  @Test(
    groups = {"ldaptest"},
    dependsOnMethods = {"addAttributes"}
  )
  public void replaceAttributes(final String dn, final String attrs)
    throws Exception
  {
    final LdapAttributes expected = TestUtil.convertStringToAttributes(attrs);
    final Ldap ldap = this.createLdap(true);
    final ModificationItem[] mods = new ModificationItem[expected.size()];
    int i = 0;
    for (LdapAttribute la : expected.getAttributes()) {
      mods[i] = new ModificationItem(
        DirContext.REPLACE_ATTRIBUTE,
        la.toAttribute());
      i++;
    }
    ldap.modifyAttributes(dn, mods);

    final Attributes a = ldap.getAttributes(dn, expected.getAttributeNames());
    AssertJUnit.assertEquals(expected, TestUtil.newLdapAttributes(a));
    ldap.close();
  }


  /**
   * @param  dn  to modify.
   * @param  attrs  to remove.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "removeAttributeDn", "removeAttributeAttribute" })
  @Test(
    groups = {"ldaptest"},
    dependsOnMethods = {"replaceAttribute"}
  )
  public void removeAttribute(final String dn, final String attrs)
    throws Exception
  {
    final LdapAttribute expected = TestUtil.convertStringToAttributes(attrs)
        .getAttributes().iterator().next();
    final LdapAttribute remove = TestUtil.convertStringToAttributes(attrs)
        .getAttributes().iterator().next();
    remove.getValues().remove("Unit Test User");
    expected.getValues().remove("Best Test User");

    final Ldap ldap = this.createLdap(true);
    ldap.modifyAttributes(
      dn,
      AttributeModification.REMOVE,
      AttributesFactory.createAttributes(
        remove.getName(),
        remove.getValues().toArray()));

    final Attributes a = ldap.getAttributes(
      dn,
      new String[] {expected.getName()});
    AssertJUnit.assertEquals(
      expected,
      TestUtil.newLdapAttributes(a).getAttribute(expected.getName()));
    ldap.close();
  }


  /**
   * @param  dn  to modify.
   * @param  attrs  to remove.
   *
   * @throws  Exception  On test failure.
   */
  @Parameters({ "removeAttributesDn", "removeAttributesAttributes" })
  @Test(
    groups = {"ldaptest"},
    dependsOnMethods = {"replaceAttributes"}
  )
  public void removeAttributes(final String dn, final String attrs)
    throws Exception
  {
    final LdapAttributes expected = TestUtil.convertStringToAttributes(attrs);
    final LdapAttributes remove = TestUtil.convertStringToAttributes(attrs);

    final String[] attrsName = remove.getAttributeNames();
    remove.getAttributes().remove(remove.getAttribute(attrsName[0]));
    expected.getAttributes().remove(expected.getAttribute(attrsName[1]));

    final Ldap ldap = this.createLdap(true);
    final ModificationItem[] mods = new ModificationItem[expected.size()];
    int i = 0;
    for (LdapAttribute la : remove.getAttributes()) {
      mods[i] = new ModificationItem(
        DirContext.REMOVE_ATTRIBUTE,
        la.toAttribute());
      i++;
    }
    ldap.modifyAttributes(dn, mods);

    final Attributes a = ldap.getAttributes(dn, expected.getAttributeNames());
    AssertJUnit.assertEquals(expected, TestUtil.newLdapAttributes(a));
    ldap.close();
  }


  /** @throws  Exception  On test failure. */
  @Test(groups = {"ldaptest"})
  public void saslExternalConnect()
    throws Exception
  {
    final Ldap ldap = TestUtil.createSaslExternalLdap();
    AssertJUnit.assertTrue(ldap.connect());
    ldap.close();
  }


  /**
   * @param  krb5Realm  kerberos realm
   * @param  krb5Kdc  kerberos kdc
   * @param  dn  to search on.
   * @param  filter  to search with.
   * @param  filterArgs  to replace args in filter with.
   * @param  returnAttrs  to return from search.
   * @param  ldifFile  to compare with
   *
   * @throws  Exception  On test failure.
   */
  @Parameters(
    {
      "krb5Realm",
      "krb5Kdc",
      "gssApiSearchDn",
      "gssApiSearchFilter",
      "gssApiSearchFilterArgs",
      "gssApiSearchReturnAttrs",
      "gssApiSearchResults"
    }
  )
  @Test(groups = {"ldaptest"})
  public void gssApiSearch(
    final String krb5Realm,
    final String krb5Kdc,
    final String dn,
    final String filter,
    final String filterArgs,
    final String returnAttrs,
    final String ldifFile)
    throws Exception
  {
    System.setProperty(
      "java.security.auth.login.config",
      "src/test/resources/ldap_jaas.config");
    System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
    System.setProperty("java.security.krb5.realm", krb5Realm);
    System.setProperty("java.security.krb5.kdc", krb5Kdc);

    final Ldap ldap = TestUtil.createGssApiLdap();
    final Iterator<SearchResult> iter = ldap.search(
      dn,
      new SearchFilter(filter, filterArgs.split("\\|")),
      returnAttrs.split("\\|"));
    final String expected = TestUtil.readFileIntoString(ldifFile);
    AssertJUnit.assertEquals(
      TestUtil.convertLdifToEntry(expected),
      TestUtil.convertLdifToEntry((new Ldif()).createLdif(iter)));
    ldap.close();

    System.clearProperty("java.security.auth.login.config");
    System.clearProperty("javax.security.auth.useSubjectCredsOnly");
    System.clearProperty("java.security.krb5.realm");
    System.clearProperty("java.security.krb5.kdc");
  }
}
