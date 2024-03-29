/*
  $Id: Dictionary.java 1252 2010-04-16 21:24:23Z dfisher $

  Copyright (C) 2003-2008 Virginia Tech.
  All rights reserved.

  SEE LICENSE FOR MORE INFORMATION

  Author:  Middleware Services
  Email:   middleware@vt.edu
  Version: $Revision: 1252 $
  Updated: $Date: 2010-04-16 17:24:23 -0400 (Fri, 16 Apr 2010) $
*/
package edu.vt.middleware.dictionary;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * <code>WordListDictionary</code> provides fast searching for dictionary
 * words using a <code>WordList</code>.
 * {@link java.util.Collections#binarySearch(List, Object) is used to search the
 * supplied word list. It's critical that the word list provided to this
 * dictionary be sorted according to the natural ordering of
 * {@link java.lang.String}. This class inherits the lower case property of the
 * supplied word list.
 *
 * @author  Middleware Services
 * @version  $Revision: 1252 $ $Date: 2010-04-16 17:24:23 -0400 (Fri, 16 Apr 2010) $
 */
public class WordListDictionary implements Dictionary
{

  /** list used for searching. */
  protected WordList wordList;

  /** whether search terms should be lowercased. Default value is {@value}. */
  protected boolean lowerCase;


  /**
   * Creates a new dictionary instance from the given {@link WordList}.
   *
   * @param  wl  List of words sorted according to
   * {@link WordList#getComparator()}.
   * <p>
   * <strong>NOTE</strong>
   * <p>
   * Failure to provide a sorted word list will produce incorrect results.
   */
  public WordListDictionary(final WordList wl)
  {
    this.wordList = wl;
  }


  /**
   * Returns the word list to used for searching.
   *
   * @return  <code>WordList</code>
   */
  public WordList getWordList()
  {
    return this.wordList;
  }


  /** {@inheritDoc} */
  public boolean search(final String word)
  {
    return WordListUtils.binarySearch(wordList, word) >= 0;
  }


  /**
   * This provides command line access to this <code>WordListDictionary</code>.
   *
   * @param  args  <code>String[]</code>
   *
   * @throws  Exception  if an error occurs
   */
  public static void main(final String[] args)
    throws Exception
  {
    final List<RandomAccessFile> files = new ArrayList<RandomAccessFile>();
    try {
      if (args.length == 0) {
        throw new ArrayIndexOutOfBoundsException();
      }

      // dictionary operations
      boolean ignoreCase = false;
      boolean search = false;
      boolean print = false;

      // operation parameters
      String word = null;

      for (int i = 0; i < args.length; i++) {
        if ("-ci".equals(args[i])) {
          ignoreCase = true;
        } else if ("-s".equals(args[i])) {
          search = true;
          word = args[++i];
        } else if ("-p".equals(args[i])) {
          print = true;
        } else if ("-h".equals(args[i])) {
          throw new ArrayIndexOutOfBoundsException();
        } else {
          files.add(new RandomAccessFile(args[i], "r"));
        }
      }

      // insert data
      final WordListDictionary dict = new WordListDictionary(
          new FilePointerWordList(
              files.toArray(new RandomAccessFile[files.size()]), ignoreCase));

      // perform operation
      if (search) {
        if (dict.search(word)) {
          System.out.println(
            String.format("%s was found in this dictionary", word));
        } else {
          System.out.println(
            String.format("%s was not found in this dictionary", word));
        }
      } else if (print) {
        System.out.println(dict.getWordList());
      } else {
        throw new ArrayIndexOutOfBoundsException();
      }

    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("Usage: java " +
        WordListDictionary.class.getName() + " \\");
      System.out.println(
        "       <dictionary1> <dictionary2> ... " +
        "<options> <operation> \\");
      System.out.println("");
      System.out.println("where <options> includes:");
      System.out.println("       -ci (Make search case-insensitive) \\");
      System.out.println("");
      System.out.println("where <operation> includes:");
      System.out.println("       -s <word> (Search for a word) \\");
      System.out.println("       -p (Print the entire dictionary) \\");
      System.out.println("       -h (Print this message) \\");
      System.exit(1);
    }
  }
}
