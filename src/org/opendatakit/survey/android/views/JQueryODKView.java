/*
 * Copyright (C) 2012-2013 University of Washington
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

package org.opendatakit.survey.android.views;

import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.activities.ODKActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

/**
 * The wrapper for the (singleton) ODKWebView
 * used to display all forms.
 *
 * @author mitchellsundt@gmail.com
 */
@SuppressLint("SetJavaScriptEnabled")
public class JQueryODKView extends FrameLayout {

  // starter random number for view IDs
  private static final String t = "JQueryODKView";

  private static ODKWebView mWebView = null;

  private final WebLogger log;

  /**
   * Ensure that the static mWebView is initialized.
   *
   * @param context
   * @param view
   * @return
   */
  private static synchronized boolean assertWebView(Context context, JQueryODKView view) {

    boolean outcome = false;

    if (mWebView == null) {
      view.log.i(t, "FIRST TIME: Creating new ODKWebView");
      mWebView = new ODKWebView(context);
      outcome = true;
    } else if ( !mWebView.getContext().equals(context)) {
      view.log.i(t, "CHANGED CONTEXT: Creating new ODKWebView");
      mWebView = new ODKWebView(context);
      outcome = true;
    } else {
      view.log.i(t, "SAME CONTEXT: Reusing ODKWebView");
    }
    return outcome;
  }


  public JQueryODKView(Context context, AttributeSet set) {
    super(context, set);

    ODKActivity a = (ODKActivity) context;
    String appName = a.getAppName();
    log = WebLogger.getLogger(appName);

    if (assertWebView(context, this)) {
      // we have already reset or loaded the page, just let it be whatever it was...
    }

    FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT);
    addView(mWebView, fp);

    InputMethodManager imm = (InputMethodManager) context
        .getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.showSoftInput(mWebView, InputMethodManager.SHOW_IMPLICIT);
  }

  public void triggerSaveAnswers(boolean asComplete) {
    // NOTE: this is asynchronous
    loadJavascriptUrl("javascript:(function() {controller.opendatakitSaveAllChanges("
        + (asComplete ? "true" : "false") + ");})()");
  }

  public void triggerIgnoreAnswers() {
    // NOTE: this is asynchronous
    loadJavascriptUrl("javascript:(function() {controller.opendatakitIgnoreAllChanges();})()");
  }

  public void goBack() {
    loadJavascriptUrl("javascript:(function() {controller.opendatakitGotoPreviousScreen();})()");
  }

  // called to invoke a javascript method inside the webView
  public void loadJavascriptUrl(String javascriptUrl) {
    mWebView.loadJavascriptUrl(javascriptUrl);
  }

  /**
   * Loads whatever page is currently active according to the ODKActivity...
   */
  public void loadPage() {
    mWebView.loadPage();
    mWebView.requestFocus();
  }

}
