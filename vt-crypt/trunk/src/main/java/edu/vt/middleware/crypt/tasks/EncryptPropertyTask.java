/*
  $Id$

  Copyright (C) 2003-2008 Virginia Tech.
  All rights reserved.

  SEE LICENSE FOR MORE INFORMATION

  Author:  Middleware Services
  Email:   middleware@vt.edu
  Version: $Revision$
  Updated: $Date$
*/
package edu.vt.middleware.crypt.tasks;

import edu.vt.middleware.crypt.symmetric.SymmetricAlgorithm;
import edu.vt.middleware.crypt.util.Base64Converter;
import edu.vt.middleware.crypt.util.Convert;
import edu.vt.middleware.crypt.util.CryptReader;
import org.apache.tools.ant.BuildException;

/**
 * <p><code>EncryptPropertyTask</code> will encrypt an ant property using a
 * symmetric algorithm. Encrypted value will be BASE64 encoded.</p>
 *
 * @author  Middleware Services
 * @version  $Revision$
 */
public final class EncryptPropertyTask extends AbstractCryptTask
{

  /**
   * <p>See @link{org.apache.tools.ant.Task}.</p>
   */
  public void execute()
  {
    try {
      final SymmetricAlgorithm crypt = SymmetricAlgorithm.newInstance(
        this.algorithm,
        this.mode,
        this.padding);
      if (this.iv != null) {
        crypt.setIV(this.iv.getBytes());
      }
      crypt.setKey(CryptReader.readPrivateKey(this.privateKey, this.algorithm));

      final String propertyValue = this.getProject().getProperty(
        this.propertyName);
      final String encryptValue = crypt.encrypt(
        Convert.toBytes(propertyValue),
        new Base64Converter());
      this.getProject().setProperty(this.propertyName, encryptValue);
    } catch (Exception e) {
      throw new BuildException(e);
    }
  }
}
