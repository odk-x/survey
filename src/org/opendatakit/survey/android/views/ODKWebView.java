package org.opendatakit.survey.android.views;

import java.util.LinkedList;

import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.activities.ODKActivity;
import org.opendatakit.survey.android.application.Survey;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebSettings.RenderPriority;
import android.webkit.WebView;
import android.widget.TableLayout;

/**
 * NOTE: assumes that the Context implements ODKActivity.
 *
 * Wrapper for a raw WebView. The enclosing application should only call:
 * initialize(appName) addJavascriptInterface(class,name)
 * loadJavascriptUrl("javascript:...") and any View methods.
 *
 * This class handles ensuring that the framework (index.html) is loaded before
 * executing the javascript URLs.
 *
 * @author mitchellsundt@gmail.com
 *
 */
@SuppressLint("SetJavaScriptEnabled")
public class ODKWebView extends WebView {
  private static String t = "ODKWebView";
  private static final String BASE_STATE = "BASE_STATE";
  private static final String JAVASCRIPT_REQUESTS_WAITING_FOR_PAGE_LOAD = "JAVASCRIPT_REQUESTS_WAITING_FOR_PAGE_LOAD";

  private final ODKActivity activity;
  private WebLogger log;
  private ODKShimJavascriptCallback shim;
  private String loadPageUrl = null;
  private boolean isLoadPageFrameworkFinished = false;
  private boolean isLoadPageFinished = false;
  private boolean isJavascriptFlushActive = false;
  private boolean isFirstPageLoad = true;
  private final LinkedList<String> javascriptRequestsWaitingForPageLoad = new LinkedList<String>();

  // TODO: the interaction with the landing.js needs to be updated
  // this is not 100% reliable because of that interaction.

  @Override
  protected Parcelable onSaveInstanceState () {
    Parcelable baseState = super.onSaveInstanceState();
    Bundle savedState = new Bundle();
    if ( baseState != null ) {
      savedState.putParcelable(BASE_STATE, baseState);
    }
    if ( javascriptRequestsWaitingForPageLoad.size() == 0 ) {
      return savedState;
    }
    String[] waitQueue = new String[javascriptRequestsWaitingForPageLoad.size()];
    int i = 0;
    for ( String s : javascriptRequestsWaitingForPageLoad ) {
      waitQueue[i++] = s;
    }
    savedState.putStringArray(JAVASCRIPT_REQUESTS_WAITING_FOR_PAGE_LOAD, waitQueue);
    return savedState;
  }

  @Override
  protected void onRestoreInstanceState (Parcelable state) {
    Bundle savedState = (Bundle) state;
    if ( savedState.containsKey(JAVASCRIPT_REQUESTS_WAITING_FOR_PAGE_LOAD)) {
      String[] waitQueue = savedState.getStringArray(JAVASCRIPT_REQUESTS_WAITING_FOR_PAGE_LOAD);
      for ( String s : waitQueue ) {
        javascriptRequestsWaitingForPageLoad.add(s);
      }
    }
    isFirstPageLoad = true;

    if ( savedState.containsKey(BASE_STATE) ) {
      Parcelable baseState = savedState.getParcelable(BASE_STATE);
      super.onRestoreInstanceState(baseState);
    }
    loadPage();
  }

  public ODKWebView(Context context) {
    super(context);

    // Context is ALWAYS an ODKActivity...

    activity = (ODKActivity) context;
    String appName = activity.getAppName();
    log = WebLogger.getLogger(appName);

    setId(98103);
    TableLayout.LayoutParams params = new TableLayout.LayoutParams();
    params.setMargins(7, 5, 7, 5);
    setLayoutParams(params);

    // for development -- always draw from source...
    WebSettings ws = getSettings();
    ws.setAllowFileAccess(true);
    ws.setAppCacheEnabled(true);
    ws.setAppCacheMaxSize(1024L * 1024L * 200L);
    ws.setAppCachePath(ODKFileUtils.getAppCacheFolder(appName));
    ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
    ws.setDatabaseEnabled(true);
    ws.setDatabasePath(ODKFileUtils.getWebDbFolder(appName));
    ws.setDefaultFixedFontSize(Survey.getQuestionFontsize());
    ws.setDefaultFontSize(Survey.getQuestionFontsize());
    ws.setDomStorageEnabled(true);
    ws.setGeolocationDatabasePath(ODKFileUtils.getGeoCacheFolder(appName));
    ws.setGeolocationEnabled(true);
    ws.setJavaScriptCanOpenWindowsAutomatically(true);
    ws.setJavaScriptEnabled(true);
    ws.setPluginState(PluginState.ON);
    ws.setRenderPriority(RenderPriority.HIGH);

    // disable to try to solve touch/mouse/swipe issues
    ws.setBuiltInZoomControls(false);
    ws.setSupportZoom(false);

    setFocusable(true);
    setFocusableInTouchMode(true);
    setInitialScale(100);

    // questionable value...
    setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
    setSaveEnabled(true);

    // set up the client...
    setWebChromeClient(new ODKWebChromeClient(this));
    setWebViewClient(new ODKWebViewClient(this));

    // stomp on the shim object...
    shim = new ODKShimJavascriptCallback(this,activity);
    addJavascriptInterface(shim, "shim");
    loadPage();
  }

  public final WebLogger getLogger() {
    return log;
  }

  // called to invoke a javascript method inside the webView
  public synchronized void loadJavascriptUrl(String javascriptUrl) {
    if (isLoadPageFinished || isJavascriptFlushActive) {
      log.i(t, "loadJavascriptUrl: IMMEDIATE: " + javascriptUrl);
      loadUrl(javascriptUrl);
    } else {
      log.i(t, "loadJavascriptUrl: QUEUING: " + javascriptUrl);
      javascriptRequestsWaitingForPageLoad.add(javascriptUrl);
    }
  }

  public void gotoUrlHash(String hash) {
    log.i(t, "gotoUrlHash: " + hash);
    loadJavascriptUrl("javascript:window.landing.opendatakitChangeUrlHash('" + hash + "')");
  }

  public synchronized void loadPage() {
    /**
     * NOTE: Reload the web framework only if it has changed.
     */

    log.i(t, "loadPage: current loadPageUrl: " + loadPageUrl);
    String baseUrl = activity.getUrlBaseLocation(isLoadPageFrameworkFinished && loadPageUrl != null);
    String hash = activity.getUrlLocationHash();

    if ( baseUrl != null ) {
      resetLoadPageStatus(baseUrl);

      log.i(t, "loadPage: full reload: " + baseUrl + hash);

      loadUrl(baseUrl + hash);
    } else if ( isLoadPageFrameworkFinished ) {
      log.i(t,  "loadPage: delegate to gotoUrlHash: " + hash);
      gotoUrlHash(hash);
    } else {
      log.w(t, "loadPage: framework did not load -- cannot load anything!");
    }
  }

  public synchronized void clearPage() {
    log.i(t, "clearPage: current loadPageUrl: " + loadPageUrl);
    String baseUrl = activity.getUrlBaseLocation(false);

    if ( baseUrl != null ) {
      resetLoadPageStatus(baseUrl);
      log.i(t, "clearPage: full reload: " + baseUrl);
      loadUrl(baseUrl);
    } else {
      log.w(t, "clearPage: framework did not load -- cannot load anything!");
    }
  }

  synchronized void frameworkHasLoaded() {
    isLoadPageFrameworkFinished = true;
    if (!isLoadPageFinished && !isJavascriptFlushActive) {
      log.i(t, "loadPageFinished: BEGINNING FLUSH refId: " + activity.getRefId());
      isJavascriptFlushActive = true;
      while (isJavascriptFlushActive && !javascriptRequestsWaitingForPageLoad.isEmpty()) {
        String s = javascriptRequestsWaitingForPageLoad.removeFirst();
        log.i(t, "loadPageFinished: DISPATCHING javascriptUrl: " + s);
        loadJavascriptUrl(s);
      }
      isLoadPageFinished = true;
      isJavascriptFlushActive = false;
      isFirstPageLoad = false;
    } else {
      log.i(t, "loadPageFinished: IGNORING completion event refId: " + activity.getRefId());
    }
  }

  private synchronized void resetLoadPageStatus(String baseUrl) {
    isLoadPageFrameworkFinished = false;
    isLoadPageFinished = false;
    loadPageUrl = baseUrl;
    isJavascriptFlushActive = false;
    // do not purge the list of actions if this is the first page load.
    // keep them queued until they can be issued.
    if ( !isFirstPageLoad ) {
      while (!javascriptRequestsWaitingForPageLoad.isEmpty()) {
        String s = javascriptRequestsWaitingForPageLoad.removeFirst();
        log.i(t, "resetLoadPageStatus: DISCARDING javascriptUrl: " + s);
      }
    }
  }

  /**
   * Tell the enclosing activity that we should restore this WebView to visible
   * and make any custom view gone.
   *
   * NOTE: Only Invoked by ODKWebChromeClient.
   */
  void swapOffCustomView() {
    log.i(t, "swapOffCustomView");
    activity.swapOffCustomView();
  }

  /**
   * Tell the enclosing activity that we should make the indicated view visible
   * and this one gone.
   *
   * NOTE: Only Invoked by ODKWebChromeClient.
   *
   * @param view
   */
  void swapToCustomView(View view) {
    log.i(t, "swapToCustomView");
    activity.swapToCustomView(view);
  }

  /**
   * Ask the browser for an icon to represent a <video> element. This icon will
   * be used if the Web page did not specify a poster attribute.
   *
   * NOTE: Only Invoked by ODKWebChromeClient.
   *
   * @return Bitmap The icon or null if no such icon is available.
   */
  Bitmap getDefaultVideoPoster() {
    return activity.getDefaultVideoPoster();
  }

  /**
   * Ask the host application for a custom progress view to show while a <video>
   * is loading.
   *
   * NOTE: Only Invoked by ODKWebChromeClient.
   *
   * @return View The progress view.
   */
  View getVideoLoadingProgressView() {
    return activity.getVideoLoadingProgressView();
  }

}
