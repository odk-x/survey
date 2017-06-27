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

package org.opendatakit.survey.views;

import org.opendatakit.logging.WebLogger;
import org.opendatakit.logging.WebLoggerIf;
import org.opendatakit.survey.activities.IOdkSurveyActivity;

import java.lang.ref.WeakReference;

/**
 * The class mapped to 'odkSurvey' in the Javascript
 *
 * @author mitchellsundt@gmail.com
 */
class OdkSurveyStateManagement {

  /**
   * Used for logging
   */
  @SuppressWarnings("unused")
  private static final String TAG = OdkSurveyStateManagement.class.getSimpleName();
  private final WebLoggerIf log;
  private WeakReference<OdkSurveyWebView> mWebView;
  private IOdkSurveyActivity mActivity;

  /**
   * Constructs a new
   * @param activity the activity, saved and used to delegate javascript actions
   * @param webView the web view, which is saved in a weak reference
   */
  OdkSurveyStateManagement(IOdkSurveyActivity activity, OdkSurveyWebView webView) {
    mWebView = new WeakReference<>(webView);
    mActivity = activity;
    log = WebLogger.getLogger(mActivity.getAppName());
  }

  boolean isInactive() {
    return mWebView.get() == null || mWebView.get().isInactive();
  }

  OdkSurveyStateManagementIf getJavascriptInterfaceWithWeakReference() {
    return new OdkSurveyStateManagementIf(this);
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#clearAuxillaryHash()}
   */
  void clearAuxillaryHash() {
    log.d("odkSurvey", "DO: clearAuxillaryHash()");
    mActivity.clearAuxillaryHash();
  }

  /**
   * Clear the instanceId. The ODK Survey webpage is no longer associated with a specific
   * instanceId or rowId.
   *
   * @param refId An id that ties the javascript interface to a particular activity
   */
  void clearInstanceId(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: clearInstanceId(" + refId + ")");
      return;
    }
    log.d("odkSurvey", "DO: clearInstanceId(" + refId + ")");
    mActivity.setInstanceId(null);
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#pushSectionScreenState} on the saved activity
   * If refId is null, clears the instanceId. If refId matches the current refId, sets the
   * instanceId.
   *
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param instanceId the instance id for the row add/edit
   */
  void setInstanceId(String refId, String instanceId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: setInstanceId(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("odkSurvey", "DO: setInstanceId(" + refId + ", " + instanceId + ")");
    mActivity.setInstanceId(instanceId);
  }

  /**
   * Get the instanceId for this web page. Returns null if the refId does not match.
   *
   * @param refId An id that ties the javascript interface to a particular activity
   * @return the instance id if the refId is valid or null
   */
  String getInstanceId(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: getInstanceId(" + refId + ")");
      return null;
    }
    log.d("odkSurvey", "DO: getInstanceId(" + refId + ")");
    return mActivity.getInstanceId();
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#pushSectionScreenState} on the saved activity
   * @param refId An id that ties the javascript interface to a particular activity
   */
  void pushSectionScreenState(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: pushSectionScreenState(" + refId + ")");
      return;
    }
    log.d("odkSurvey", "DO: pushSectionScreenState(" + refId + ")");
    mActivity.pushSectionScreenState();
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#setSectionScreenState} on the saved activity
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param screenPath the screen path to be updated in MainMenuActivity
   * @param state      the state to be updated in MainMenuActivity
   */
  void setSectionScreenState(String refId, String screenPath, String state) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey",
          "IGNORED: setSectionScreenState(" + refId + ", " + screenPath + ", " + state + ")");
      return;
    }
    log.d("odkSurvey",
        "DO: setSectionScreenState(" + refId + ", " + screenPath + ", " + state + ")");
    mActivity.setSectionScreenState(screenPath, state);
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#clearSectionScreenState} on the saved activity
   * @param refId An id that ties the javascript interface to a particular activity
   */
  void clearSectionScreenState(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: clearSectionScreenState(" + refId + ")");
      return;
    }
    log.d("odkSurvey", "DO: clearSectionScreenState(" + refId + ")");
    mActivity.clearSectionScreenState();
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#getControllerState} on the saved activity
   * @param refId An id that ties the javascript interface to a particular activity
   * @return the current controller state
   */
  String getControllerState(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: getControllerState(" + refId + ")");
      return null;
    }
    log.d("odkSurvey", "DO: getControllerState(" + refId + ")");
    return mActivity.getControllerState();
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#getScreenPath} on the saved activity
   * @param refId An id that ties the javascript interface to a particular activity
   * @return the current screen path
   */
  String getScreenPath(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: getScreenPath(" + refId + ")");
      return null;
    }
    log.d("odkSurvey", "DO: getScreenPath(" + refId + ")");
    return mActivity.getScreenPath();
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#hasScreenHistory} on the saved activity
   * @param refId An id that ties the javascript interface to a particular activity
   * @return whether the screen has history
   */
  boolean hasScreenHistory(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: hasScreenHistory(" + refId + ")");
      return false;
    }
    log.d("odkSurvey", "DO: hasScreenHistory(" + refId + ")");
    return mActivity.hasScreenHistory();
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#popScreenHistory} on the saved activity
   * @param refId An id that ties the javascript interface to a particular activity
   * @return the last thing in the page history
   */
  String popScreenHistory(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: popScreenHistory(" + refId + ")");
      return null;
    }
    log.d("odkSurvey", "DO: popScreenHistory(" + refId + ")");
    return mActivity.popScreenHistory();
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#hasSectionStack} on the saved activity
   * @param refId An id that ties the javascript interface to a particular activity
   * @return whether the section state has history
   */
  boolean hasSectionStack(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: hasSectionStack(" + refId + ")");
      return false;
    }
    log.d("odkSurvey", "DO: hasSectionStack(" + refId + ")");
    return mActivity.hasSectionStack();
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#popSectionStack} on the saved activity
   * @param refId An id that ties the javascript interface to a particular activity
   * @return the last item in the state history
   */
  String popSectionStack(String refId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: popSectionStack(" + refId + ")");
      return null;
    }
    log.d("odkSurvey", "DO: popSectionStack(" + refId + ")");
    return mActivity.popSectionStack();
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#getRefId} on the saved activity
   * @param refId   An id that ties the javascript interface to a particular activity
   * @param outcome whether the framework load was successful
   */
  void frameworkHasLoaded(String refId, boolean outcome) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: frameworkHasLoaded(" + refId + ", " + outcome + ")");
      return;
    }
    log.d("odkSurvey", "DO: frameworkHasLoaded(" + refId + ", " + outcome + ")");
    mWebView.get().frameworkHasLoaded();
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#ignoreAllChangesCompleted} on the saved activity
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param instanceId the instance id for the row add/edit
   */
  void ignoreAllChangesCompleted(String refId, String instanceId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: ignoreAllChangesCompleted(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("odkSurvey", "DO: ignoreAllChangesCompleted(" + refId + ", " + instanceId + ")");
    mActivity.ignoreAllChangesCompleted(instanceId);
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#ignoreAllChangesFailed} on the saved activity
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param instanceId the instance id for the row add/edit
   */
  void ignoreAllChangesFailed(String refId, String instanceId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: ignoreAllChangesFailed(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("odkSurvey", "DO: ignoreAllChangesFailed(" + refId + ", " + instanceId + ")");
    mActivity.ignoreAllChangesFailed(instanceId);
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#saveAllChangesCompleted} on the saved activity
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param instanceId the instance id for the row add/edit
   * @param asComplete whether to save as finalized or save as incomplete
   */
  void saveAllChangesCompleted(String refId, String instanceId, boolean asComplete) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey",
          "IGNORED: saveAllChangesCompleted(" + refId + ", " + instanceId + ", " + asComplete
              + ")");
      return;
    }
    // go through the FC because there are additional keys that should be
    // set here...
    log.d("odkSurvey",
        "DO: saveAllChangesCompleted(" + refId + ", " + instanceId + ", " + asComplete + ")");
    mActivity.saveAllChangesCompleted(instanceId, asComplete);
  }

  /**
   * Thin wrapper for {@link IOdkSurveyActivity#saveAllChangesFailed} on the saved activity
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param instanceId the instance id for the row add/edit
   */
  void saveAllChangesFailed(String refId, String instanceId) {
    if (!mActivity.getRefId().equals(refId)) {
      log.w("odkSurvey", "IGNORED: saveAllChangesFailed(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("odkSurvey", "DO: saveAllChangesFailed(" + refId + ", " + instanceId + ")");
    mActivity.saveAllChangesFailed(instanceId);
  }
}