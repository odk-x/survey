package org.opendatakit.survey.android.views;

import java.util.LinkedList;

import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.activities.ODKActivity;
import org.opendatakit.survey.android.application.Survey;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
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
  private static String t = "WrappedWebView";

  private final ODKActivity activity;
  private WebLogger log;
  private ODKShimJavascriptCallback shim;
  private String loadPageUrl = null;
  private boolean isLoadPageFinished = false;
  private boolean isJavascriptFlushActive = false;
  private final LinkedList<String> javascriptRequestsWaitingForPageLoad = new LinkedList<String>();

  public ODKWebView(Context context) {
    super(context);

    // Context is ALWAYS an ODKActivity...

    activity = (ODKActivity) context;
    String appName = activity.getAppName();
    log = WebLogger.getLogger(appName);

    setId(984732);
    TableLayout.LayoutParams params = new TableLayout.LayoutParams();
    params.setMargins(7, 5, 7, 5);
    setLayoutParams(params);

    // for development -- always draw from source...
    WebSettings ws = getSettings();
    ws.setAllowFileAccess(true);
    ws.setAppCacheEnabled(true);
    ws.setAppCacheMaxSize(1024L * 1024L * 200L);
    ws.setAppCachePath(ODKFileUtils.getAppCacheFolder(appName));
    ws.setCacheMode(WebSettings.LOAD_NORMAL);
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
    shim = new ODKShimJavascriptCallback(activity);
    addJavascriptInterface(shim, "shim");

    loadPage();
  }

  public final WebLogger getLogger() {
    return log;
  }

  // called to invoke a javascript method inside the webView
  public void loadJavascriptUrl(String javascriptUrl) {
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

  public void loadPage() {
    /**
     * NOTE: Reload the web framework only if it has changed.
     */

    String baseUrl = activity.getUrlBaseLocation(loadPageUrl != null);
    String hash = activity.getUrlLocationHash();

    if ( baseUrl != null ) {
      resetLoadPageStatus(baseUrl);
      loadUrl(baseUrl + hash);
    } else if ( loadPageUrl != null ) {
      gotoUrlHash(hash);
    } else {
      log.w(t, "loadPage: no framework -- cannot load anything!");
    }
  }

  void loadPageFinished() {
    if (!isLoadPageFinished && !isJavascriptFlushActive) {
      isJavascriptFlushActive = true;
      while (isJavascriptFlushActive && !javascriptRequestsWaitingForPageLoad.isEmpty()) {
        String s = javascriptRequestsWaitingForPageLoad.removeFirst();
        loadJavascriptUrl(s);
      }
      isLoadPageFinished = true;
      isJavascriptFlushActive = false;
    }
  }

  private void resetLoadPageStatus(String baseUrl) {
    isLoadPageFinished = false;
    loadPageUrl = baseUrl;
    isJavascriptFlushActive = false;
    while (!javascriptRequestsWaitingForPageLoad.isEmpty()) {
      String s = javascriptRequestsWaitingForPageLoad.removeFirst();
      log.i(t, "resetLoadPageStatus: DISCARDING javascriptUrl: " + s);
    }
  }

  /**
   * Tell the enclosing activity that we should restore this WebView to visible
   * and make any custom view gone.
   *
   * NOTE: Only Invoked by ODKWebChromeClient.
   */
  void swapOffCustomView() {
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
