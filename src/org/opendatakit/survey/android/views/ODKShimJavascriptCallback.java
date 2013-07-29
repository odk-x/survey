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

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.opendatakit.common.android.database.WebDbDatabaseHelper;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.activities.ODKActivity;
import org.opendatakit.survey.android.activities.ODKActivity.FrameworkFormPathInfo;

import android.os.Build;

/**
 * The class mapped to 'shim' in the Javascript
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class ODKShimJavascriptCallback {

  public static final String t = "ODKShimJavascriptCallback";

  private final ODKActivity mActivity;
  private final WebLogger log;

  public ODKShimJavascriptCallback(ODKActivity activity) {
    mActivity = activity;
    log = WebLogger.getLogger(mActivity.getAppName());
  }

  // @JavascriptInterface
  public String getBaseUrl() {
    FrameworkFormPathInfo info = mActivity.getFrameworkFormPathInfo();

    return (info == null) ? "" : info.relativePath.substring(0, info.relativePath.length() - 1);
  }

  /**
   * <p>
   * Get information about the platform we are running on.
   * </p>
   *
   * <pre>
   * {"container":"Android",
   *  "version":"2.2.3"
   *  }
   * </pre>
   *
   * Version should map to the capabilities of the WebKit or browser in which
   * the form is rendered. For Android, this is part of the operating system and
   * is not updated separately, so its version is the OS build version.
   *
   * @return JSONstring as defined above.
   */
  // @JavascriptInterface
  public String getPlatformInfo() {
    // @formatter:off
	 return "{\"container\":\"Android\"," +
		      "\"version\":\""	+ Build.VERSION.RELEASE + "\"}";
    // @formatter:on
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
   * javascript to adjust to whatever database structure is expected to be used
   * within the ODK frameworks.
   *
   * @return JSONstring as defined above.
   */
  // @JavascriptInterface
  public String getDatabaseSettings() {
    // maxSize is in bytes
    return "{\"shortName\":\"" + WebDbDatabaseHelper.WEBDB_INSTANCE_DB_SHORT_NAME
        + "\",\"version\":\"" + WebDbDatabaseHelper.WEBDB_INSTANCE_DB_VERSION
        + "\",\"displayName\":\"" + WebDbDatabaseHelper.WEBDB_INSTANCE_DB_DISPLAY_NAME
        + "\",\"maxSize\":" + WebDbDatabaseHelper.WEBDB_INSTANCE_DB_ESTIMATED_SIZE + "}";
  }

  // @JavascriptInterface
  public void setInstanceId(String instanceId) {
    mActivity.setInstanceId(instanceId);
  }

  // @JavascriptInterface
  public void setScreenPath(String screenPath) {
    mActivity.setScreenPath(screenPath);
  }

  // @JavascriptInterface
  public boolean hasScreenHistory() {
    return mActivity.hasScreenHistory();
  }

  // @JavascriptInterface
  public void clearScreenHistory() {
    mActivity.clearScreenHistory();
  }

  // @JavascriptInterface
  public String popScreenHistory() {
    return mActivity.popScreenHistory();
  }

  // @JavascriptInterface
  public void pushScreenHistory(String idx) {
    mActivity.pushScreenHistory(idx);
  }

  // @JavascriptInterface
  public boolean hasSectionStack() {
    return mActivity.hasSectionStack();
  }

  // @JavascriptInterface
  public void clearSectionStack() {
    mActivity.clearSectionStack();
  }

  // @JavascriptInterface
  public String popSectionStack() {
    return mActivity.popSectionStack();
  }

  // @JavascriptInterface
  public void pushSectionStack(String sectionName) {
    mActivity.pushSectionStack(sectionName);
  }

  // @JavascriptInterface
  public void setAuxillaryHash(String auxillaryHash) {
    // NOTE: not currently used...
    mActivity.setAuxillaryHash(auxillaryHash);
  }

  // @JavascriptInterface
  public void ignoreAllChangesCompleted(String formId, String instanceId) {
    mActivity.ignoreAllChangesCompleted(formId, instanceId);
  }

  // @JavascriptInterface
  public void ignoreAllChangesFailed(String formId, String instanceId) {
    mActivity.ignoreAllChangesFailed(formId, instanceId);
  }

  // @JavascriptInterface
  public void saveAllChangesCompleted(String formId, String instanceId, boolean asComplete) {
    // go through the FC because there are additional keys that should be
    // set here...
    mActivity.saveAllChangesCompleted(formId, instanceId, asComplete);
  }

  // @JavascriptInterface
  public void saveAllChangesFailed(String formId, String instanceId) {
    mActivity.saveAllChangesFailed(formId, instanceId);
  }

  // @JavascriptInterface
  public void log(String level, String loggingString) {
    char l = (level == null) ? 'I' : level.charAt(0);
    switch (l) {
    case 'A':
      log.a("shim", loggingString);
      break;
    case 'D':
      log.d("shim", loggingString);
      break;
    case 'E':
      log.e("shim", loggingString);
      break;
    case 'I':
      log.i("shim", loggingString);
      break;
    case 'S':
      log.s("shim", loggingString);
      break;
    case 'V':
      log.v("shim", loggingString);
      break;
    case 'W':
      log.w("shim", loggingString);
      break;
    default:
      log.i("shim", loggingString);
      break;
    }
  }

  // @JavascriptInterface
  public String doAction(String page, String path, String action, String jsonMap) {

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