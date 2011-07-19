package com.bfgsync;

import com.google.api.client.googleapis.xml.atom.AtomPatchRelativeToOriginalContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.util.DataUtil;
import com.google.api.client.util.Key;
import com.google.api.client.xml.atom.AtomContent;

import java.io.IOException;
import java.util.List;

/**
 * @author Yaniv Inbar
 */
public class Entry implements Cloneable {

  @Key
  public String summary;

  @Key
  public String title;

  @Key
  public String updated;

  @Key("link")
  public List<Link> links;

  @Override
  protected Entry clone() {
    return DataUtil.clone(this);
  }

  public void executeDelete(HttpTransport transport) throws IOException {
    HttpRequest request = transport.buildDeleteRequest();
    request.setUrl(getEditLink());
    RedirectHandler.execute(request).ignore();
  }

  Entry executeInsert(HttpTransport transport, CalendarUrl url) throws IOException {
    HttpRequest request = transport.buildPostRequest();
    request.url = url;
    AtomContent content = new AtomContent();
    content.namespaceDictionary = Util.DICTIONARY;
    content.entry = this;
    request.content = content;
    return RedirectHandler.execute(request).parseAs(getClass());
  }

  Entry executePatchRelativeToOriginal(HttpTransport transport, Entry original) throws IOException {
    HttpRequest request = transport.buildPatchRequest();
    request.setUrl(getEditLink());
    AtomPatchRelativeToOriginalContent content = new AtomPatchRelativeToOriginalContent();
    content.namespaceDictionary = Util.DICTIONARY;
    content.originalEntry = original;
    content.patchedEntry = this;
    request.content = content;
    return RedirectHandler.execute(request).parseAs(getClass());
  }

  private String getEditLink() {
    return Link.find(links, "edit");
  }
}
