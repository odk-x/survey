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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opendatakit.common.android.utilities.StaticStateManipulator.IStaticFieldManipulator;
import org.opendatakit.httpclientandroidlib.auth.AuthScope;
import org.opendatakit.httpclientandroidlib.auth.Credentials;
import org.opendatakit.httpclientandroidlib.auth.UsernamePasswordCredentials;
import org.opendatakit.httpclientandroidlib.client.CookieStore;
import org.opendatakit.httpclientandroidlib.client.CredentialsProvider;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.client.params.AuthPolicy;
import org.opendatakit.httpclientandroidlib.client.params.ClientPNames;
import org.opendatakit.httpclientandroidlib.client.params.HttpClientParams;
import org.opendatakit.httpclientandroidlib.client.protocol.ClientContext;
import org.opendatakit.httpclientandroidlib.conn.ClientConnectionManager;
import org.opendatakit.httpclientandroidlib.impl.client.BasicCookieStore;
import org.opendatakit.httpclientandroidlib.impl.client.DefaultHttpClient;
import org.opendatakit.httpclientandroidlib.params.BasicHttpParams;
import org.opendatakit.httpclientandroidlib.params.HttpConnectionParams;
import org.opendatakit.httpclientandroidlib.params.HttpParams;
import org.opendatakit.httpclientandroidlib.protocol.BasicHttpContext;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;

/**
 * Common utility methods for managing the credentials associated with the
 * request context and constructing http context and client with the
 * proper parameters.
 * 
 * Like the WebLogger, the instances of this class are specific to a 
 * particular appName, as they manage the cookies and credentials for 
 * the connections that an appName has to external websites or servers.
 *
 * @author mitchellsundt@gmail.com
 */
public final class ClientConnectionManagerFactory {
  private static final String t = "ClientConnectionManagerFactory";

  private static Map<String,ClientConnectionManagerFactory> appNameFactories = 
      new HashMap<String,ClientConnectionManagerFactory>();
  
  static {
    // register a state-reset manipulator for 'appNameFactories' field.
    StaticStateManipulator.get().register(75, new IStaticFieldManipulator() {

      @Override
      public void reset() {
        for ( ClientConnectionManagerFactory utils : appNameFactories.values()) {
          utils.clearHttpConnectionManager();
        }
        appNameFactories.clear();
      }
      
    });
  }

  public static synchronized ClientConnectionManagerFactory get(String appName) {
    ClientConnectionManagerFactory f = appNameFactories.get(appName);
    if ( f == null ) {
      f = new ClientConnectionManagerFactory(appName);
      appNameFactories.put(appName, f);
    }
    return f;
  }
  
  /**
   * For mocking -- supply a mocked object.
   * It will be inserted as the ClientConnectionManager
   * for the given appName.
   * 
   * @param utils
   */
  public static synchronized void set(ClientConnectionManagerFactory factory) {
    appNameFactories.put(factory.appName, factory);
  }
  
  private final String appName;
  
  // share all session cookies across all sessions...
  private final CookieStore cookieStore = new BasicCookieStore();
  // retain credentials for 7 minutes...
  private final CredentialsProvider credsProvider = new AgingCredentialsProvider(7 * 60 * 1000);

  private ClientConnectionManager httpConnectionManager = null;

  protected ClientConnectionManagerFactory(String appName) {
    this.appName = appName;
  };

  /**
   * Construct the list of scopes (port + authProtocol) for a given host.
   * @param host
   * @return
   */
  private List<AuthScope> buildAuthScopes(String host) {
    List<AuthScope> asList = new ArrayList<AuthScope>();

    AuthScope a;
    // allow digest auth on any port...
    a = new AuthScope(host, -1, null, AuthPolicy.DIGEST);
    asList.add(a);
    // and allow basic auth on the standard TLS/SSL ports...
    a = new AuthScope(host, 443, null, AuthPolicy.BASIC);
    asList.add(a);
    a = new AuthScope(host, 8443, null, AuthPolicy.BASIC);
    asList.add(a);

    return asList;
  }

  public synchronized void clearAllCredentials() {
    WebLogger.getLogger(appName).i(t, "clearAllCredentials");
    credsProvider.clear();
  }

  public synchronized boolean hasCredentials(String userEmail, String host) {
    List<AuthScope> asList = buildAuthScopes(host);
    boolean hasCreds = true;
    for (AuthScope a : asList) {
      Credentials c = credsProvider.getCredentials(a);
      if (c == null) {
        hasCreds = false;
        continue;
      }
    }
    return hasCreds;
  }

  /**
   * Remove all credentials for accessing the specified host and, if the
   * username is not null or blank then add a (username, password) credential
   * for accessing this host.
   *
   * @param username
   * @param password
   * @param host
   */
  public synchronized void addCredentials(String username, String password, String host) {
    List<AuthScope> asList = buildAuthScopes(host);

    // ensure that this is the only authentication available for this host...
    WebLogger.getLogger(appName).i(t, "clearHostCredentials: " + host);
    for (AuthScope a : asList) {
      credsProvider.setCredentials(a, null);
    }

    // add username
    if (username != null && username.trim().length() != 0) {
      WebLogger.getLogger(appName).i(t, "adding credential for host: " + host + " username:" + username);
      Credentials c = new UsernamePasswordCredentials(username, password);

      for (AuthScope a : asList) {
        credsProvider.setCredentials(a, c);
      }
    }
  }

  /**
   * Shared HttpContext so a user doesn't have to re-enter login information
   *
   * @return
   */
  public synchronized HttpContext getHttpContext() {

    // context holds authentication state machine, so it cannot be
    // shared across independent activities.
    HttpContext localContext = new BasicHttpContext();

    localContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
    localContext.setAttribute(ClientContext.CREDS_PROVIDER, credsProvider);

    return localContext;
  }

  /**
   * Create an httpClient with connection timeouts and other parameters set.
   * Save and reuse the connection manager across invocations (this is what
   * requires synchronized access).
   *
   * @param timeout
   * @return HttpClient properly configured.
   */
  public synchronized HttpClient createHttpClient(int timeout) {
    return createHttpClient(timeout, 1);
  }

  public synchronized HttpClient createHttpClient(int timeout, int maxRedirects) {
    // configure connection
    HttpParams params = new BasicHttpParams();
    HttpConnectionParams.setConnectionTimeout(params, timeout);
    HttpConnectionParams.setSoTimeout(params, 2 * timeout);
    // support redirecting to handle http: => https: transition
    HttpClientParams.setRedirecting(params, true);
    // support authenticating
    HttpClientParams.setAuthenticating(params, true);
    // if possible, bias toward digest auth (may not be in 4.0 beta 2)
    List<String> authPref = new ArrayList<String>();
    authPref.add(AuthPolicy.DIGEST);
    authPref.add(AuthPolicy.BASIC);
    // does this work in Google's 4.0 beta 2 snapshot?
    params.setParameter("http.auth-target.scheme-pref", authPref);

    // setup client
    HttpClient httpclient;

    // reuse the connection manager across all clients this ODK Survey
    // creates.
    if (httpConnectionManager == null) {
      // let Apache stack create a connection manager.
      httpclient = new DefaultHttpClient(params);
      httpConnectionManager = httpclient.getConnectionManager();
    } else {
      // reuse the connection manager we already got.
      httpclient = new DefaultHttpClient(httpConnectionManager, params);
    }

    httpclient.getParams().setParameter(ClientPNames.MAX_REDIRECTS, maxRedirects);
    httpclient.getParams().setParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);

    return httpclient;
  }

  public synchronized void clearHttpConnectionManager() {
    // If we get an unexpected exception, the safest thing is to close
    // all connections
    // so that if there is garbage on the connection we ensure it is
    // removed. This
    // is especially important if the connection times out.
    WebLogger.getLogger(appName).i(t, "clearHttpConnectionManager");
    httpConnectionManager.shutdown();
    httpConnectionManager = null;
  }
}
