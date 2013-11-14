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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * The wrapper for the (singleton) ODKWebView
 * used to display all forms.
 *
 * @author mitchellsundt@gmail.com
 */
public class JQueryODKView extends FrameLayout {

  private final ODKWebView mWebView;


  public JQueryODKView(Context context, AttributeSet set) {
    super(context, set);

    mWebView = new ODKWebView(context);

    FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT);
    addView(mWebView, fp);
  }

  /**
   * Communicates the results for an action callback to the webkit.
   *
   * @param pageWaitingForData
   * @param pathWaitingForData
   * @param actionWaitingForData
   * @param jsonObject
   */
  public void doActionResult(String pageWaitingForData, String pathWaitingForData, String actionWaitingForData, String jsonObject ) {
    // NOTE: this is asynchronous
    mWebView.loadJavascriptUrl("javascript:window.landing.opendatakitCallback('" + pageWaitingForData
        + "','" + pathWaitingForData + "','" + actionWaitingForData + "', '" + jsonObject
        + "' )");
    mWebView.requestFocus();
  }

  public void requestPageFocus() {
    mWebView.requestFocus();
  }

  /**
   * Clear the current form (reset to framework)...
   */
  public void clearPage() {
    mWebView.clearPage();
  }
}
