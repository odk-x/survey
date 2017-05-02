package org.opendatakit.survey.views;

import android.content.Context;
import android.util.AttributeSet;
import org.opendatakit.views.ODKWebView;
import org.opendatakit.survey.activities.IOdkSurveyActivity;

/**
 * @author mitchellsundt@gmail.com
 */
public class OdkSurveyWebView extends ODKWebView {
   private static final String t = "OdkSurveyWebView";

   private OdkSurveyStateManagement odkSurveyStateManagement;

   public OdkSurveyWebView(Context context, AttributeSet attrs) {
      super(context, attrs);

      // stomp on the odkSurveyStateManagement object...
      odkSurveyStateManagement = new OdkSurveyStateManagement((IOdkSurveyActivity) context, this);
      addJavascriptInterface(odkSurveyStateManagement.getJavascriptInterfaceWithWeakReference(), "odkSurveyStateManagement");
   }

   @Override public boolean hasPageFramework() {
      return true;
   }

   /**
    * IMPORTANT: This function should only be called with the context of the database listeners
    * OR if called from elsewhere there should be an if statement before invoking that checks
    * if the database is currently available.
    */
   @Override public synchronized void loadPage() {
      /**
       * NOTE: Reload the web framework only if it has changed.
       */

      log.i(t, "loadPage: current loadPageUrl: " + getLoadPageUrl());
      String baseUrl = ((IOdkSurveyActivity) getContext())
          .getUrlBaseLocation(hasPageFrameworkFinishedLoading() && getLoadPageUrl() != null);
      String hash = ((IOdkSurveyActivity) getContext()).getUrlLocationHash();

      if (baseUrl != null) {
         // for Survey, we do care about the URL
         String fullUrl = baseUrl + hash;

         loadPageOnUiThread(fullUrl, false);

      } else if (hasPageFrameworkFinishedLoading()) {
         log.i(t, "loadPage: delegate to gotoUrlHash: " + hash);
         gotoUrlHash(hash);
      } else {
         log.w(t, "loadPage: framework did not load -- cannot load anything!");
      }
   }

   /**
    * IMPORTANT: This function should only be called with the context of the database listeners
    * OR if called from elsewhere there should be an if statement before invoking that checks
    * if the database is currently available.
    */
   @Override public synchronized void reloadPage() {

      log.i(t, "reloadPage: current loadPageUrl: " + getLoadPageUrl());
      String baseUrl = ((IOdkSurveyActivity) getContext()).getUrlBaseLocation(false);
      String hash = ((IOdkSurveyActivity) getContext()).getUrlLocationHash();

      if (baseUrl != null) {
         // for Survey, we do care about the URL
         String fullUrl = baseUrl + hash;
         loadPageOnUiThread(fullUrl, true);
      } else {
         log.w(t, "reloadPage: framework did not load -- cannot load anything!");
      }
   }

}
