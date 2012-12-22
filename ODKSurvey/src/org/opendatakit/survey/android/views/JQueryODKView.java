/*
 * Copyright (C) 2012 University of Washington
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
import org.opendatakit.survey.android.activities.MainMenuActivity;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.logic.FormIdStruct;
import org.opendatakit.survey.android.provider.FileProvider;
import org.opendatakit.survey.android.provider.FormsProviderAPI.FormsColumns;
import org.opendatakit.survey.android.utilities.WebLogger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
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

	private WebLogger log = Survey.getInstance().getLogger();
	private WebView mWebView;

	public JQueryODKView(Context context, AttributeSet set) {
    	super(context, set);

		mWebView = new WebView(context);
		mWebView.setId(984732);

        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        TableLayout.LayoutParams params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);
        mWebView.setLayoutParams(params);

        mWebView.setWebChromeClient(new ODKWebChromeClient(context));
        WebViewClient wvc = new ODKWebViewClient();
		mWebView.setWebViewClient(wvc);

        // for development -- always draw from source...
        WebSettings ws = mWebView.getSettings();
        ws.setAllowFileAccess(true);
        ws.setAppCacheEnabled(true);
        ws.setAppCacheMaxSize(1024L*1024L*200L);
        ws.setAppCachePath(Survey.APPCACHE_PATH);
        ws.setCacheMode(WebSettings.LOAD_NORMAL);
        ws.setDatabaseEnabled(true);
        ws.setDatabasePath(Survey.WEBDB_PATH);
        ws.setDefaultFixedFontSize(Survey.getQuestionFontsize());
        ws.setDefaultFontSize(Survey.getQuestionFontsize());
        ws.setDomStorageEnabled(true);
        ws.setGeolocationDatabasePath(Survey.GEOCACHE_PATH);
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

        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mWebView, InputMethodManager.SHOW_IMPLICIT);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////

	public void setJavascriptCallback(JQueryJavascriptCallback jsCallback) {
		mWebView.addJavascriptInterface(jsCallback, "shim");
	}

    public void triggerSaveAnswers(boolean asComplete) {
    	// NOTE: this is asynchronous
    	loadJavascriptUrl("javascript:(function() {controller.opendatakitSaveAllChanges(" + (asComplete ? "true" : "false") + ");})()");
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

	public void loadPage(String pageRef) {
		String url = getHtmlUrl(pageRef);
		if ( url == null ) return;
    	log.i(t, "loadUrl: " + url);
		mWebView.loadUrl(url);
		mWebView.requestFocus();
	}

	public static String getHtmlUrl(String pageRef) {
		FormIdStruct s = MainMenuActivity.currentForm;

		// Find the formPath for the default form with the most recent version...
		Cursor c = null;
		String formPath = null;
		try {
            //
            // find if there is already a form definition with the same formId and formVersion...
        	String selection = FormsColumns.FORM_ID + "=?";
    		String[] selectionArgs = { "default" };
    		String orderBy = FormsColumns.FORM_VERSION + " DESC"; // use the most recently created of the matches (in case DB corrupted)
            c = Survey.getInstance()
                    .getContentResolver()
                    .query(FormsColumns.CONTENT_URI, null, selection, selectionArgs, orderBy);

            if (c.getCount() > 0) {
        		// we found a match...
            	c.moveToFirst();
        		formPath = c.getString(c.getColumnIndex(FormsColumns.FORM_PATH));
            }

		} finally {
			if ( c != null && !c.isClosed() ) {
				c.close();
			}
		}

		if ( formPath == null ) return null;

		// formPath always begins ../ -- strip that off to get explicit path suffix...
		File mediaFolder = new File(new File(Survey.FORMS_PATH), formPath.substring(3));
		String instanceID = MainMenuActivity.instanceId;

		// File htmlFile = new File(mediaFolder, mPrompt.getAppearanceHint());
        File htmlFile = new File(mediaFolder, "index.html");

        if ( !htmlFile.exists() ) return null;

        String fullPath = FileProvider.getAsUrl(htmlFile);
        String htmlUrl = fullPath +
        		"#formPath=" + StringEscapeUtils.escapeHtml4((s == null) ? formPath : s.formPath) +
		        ((instanceID == null) ? "" : "&instanceId=" + StringEscapeUtils.escapeHtml4(instanceID)) +
		        ((pageRef == null) ? "" : "&pageRef=" + StringEscapeUtils.escapeHtml4(pageRef));

        return htmlUrl;
	}

}
