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

package org.opendatakit.survey.android.activities;

import org.json.JSONObject;

import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;

/**
 * Interface that implements some of the shim.js callbacks from the WebKit.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public interface ODKActivity {

  public static class FrameworkFormPathInfo {
    // the FormsColumns.DATE field.
    public final Long lastModified;
    // the FormsColumns.FORM_PATH field
    // a relative path always beginning with ../
    public final String relativePath;

    FrameworkFormPathInfo(String relativePath, Long lastModified) {
      this.relativePath = relativePath;
      this.lastModified = lastModified;
    }
  };

  public FrameworkFormPathInfo getFrameworkFormPathInfo();

  public String getUrlBaseLocation(boolean ifChanged);

  public String getUrlLocationHash();

  public String getAppName();

  public String getUploadTableId();

  public String getActiveUser();

  /** for completing the uriFragment of the media attachments */
  public String getWebViewContentUri();

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

  public void setSessionVariable( String elementPath, String jsonValue );

  public String getSessionVariable( String elementPath );

  public void saveAllChangesCompleted(String instanceId, boolean asComplete);

  public void saveAllChangesFailed(String instanceId);

  public void ignoreAllChangesCompleted(String instanceId);

  public void ignoreAllChangesFailed(String instanceId);

  public String doAction(String page, String path, String action, JSONObject valueMap);

  public void swapToCustomView(View view);

  public void swapOffCustomView();

  public View getVideoLoadingProgressView();

  public Bitmap getDefaultVideoPoster();

  // for CopyExpansionFilesFragment
  public void initializationCompleted(String fragmentToShowNext);

  // for FormChooserListFragment
  public void chooseForm(Uri formUri);

  // for InstanceUploaderTableChooserListFragment
  public void chooseInstanceUploaderTable(String tableId);
  /**
   * Use the Activity implementation of this.
   *
   * @param r
   */
  public void runOnUiThread(Runnable r);
}
