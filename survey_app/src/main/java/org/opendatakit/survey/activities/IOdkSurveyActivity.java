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

package org.opendatakit.survey.activities;

import android.net.Uri;
import org.opendatakit.activities.IOdkCommonActivity;
import org.opendatakit.activities.IOdkDataActivity;

/**
 * Interface that implements the survey-specific odkSurvey.js callbacks from the WebKit.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public interface IOdkSurveyActivity extends IOdkCommonActivity, IOdkDataActivity {

  /**
   * Gets the base uri for the web view
   * @param ifChanged unused
   * @return the uri
   */
  String getUrlBaseLocation(boolean ifChanged);

  /**
   * Clears the extra stuff after the # in the url
   */
  void clearAuxillaryHash();

  /**
   * Gets the full hash (stuff after the #) from the url, including the auxillary hash
   * @return the full hash containing the form path, instance id, screen path and auxillary hash
   */
  String getUrlLocationHash();

  /**
   * Returns the refId for the activity
   * @return An id that ties the javascript interface to a particular activity
   */
  String getRefId();

  /**
   * If refId is null, clears the instanceId. If refId matches the current refId, sets the
   * instanceId.
   *
   * @param instanceId the instance id for the row add/edit
   */
  void setInstanceId(String instanceId);

  /**
   * Get the instanceId for this web page. Returns null if the refId does not match.
   *
   * @return the instance id if the refId is valid or null
   */
  String getInstanceId();

  /**
   * Pushes the current screen state into the history
   */
  void pushSectionScreenState();

  /**
   * Updates the current screen state to the new parameters
   * @param screenPath the screen path to be updated in MainMenuActivity
   * @param state      the state to be updated in MainMenuActivity
   */
  void setSectionScreenState(String screenPath, String state);

  /**
   * Clears the current screen state
   */
  void clearSectionScreenState();

  /**
   * Returns the current screen state
   * @return the current screen state
   */
  String getControllerState();

  /**
   * Returns the current screen path
   * @return the current screen path
   */
  String getScreenPath();

  /**
   * Returns whether there is screen history
   * @return whether the screen has history
   */
  boolean hasScreenHistory();

  /**
   * Returns and removes the last thing from the screen history
   * @return the popped history item
   */
  String popScreenHistory();

  /**
   * Returns whether there is a stack of screens
   * @return whether there is a stack of screen objects with histories
   */
  boolean hasSectionStack();

  /**
   * Returns and removes the last screen from the stack
   * @return the popped screen item
   */
  String popSectionStack();

  /**
   * Saves the changes as completed and finishes the activity
   * @param instanceId the instance id for the row add/edit
   * @param asComplete whether to save as finalized or save as incomplete
   */
  void saveAllChangesCompleted(String instanceId, boolean asComplete);

  /**
   * Saves the changes as failed and finishes the activity
   * @param instanceId the instance id for the row add/edit
   */
  void saveAllChangesFailed(String instanceId);

  /**
   * Ignores all changes and doesn't save anything, then finishes
   * @param instanceId the instance id for the row add/edit
   */
  void ignoreAllChangesCompleted(String instanceId);

  /**
   * Ignores all changes and doesn't save anything
   * @param instanceId the instance id for the row add/edit
   */
  void ignoreAllChangesFailed(String instanceId);

  /**
   * Launches an activity to edit the row using the given form
   * for FormChooserListFragment
   * @param formUri the uri of the form to use to edit the row
   */
  void chooseForm(Uri formUri);

  /**
   * Saves the checkpoint and finishes the activity
   */
  void saveAllAsIncompleteThenPopBackStack();

  /**
   * Resolves all checkpoints then finishes
   */
  void resolveAllCheckpointsThenPopBackStack();
}
