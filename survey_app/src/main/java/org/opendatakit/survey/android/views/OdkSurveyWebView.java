package org.opendatakit.survey.android.views;

import android.content.Context;
import android.util.AttributeSet;
import org.opendatakit.common.android.activities.IOdkDataActivity;
import org.opendatakit.common.android.views.ODKWebView;
import org.opendatakit.survey.android.activities.IOdkSurveyActivity;

/**
 * @author mitchellsundt@gmail.com
 */
public class OdkSurveyWebView extends ODKWebView {
  private static final String t = "OdkSurveyWebView";

  private OdkSurvey shim;

  public OdkSurveyWebView(Context context, AttributeSet attrs) {
    super(context, attrs);

    // stomp on the odkSurvey object...
    shim = new OdkSurvey((IOdkSurveyActivity) context, this);
    addJavascriptInterface(shim.getJavascriptInterfaceWithWeakReference(), "odkSurvey");
  }

  @Override
  public boolean hasPageFramework() {
    return true;
  }

  @Override
  public synchronized void loadPage() {
    /**
     * NOTE: Reload the web framework only if it has changed.
     */

    if ( ((IOdkDataActivity) getContext()).getDatabase() == null ) {
      // do not initiate reload until we have the database set up...
      return;
    }

    log.i(t, "loadPage: current loadPageUrl: " + getLoadPageUrl());
    String baseUrl = ((IOdkSurveyActivity) getContext()).getUrlBaseLocation(
        hasPageFrameworkFinishedLoading() && getLoadPageUrl() != null);
    String hash = ((IOdkSurveyActivity) getContext()).getUrlLocationHash();

    if ( baseUrl != null ) {
      resetLoadPageStatus(baseUrl);

      log.i(t, "loadPage: full reload: " + baseUrl + hash);

      loadUrl(baseUrl + hash);
    } else if ( hasPageFrameworkFinishedLoading() ) {
      log.i(t,  "loadPage: delegate to gotoUrlHash: " + hash);
      gotoUrlHash(hash);
    } else {
      log.w(t, "loadPage: framework did not load -- cannot load anything!");
    }
  }

  @Override
  public synchronized void reloadPage() {
    log.i(t, "reloadPage: current loadPageUrl: " + getLoadPageUrl());
    String baseUrl = ((IOdkSurveyActivity) getContext()).getUrlBaseLocation(false);

    if ( baseUrl != null ) {
      resetLoadPageStatus(baseUrl);
      log.i(t, "reloadPage: full reload: " + baseUrl);
      loadUrl(baseUrl);
    } else {
      log.w(t, "reloadPage: framework did not load -- cannot load anything!");
    }
  }


}
