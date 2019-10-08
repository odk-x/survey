package org.opendatakit.survey.views;

import android.content.Context;
import android.util.AttributeSet;
import org.opendatakit.survey.activities.IOdkSurveyActivity;
import org.opendatakit.views.ODKWebView;

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
   @Override public synchronized void reloadPage() {

      log.i(t, "reloadPage: current loadPageUrl: " + getLoadPageUrl());
      String baseUrl = ((IOdkSurveyActivity) getOdkContext()).getUrlBaseLocation(false);
      String hash = ((IOdkSurveyActivity) getOdkContext()).getUrlLocationHash();

      if (baseUrl != null) {
         // for Survey, we do care about the URL
         String fullUrl = baseUrl + hash;
         loadPageOnUiThread(fullUrl, null);
      } else {
         log.w(t, "reloadPage: framework did not load -- cannot load anything!");
      }
   }

}
