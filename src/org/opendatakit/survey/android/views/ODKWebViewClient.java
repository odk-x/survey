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

import org.apache.commons.lang3.StringUtils;
import org.opendatakit.common.android.utilities.WebLogger;

import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class ODKWebViewClient extends WebViewClient {
  private static final String t = "ODKWebViewClient";
  private WebLogger log;
  private JQueryODKView jQueryODKView;

  ODKWebViewClient(JQueryODKView jQueryODKView, String appName) {
	this.jQueryODKView = jQueryODKView;
    log = WebLogger.getLogger(appName);
  }

  @Override
  public boolean shouldOverrideUrlLoading(WebView view, String url) {
    Log.i(t,
        "shouldOverrideUrlLoading: " + url + " ms: " + Long.toString(System.currentTimeMillis()));
    // // TODO Auto-generated method stub
    // if (url.endsWith(".3gpp") || url.endsWith(".3gp")) {
    // Log.d(t, "Media player");
    // Uri tempPath = Uri.parse(url);
    // MediaPlayer player = MediaPlayer.create(view.getContext(), tempPath);
    // player.start();
    // return true;
    // }else{
    return super.shouldOverrideUrlLoading(view, url);
    // }
  }

  @Override
  public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
    log.i(t, "doUpdateVisitedHistory: " + url + " ms: " + Long.toString(System.currentTimeMillis()));
  }

  @Override
  public void onLoadResource(WebView view, String url) {
    log.i(t, "onLoadResource: " + url + " ms: " + Long.toString(System.currentTimeMillis()));
    super.onLoadResource(view, url);
  }

  @Override
  public void onPageFinished(WebView view, String url) {
    log.i(t, "onPageFinished: " + url + " ms: " + Long.toString(System.currentTimeMillis()));
    super.onPageFinished(view, url);
    if ( !StringUtils.startsWithIgnoreCase(url, "javascript:") ) {
      jQueryODKView.loadPageFinished();
    }
  }

  @Override
  public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
    log.i(t, "onReceivedError: " + failingUrl + " ms: " + Long.toString(System.currentTimeMillis()));
    super.onReceivedError(view, errorCode, description, failingUrl);
  }

  @Override
  public void onScaleChanged(WebView view, float oldScale, float newScale) {
    log.i(t, "onScaleChanged: " + newScale);
    super.onScaleChanged(view, oldScale, newScale);
  }

  @Override
  public void onTooManyRedirects(WebView view, Message cancelMsg, Message continueMsg) {
    log.i(t, "onTooManyRedirects: " + cancelMsg.toString());
    super.onTooManyRedirects(view, cancelMsg, continueMsg);
  }

  @Override
  public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
    log.i(t, "onUnhandledKeyEvent: " + event.toString());
    super.onUnhandledKeyEvent(view, event);
  }
}
