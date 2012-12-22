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
import android.provider.MediaStore.Audio;
import android.util.Log;
import android.widget.Toast;

/**
 * Simple shim for media interactions.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class MediaCaptureAudioActivity extends Activity {
	private static final String t = "MediaCaptureAudioActivity";

	private static final int ACTION_CODE = 1;
	private static final String MEDIA_CLASS = "audio/";
	private static final String URI = "uri";
	private static final String CONTENT_TYPE = "contentType";

	private static final String HAS_LAUNCHED = "hasLaunched";
	private static final String EXTENSION = ".3gpp";
	private static final String ERROR_NO_FILE = "Media file does not exist! ";
	private static final String ERROR_COPY_FILE = "Media file copy failed! ";

	private String mediaPath = null;
	private boolean hasLaunched = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			mediaPath = savedInstanceState.getString(URI);
			hasLaunched = savedInstanceState.getBoolean(HAS_LAUNCHED);
		}

		if (mediaPath == null) {
			mediaPath = MainMenuActivity.getInstanceFilePath(EXTENSION);
		}

		if (!hasLaunched) {
			Intent i = new Intent(Audio.Media.RECORD_SOUND_ACTION);

			try {
				hasLaunched = true;
				startActivityForResult(i, ACTION_CODE);
			} catch (ActivityNotFoundException e) {
				String err = getString(R.string.activity_not_found,
						Audio.Media.RECORD_SOUND_ACTION);
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
	}

	private void deleteMedia() {
		// get the file path and delete the file
		String path = mediaPath;
		// clean up variables
		mediaPath = null;
		// delete from media provider
		int del = MediaUtils.deleteAudioFileFromMediaProvider(path);
		Log.i(t, "Deleted " + del + " rows from audio media content provider");
	}

	private String getPathFromUri(Uri uri) {
		if (uri.toString().startsWith("file://")) {
			return uri.toString().substring(7);
		} else {
			String[] mediaProjection = { Audio.Media.DATA };
			String mediaPath = null;
			Cursor c = null;
			try {
				c = getApplicationContext().getContentResolver().query(uri,
						mediaProjection, null, null, null);
				int column_index = c.getColumnIndexOrThrow(Audio.Media.DATA);
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

		// when replacing an answer. remove the current media.
		if (mediaPath != null) {
			deleteMedia();
		}

		// get the file path and create a copy in the instance folder
		String binaryPath = getPathFromUri((Uri) mediaUri);
		String extension = binaryPath.substring(binaryPath.lastIndexOf("."));
		String destAudioPath = MainMenuActivity.getInstanceFilePath(extension);

		File source = new File(binaryPath);
		File sourceMedia = new File(destAudioPath);
		try {
			FileUtils.copyFile(source, sourceMedia);
		} catch (IOException e) {
			Log.e(t, ERROR_COPY_FILE + sourceMedia.getAbsolutePath());
			Toast.makeText(this, R.string.media_save_failed, Toast.LENGTH_SHORT)
					.show();
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
					Audio.Media.EXTERNAL_CONTENT_URI, values);
			Log.i(t, "Inserting AUDIO returned uri = " + MediaURI.toString());
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
			Log.e(t, "Inserting Audio file FAILED");
		}

		/*
		 * We saved the audio to the instance directory. Verify that it is
		 * there...
		 */

		if (!sourceMedia.exists()) {
			Log.e(t, ERROR_NO_FILE + sourceMedia.getAbsolutePath());
			Toast.makeText(this, R.string.media_save_failed, Toast.LENGTH_SHORT)
					.show();
			setResult(Activity.RESULT_CANCELED);
			finish();
		} else {
			Intent i = new Intent();
			i.putExtra(URI, FileProvider.getAsUrl(sourceMedia));
			String name = sourceMedia.getName();
			i.putExtra(CONTENT_TYPE, MEDIA_CLASS + name.substring(name.lastIndexOf(".") + 1));
			setResult(Activity.RESULT_OK, i);
			finish();
		}

		return;
	}

	@Override
	public void finish() {
		mediaPath = null;
		hasLaunched = false;
		super.finish();
	}

}
