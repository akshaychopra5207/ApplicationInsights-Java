/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.common;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Created by oriy on 11/2/2016. */
public class CommonUtils {

  private static final Logger logger = LoggerFactory.getLogger(CommonUtils.class);

  public static boolean isNullOrEmpty(String string) {
    return string == null || string.length() == 0;
  }

  /**
   * Returns the hostname using {@link InetAddress#getCanonicalHostName()} on {@link InetAddress#getLocalHost()}.
   * If an error is encountered, the error is logged and it returns null.
   *
   * @return the local hostname, or null
   */
  public static String getHostName() {
    try {
      InetAddress addr = InetAddress.getLocalHost();
      return addr.getHostName();
    } catch (UnknownHostException ex) {
      logger.warn("Error resolving hostname", ex);
      return null;
    }
  }

  /**
   * This method is used to test if the given class is loaded in the specified ClassLoader
   * @param classSignature Fully Qualified signature of class
   * @param classLoader ClassLoader under consideration
   * @return true if class is loaded otherwise false
   */
  public static boolean isClassPresentOnClassPath(String classSignature, ClassLoader classLoader) {

    try {
      Class.forName(classSignature, false, classLoader);
      return true;
    } catch (ClassNotFoundException e) {
      logger.info(
          "Specified class {} is not present on the classpath", classSignature);
      return false;
    }
  }
}
