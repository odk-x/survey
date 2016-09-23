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

import java.io.File;

import org.opendatakit.consts.IntentConsts;
import org.opendatakit.activities.BaseActivity;
import org.opendatakit.utilities.MediaUtils;
import org.opendatakit.utilities.ODKFileUtils;
import org.opendatakit.logging.WebLogger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Simple shim for media interactions.
 *
 * Called from javascript with: odkCommon.doAction(dispatchString,
 * "org.opendatakit.survey.activities.MediaDeleteImageActivity",
 * JSON.stringify({ appName: 'myApp', uriFragment: uriFromDatabase }));
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class MediaDeleteImageActivity extends BaseActivity {
  private static final String t = "MediaDeleteImageActivity";

  private String appName = null;
  private String tableId = null;
  private String instanceId = null;
  private String uriFragmentToMedia = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle extras = getIntent().getExtras();
    if (extras != null) {
      appName = extras.getString(IntentConsts.INTENT_KEY_APP_NAME);
      tableId = extras.getString(IntentConsts.INTENT_KEY_TABLE_ID);
      instanceId = extras.getString(IntentConsts.INTENT_KEY_INSTANCE_ID);
      uriFragmentToMedia = extras.getString(IntentConsts.INTENT_KEY_URI_FRAGMENT);
    }

    if (savedInstanceState != null) {
      appName = savedInstanceState.getString(IntentConsts.INTENT_KEY_APP_NAME);
      tableId = savedInstanceState.getString(IntentConsts.INTENT_KEY_TABLE_ID);
      instanceId = savedInstanceState.getString(IntentConsts.INTENT_KEY_INSTANCE_ID);
      uriFragmentToMedia = savedInstanceState.getString(IntentConsts.INTENT_KEY_URI_FRAGMENT);
    }

    if (appName == null) {
      throw new IllegalArgumentException("Expected " + IntentConsts.INTENT_KEY_APP_NAME
            + " key in intent bundle. Not found.");
    }

    if (tableId == null) {
      throw new IllegalArgumentException("Expected " + IntentConsts.INTENT_KEY_TABLE_ID
              + " key in intent bundle. Not found.");
    }
    if (instanceId == null) {
      throw new IllegalArgumentException("Expected " + IntentConsts.INTENT_KEY_INSTANCE_ID
              + " key in intent bundle. Not found.");
    }

    if (uriFragmentToMedia == null) {
        throw new IllegalArgumentException("Expected " + IntentConsts.INTENT_KEY_URI_FRAGMENT
            + " key in intent bundle. Not found.");
    }

    File f = ODKFileUtils.getRowpathFile(appName, tableId, instanceId, uriFragmentToMedia);

    int del = MediaUtils.deleteImageFileFromMediaProvider(this, appName, f.getAbsolutePath());
    WebLogger.getLogger(appName).i(t, "Deleted " + del + " matching entries for " + IntentConsts.INTENT_KEY_URI_FRAGMENT + ": " + uriFragmentToMedia);

    Intent i = new Intent();

    i.putExtra(IntentConsts.INTENT_KEY_URI_FRAGMENT, uriFragmentToMedia);
    i.putExtra("deleteCount", del);
    setResult(Activity.RESULT_OK, i);
    finish();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(IntentConsts.INTENT_KEY_APP_NAME, appName);
    outState.putString(IntentConsts.INTENT_KEY_TABLE_ID, tableId);
    outState.putString(IntentConsts.INTENT_KEY_INSTANCE_ID, instanceId);
    outState.putString(IntentConsts.INTENT_KEY_URI_FRAGMENT, uriFragmentToMedia);
  }

  @Override
  public String getAppName() {
    return appName;
  }

  @Override
  public void databaseAvailable() {
  }

  @Override
  public void databaseUnavailable() {
  }

}
