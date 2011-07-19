package com.bfgsync.gcal;

import com.google.api.client.http.HttpTransport;

import java.io.IOException;


/**
 * @author Yaniv Inbar
 */
public class CalendarEntry extends Entry {

  public String getEventFeedLink() {
    return Link.find(links, "http://schemas.google.com/gCal/2005#eventFeed");
  }

  @Override
  public CalendarEntry clone() {
    return (CalendarEntry) super.clone();
  }

  @Override
  public CalendarEntry executeInsert(HttpTransport transport, CalendarUrl url) throws IOException {
    return (CalendarEntry) super.executeInsert(transport, url);
  }

  public CalendarEntry executePatchRelativeToOriginal(
      HttpTransport transport, CalendarEntry original) throws IOException {
    return (CalendarEntry) super.executePatchRelativeToOriginal(transport, original);
  }
}

