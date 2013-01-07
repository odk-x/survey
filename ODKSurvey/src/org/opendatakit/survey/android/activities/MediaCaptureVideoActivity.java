/*
 * Copyright (C) 2012 University of Washington
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
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.provider.FileProvider;
import org.opendatakit.survey.android.utilities.MediaUtils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
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
	private static final String URI = "uri";
	private static final String CONTENT_TYPE = "contentType";

	private static final String HAS_LAUNCHED = "hasLaunched";
	private static final String AFTER_RESULT = "afterResult";
	private static final String EXTENSION = ".3gpp";
	private static final String ERROR_NO_FILE = "Media file does not exist! ";
	private static final String ERROR_COPY_FILE = "Media file copy failed! ";

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
		}

		if (mediaPath == null) {
			mediaPath = MainMenuActivity.getInstanceFilePath(EXTENSION);
			afterResult = false;
			hasLaunched = false;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (afterResult) {
			// this occurs if we re-orient the phone during the save-recording action
			returnResult();
		} else if (!hasLaunched && !afterResult) {
			Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
			// to make the name unique...
			File f = new File(mediaPath);
			i.putExtra(Video.Media.DISPLAY_NAME, f.getName().substring(0,f.getName().lastIndexOf('.')));

			try {
				hasLaunched = true;
				startActivityForResult(i, ACTION_CODE);
			} catch (ActivityNotFoundException e) {
				String err = getString(R.string.activity_not_found,
						MediaStore.ACTION_VIDEO_CAPTURE);
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
		outState.putString(URI, mediaPath);
		outState.putBoolean(HAS_LAUNCHED, hasLaunched);
    	outState.putBoolean(AFTER_RESULT, afterResult);
	}

	private void deleteMedia() {
		// get the file path and delete the file
		String path = mediaPath;
		// delete from media provider
		int del = MediaUtils.deleteVideoFileFromMediaProvider(path);
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
				c = getApplicationContext().getContentResolver().query(uri,
						mediaProjection, null, null, null);
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

		if ( mediaPath == null ) {
			// we are in trouble
			Log.e(t, "Unexpectedly null mediaPath!");
			setResult(Activity.RESULT_CANCELED);
			finish();
			return;
		}

		Uri mediaUri = intent.getData();

		// it is unclear whether getData() always returns a value or if getDataString() does...
		String str = intent.getDataString();
		if ( mediaUri == null && str != null ) {
			Log.w(t, "Attempting to work around null mediaUri");
			mediaUri = Uri.parse(str);
		}

		if ( mediaUri == null ) {
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

		// adjust the mediaPath (destination) to have the same extension
		// and delete any existing file.
		File f = new File(mediaPath);
		File sourceMedia = new File(f.getParentFile(), f.getName().substring(0,f.getName().lastIndexOf('.')) + extension);
		mediaPath = sourceMedia.getAbsolutePath();
		deleteMedia();

		try {
			FileUtils.copyFile(source, sourceMedia);
		} catch (IOException e) {
			Log.e(t, ERROR_COPY_FILE + sourceMedia.getAbsolutePath());
			Toast.makeText(this, R.string.media_save_failed, Toast.LENGTH_SHORT)
					.show();
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
			mediaPath = sourceMedia.getAbsolutePath();
			Log.i(t,
					"Setting current answer to "
							+ sourceMedia.getAbsolutePath());

			int delCount = getApplicationContext().getContentResolver().delete(
					mediaUri, null, null);
			Log.i(t,
					"Deleting original capture of file: " + mediaUri.toString()
							+ " count: " + delCount);
		} else {
			Log.e(t, "Inserting Video file FAILED");
		}

		/*
		 * We saved the audio to the instance directory. Verify that it is
		 * there...
		 */
		returnResult();
		return;
	}

	private void returnResult() {
		File sourceMedia = (mediaPath != null) ? new File(mediaPath) : null;
		if ( sourceMedia != null && sourceMedia.exists() ) {
			Intent i = new Intent();
			i.putExtra(URI, FileProvider.getAsUrl(sourceMedia));
			String name = sourceMedia.getName();
			i.putExtra(CONTENT_TYPE, MEDIA_CLASS + name.substring(name.lastIndexOf(".") + 1));
			setResult(Activity.RESULT_OK, i);
			finish();
		} else {
			Log.e(t, ERROR_NO_FILE + ((mediaPath != null) ? sourceMedia.getAbsolutePath() : "null mediaPath"));
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
