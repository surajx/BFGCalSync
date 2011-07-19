package com.bfgsync.gcal;

import com.google.api.client.googleapis.GoogleUrl;
import com.google.api.client.http.HttpExecuteIntercepter;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;

import java.io.IOException;

/**
 * @author Yaniv Inbar
 */
public class RedirectHandler {

  /**
   * See <a href="http://code.google.com/apis/calendar/faq.html#redirect_handling">How do I handle
   * redirects...?</a>.
   */
  static class SessionIntercepter implements HttpExecuteIntercepter {

    private String gsessionid;

    SessionIntercepter(HttpTransport transport, GoogleUrl locationUrl) {
      resetSessionId(transport);
      this.gsessionid = (String) locationUrl.getFirst("gsessionid");
      transport.intercepters.add(0, this); // must be first
    }

    public void intercept(HttpRequest request) {
      request.url.set("gsessionid", this.gsessionid);
    }
  }

  /** Resets the session ID stored for the HTTP transport. */
  public static void resetSessionId(HttpTransport transport) {
    transport.removeIntercepters(SessionIntercepter.class);
  }

  static HttpResponse execute(HttpRequest request) throws IOException {
    try {
      return request.execute();
    } catch (HttpResponseException e) {
      if (e.response.statusCode == 302) {
        GoogleUrl url = new GoogleUrl(e.response.headers.location);
        request.url = url;
        new SessionIntercepter(request.transport, url);
        e.response.ignore(); // force the connection to close
        return request.execute();
      }
      throw e;
    }
  }
}

