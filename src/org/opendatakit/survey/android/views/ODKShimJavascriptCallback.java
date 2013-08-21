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
  public void clearInstanceId(String refId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: clearInstanceId(" + refId + ")");
      return;
    }
    log.d("shim", "DO: clearInstanceId(" + refId + ")");
    mActivity.setInstanceId(null);
  }

  /**
   * If formId is null, clears the instanceId.
   * If formId matches the current formId, sets the instanceId.
   *
   * @param formId
   * @param instanceId
   */
  // @JavascriptInterface
  public void setInstanceId(String refId, String instanceId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: setInstanceId(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("shim", "DO: setInstanceId(" + refId + ", " + instanceId + ")");
    mActivity.setInstanceId(instanceId);
  }

  // @JavascriptInterface
  public void pushSectionScreenState(String refId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: pushSectionScreenState(" + refId + ")");
      return;
    }
    log.d("shim", "DO: pushSectionScreenState(" + refId + ")");
    mActivity.pushSectionScreenState();
  }

  // @JavascriptInterface
  public void setSectionScreenState(String refId, String screenPath, String state) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: setSectionScreenState(" + refId + ", " + screenPath + ", " + state + ")");
      return;
    }
    log.d("shim", "DO: setSectionScreenState(" + refId + ", " + screenPath + ", " + state + ")");
    mActivity.setSectionScreenState(screenPath, state);
  }

  // @JavascriptInterface
  public void clearSectionScreenState(String refId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: clearSectionScreenState(" + refId + ")");
      return;
    }
    log.d("shim", "DO: clearSectionScreenState(" + refId + ")");
    mActivity.clearSectionScreenState();
  }

  // @JavascriptInterface
  public String getControllerState(String refId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: getControllerState(" + refId + ")");
      return null;
    }
    log.d("shim", "DO: getControllerState(" + refId + ")");
    return mActivity.getControllerState();
  }

  // @JavascriptInterface
  public String getScreenPath(String refId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: getScreenPath(" + refId + ")");
      return null;
    }
    log.d("shim", "DO: getScreenPath(" + refId + ")");
    return mActivity.getScreenPath();
  }

  // @JavascriptInterface
  public boolean hasScreenHistory(String refId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: hasScreenHistory(" + refId + ")");
      return false;
    }
    log.d("shim", "DO: hasScreenHistory(" + refId + ")");
    return mActivity.hasScreenHistory();
  }

  // @JavascriptInterface
  public String popScreenHistory(String refId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: popScreenHistory(" + refId + ")");
      return null;
    }
    log.d("shim", "DO: popScreenHistory(" + refId + ")");
    return mActivity.popScreenHistory();
  }

  // @JavascriptInterface
  public boolean hasSectionStack(String refId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: hasSectionStack(" + refId + ")");
      return false;
    }
    log.d("shim", "DO: hasSectionStack(" + refId + ")");
    return mActivity.hasSectionStack();
  }

  // @JavascriptInterface
  public String popSectionStack(String refId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: popSectionStack(" + refId + ")");
      return null;
    }
    log.d("shim", "DO: popSectionStack(" + refId + ")");
    return mActivity.popSectionStack();
  }

  // @JavascriptInterface
  public void ignoreAllChangesCompleted(String refId, String instanceId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: ignoreAllChangesCompleted(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("shim", "DO: ignoreAllChangesCompleted(" + refId + ", " + instanceId + ")");
    mActivity.ignoreAllChangesCompleted(instanceId);
  }

  // @JavascriptInterface
  public void ignoreAllChangesFailed(String refId, String instanceId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: ignoreAllChangesFailed(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("shim", "DO: ignoreAllChangesFailed(" + refId + ", " + instanceId + ")");
    mActivity.ignoreAllChangesFailed(instanceId);
  }

  // @JavascriptInterface
  public void saveAllChangesCompleted(String refId, String instanceId, boolean asComplete) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: saveAllChangesCompleted(" + refId + ", " + instanceId + ", " + asComplete + ")");
      return;
    }
    // go through the FC because there are additional keys that should be
    // set here...
    log.d("shim", "DO: saveAllChangesCompleted(" + refId + ", " + instanceId + ", " + asComplete + ")");
    mActivity.saveAllChangesCompleted(instanceId, asComplete);
  }

  // @JavascriptInterface
  public void saveAllChangesFailed(String refId, String instanceId) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: saveAllChangesFailed(" + refId + ", " + instanceId + ")");
      return;
    }
    log.d("shim", "DO: saveAllChangesFailed(" + refId + ", " + instanceId + ")");
    mActivity.saveAllChangesFailed(instanceId);
  }

  // @JavascriptInterface
  public String doAction(String refId, String page, String path, String action, String jsonMap) {
    if ( !mActivity.getRefId().equals(refId) ) {
      log.w("shim", "IGNORED: doAction(" + refId + ", " + page + ", " + path + ", " + action + ", ...)");
      return "IGNORE";
    }

    JSONObject valueMap = null;
    try {
      if (jsonMap != null && jsonMap.length() != 0) {
        valueMap = (JSONObject) new JSONTokener(jsonMap).nextValue();
      }
    } catch (JSONException e) {
      e.printStackTrace();
      log.e("shim", "ERROR: doAction(" + refId + ", " + page + ", " + path + ", " + action + ", ...) " + e.toString());
      return "ERROR";
    }

    log.d("shim", "DO: doAction(" + refId + ", " + page + ", " + path + ", " + action + ", ...)");
    return mActivity.doAction(page, path, action, valueMap);
  }

}