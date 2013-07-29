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
    // a relative path always beginning with ../../
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

  public void setInstanceId(String instanceId);

  public void setScreenPath(String setScreenPath);

  public boolean hasScreenHistory();

  public void clearScreenHistory();

  public String popScreenHistory();

  public void pushScreenHistory(String idx);

  public boolean hasSectionStack();

  public void clearSectionStack();

  public String popSectionStack();

  public void pushSectionStack(String idx);

  public void setAuxillaryHash(String hash);

  public void saveAllChangesCompleted(String tableId, String instanceId, boolean asComplete);

  public void saveAllChangesFailed(String tableId, String instanceId);

  public void ignoreAllChangesCompleted(String tableId, String instanceId);

  public void ignoreAllChangesFailed(String tableId, String instanceId);

  public String doAction(String page, String path, String action, JSONObject valueMap);

  public void swapToCustomView(View view);

  public void swapOffCustomView();

  public View getVideoLoadingProgressView();

  public Bitmap getDefaultVideoPoster();

  // for CopyExpansionFilesFragment
  public void expansionFilesCopied(String fragmentToShowNext);

  // for FormChooserListFragment
  public void chooseForm(Uri formUri);

  /**
   * Use the Activity implementation of this.
   *
   * @param r
   */
  public void runOnUiThread(Runnable r);
}
