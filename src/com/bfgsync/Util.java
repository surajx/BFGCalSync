package com.bfgsync;

import com.google.api.client.xml.XmlNamespaceDictionary;

/**
 * @author Yaniv Inbar
 */
public class Util {
  public static final boolean DEBUG = false;

  public static final XmlNamespaceDictionary DICTIONARY =
      new XmlNamespaceDictionary().set("", "http://www.w3.org/2005/Atom");
}

