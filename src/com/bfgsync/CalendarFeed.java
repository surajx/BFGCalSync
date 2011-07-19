package com.bfgsync;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.util.Key;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;


/**
 * @author Yaniv Inbar
 */
public class CalendarFeed extends Feed {

  @Key("entry")
  public List<CalendarEntry> calendars = Lists.newArrayList();

  public static CalendarFeed executeGet(HttpTransport transport, CalendarUrl url)
      throws IOException {
    return (CalendarFeed) Feed.executeGet(transport, url, CalendarFeed.class);
  }
}

