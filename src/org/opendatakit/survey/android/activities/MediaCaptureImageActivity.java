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
import org.opendatakit.survey.android.R;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.widget.Toast;

/**
 * Simple shim for media interactions.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class MediaCaptureImageActivity extends Activity {

  private static final String t = "MediaCaptureImageActivity";

  private static final int ACTION_CODE = 1;
  private static final String MEDIA_CLASS = "image/";
  private static final String URI = "uri";
  private static final String CONTENT_TYPE = "contentType";

  private static final String TMP_EXTENSION = ".tmp.jpg";

  private static final String NEW_FILE = "newFile";
  private static final String HAS_LAUNCHED = "hasLaunched";
  private static final String AFTER_RESULT = "afterResult";
  private static final String ERROR_NO_FILE = "Media file does not exist! ";
  private static final String ERROR_COPY_FILE = "Media file copy failed! ";

  private String newFileBase = null;
  private String mediaPath = null;
  private boolean afterResult = false;
  private boolean hasLaunched = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (savedInstanceState != null) {
      mediaPath = savedInstanceState.getString(URI);
      hasLaunched = savedInstanceState.getBoolean(HAS_LAUNCHED);
      afterResult = savedInstanceState.getBoolean(AFTER_RESULT);
      newFileBase = savedInstanceState.getString(NEW_FILE);
    }

    if (mediaPath == null) {
      if (newFileBase == null) {
        throw new IllegalArgumentException("Expected " + NEW_FILE
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
      Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
      // workaround for image capture bug
      // create an empty file and pass filename to Camera app.
      if ( mediaPath == null ) {
        mediaPath = newFileBase + TMP_EXTENSION;
      }
      File mediaFile = new File(mediaPath);
      if (!mediaFile.exists()) {
        boolean success = false;
        String errorString = " Could not create: " + mediaPath;
        try {
          success = (mediaFile.getParentFile().exists() || mediaFile.getParentFile().mkdirs())
              && mediaFile.createNewFile();
        } catch (IOException e) {
          e.printStackTrace();
          errorString = e.toString();
        } finally {
          if (!success) {
            String err = getString(R.string.media_save_failed);
            Log.e(t, err + errorString);
            deleteMedia();
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
          }
        }
      }
      i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(mediaPath)));

      try {
        hasLaunched = true;
        startActivityForResult(i, ACTION_CODE);
      } catch (ActivityNotFoundException e) {
        String err = getString(R.string.activity_not_found,
            android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        Log.e(t, err);
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
    outState.putString(URI, mediaPath);
    outState.putString(NEW_FILE, newFileBase);
    outState.putBoolean(HAS_LAUNCHED, hasLaunched);
    outState.putBoolean(AFTER_RESULT, afterResult);
  }

  private void deleteMedia() {
    if (mediaPath == null) {
      return;
    }
    // get the file path and delete the file
    String path = mediaPath;
    // delete from media provider
    int del = MediaUtils.deleteImageFileFromMediaProvider(this, path);
    Log.i(t, "Deleted " + del + " rows from image media content provider");
  }

  private String getPathFromUri(Uri uri) {
    if (uri.toString().startsWith("file://")) {
      return uri.toString().substring(7);
    } else {
      String[] mediaProjection = { Images.Media.DATA };
      String mediaPath = null;
      Cursor c = null;
      try {
        c = getApplicationContext().getContentResolver().query(uri, mediaProjection, null, null,
            null);
        int column_index = c.getColumnIndexOrThrow(Images.Media.DATA);
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

    if (mediaPath == null) {
      // we are in trouble
      Log.e(t, "Unexpectedly null mediaPath!");
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    if (newFileBase == null) {
      // we are in trouble
      Log.e(t, "Unexpectedly null newFileBase!");
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    Uri mediaUri = Uri.fromFile(new File(mediaPath));
    // we never have to deal with deleting, as the Camera is overwriting
    // this...

    // get the file path and create a copy in the instance folder
    String binaryPath = getPathFromUri((Uri) mediaUri);
    String extension = binaryPath.substring(binaryPath.lastIndexOf("."));
    String destImagePath = newFileBase + extension;

    File source = new File(binaryPath);
    File sourceMedia = new File(destImagePath);
    try {
      FileUtils.copyFile(source, sourceMedia);
    } catch (IOException e) {
      Log.e(t, ERROR_COPY_FILE + sourceMedia.getAbsolutePath());
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
      Log.i(t, "Inserting IMAGE returned uri = " + MediaURI.toString());

      if (mediaPath != null) {
        deleteMedia();
      }
      mediaPath = sourceMedia.getAbsolutePath();
      Log.i(t, "Setting current answer to " + sourceMedia.getAbsolutePath());
    } else {
      if (mediaPath != null) {
        deleteMedia();
      }
      mediaPath = null;
      Log.e(t, "Inserting Image file FAILED");
    }

    /*
     * We saved the image to the instance directory. Verify that it is there...
     */
    returnResult();
    return;
  }

  private void returnResult() {
    File sourceMedia = (mediaPath != null) ? new File(mediaPath) : null;
    if (sourceMedia != null && sourceMedia.exists()) {
      Intent i = new Intent();
      i.putExtra(URI, FileProvider.getAsUrl(sourceMedia));
      String name = sourceMedia.getName();
      i.putExtra(CONTENT_TYPE, MEDIA_CLASS + name.substring(name.lastIndexOf(".") + 1));
      setResult(Activity.RESULT_OK, i);
      finish();
    } else {
      Log.e(t, ERROR_NO_FILE
          + ((mediaPath != null) ? sourceMedia.getAbsolutePath() : "null mediaPath"));
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
