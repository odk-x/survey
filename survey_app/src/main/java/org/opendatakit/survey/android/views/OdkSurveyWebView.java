package org.opendatakit.survey.android.views;

import android.content.Context;
import android.os.Looper;
import android.util.AttributeSet;
import org.opendatakit.common.android.activities.IOdkDataActivity;
import org.opendatakit.common.android.views.ODKWebView;
import org.opendatakit.survey.android.activities.IOdkSurveyActivity;

/**
 * @author mitchellsundt@gmail.com
 */
public class OdkSurveyWebView extends ODKWebView {
  private static final String t = "OdkSurveyWebView";

  private OdkSurvey odkSurvey;

  public OdkSurveyWebView(Context context, AttributeSet attrs) {
    super(context, attrs);

    // stomp on the odkSurvey object...
    odkSurvey = new OdkSurvey((IOdkSurveyActivity) context, this);
    addJavascriptInterface(odkSurvey.getJavascriptInterfaceWithWeakReference(), "odkSurvey");
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
      // for Survey, we do care about the URL
      final String fullUrl = baseUrl + hash;

      resetLoadPageStatus(fullUrl);

      log.i(t, "loadPage: full reload: " + fullUrl);

      // Ensure that this is run on the UI thread
      if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
        post(new Runnable() {
          public void run() {
            loadUrl(fullUrl);
          }
        });
      } else {
        loadUrl(fullUrl);
      }

    } else if ( hasPageFrameworkFinishedLoading() ) {
      log.i(t,  "loadPage: delegate to gotoUrlHash: " + hash);
      gotoUrlHash(hash);
    } else {
      log.w(t, "loadPage: framework did not load -- cannot load anything!");
    }
  }

  @Override
  public synchronized void reloadPage() {
    if ( ((IOdkDataActivity) getContext()).getDatabase() == null ) {
      // do not initiate reload until we have the database set up...
      return;
    }

    log.i(t, "reloadPage: current loadPageUrl: " + getLoadPageUrl());
    String baseUrl = ((IOdkSurveyActivity) getContext()).getUrlBaseLocation(false);
    String hash = ((IOdkSurveyActivity) getContext()).getUrlLocationHash();

    if ( baseUrl != null ) {
      // for Survey, we do care about the URL
      final String fullUrl = baseUrl + hash;

      if ( shouldForceLoadDuringReload() ||
          hasPageFrameworkFinishedLoading() || !fullUrl.equals(getLoadPageUrl()) ) {

        resetLoadPageStatus(fullUrl);

        log.i(t, "reloadPage: full reload: " + fullUrl);

        // Ensure that this is run on the UI thread
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
          post(new Runnable() {
            public void run() {
              loadUrl(fullUrl);
            }
          });
        } else {
          loadUrl(fullUrl);
        }

      } else {
        log.w(t, "reloadPage: framework in process of loading -- ignoring request!");
      }
    } else {
      log.w(t, "reloadPage: framework did not load -- cannot load anything!");
    }
  }


}
