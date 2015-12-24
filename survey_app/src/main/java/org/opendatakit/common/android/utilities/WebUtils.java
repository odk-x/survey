/*
 * Copyright (C) 2011-2013 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.common.android.utilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.CharEncoding;
import org.opendatakit.common.android.utilities.StaticStateManipulator.IStaticFieldManipulator;
import org.opendatakit.httpclientandroidlib.Header;
import org.opendatakit.httpclientandroidlib.HttpEntity;
import org.opendatakit.httpclientandroidlib.HttpRequest;
import org.opendatakit.httpclientandroidlib.HttpResponse;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.client.methods.HttpGet;
import org.opendatakit.httpclientandroidlib.client.methods.HttpHead;
import org.opendatakit.httpclientandroidlib.client.methods.HttpPost;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import android.text.format.DateFormat;

/**
 * Common utility methods for managing constructing requests with the proper
 * parameters and OpenRosa headers.
 *
 * @author mitchellsundt@gmail.com
 */
public final class WebUtils {
  private static final String t = "WebUtils";

  public static final String HTTP_CONTENT_TYPE_TEXT_XML = "text/xml";
  public static final int CONNECTION_TIMEOUT = 45000;

  public static final String OPEN_ROSA_VERSION_HEADER = "X-OpenRosa-Version";
  public static final String OPEN_ROSA_VERSION = "1.0";

  private static final String DATE_HEADER = "Date";

  private static final GregorianCalendar g = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

  private static WebUtils webUtils = new WebUtils();

  static {
    // register a state-reset manipulator for 'webUtils' field.
    StaticStateManipulator.get().register(50, new IStaticFieldManipulator() {

      @Override
      public void reset() {
        webUtils = new WebUtils();
      }

    });
  }

  public static WebUtils get() {
    return webUtils;
  }

  /**
   * For mocking -- supply a mocked object.
   * 
   * @param utils
   */
  public static void set(WebUtils utils) {
    webUtils = utils;
  }

  protected WebUtils() {
  };

  private void setOpenRosaHeaders(HttpRequest req) {
    req.setHeader(OPEN_ROSA_VERSION_HEADER, OPEN_ROSA_VERSION);
    g.setTime(new Date());
    req.setHeader(DATE_HEADER, DateFormat.format("E, dd MMM yyyy hh:mm:ss zz", g).toString());
  }

  public HttpHead createOpenRosaHttpHead(URI uri) {
    HttpHead req = new HttpHead(uri);
    setOpenRosaHeaders(req);
    return req;
  }

  public HttpGet createOpenRosaHttpGet(URI uri) {
    return createOpenRosaHttpGet(uri, "");
  }

  public HttpGet createOpenRosaHttpGet(URI uri, String auth) {
    HttpGet req = new HttpGet();
    setOpenRosaHeaders(req);
    setGoogleHeaders(req, auth);
    req.setURI(uri);
    return req;
  }

  public void setGoogleHeaders(HttpRequest req, String auth) {
    if ((auth != null) && (auth.length() > 0)) {
      req.setHeader("Authorization", "GoogleLogin auth=" + auth);
    }
  }

  public HttpPost createOpenRosaHttpPost(URI uri) {
    return createOpenRosaHttpPost(uri, "");
  }

  public HttpPost createOpenRosaHttpPost(URI uri, String auth) {
    HttpPost req = new HttpPost(uri);
    setOpenRosaHeaders(req);
    setGoogleHeaders(req, auth);
    return req;
  }

  /**
   * Utility to ensure that the entity stream of a response is drained of bytes.
   *
   * @param response
   */
  public void discardEntityBytes(HttpResponse response) {
    // may be a server that does not handle
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      try {
        // have to read the stream in order to reuse the connection
        InputStream is = response.getEntity().getContent();
        // read to end of stream...
        final long count = 1024L;
        while (is.skip(count) == count)
          ;
        is.close();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Common method for returning a parsed xml document given a url and the http
   * context and client objects involved in the web connection.
   *
   * @param urlString
   * @param localContext
   * @param httpclient
   * @return
   */
  public DocumentFetchResult getXmlDocument(String appName, String urlString,
      HttpContext localContext, HttpClient httpclient, String auth) {
    URI u = null;
    try {
      URL url = new URL(URLDecoder.decode(urlString, CharEncoding.UTF_8));
      u = url.toURI();
    } catch (Exception e) {
      e.printStackTrace();
      return new DocumentFetchResult(e.getLocalizedMessage()
      // + app.getString(R.string.while_accessing) + urlString);
          + ("while accessing") + urlString, 0);
    }

    // set up request...
    HttpGet req = createOpenRosaHttpGet(u, auth);

    HttpResponse response = null;
    try {
      response = httpclient.execute(req, localContext);
      int statusCode = response.getStatusLine().getStatusCode();

      HttpEntity entity = response.getEntity();

      if (statusCode != 200) {
        discardEntityBytes(response);
        String webError = response.getStatusLine().getReasonPhrase() + " (" + statusCode + ")";

        return new DocumentFetchResult(u.toString() + " responded with: " + webError, statusCode);
      }

      if (entity == null) {
        String error = "No entity body returned from: " + u.toString();
        WebLogger.getLogger(appName).e(t, error);
        return new DocumentFetchResult(error, 0);
      }

      if (!entity.getContentType().getValue().toLowerCase(Locale.ENGLISH)
          .contains(WebUtils.HTTP_CONTENT_TYPE_TEXT_XML)) {
        discardEntityBytes(response);
        String error = "ContentType: "
            + entity.getContentType().getValue()
            + " returned from: "
            + u.toString()
            + " is not text/xml.  This is often caused a network proxy.  Do you need to login to your network?";
        WebLogger.getLogger(appName).e(t, error);
        return new DocumentFetchResult(error, 0);
      }

      // parse response
      Document doc = null;
      try {
        InputStream is = null;
        InputStreamReader isr = null;
        InputSource iss = null;
        try {
          is = entity.getContent();
          isr = new InputStreamReader(is, Charsets.UTF_8);
          iss = new InputSource(isr);
          DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
          dbf.setNamespaceAware(true);
          DocumentBuilder db = dbf.newDocumentBuilder();
          doc = db.parse(iss);
          isr.close();
          isr = null;
        } catch (Exception e) {
          WebLogger.getLogger(appName).printStackTrace(e);
          throw e;
        } finally {
          if (isr != null) {
            try {
              // ensure stream is consumed...
              final long count = 1024L;
              while (isr.skip(count) == count)
                ;
            } catch (Exception e) {
              // no-op
            }
            try {
              isr.close();
            } catch (Exception e) {
              // no-op
            }
          }
          if (is != null) {
            try {
              is.close();
            } catch (Exception e) {
              // no-op
            }
          }
        }
      } catch (Exception e) {
        String error = "Parsing failed with " + e.getMessage() + "while accessing " + u.toString();
        WebLogger.getLogger(appName).e(t, error);
        WebLogger.getLogger(appName).printStackTrace(e);
        return new DocumentFetchResult(error, 0);
      }

      boolean isOR = false;
      Header[] fields = response.getHeaders(WebUtils.OPEN_ROSA_VERSION_HEADER);
      if (fields != null && fields.length >= 1) {
        isOR = true;
        boolean versionMatch = false;
        boolean first = true;
        StringBuilder b = new StringBuilder();
        for (Header h : fields) {
          if (WebUtils.OPEN_ROSA_VERSION.equals(h.getValue())) {
            versionMatch = true;
            break;
          }
          if (!first) {
            b.append("; ");
          }
          first = false;
          b.append(h.getValue());
        }
        if (!versionMatch) {
          WebLogger.getLogger(appName).w(t,
              WebUtils.OPEN_ROSA_VERSION_HEADER + " unrecognized version(s): " + b.toString());
        }
      }
      return new DocumentFetchResult(doc, isOR);
    } catch (Exception e) {
      ClientConnectionManagerFactory.get(appName).clearHttpConnectionManager();
      WebLogger.getLogger(appName).printStackTrace(e);
      String cause;
      if (e.getCause() != null) {
        cause = e.getCause().getMessage();
      } else {
        cause = e.getMessage();
      }
      String error = "Error: " + cause + " while accessing " + u.toString();

      WebLogger.getLogger(appName).w(t, error);
      return new DocumentFetchResult(error, 0);
    }
  }
}
