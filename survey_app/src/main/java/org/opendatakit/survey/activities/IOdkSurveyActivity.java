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

  public String getUrlBaseLocation(boolean ifChanged);

  public void clearAuxillaryHash();

  public String getUrlLocationHash();

  public String getUploadTableId();

  public String getRefId();

  public void setInstanceId(String instanceId);

  public String getInstanceId();

  public void pushSectionScreenState();

  public void setSectionScreenState(String screenPath, String state);

  public void clearSectionScreenState();

  public String getControllerState();

  public String getScreenPath();

  public boolean hasScreenHistory();

  public String popScreenHistory();

  public boolean hasSectionStack();

  public String popSectionStack();

  public void saveAllChangesCompleted(String instanceId, boolean asComplete);

  public void saveAllChangesFailed(String instanceId);

  public void ignoreAllChangesCompleted(String instanceId);

  public void ignoreAllChangesFailed(String instanceId);

  // for FormChooserListFragment
  public void chooseForm(Uri formUri);

  // for back press suppression
  // trigger save...
  public void saveAllAsIncompleteThenPopBackStack();

  // trigger resolve...
  public void resolveAllCheckpointsThenPopBackStack();
}
