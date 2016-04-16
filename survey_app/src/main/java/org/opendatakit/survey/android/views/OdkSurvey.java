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
import org.opendatakit.survey.android.activities.IOdkSurveyActivity;

import java.lang.ref.WeakReference;

/**
 * The class mapped to 'odkSurvey' in the Javascript
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class OdkSurvey {

  public static final String t = "OdkSurvey";

  private WeakReference<OdkSurveyWebView> mWebView;
  private IOdkSurveyActivity mActivity;
  private final WebLoggerIf log;

  public OdkSurvey(IOdkSurveyActivity activity, OdkSurveyWebView webView) {
    mWebView = new WeakReference<OdkSurveyWebView>(webView);
    mActivity = activity;
    log = WebLogger.getLogger(mActivity.getAppName());
  }

  public boolean isInactive() {
    return (mWebView.get() == null) || (mWebView.get().isInactive());
  }


  public OdkSurveyIf getJavascriptInterfaceWithWeakReference() {
    return new OdkSurveyIf(this);
  }

  public void clearAuxillaryHash() {
    log.d("odkSurvey", "DO: clearAuxillaryHash()");
    mActivity.clearAuxillaryHash();
  }

  public void clearInstanceId(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: clearInstanceId(" + refId + ")");
      return;
    }
    log.d("odkSurvey", "DO: clearInstanceId(" + refId + ")");
    mActivity.setInstanceId(null);
  }

  /**
   * If refId is null, clears the instanceId. If refId matches the current
   * refId, sets the instanceId.
   *
   * @param refId
   * @param instanceId
   */
  public void setInstanceId(String refId, String instanceId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: setInstanceId(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("odkSurvey", "DO: setInstanceId(" + refId + ", " + instanceId + ")");
    mActivity.setInstanceId(instanceId);
  }

  public String getInstanceId(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: getInstanceId(" + refId + ")");
      return null;
    }
    log.d("odkSurvey", "DO: getInstanceId(" + refId + ")");
    return mActivity.getInstanceId();
  }

  public void pushSectionScreenState(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: pushSectionScreenState(" + refId + ")");
      return;
    }
    log.d("odkSurvey", "DO: pushSectionScreenState(" + refId + ")");
    mActivity.pushSectionScreenState();
  }

  public void setSectionScreenState(String refId, String screenPath, String state) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: setSectionScreenState(" + refId + ", " + screenPath + ", " + state
          + ")");
      return;
    }
    log.d("odkSurvey", "DO: setSectionScreenState(" + refId + ", " + screenPath + ", " + state + ")");
    mActivity.setSectionScreenState(screenPath, state);
  }

  public void clearSectionScreenState(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: clearSectionScreenState(" + refId + ")");
      return;
    }
    log.d("odkSurvey", "DO: clearSectionScreenState(" + refId + ")");
    mActivity.clearSectionScreenState();
  }

  public String getControllerState(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: getControllerState(" + refId + ")");
      return null;
    }
    log.d("odkSurvey", "DO: getControllerState(" + refId + ")");
    return mActivity.getControllerState();
  }

  public String getScreenPath(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: getScreenPath(" + refId + ")");
      return null;
    }
    log.d("odkSurvey", "DO: getScreenPath(" + refId + ")");
    return mActivity.getScreenPath();
  }

  public boolean hasScreenHistory(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: hasScreenHistory(" + refId + ")");
      return false;
    }
    log.d("odkSurvey", "DO: hasScreenHistory(" + refId + ")");
    return mActivity.hasScreenHistory();
  }

  public String popScreenHistory(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: popScreenHistory(" + refId + ")");
      return null;
    }
    log.d("odkSurvey", "DO: popScreenHistory(" + refId + ")");
    return mActivity.popScreenHistory();
  }

  public boolean hasSectionStack(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: hasSectionStack(" + refId + ")");
      return false;
    }
    log.d("odkSurvey", "DO: hasSectionStack(" + refId + ")");
    return mActivity.hasSectionStack();
  }

  public String popSectionStack(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: popSectionStack(" + refId + ")");
      return null;
    }
    log.d("odkSurvey", "DO: popSectionStack(" + refId + ")");
    return mActivity.popSectionStack();
  }

  public void frameworkHasLoaded(String refId, boolean outcome) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: frameworkHasLoaded(" + refId + ", " + outcome + ")");
      return;
    }
    log.d("odkSurvey", "DO: frameworkHasLoaded(" + refId + ", " + outcome + ")");
    mWebView.get().frameworkHasLoaded();
  }

  public void ignoreAllChangesCompleted(String refId, String instanceId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: ignoreAllChangesCompleted(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("odkSurvey", "DO: ignoreAllChangesCompleted(" + refId + ", " + instanceId + ")");
    mActivity.ignoreAllChangesCompleted(instanceId);
  }

  public void ignoreAllChangesFailed(String refId, String instanceId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: ignoreAllChangesFailed(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("odkSurvey", "DO: ignoreAllChangesFailed(" + refId + ", " + instanceId + ")");
    mActivity.ignoreAllChangesFailed(instanceId);
  }

  public void saveAllChangesCompleted(String refId, String instanceId, boolean asComplete) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: saveAllChangesCompleted(" + refId + ", " + instanceId + ", "
          + asComplete + ")");
      return;
    }
    // go through the FC because there are additional keys that should be
    // set here...
    log.d("odkSurvey", "DO: saveAllChangesCompleted(" + refId + ", " + instanceId + ", " + asComplete
        + ")");
    mActivity.saveAllChangesCompleted(instanceId, asComplete);
  }

  public void saveAllChangesFailed(String refId, String instanceId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: saveAllChangesFailed(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("odkSurvey", "DO: saveAllChangesFailed(" + refId + ", " + instanceId + ")");
    mActivity.saveAllChangesFailed(instanceId);
  }
}