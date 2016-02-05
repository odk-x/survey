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
import org.opendatakit.common.android.utilities.WebLoggerIf;
import org.opendatakit.common.android.views.ODKWebView;
import org.opendatakit.survey.android.activities.IOdkSurveyActivity;

/**
 * The class mapped to 'shim' in the Javascript
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class ODKShimJavascriptCallback {

  public static final String t = "ODKShimJavascriptCallback";

  private ODKWebView mWebView;
  private IOdkSurveyActivity mActivity;
  private final WebLoggerIf log;

  public ODKShimJavascriptCallback(ODKWebView webView, IOdkSurveyActivity activity) {
    mWebView = webView;
    mActivity = activity;
    log = WebLogger.getLogger(mActivity.getAppName());
  }

  @android.webkit.JavascriptInterface
  public void clearAuxillaryHash() {
    if (mWebView == null) {
      log.w("shim", "clearInstanceId -- interface removed");
      return;
    }
    log.d("shim", "DO: clearAuxillaryHash()");
    mActivity.clearAuxillaryHash();
  }

  @android.webkit.JavascriptInterface
  public void clearInstanceId(String refId) {
    if (mWebView == null) {
      log.w("shim", "clearInstanceId -- interface removed");
      return;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: clearInstanceId(" + refId + ")");
      return;
    }
    log.d("shim", "DO: clearInstanceId(" + refId + ")");
    mActivity.setInstanceId(null);
  }

  /**
   * If refId is null, clears the instanceId. If refId matches the current
   * refId, sets the instanceId.
   *
   * @param refId
   * @param instanceId
   */
  @android.webkit.JavascriptInterface
  public void setInstanceId(String refId, String instanceId) {
    if (mWebView == null) {
      log.w("shim", "setInstanceId -- interface removed");
      return;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: setInstanceId(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("shim", "DO: setInstanceId(" + refId + ", " + instanceId + ")");
    mActivity.setInstanceId(instanceId);
  }

  @android.webkit.JavascriptInterface
  public String getInstanceId(String refId) {
    if (mWebView == null) {
      log.w("shim", "getInstanceId -- interface removed");
      return null;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: getInstanceId(" + refId + ")");
      return null;
    }
    log.d("shim", "DO: getInstanceId(" + refId + ")");
    return mActivity.getInstanceId();
  }

  @android.webkit.JavascriptInterface
  public void pushSectionScreenState(String refId) {
    if (mWebView == null) {
      log.w("shim", "pushSectionScreenState -- interface removed");
      return;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: pushSectionScreenState(" + refId + ")");
      return;
    }
    log.d("shim", "DO: pushSectionScreenState(" + refId + ")");
    mActivity.pushSectionScreenState();
  }

  @android.webkit.JavascriptInterface
  public void setSectionScreenState(String refId, String screenPath, String state) {
    if (mWebView == null) {
      log.w("shim", "setSectionScreenState -- interface removed");
      return;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: setSectionScreenState(" + refId + ", " + screenPath + ", " + state
          + ")");
      return;
    }
    log.d("shim", "DO: setSectionScreenState(" + refId + ", " + screenPath + ", " + state + ")");
    mActivity.setSectionScreenState(screenPath, state);
  }

  @android.webkit.JavascriptInterface
  public void clearSectionScreenState(String refId) {
    if (mWebView == null) {
      log.w("shim", "clearSectionScreenState -- interface removed");
      return;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: clearSectionScreenState(" + refId + ")");
      return;
    }
    log.d("shim", "DO: clearSectionScreenState(" + refId + ")");
    mActivity.clearSectionScreenState();
  }

  @android.webkit.JavascriptInterface
  public String getControllerState(String refId) {
    if (mWebView == null) {
      log.w("shim", "getControllerState -- interface removed");
      return null;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: getControllerState(" + refId + ")");
      return null;
    }
    log.d("shim", "DO: getControllerState(" + refId + ")");
    return mActivity.getControllerState();
  }

  @android.webkit.JavascriptInterface
  public String getScreenPath(String refId) {
    if (mWebView == null) {
      log.w("shim", "getScreenPath -- interface removed");
      return null;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: getScreenPath(" + refId + ")");
      return null;
    }
    log.d("shim", "DO: getScreenPath(" + refId + ")");
    return mActivity.getScreenPath();
  }

  @android.webkit.JavascriptInterface
  public boolean hasScreenHistory(String refId) {
    if (mWebView == null) {
      log.w("shim", "hasScreenHistory -- interface removed");
      return false;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: hasScreenHistory(" + refId + ")");
      return false;
    }
    log.d("shim", "DO: hasScreenHistory(" + refId + ")");
    return mActivity.hasScreenHistory();
  }

  @android.webkit.JavascriptInterface
  public String popScreenHistory(String refId) {
    if (mWebView == null) {
      log.w("shim", "popScreenHistory -- interface removed");
      return null;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: popScreenHistory(" + refId + ")");
      return null;
    }
    log.d("shim", "DO: popScreenHistory(" + refId + ")");
    return mActivity.popScreenHistory();
  }

  @android.webkit.JavascriptInterface
  public boolean hasSectionStack(String refId) {
    if (mWebView == null) {
      log.w("shim", "hasSectionStack -- interface removed");
      return false;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: hasSectionStack(" + refId + ")");
      return false;
    }
    log.d("shim", "DO: hasSectionStack(" + refId + ")");
    return mActivity.hasSectionStack();
  }

  @android.webkit.JavascriptInterface
  public String popSectionStack(String refId) {
    if (mWebView == null) {
      log.w("shim", "popSectionStack -- interface removed");
      return null;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: popSectionStack(" + refId + ")");
      return null;
    }
    log.d("shim", "DO: popSectionStack(" + refId + ")");
    return mActivity.popSectionStack();
  }

  @android.webkit.JavascriptInterface
  public void frameworkHasLoaded(String refId, boolean outcome) {
    if (mWebView == null) {
      log.w("shim", "frameworkHasLoaded -- interface removed");
      return;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: frameworkHasLoaded(" + refId + ", " + outcome + ")");
      return;
    }
    log.d("shim", "DO: frameworkHasLoaded(" + refId + ", " + outcome + ")");
    mWebView.frameworkHasLoaded();
  }

  @android.webkit.JavascriptInterface
  public void ignoreAllChangesCompleted(String refId, String instanceId) {
    if (mWebView == null) {
      log.w("shim", "ignoreAllChangesCompleted -- interface removed");
      return;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: ignoreAllChangesCompleted(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("shim", "DO: ignoreAllChangesCompleted(" + refId + ", " + instanceId + ")");
    mActivity.ignoreAllChangesCompleted(instanceId);
  }

  @android.webkit.JavascriptInterface
  public void ignoreAllChangesFailed(String refId, String instanceId) {
    if (mWebView == null) {
      log.w("shim", "ignoreAllChangesFailed -- interface removed");
      return;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: ignoreAllChangesFailed(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("shim", "DO: ignoreAllChangesFailed(" + refId + ", " + instanceId + ")");
    mActivity.ignoreAllChangesFailed(instanceId);
  }

  @android.webkit.JavascriptInterface
  public void saveAllChangesCompleted(String refId, String instanceId, boolean asComplete) {
    if (mWebView == null) {
      log.w("shim", "saveAllChangesCompleted -- interface removed");
      return;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: saveAllChangesCompleted(" + refId + ", " + instanceId + ", "
          + asComplete + ")");
      return;
    }
    // go through the FC because there are additional keys that should be
    // set here...
    log.d("shim", "DO: saveAllChangesCompleted(" + refId + ", " + instanceId + ", " + asComplete
        + ")");
    mActivity.saveAllChangesCompleted(instanceId, asComplete);
  }

  @android.webkit.JavascriptInterface
  public void saveAllChangesFailed(String refId, String instanceId) {
    if (mWebView == null) {
      log.w("shim", "saveAllChangesFailed -- interface removed");
      return;
    }
    if (!mActivity.getRefId().equals(refId)) {
      log.w("shim", "IGNORED: saveAllChangesFailed(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("shim", "DO: saveAllChangesFailed(" + refId + ", " + instanceId + ")");
    mActivity.saveAllChangesFailed(instanceId);
  }
}