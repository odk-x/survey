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

import java.lang.ref.WeakReference;

/**
 * The class mapped to 'odkSurvey' in the Javascript
 *
 * @author mitchellsundt@gmail.com
 */
class OdkSurveyStateManagementIf {

  /**
   * Used for logging
   */
  @SuppressWarnings("unused")
  private static final String TAG = OdkSurveyStateManagementIf.class.getSimpleName();

  private WeakReference<OdkSurveyStateManagement> weakSurvey;

  /**
   * Sets up the weak reference to the odk data object
   *
   * @param odkData The odk data object to get a weak reference to
   */
  OdkSurveyStateManagementIf(OdkSurveyStateManagement odkData) {
    weakSurvey = new WeakReference<>(odkData);
  }

  private boolean isInactive() {
    return weakSurvey.get() == null || weakSurvey.get().isInactive();
  }

  /**
   * Clear the auxillary hash. The auxillary hash is used to pass initialization
   * parameters into a form. Once those parameters have been safely updated to the
   * database or stored in session variables, ODK Survey will clear them so that
   * the form logic can update or revise their values.
   */
  @android.webkit.JavascriptInterface
  public void clearAuxillaryHash() {
    if (isInactive())
      return;
    weakSurvey.get().clearAuxillaryHash();
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#clearInstanceId}
   *
   * @param refId An id that ties the javascript interface to a particular activity the id of the javascript interface
   */
  @android.webkit.JavascriptInterface
  public void clearInstanceId(String refId) {
    if (isInactive())
      return;
    weakSurvey.get().clearInstanceId(refId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#setInstanceId}
   *
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param instanceId the instance id for the row add/edit
   */
  @android.webkit.JavascriptInterface
  public void setInstanceId(String refId, String instanceId) {
    if (isInactive())
      return;
    weakSurvey.get().setInstanceId(refId, instanceId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#getInstanceId}
   *
   * @param refId An id that ties the javascript interface to a particular activity
   * @return the current instance id, if the refId is valid
   */
  @android.webkit.JavascriptInterface
  public String getInstanceId(String refId) {
    if (isInactive())
      return null;
    return weakSurvey.get().getInstanceId(refId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#pushSectionScreenState}
   *
   * @param refId An id that ties the javascript interface to a particular activity
   */
  @android.webkit.JavascriptInterface
  public void pushSectionScreenState(String refId) {
    if (isInactive())
      return;
    weakSurvey.get().pushSectionScreenState(refId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#setSectionScreenState}
   *
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param screenPath the screen path to be updated in MainMenuActivity
   * @param state      the state to be updated in MainMenuActivity
   */
  @android.webkit.JavascriptInterface
  public void setSectionScreenState(String refId, String screenPath, String state) {
    if (isInactive())
      return;
    weakSurvey.get().setSectionScreenState(refId, screenPath, state);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#clearSectionScreenState}
   *
   * @param refId An id that ties the javascript interface to a particular activity
   */
  @android.webkit.JavascriptInterface
  public void clearSectionScreenState(String refId) {
    if (isInactive())
      return;
    weakSurvey.get().clearSectionScreenState(refId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#getControllerState}
   *
   * @param refId An id that ties the javascript interface to a particular activity
   * @return the current controller state
   */
  @android.webkit.JavascriptInterface
  public String getControllerState(String refId) {
    if (isInactive())
      return null;
    return weakSurvey.get().getControllerState(refId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#getScreenPath}
   *
   * @param refId An id that ties the javascript interface to a particular activity
   * @return the current screen path
   */
  @android.webkit.JavascriptInterface
  public String getScreenPath(String refId) {
    if (isInactive())
      return null;
    return weakSurvey.get().getScreenPath(refId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#hasScreenHistory}
   *
   * @param refId An id that ties the javascript interface to a particular activity
   * @return whether the screen has history
   */
  @android.webkit.JavascriptInterface
  public boolean hasScreenHistory(String refId) {
    //noinspection SimplifiableIfStatement
    if (isInactive())
      return false;
    return weakSurvey.get().hasScreenHistory(refId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#popScreenHistory}
   *
   * @param refId An id that ties the javascript interface to a particular activity
   * @return the last thing in the page history
   */
  @android.webkit.JavascriptInterface
  public String popScreenHistory(String refId) {
    if (isInactive())
      return null;
    return weakSurvey.get().popScreenHistory(refId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#hasSectionStack}
   *
   * @param refId An id that ties the javascript interface to a particular activity
   * @return whether the section state has history
   */
  @android.webkit.JavascriptInterface
  public boolean hasSectionStack(String refId) {
    //noinspection SimplifiableIfStatement
    if (isInactive())
      return false;
    return weakSurvey.get().hasSectionStack(refId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#popSectionStack}
   *
   * @param refId An id that ties the javascript interface to a particular activity
   * @return the last item in the state history
   */
  @android.webkit.JavascriptInterface
  public String popSectionStack(String refId) {
    if (isInactive())
      return null;
    return weakSurvey.get().popSectionStack(refId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#frameworkHasLoaded}
   *
   * @param refId   An id that ties the javascript interface to a particular activity
   * @param outcome whether the framework load was successful
   */
  @android.webkit.JavascriptInterface
  public void frameworkHasLoaded(String refId, boolean outcome) {
    if (isInactive())
      return;
    weakSurvey.get().frameworkHasLoaded(refId, outcome);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#ignoreAllChangesCompleted}
   *
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param instanceId the instance id for the row add/edit
   */
  @android.webkit.JavascriptInterface
  public void ignoreAllChangesCompleted(String refId, String instanceId) {
    if (isInactive())
      return;
    weakSurvey.get().ignoreAllChangesCompleted(refId, instanceId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#ignoreAllChangesFailed}
   *
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param instanceId the instance id for the row add/edit
   */
  @android.webkit.JavascriptInterface
  public void ignoreAllChangesFailed(String refId, String instanceId) {
    if (isInactive())
      return;
    weakSurvey.get().ignoreAllChangesFailed(refId, instanceId);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#saveAllChangesCompleted}
   *
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param instanceId the instance id for the row add/edit
   * @param asComplete whether to save as finalized or save as incomplete
   */
  @android.webkit.JavascriptInterface
  public void saveAllChangesCompleted(String refId, String instanceId, boolean asComplete) {
    if (isInactive())
      return;
    weakSurvey.get().saveAllChangesCompleted(refId, instanceId, asComplete);
  }

  /**
   * Thin wrapper for {@link OdkSurveyStateManagement#saveAllChangesFailed}
   *
   * @param refId      An id that ties the javascript interface to a particular activity
   * @param instanceId the instance id for the row add/edit
   */
  @android.webkit.JavascriptInterface
  public void saveAllChangesFailed(String refId, String instanceId) {
    if (isInactive())
      return;
    weakSurvey.get().saveAllChangesFailed(refId, instanceId);
  }
}