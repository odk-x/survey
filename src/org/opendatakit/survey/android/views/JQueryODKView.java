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

import java.io.File;

import org.apache.commons.lang3.StringEscapeUtils;
import org.opendatakit.common.android.provider.FileProvider;
import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.activities.ODKActivity;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.logic.FormIdStruct;
import org.opendatakit.survey.android.provider.FormsProviderAPI;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebSettings.RenderPriority;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TableLayout;

/**
 * The WebKit used to display all forms.
 *
 * @author mitchellsundt@gmail.com
 */
@SuppressLint("SetJavaScriptEnabled")
public class JQueryODKView extends FrameLayout {

  // starter random number for view IDs
  private static final String t = "JQueryODKView";

  private WebLogger log;
  private WebView mWebView;

  public JQueryODKView(Context context, AttributeSet set) {
    super(context, set);
    ODKActivity a = (ODKActivity) context;
    String appName = a.getAppName();

    log = WebLogger.getLogger(appName);

    mWebView = new WebView(context);
    mWebView.setId(984732);

    FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT);
    TableLayout.LayoutParams params = new TableLayout.LayoutParams();
    params.setMargins(7, 5, 7, 5);
    mWebView.setLayoutParams(params);

    mWebView.setWebChromeClient(new ODKWebChromeClient(a));
    WebViewClient wvc = new ODKWebViewClient(appName);
    mWebView.setWebViewClient(wvc);

    // for development -- always draw from source...
    WebSettings ws = mWebView.getSettings();
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

    mWebView.setFocusable(true);
    mWebView.setFocusableInTouchMode(true);
    mWebView.setInitialScale(100);

    // questionable value...
    mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
    mWebView.setSaveEnabled(true);

    addView(mWebView, fp);

    InputMethodManager imm = (InputMethodManager) context
        .getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.showSoftInput(mWebView, InputMethodManager.SHOW_IMPLICIT);
  }

  // /////////////////////////////////////////////////////////////////////////////////////////
  // /////////////////////////////////////////////////////////////////////////////////////////

  public void setJavascriptCallback(JQueryJavascriptCallback jsCallback) {
    mWebView.addJavascriptInterface(jsCallback, "shim");
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
    log.i(t, "loadUrl: " + javascriptUrl);
    mWebView.loadUrl(javascriptUrl);
  }

  /**
   *
   * @param appName
   *          -- appName to use when FormIdStruct is null
   * @param s
   *          -- FormIdStruct of the form to open -- may be null
   * @param instanceID
   * @param pageRef
   * @param auxillaryHash
   */
  public void loadPage(String appName, FormIdStruct s, String instanceID, String pageRef,
      String auxillaryHash) {

    String url = getHtmlUrl(((s == null) ? appName : s.appName), s, instanceID, pageRef,
        auxillaryHash);
    if (url == null)
      return;
    log.i(t, "loadUrl: " + url);
    mWebView.loadUrl(url);
    mWebView.requestFocus();
  }

  private String getHtmlUrl(String appName, FormIdStruct s, String instanceID, String pageRef,
      String auxillaryHash) {

    // Find the formPath for the default form with the most recent
    // version...
    // we need this so that we can load the index.html and main javascript
    // code
    String formPath = null;

    Cursor c = null;
    try {
      //
      // the default form is named 'default' ...
      String selection = FormsColumns.FORM_ID + "=?";
      String[] selectionArgs = { FormsColumns.COMMON_BASE_FORM_ID };
      String orderBy = FormsColumns.FORM_VERSION + " DESC"; // use the
      // most
      // recently
      // created
      // of the
      // matches
      // (in case
      // DB
      // corrupted)
      c = getContext().getContentResolver().query(
          Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName), null, selection,
          selectionArgs, orderBy);

      if (c.getCount() > 0) {
        // we found a match...
        c.moveToFirst();
        formPath = c.getString(c.getColumnIndex(FormsColumns.FORM_PATH));
      }

    } finally {
      if (c != null && !c.isClosed()) {
        c.close();
      }
    }

    if (formPath == null)
      return null;

    // formPath always begins ../../ -- strip that off to get explicit path
    // suffix...
    File mediaFolder = new File(new File(ODKFileUtils.getAppFolder(appName)), formPath.substring(6));

    // File htmlFile = new File(mediaFolder, mPrompt.getAppearanceHint());
    File htmlFile = new File(mediaFolder, "index.html");

    if (!htmlFile.exists())
      return null;

    String fullPath = FileProvider.getAsUrl(htmlFile);
    String htmlUrl = fullPath + "#formPath="
        + StringEscapeUtils.escapeHtml4((s == null) ? formPath : s.formPath)
        + ((instanceID == null) ? "" : "&instanceId=" + StringEscapeUtils.escapeHtml4(instanceID))
        + ((pageRef == null) ? "" : "&pageRef=" + StringEscapeUtils.escapeHtml4(pageRef))
        + ((auxillaryHash == null) ? "" : "&" + auxillaryHash);

    return htmlUrl;
  }

}
