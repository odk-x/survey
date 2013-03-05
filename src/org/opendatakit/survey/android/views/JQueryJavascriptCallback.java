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

package org.opendatakit.survey.android.views;

import java.io.File;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.opendatakit.common.android.database.WebDbDatabaseHelper;
import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.activities.ODKActivity;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.provider.FormsProviderAPI;

import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

/**
 * The class mapped to 'shim' in the Javascript
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class JQueryJavascriptCallback {

	public static final String t = "HtmlJavascriptCallback";

	final ODKActivity mActivity;
	private WebLogger log;

	public JQueryJavascriptCallback(ODKActivity activity) {
		mActivity = activity;
		log = WebLogger.getLogger(mActivity.getAppName());
	}

	public String getBaseUrl() {

		// Find the formPath for the default form with the most recent
		// version...
		Cursor c = null;
		String formPath = null;
		try {
			//
			// find if there is already a form definition with the same formId
			// and formVersion...
			String selection = FormsColumns.FORM_ID + "=?";
			String[] selectionArgs = { "default" };
			String orderBy = FormsColumns.FORM_VERSION + " DESC"; // use the
																	// most
																	// recently
																	// created
																	// of the
																	// matches
																	// (in case
																	// DB
																	// corrupted)
			c = Survey
					.getInstance()
					.getContentResolver()
					.query(Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, mActivity.getAppName()), null, selection,
							selectionArgs, orderBy);

			if (c.getCount() > 0) {
				// we found a match...
				c.moveToFirst();
				formPath = c
						.getString(c.getColumnIndex(FormsColumns.FORM_PATH));
			}

		} finally {
			if (c != null && !c.isClosed()) {
				c.close();
			}
		}

		return (formPath == null) ? "" : formPath.substring(0,
				formPath.length() - 1);
	}

	/**
	 * <p>
	 * Get information about the platform we are running on.
	 * </p>
	 *
	 * <pre>
	 * {"container":"Android",
	 *  "version":"2.2.3",
	 *  "appPath":"pathToDirContaining/index.html"
	 *  }
	 * </pre>
	 *
	 * Version should map to the capabilities of the WebKit or browser in which
	 * the form is rendered. For Android, this is part of the operating system
	 * and is not updated separately, so its version is the OS build version.
	 * AppDir should end with a trailing slash.
	 *
	 * @return JSONstring as defined above.
	 */
	public String getPlatformInfo() {
		File mediaFolder = new File(ODKFileUtils.getFormsFolder(mActivity.getAppName()) + File.separator
				+ "default");

		return "{\"container\":\"Android\"," + "\"version\":\""
				+ Build.VERSION.RELEASE + "\"," + "\"appPath\":\""
				+ mediaFolder.getAbsolutePath() + File.separator + "\"}";
	}

	/**
	 * <p>
	 * Get information needed to open the W3C SQLite database.
	 * </p>
	 *
	 * <pre>
	 * {"shortName":"odk",
	 *  "version":"1","
	 *  "displayName":"ODK Instances Database",
	 *  "maxSize":65536 }
	 * </pre>
	 *
	 * The database interaction is always asynchronous because that is what
	 * Android 2.2.3 WebKit supports. The "version" value can be used by the
	 * javascript to adjust to whatever database structure is expected to be
	 * used within the ODK frameworks.
	 *
	 * @return JSONstring as defined above.
	 */
	public String getDatabaseSettings() {
		// maxSize is in bytes
		return "{\"shortName\":\""
				+ WebDbDatabaseHelper.WEBDB_INSTANCE_DB_SHORT_NAME
				+ "\",\"version\":\""
				+ WebDbDatabaseHelper.WEBDB_INSTANCE_DB_VERSION
				+ "\",\"displayName\":\""
				+ WebDbDatabaseHelper.WEBDB_INSTANCE_DB_DISPLAY_NAME
				+ "\",\"maxSize\":"
				+ WebDbDatabaseHelper.WEBDB_INSTANCE_DB_ESTIMATED_SIZE + "}";
	}

	public void setInstanceId(String instanceId) {
		mActivity.setInstanceId(instanceId);
	}

	public void setPageRef(String pageRef) {
		mActivity.setPageRef(pageRef);
	}

	public boolean hasPromptHistory() {
     return mActivity.hasPromptHistory();
	}

	public void clearPromptHistory() {
	  mActivity.clearPromptHistory();
	}

	public String popPromptHistory() {
	  return mActivity.popPromptHistory();
	}

	public void pushPromptHistory(String idx) {
	  mActivity.pushPromptHistory(idx);
	}

	public void setAuxillaryHash(String auxillaryHash) {
		// NOTE: not currently used...
		mActivity.setAuxillaryHash(auxillaryHash);
	}

	public void ignoreAllChangesCompleted(String formId, String instanceId) {
		mActivity.ignoreAllChangesCompleted(formId, instanceId);
	}

	public void ignoreAllChangesFailed(String formId, String instanceId) {
		mActivity.ignoreAllChangesFailed(formId, instanceId);
	}

	public void saveAllChangesCompleted(String formId, String instanceId,
			boolean asComplete) {
		// go through the FC because there are additional keys that should be
		// set here...
		mActivity.saveAllChangesCompleted(formId, instanceId, asComplete);
	}

	public void saveAllChangesFailed(String formId, String instanceId) {
		mActivity.saveAllChangesFailed(formId, instanceId);
	}

	public void log(String level, String loggingString) {
		char l = (level == null) ? 'I' : level.charAt(0);
		switch (l) {
		case 'A':
			log.log(WebLogger.ASSERT, "shim", loggingString);
			break;
		case 'D':
			log.log(WebLogger.DEBUG, "shim", loggingString);
			break;
		case 'E':
			log.log(WebLogger.ERROR, "shim", loggingString);
			break;
		case 'I':
			log.log(WebLogger.INFO, "shim", loggingString);
			break;
		case 'S':
			log.log(WebLogger.SUCCESS, "shim", loggingString);
			break;
		case 'V':
			log.log(WebLogger.VERBOSE, "shim", loggingString);
			break;
		case 'W':
			log.log(WebLogger.WARN, "shim", loggingString);
			break;
		default:
			log.log(WebLogger.INFO, "shim", loggingString);
			break;
		}
	}

	public String doAction(String page, String path, String action,
			String jsonMap) {

		JSONObject valueMap = null;
		try {
			if (jsonMap != null && jsonMap.length() != 0) {
				valueMap = (JSONObject) new JSONTokener(jsonMap).nextValue();
			}
		} catch (JSONException e) {
			e.printStackTrace();
			return e.toString();
		}

		return mActivity.doAction(page, path, action, valueMap);
	}

}