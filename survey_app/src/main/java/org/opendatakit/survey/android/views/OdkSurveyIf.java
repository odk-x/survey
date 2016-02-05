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

import java.lang.ref.WeakReference;

/**
 * The class mapped to 'odkSurvey' in the Javascript
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class OdkSurveyIf {

  public static final String t = "OdkSurveyIf";

  private WeakReference<OdkSurvey> weakShim;

  OdkSurveyIf(OdkSurvey odkData) {
    weakShim = new WeakReference<OdkSurvey>(odkData);
  }

  private boolean isInactive() {
    return (weakShim.get() == null) || (weakShim.get().isInactive());
  }

  /**
   * Clear the auxillary hash. The auxillary hash is used to pass initialization
   * parameters into a form. Once those parameters have been safely updated to the
   * database or stored in session variables, ODK Survey will clear them so that
   * the form logic can update or revise their values.
   */
  @android.webkit.JavascriptInterface
  public void clearAuxillaryHash() {
    if (isInactive()) return;
    weakShim.get().clearAuxillaryHash();
  }

  /**
   * Clear the instanceId. The ODK Survey webpage is no longer associated with a
   * specific instanceId or rowId.
   *
   * @param refId
   */
  @android.webkit.JavascriptInterface
  public void clearInstanceId(String refId) {
    if (isInactive()) return;
    weakShim.get().clearInstanceId(refId);
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
    if (isInactive()) return;
    weakShim.get().setInstanceId(refId, instanceId);
  }

  /**
   * Get the instanceId for this web page.
   * Returns null if the refId does not match.
   *
   * @param refId
   * @return
   */
  @android.webkit.JavascriptInterface
  public String getInstanceId(String refId) {
    if (isInactive()) return null;
    return weakShim.get().getInstanceId(refId);
  }

  /**
   *
   * @param refId
   */
  @android.webkit.JavascriptInterface
  public void pushSectionScreenState(String refId) {
    if (isInactive()) return;
    weakShim.get().pushSectionScreenState(refId);
  }

  /**
   *
   * @param refId
   * @param screenPath
   * @param state
   */
  @android.webkit.JavascriptInterface
  public void setSectionScreenState(String refId, String screenPath, String state) {
    if (isInactive()) return;
    weakShim.get().setSectionScreenState(refId, screenPath, state);
  }

  /**
   *
   * @param refId
   */
  @android.webkit.JavascriptInterface
  public void clearSectionScreenState(String refId) {
    if (isInactive()) return;
    weakShim.get().clearSectionScreenState(refId);
  }

  @android.webkit.JavascriptInterface
  public String getControllerState(String refId) {
    if (isInactive()) return null;
    return weakShim.get().getControllerState(refId);
  }

  @android.webkit.JavascriptInterface
  public String getScreenPath(String refId) {
    if (isInactive()) return null;
    return weakShim.get().getScreenPath(refId);
  }

  @android.webkit.JavascriptInterface
  public boolean hasScreenHistory(String refId) {
    if (isInactive()) return false;
    return weakShim.get().hasScreenHistory(refId);
  }

  @android.webkit.JavascriptInterface
  public String popScreenHistory(String refId) {
    if (isInactive()) return null;
    return weakShim.get().popScreenHistory(refId);
  }

  @android.webkit.JavascriptInterface
  public boolean hasSectionStack(String refId) {
    if (isInactive()) return false;
    return weakShim.get().hasSectionStack(refId);
  }

  @android.webkit.JavascriptInterface
  public String popSectionStack(String refId) {
    if (isInactive()) return null;
    return weakShim.get().popSectionStack(refId);
  }

  @android.webkit.JavascriptInterface
  public void frameworkHasLoaded(String refId, boolean outcome) {
    if (isInactive()) return;
    weakShim.get().frameworkHasLoaded(refId, outcome);
  }

  @android.webkit.JavascriptInterface
  public void ignoreAllChangesCompleted(String refId, String instanceId) {
    if (isInactive()) return;
    weakShim.get().ignoreAllChangesCompleted(refId, instanceId);
  }

  @android.webkit.JavascriptInterface
  public void ignoreAllChangesFailed(String refId, String instanceId) {
    if (isInactive()) return;
    weakShim.get().ignoreAllChangesFailed(refId, instanceId);
  }

  @android.webkit.JavascriptInterface
  public void saveAllChangesCompleted(String refId, String instanceId, boolean asComplete) {
    if (isInactive()) return;
    weakShim.get().saveAllChangesCompleted(refId, instanceId, asComplete);
  }

  @android.webkit.JavascriptInterface
  public void saveAllChangesFailed(String refId, String instanceId) {
    if (isInactive()) return;
    weakShim.get().saveAllChangesFailed(refId, instanceId);
 }
}