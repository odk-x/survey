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
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.opendatakit.consts.IntentConsts;
import org.opendatakit.activities.BaseActivity;
import org.opendatakit.utilities.MediaUtils;
import org.opendatakit.utilities.ODKFileUtils;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.survey.R;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.widget.Toast;

/**
 * Simple shim for media interactions.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class MediaCaptureVideoActivity extends BaseActivity {
  private static final String t = "MediaCaptureVideoActivity";

  private static final int ACTION_CODE = 1;
  private static final String MEDIA_CLASS = "video/";

  private static final String URI_FRAGMENT_NEW_FILE_BASE = "uriFragmentNewFileBase";
  private static final String HAS_LAUNCHED = "hasLaunched";
  private static final String AFTER_RESULT = "afterResult";
  private static final String ERROR_NO_FILE = "Media file does not exist! ";
  private static final String ERROR_COPY_FILE = "Media file copy failed! ";
  
  private static final String NEXUS7 = "Nexus 7";
  public static final int MEDIA_TYPE_IMAGE = 1;
  public static final int MEDIA_TYPE_VIDEO = 2;
  private Uri nexus7Uri;

  private String appName = null;
  private String tableId = null;
  private String instanceId = null;
  private String uriFragmentNewFileBase = null;
  private String uriFragmentToMedia = null;
  private boolean afterResult = false;
  private boolean hasLaunched = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle extras = getIntent().getExtras();
    if (extras != null) {
      appName = extras.getString(IntentConsts.INTENT_KEY_APP_NAME);
      tableId = extras.getString(IntentConsts.INTENT_KEY_TABLE_ID);
      instanceId = extras.getString(IntentConsts.INTENT_KEY_INSTANCE_ID);
      uriFragmentToMedia = extras.getString(IntentConsts.INTENT_KEY_URI_FRAGMENT);
      hasLaunched = extras.getBoolean(HAS_LAUNCHED);
      afterResult = extras.getBoolean(AFTER_RESULT);
      uriFragmentNewFileBase = extras.getString(URI_FRAGMENT_NEW_FILE_BASE);
    }

    if (savedInstanceState != null) {
      appName = savedInstanceState.getString(IntentConsts.INTENT_KEY_APP_NAME);
      tableId = savedInstanceState.getString(IntentConsts.INTENT_KEY_TABLE_ID);
      instanceId = savedInstanceState.getString(IntentConsts.INTENT_KEY_INSTANCE_ID);
      uriFragmentToMedia = savedInstanceState.getString(IntentConsts.INTENT_KEY_URI_FRAGMENT);
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
      Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
      // to make the name unique...
      File f = ODKFileUtils.getRowpathFile(appName, tableId, instanceId,
              (uriFragmentToMedia == null ? uriFragmentNewFileBase : uriFragmentToMedia));
      int idx = f.getName().lastIndexOf('.');
      if (idx == -1) {
        i.putExtra(Video.Media.DISPLAY_NAME, f.getName());
      } else {
        i.putExtra(Video.Media.DISPLAY_NAME, f.getName().substring(0, idx));
      }
      
      // Need to have this ugly code to account for 
      // a bug in the Nexus 7 on 4.3 not returning the mediaUri in the data
      // of the intent - using the MediaStore.EXTRA_OUTPUT to get the data
      // Have it saving to an intermediate location instead of final destination
      // to allow the current location to catch issues with the intermediate file
      WebLogger.getLogger(appName).i(t, "The build of this device is " + android.os.Build.MODEL);
      if (NEXUS7.equals(android.os.Build.MODEL) && Build.VERSION.SDK_INT == 18) {
        nexus7Uri = getOutputMediaFileUri(MEDIA_TYPE_VIDEO);  
        i.putExtra(MediaStore.EXTRA_OUTPUT, nexus7Uri);
      }

      try {
        hasLaunched = true;
        startActivityForResult(i, ACTION_CODE);
      } catch (ActivityNotFoundException e) {
        String err = getString(R.string.activity_not_found, MediaStore.ACTION_VIDEO_CAPTURE);
        WebLogger.getLogger(appName).e(t, err);
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
    outState.putString(URI_FRAGMENT_NEW_FILE_BASE, uriFragmentNewFileBase);
    outState.putBoolean(HAS_LAUNCHED, hasLaunched);
    outState.putBoolean(AFTER_RESULT, afterResult);
  }

  private void deleteMedia() {
    if (uriFragmentToMedia == null) {
      return;
    }
    // get the file path and delete the file
    File f = ODKFileUtils.getRowpathFile(appName, tableId, instanceId, uriFragmentToMedia);
    String path = f.getAbsolutePath();
    // delete from media provider
    int del = MediaUtils.deleteVideoFileFromMediaProvider(this, appName, path);
    WebLogger.getLogger(appName).i(t, "Deleted " + del + " rows from video media content provider");
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {

    if (resultCode == Activity.RESULT_CANCELED) {
      // request was canceled -- propagate
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    Uri mediaUri = intent.getData();

    // it is unclear whether getData() always returns a value or if
    // getDataString() does...
    String str = intent.getDataString();
    if (mediaUri == null && str != null) {
      WebLogger.getLogger(appName).w(t, "Attempting to work around null mediaUri");
      mediaUri = Uri.parse(str);
    }

    if (mediaUri == null) {
      // we are in trouble
      WebLogger.getLogger(appName).e(t, "No uri returned from ACTION_CAPTURE_VIDEO!");
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    // Remove the current media.
    deleteMedia();

    // get the file path and create a copy in the instance folder
    String binaryPath = MediaUtils.getPathFromUri(this, (Uri) mediaUri, Video.Media.DATA);
    File source = new File(binaryPath);
    String extension = binaryPath.substring(binaryPath.lastIndexOf("."));

    if (uriFragmentToMedia == null) {
      // use the newFileBase as a starting point...
      uriFragmentToMedia = uriFragmentNewFileBase + extension;
    }

    // adjust the mediaPath (destination) to have the same extension
    // and delete any existing file.
    File f = ODKFileUtils.getRowpathFile(appName, tableId, instanceId, uriFragmentToMedia);
    File sourceMedia = new File(f.getParentFile(), f.getName().substring(0,
        f.getName().lastIndexOf('.'))
        + extension);
    uriFragmentToMedia = ODKFileUtils.asRowpathUri(appName, tableId, instanceId, sourceMedia);
    deleteMedia();

    try {
      ODKFileUtils.copyFile(source, sourceMedia);
    } catch (IOException e) {
      WebLogger.getLogger(appName).e(t, ERROR_COPY_FILE + sourceMedia.getAbsolutePath());
      Toast.makeText(this, R.string.media_save_failed, Toast.LENGTH_SHORT).show();
      deleteMedia();
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    if (sourceMedia.exists()) {
      // Add the copy to the content provier
      ContentValues values = new ContentValues(6);
      values.put(Video.Media.TITLE, sourceMedia.getName());
      values.put(Video.Media.DISPLAY_NAME, sourceMedia.getName());
      values.put(Video.Media.DATE_ADDED, System.currentTimeMillis());
      values.put(Video.Media.DATA, sourceMedia.getAbsolutePath());

      Uri MediaURI = getApplicationContext().getContentResolver().insert(
          Video.Media.EXTERNAL_CONTENT_URI, values);
      WebLogger.getLogger(appName).i(t, "Inserting VIDEO returned uri = " + MediaURI.toString());
      uriFragmentToMedia = ODKFileUtils.asRowpathUri(appName, tableId, instanceId, sourceMedia);
      WebLogger.getLogger(appName).i(t, "Setting current answer to " + sourceMedia.getAbsolutePath());
      
      // Need to have this ugly code to account for 
      // a bug in the Nexus 7 on 4.3 not returning the mediaUri in the data
      // of the intent - uri in this case is a file 
      int delCount = 0;
      if (NEXUS7.equals(android.os.Build.MODEL) && Build.VERSION.SDK_INT == 18) {
        File fileToDelete = new File(mediaUri.getPath());
        delCount = fileToDelete.delete() ? 1 : 0;
      } else {
        delCount = getApplicationContext().getContentResolver().delete(mediaUri, null, null);
      }
      WebLogger.getLogger(appName).i(t, "Deleting original capture of file: " + mediaUri.toString() + " count: " + delCount);
    } else {
      WebLogger.getLogger(appName).e(t, "Inserting Video file FAILED");
    }

    /*
     * We saved the audio to the instance directory. Verify that it is there...
     */
    returnResult();
    return;
  }

  private void returnResult() {
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

  /*
   * Create a file Uri for saving an image or video 
   * For Nexus 7 fix ... 
   * See http://developer.android.com/guide/topics/media/camera.html for more info
   */
  private Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
  }

  /*
   *  Create a File for saving an image or video 
   *  For Nexus 7 fix ... 
   *  See http://developer.android.com/guide/topics/media/camera.html for more info
   */
  private File getOutputMediaFile(int type) {
      // To be safe, you should check that the SDCard is mounted
      // using Environment.getExternalStorageState() before doing this.

      File mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
      // This location works best if you want the created images to be shared
      // between applications and persist after your app has been uninstalled.

      // Create the storage directory if it does not exist
      if (! mediaStorageDir.exists()){
          if (! mediaStorageDir.mkdirs()){
              WebLogger.getLogger(appName).d(t, "failed to create directory");
              return null;
          }
      }

      // Create a media file name
      String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSSZ", Locale.US).format(new Date());
      File mediaFile;
      if (type == MEDIA_TYPE_IMAGE){
          mediaFile = new File(mediaStorageDir.getPath() + File.separator +
          "IMG_"+ timeStamp + ".jpg");
      } else if(type == MEDIA_TYPE_VIDEO) {
          mediaFile = new File(mediaStorageDir.getPath() + File.separator +
          "VID_"+ timeStamp + ".mp4");
      } else {
          return null;
      }

      return mediaFile;
  }

  @Override
  public void databaseAvailable() {
  }

  @Override
  public void databaseUnavailable() {
  }

}
