package org.opendatakit.survey.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import org.opendatakit.survey.activities.IOdkSurveyActivity;
import org.opendatakit.views.ODKWebView;

/**
 * @author mitchellsundt@gmail.com
 */
public class OdkSurveyWebView extends ODKWebView {
  private static final String TAG = OdkSurveyWebView.class.getSimpleName();

  /**
   * DO NOT MAKE THIS LOCAL
   * We need it to stay around after the constructor exits or it will get garbage collected
   * while we're still using it
   */
  @SuppressWarnings("FieldCanBeLocal")
  private OdkSurveyStateManagement odkSurveyStateManagement;

    /* Some information about the warning we're about to ignore
   * "For applications built for API levels below 17, WebView#addJavascriptInterface presents a
   * security hazard as JavaScript on the target web page has the ability to use reflection to
   * access the injected object's public fields and thus manipulate the host application in
   * unintended ways."
   * https://labs.mwrinfosecurity.com/blog/2013/09/24/
   * webview-addjavascriptinterface-remote-code-execution/
   */
  /**
   * Constructs a new Survey State Management object and gives it a weak reference to us
   * @param context unused
   * @param attrs unused
   */
  @SuppressLint("AddJavascriptInterface")
  public OdkSurveyWebView(Context context, AttributeSet attrs) {
    super(context, attrs);

    // stomp on the odkSurveyStateManagement object...
    //noinspection ThisEscapedInObjectConstruction -- We're already in a stable state here
    odkSurveyStateManagement = new OdkSurveyStateManagement((IOdkSurveyActivity) context, this);
    addJavascriptInterface(odkSurveyStateManagement.getJavascriptInterfaceWithWeakReference(),
        "odkSurveyStateManagement");
  }

  @Override
  public boolean hasPageFramework() {
    return true;
  }

  /**
   * IMPORTANT: This function should only be called with the context of the database listeners
   * OR if called from elsewhere there should be an if statement before invoking that checks
   * if the database is currently available.
   * NOTE: Reloads the web framework only if it has changed.
   */
  @Override
  public synchronized void loadPage() {
    log.i(TAG, "loadPage: current loadPageUrl: " + getLoadPageUrl());
    String baseUrl = ((IOdkSurveyActivity) getContext())
        .getUrlBaseLocation(hasPageFrameworkFinishedLoading() && getLoadPageUrl() != null);
    String hash = ((IOdkSurveyActivity) getContext()).getUrlLocationHash();

    if (baseUrl != null) {
      // for Survey, we do care about the URL
      String fullUrl = baseUrl + hash;

      loadPageOnUiThread(fullUrl, null, false);

    } else if (hasPageFrameworkFinishedLoading()) {
      log.i(TAG, "loadPage: delegate to gotoUrlHash: " + hash);
      gotoUrlHash(hash);
    } else {
      log.w(TAG, "loadPage: framework did not load -- cannot load anything!");
    }
  }

  /**
   * IMPORTANT: This function should only be called with the context of the database listeners
   * OR if called from elsewhere there should be an if statement before invoking that checks
   * if the database is currently available.
   */
  @Override
  public synchronized void reloadPage() {

    log.i(TAG, "reloadPage: current loadPageUrl: " + getLoadPageUrl());
    String baseUrl = ((IOdkSurveyActivity) getContext()).getUrlBaseLocation(false);
    String hash = ((IOdkSurveyActivity) getContext()).getUrlLocationHash();

    if (baseUrl != null) {
      // for Survey, we do care about the URL
      String fullUrl = baseUrl + hash;
      loadPageOnUiThread(fullUrl, null, true);
    } else {
      log.w(TAG, "reloadPage: framework did not load -- cannot load anything!");
    }
  }

}
