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

import java.io.File;

import org.opendatakit.common.android.utilities.MediaUtils;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebLogger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Simple shim for media interactions.
 *
 * Called from javascript with: shim.doAction(promptPath, internalPromptContext,
 * "org.opendatakit.survey.android.activities.MediaDeleteImageActivity",
 * JSON.stringify({ appName: 'myApp', uriFragment: uriFromDatabase }));
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class MediaDeleteImageActivity extends Activity {
  private static final String t = "MediaDeleteImageActivity";
  private static final String APP_NAME = "appName";
  private static final String URI_FRAGMENT = "uriFragment";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    String appName = null;
    String uriFragmentToMedia = null;
    Bundle extras = getIntent().getExtras();
    if (extras != null) {
      appName = extras.getString(APP_NAME);
      uriFragmentToMedia = extras.getString(URI_FRAGMENT);
    }

    if (savedInstanceState != null) {
      appName = savedInstanceState.getString(APP_NAME);
      uriFragmentToMedia = savedInstanceState.getString(URI_FRAGMENT);
    }

    if (appName == null) {
      throw new IllegalArgumentException("Expected " + APP_NAME
            + " key in intent bundle. Not found.");
    }

    if (uriFragmentToMedia == null) {
        throw new IllegalArgumentException("Expected " + URI_FRAGMENT
            + " key in intent bundle. Not found.");
    }

    File f = ODKFileUtils.getAsFile(appName, uriFragmentToMedia);

    int del = MediaUtils.deleteImageFileFromMediaProvider(this, appName, f.getAbsolutePath());
    WebLogger.getLogger(appName).i(t, "Deleted " + del + " matching entries for " + URI_FRAGMENT + ": " + uriFragmentToMedia);

    Intent i = new Intent();

    i.putExtra(URI_FRAGMENT, uriFragmentToMedia);
    i.putExtra("deleteCount", del);
    setResult(Activity.RESULT_OK, i);
    finish();
  }
}
