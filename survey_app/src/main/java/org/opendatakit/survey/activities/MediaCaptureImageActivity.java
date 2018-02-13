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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Images;
import android.widget.Toast;
import org.opendatakit.activities.BaseActivity;
import org.opendatakit.consts.IntentConsts;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.survey.R;
import org.opendatakit.utilities.MediaUtils;
import org.opendatakit.utilities.ODKFileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Simple shim for media interactions.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class MediaCaptureImageActivity extends BaseActivity {
  private static final String t = "MediaCaptureImageActivity";

  protected static final int ACTION_CODE = 1;
  private static final String MEDIA_CLASS = "image/";

  protected static final String TMP_EXTENSION = ".tmp.jpg";

  private static final String URI_FRAGMENT_NEW_FILE_BASE = "uriFragmentNewFileBase";
  private static final String HAS_LAUNCHED = "hasLaunched";
  private static final String AFTER_RESULT = "afterResult";
  private static final String ERROR_NO_FILE = "Media file does not exist! ";
  private static final String ERROR_COPY_FILE = "Media file copy failed! ";

  protected String appName = null;
  protected String tableId = null;
  protected String instanceId = null;
  protected String uriFragmentNewFileBase = null;
  protected String uriFragmentToMedia = null;
  protected String currentUriFragment = null;
  protected boolean afterResult = false;
  protected boolean hasLaunched = false;
  protected Intent launchIntent = null;


  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle extras = getIntent().getExtras();
    if (extras != null) {
      appName = extras.getString(IntentConsts.INTENT_KEY_APP_NAME);
      tableId = extras.getString(IntentConsts.INTENT_KEY_TABLE_ID);
      instanceId = extras.getString(IntentConsts.INTENT_KEY_INSTANCE_ID);
      uriFragmentToMedia = extras.getString(IntentConsts.INTENT_KEY_URI_FRAGMENT);
      currentUriFragment = extras.getString(IntentConsts.INTENT_KEY_CURRENT_URI_FRAGMENT);
      hasLaunched = extras.getBoolean(HAS_LAUNCHED);
      afterResult = extras.getBoolean(AFTER_RESULT);
      uriFragmentNewFileBase = extras.getString(URI_FRAGMENT_NEW_FILE_BASE);
    }

    if (savedInstanceState != null) {
      appName = savedInstanceState.getString(IntentConsts.INTENT_KEY_APP_NAME);
      tableId = savedInstanceState.getString(IntentConsts.INTENT_KEY_TABLE_ID);
      instanceId = savedInstanceState.getString(IntentConsts.INTENT_KEY_INSTANCE_ID);
      uriFragmentToMedia = savedInstanceState.getString(IntentConsts.INTENT_KEY_URI_FRAGMENT);
      currentUriFragment = extras.getString(IntentConsts.INTENT_KEY_CURRENT_URI_FRAGMENT);
      hasLaunched = savedInstanceState.getBoolean(HAS_LAUNCHED);
      afterResult = savedInstanceState.getBoolean(AFTER_RESULT);
      uriFragmentNewFileBase = savedInstanceState.getString(URI_FRAGMENT_NEW_FILE_BASE);
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

    // On Nexus 6 running 6.0.1, the directory needs to exist before the capture.
    // need to ensure that the directory exists before we launch the camera app.
    if ( !ODKFileUtils.createFolder(ODKFileUtils.getInstanceFolder(appName, tableId, instanceId)) ) {
      Toast.makeText(this, R.string.media_save_failed, Toast.LENGTH_SHORT).show();
      // keep the image as a captured image so user can choose it.
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    if (uriFragmentToMedia == null) {
      if (uriFragmentNewFileBase == null) {
        throw new IllegalArgumentException("Expected " + URI_FRAGMENT_NEW_FILE_BASE
            + " key in intent bundle. Not found.");
      }
      afterResult = false;
      hasLaunched = false;
    }
  }
  
  @Override
  public String getAppName() {
    return appName;
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (afterResult) {
      // this occurs if we re-orient the phone during the save-recording
      // action
      returnResult();
    } else if (!hasLaunched && !afterResult) {
      Intent i = null;
      if (launchIntent == null) {
        i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
      } else {
        i = launchIntent;
      }
      // workaround for image capture bug
      // create an empty file and pass filename to Camera app.
      if (uriFragmentToMedia == null) {
        uriFragmentToMedia = uriFragmentNewFileBase + TMP_EXTENSION;
      }
      // to make the name unique...
      File mediaFile = ODKFileUtils.getRowpathFile(appName, tableId, instanceId, uriFragmentToMedia);
      if (!mediaFile.exists()) {
        boolean success = false;
        String errorString = " Could not create: " + mediaFile.getAbsolutePath();
        try {
          success = (mediaFile.getParentFile().exists() || mediaFile.getParentFile().mkdirs())
              && mediaFile.createNewFile();
        } catch (IOException e) {
          WebLogger.getLogger(appName).printStackTrace(e);
          errorString = e.toString();
        } finally {
          if (!success) {
            String err = getString(R.string.media_save_failed);
            WebLogger.getLogger(appName).e(t, err + errorString);
            deleteMedia();
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
          }
        }
      }
      i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, Uri.fromFile(mediaFile));

      try {
        hasLaunched = true;
        startActivityForResult(i, ACTION_CODE);
      } catch (ActivityNotFoundException e) {
        String intentActivityName = null;
        if (launchIntent != null && launchIntent.getComponent() != null ) {
          intentActivityName = launchIntent.getComponent().getClassName();
        }
        String err = getString(R.string.activity_not_found,
            android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        WebLogger.getLogger(appName).e(t, err);
        deleteMedia();
        Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
        setResult(Activity.RESULT_CANCELED);
        finish();
      }
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(IntentConsts.INTENT_KEY_APP_NAME, appName);
    outState.putString(IntentConsts.INTENT_KEY_TABLE_ID, tableId);
    outState.putString(IntentConsts.INTENT_KEY_INSTANCE_ID, instanceId);
    outState.putString(IntentConsts.INTENT_KEY_URI_FRAGMENT, uriFragmentToMedia);
    outState.putString(IntentConsts.INTENT_KEY_CURRENT_URI_FRAGMENT, currentUriFragment);
    outState.putString(URI_FRAGMENT_NEW_FILE_BASE, uriFragmentNewFileBase);
    outState.putBoolean(HAS_LAUNCHED, hasLaunched);
    outState.putBoolean(AFTER_RESULT, afterResult);
  }

  protected void deleteMedia() {
    if (uriFragmentToMedia == null) {
      return;
    }
    // get the file path and delete the file
    File f = ODKFileUtils.getRowpathFile(appName, tableId, instanceId, uriFragmentToMedia);
    String path = f.getAbsolutePath();
    // delete from media provider
    int del = MediaUtils.deleteImageFileFromMediaProvider(this, appName, path);
    WebLogger.getLogger(appName).i(t, "Deleted " + del + " rows from image media content provider");
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {

    if (resultCode == Activity.RESULT_CANCELED) {
      // request was canceled -- propagate
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    if (uriFragmentToMedia == null) {
      // we are in trouble
      WebLogger.getLogger(appName).e(t, "Unexpectedly null uriFragmentToMedia!");
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    if (uriFragmentNewFileBase == null) {
      // we are in trouble
      WebLogger.getLogger(appName).e(t, "Unexpectedly null newFileBase!");
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    File f = ODKFileUtils.getRowpathFile(appName, tableId, instanceId, uriFragmentToMedia);
    Uri mediaUri = Uri.fromFile(f);
    // we never have to deal with deleting, as the Camera is overwriting
    // this...

    // get the file path and create a copy in the instance folder
    String binaryPath = MediaUtils.getPathFromUri(this, (Uri)mediaUri, Images.Media.DATA);
    String extension = binaryPath.substring(binaryPath.lastIndexOf("."));

    File source = new File(binaryPath);
    File sourceMedia = ODKFileUtils.getRowpathFile(appName, tableId, instanceId, uriFragmentNewFileBase + extension);
    try {
      ODKFileUtils.copyFile(source, sourceMedia);
    } catch (IOException e) {
      WebLogger.getLogger(appName).e(t, ERROR_COPY_FILE + sourceMedia.getAbsolutePath());
      Toast.makeText(this, R.string.media_save_failed, Toast.LENGTH_SHORT).show();
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    if (sourceMedia.exists()) {
      // Add the copy to the content provier
      ContentValues values = new ContentValues(6);
      values.put(Audio.Media.TITLE, sourceMedia.getName());
      values.put(Audio.Media.DISPLAY_NAME, sourceMedia.getName());
      values.put(Audio.Media.DATE_ADDED, System.currentTimeMillis());
      values.put(Audio.Media.DATA, sourceMedia.getAbsolutePath());

      Uri MediaURI = getApplicationContext().getContentResolver().insert(
          Images.Media.EXTERNAL_CONTENT_URI, values);
      WebLogger.getLogger(appName).i(t, "Inserting IMAGE returned uri = " + MediaURI.toString());

      if (uriFragmentToMedia != null) {
        deleteMedia();
      }
      uriFragmentToMedia = ODKFileUtils.asRowpathUri(appName, tableId, instanceId, sourceMedia);
      WebLogger.getLogger(appName).i(t, "Setting current answer to " + sourceMedia.getAbsolutePath());
    } else {
      if (uriFragmentToMedia != null) {
        deleteMedia();
      }
      uriFragmentToMedia = null;
      WebLogger.getLogger(appName).e(t, "Inserting Image file FAILED");
    }

    /*
     * We saved the image to the instance directory. Verify that it is there...
     */
    returnResult();
    return;
  }

  protected void returnResult() {
    File sourceMedia = (uriFragmentToMedia != null) ?
        ODKFileUtils.getRowpathFile(appName, tableId, instanceId, uriFragmentToMedia) : null;
    if (sourceMedia != null && sourceMedia.exists()) {
      Intent i = new Intent();
      i.putExtra(IntentConsts.INTENT_KEY_URI_FRAGMENT, ODKFileUtils.asRowpathUri(appName, tableId, instanceId, sourceMedia));
      String name = sourceMedia.getName();
      i.putExtra(IntentConsts.INTENT_KEY_CONTENT_TYPE, MEDIA_CLASS + name.substring(name.lastIndexOf(".") + 1));
      setResult(Activity.RESULT_OK, i);
      finish();
    } else {
      WebLogger.getLogger(appName).e(t, ERROR_NO_FILE
          + ((uriFragmentToMedia != null) ? sourceMedia.getAbsolutePath() : "null mediaPath"));
      Toast.makeText(this, R.string.media_save_failed, Toast.LENGTH_SHORT).show();
      setResult(Activity.RESULT_CANCELED);
      finish();
    }
  }

  @Override
  public void finish() {
    hasLaunched = false;
    afterResult = true;
    super.finish();
  }

  @Override
  public void databaseAvailable() {
  }

  @Override
  public void databaseUnavailable() {
  }

}
