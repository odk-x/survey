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
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.opendatakit.common.android.provider.FileProvider;
import org.opendatakit.common.android.utilities.MediaUtils;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.R;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.widget.Toast;

/**
 * Simple shim for media interactions.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class MediaCaptureVideoActivity extends Activity {
  private static final String t = "MediaCaptureVideoActivity";

  private static final int ACTION_CODE = 1;
  private static final String MEDIA_CLASS = "video/";
  private static final String APP_NAME = "appName";
  private static final String URI_FRAGMENT = "uriFragment";
  private static final String CONTENT_TYPE = "contentType";

  private static final String URI_FRAGMENT_NEW_FILE_BASE = "uriFragmentNewFileBase";
  private static final String HAS_LAUNCHED = "hasLaunched";
  private static final String AFTER_RESULT = "afterResult";
  private static final String ERROR_NO_FILE = "Media file does not exist! ";
  private static final String ERROR_COPY_FILE = "Media file copy failed! ";

  private String appName = null;
  private String uriFragmentNewFileBase = null;
  private String uriFragmentToMedia = null;
  private boolean afterResult = false;
  private boolean hasLaunched = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle extras = getIntent().getExtras();
    if (extras != null) {
      appName = extras.getString(APP_NAME);
      uriFragmentToMedia = extras.getString(URI_FRAGMENT);
      hasLaunched = extras.getBoolean(HAS_LAUNCHED);
      afterResult = extras.getBoolean(AFTER_RESULT);
      uriFragmentNewFileBase = extras.getString(URI_FRAGMENT_NEW_FILE_BASE);
    }

    if (savedInstanceState != null) {
      appName = savedInstanceState.getString(APP_NAME);
      uriFragmentToMedia = savedInstanceState.getString(URI_FRAGMENT);
      hasLaunched = savedInstanceState.getBoolean(HAS_LAUNCHED);
      afterResult = savedInstanceState.getBoolean(AFTER_RESULT);
      uriFragmentNewFileBase = savedInstanceState.getString(URI_FRAGMENT_NEW_FILE_BASE);
    }

    if (appName == null) {
      throw new IllegalArgumentException("Expected " + APP_NAME
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
  protected void onResume() {
    super.onResume();

    if (afterResult) {
      // this occurs if we re-orient the phone during the save-recording
      // action
      returnResult();
    } else if (!hasLaunched && !afterResult) {
      Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
      // to make the name unique...
      File f = FileProvider.getAsFile(this,
          FileProvider.getAsUri(this, appName, (uriFragmentToMedia == null ? uriFragmentNewFileBase : uriFragmentToMedia)));
      int idx = f.getName().lastIndexOf('.');
      if (idx == -1) {
        i.putExtra(Video.Media.DISPLAY_NAME, f.getName());
      } else {
        i.putExtra(Video.Media.DISPLAY_NAME, f.getName().substring(0, idx));
      }

      try {
        hasLaunched = true;
        startActivityForResult(i, ACTION_CODE);
      } catch (ActivityNotFoundException e) {
        String err = getString(R.string.activity_not_found, MediaStore.ACTION_VIDEO_CAPTURE);
        Log.e(t, err);
        Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
        setResult(Activity.RESULT_CANCELED);
        finish();
      }
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(APP_NAME, appName);
    outState.putString(URI_FRAGMENT, uriFragmentToMedia);
    outState.putString(URI_FRAGMENT_NEW_FILE_BASE, uriFragmentNewFileBase);
    outState.putBoolean(HAS_LAUNCHED, hasLaunched);
    outState.putBoolean(AFTER_RESULT, afterResult);
  }

  private void deleteMedia() {
    if (uriFragmentToMedia == null) {
      return;
    }
    // get the file path and delete the file
    File f = FileProvider.getAsFile(this,
        FileProvider.getAsUri(this, appName, uriFragmentToMedia));
    String path = f.getAbsolutePath();
    // delete from media provider
    int del = MediaUtils.deleteVideoFileFromMediaProvider(this, path);
    Log.i(t, "Deleted " + del + " rows from video media content provider");
  }

  private String getPathFromUri(Uri uri) {
    if (uri.toString().startsWith("file://")) {
      return uri.toString().substring(7);
    } else {
      String[] mediaProjection = { Video.Media.DATA };
      String mediaPath = null;
      Cursor c = null;
      try {
        c = getApplicationContext().getContentResolver().query(uri, mediaProjection, null, null,
            null);
        int column_index = c.getColumnIndexOrThrow(Video.Media.DATA);
        if (c.getCount() > 0) {
          c.moveToFirst();
          mediaPath = c.getString(column_index);
        }
        return mediaPath;
      } finally {
        if (c != null) {
          c.close();
        }
      }
    }
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
      Log.w(t, "Attempting to work around null mediaUri");
      mediaUri = Uri.parse(str);
    }

    if (mediaUri == null) {
      // we are in trouble
      Log.e(t, "No uri returned from ACTION_CAPTURE_VIDEO!");
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    // Remove the current media.
    deleteMedia();

    // get the file path and create a copy in the instance folder
    String binaryPath = getPathFromUri((Uri) mediaUri);
    File source = new File(binaryPath);
    String extension = binaryPath.substring(binaryPath.lastIndexOf("."));

    if (uriFragmentToMedia == null) {
      // use the newFileBase as a starting point...
      uriFragmentToMedia = uriFragmentNewFileBase + extension;
    }

    // adjust the mediaPath (destination) to have the same extension
    // and delete any existing file.
    File f = FileProvider.getAsFile(this,
        FileProvider.getAsUri(this, appName, uriFragmentToMedia));
    File sourceMedia = new File(f.getParentFile(), f.getName().substring(0,
        f.getName().lastIndexOf('.'))
        + extension);
    uriFragmentToMedia = ODKFileUtils.asUriFragment(appName,  sourceMedia);
    deleteMedia();

    try {
      FileUtils.copyFile(source, sourceMedia);
    } catch (IOException e) {
      Log.e(t, ERROR_COPY_FILE + sourceMedia.getAbsolutePath());
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
      Log.i(t, "Inserting VIDEO returned uri = " + MediaURI.toString());
      uriFragmentToMedia = ODKFileUtils.asUriFragment(appName,  sourceMedia);
      Log.i(t, "Setting current answer to " + sourceMedia.getAbsolutePath());

      int delCount = getApplicationContext().getContentResolver().delete(mediaUri, null, null);
      Log.i(t, "Deleting original capture of file: " + mediaUri.toString() + " count: " + delCount);
    } else {
      Log.e(t, "Inserting Video file FAILED");
    }

    /*
     * We saved the audio to the instance directory. Verify that it is there...
     */
    returnResult();
    return;
  }

  private void returnResult() {
    File sourceMedia = (uriFragmentToMedia != null) ? FileProvider.getAsFile(this,
        FileProvider.getAsUri(this, appName, uriFragmentToMedia)) : null;
    if (sourceMedia != null && sourceMedia.exists()) {
      Intent i = new Intent();
      i.putExtra(URI_FRAGMENT, ODKFileUtils.asUriFragment(appName, sourceMedia));
      String name = sourceMedia.getName();
      i.putExtra(CONTENT_TYPE, MEDIA_CLASS + name.substring(name.lastIndexOf(".") + 1));
      setResult(Activity.RESULT_OK, i);
      finish();
    } else {
      Log.e(t, ERROR_NO_FILE
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

}
