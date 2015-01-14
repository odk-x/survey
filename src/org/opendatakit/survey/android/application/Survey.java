/*
 * Copyright (C) 2011-2013 University of Washington
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
package org.opendatakit.survey.android.application;

import java.util.HashSet;
import java.util.Set;

import org.opendatakit.common.android.logic.PropertyManager;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.logic.PropertiesSingleton;
import org.opendatakit.survey.android.preferences.PreferencesActivity;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;
import android.webkit.WebView;

/**
 * Extends the Application class to implement
 *
 * @author carlhartung
 *
 */
public class Survey extends Application {
  public static final String t = "Survey";

  // private values
  private static final String DEFAULT_FONTSIZE = "21";

  private Set<String> appNameHasBeenInitialized = new HashSet<String>();

  private static Survey singleton = null;

  public static Survey getInstance() {
    return singleton;
  }

  public static int getQuestionFontsize(String appName) {
    String question_font = PropertiesSingleton.getProperty(appName, PreferencesActivity.KEY_FONT_SIZE);
    int questionFontsize = Integer.valueOf(question_font);
    return questionFontsize;
  }
  
  public boolean shouldRunInitializationTask(String appName) {
    return !appNameHasBeenInitialized.contains(appName);
  }

  public void clearRunInitializationTask(String appName) {
    appNameHasBeenInitialized.add(appName);
  }

  public void setRunInitializationTask(String appName) {
    appNameHasBeenInitialized.remove(appName);
  }

  public String getVersionCodeString() {
    try {
      PackageInfo pinfo;
      pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
      int versionNumber = pinfo.versionCode;
      return Integer.toString(versionNumber);
    } catch (NameNotFoundException e) {
      e.printStackTrace();
      return "";
    }
  }

  public String getVersionedAppName() {
    String versionDetail = "";
    try {
      PackageInfo pinfo;
      pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
      int versionNumber = pinfo.versionCode;
      String versionName = pinfo.versionName;
      versionDetail = " " + versionName + " (rev " + versionNumber + ")";
    } catch (NameNotFoundException e) {
      e.printStackTrace();
    }
    return getString(R.string.app_name) + versionDetail;
  }

  /**
   * Creates required directories on the SDCard (or other external storage)
   *
   * @throws RuntimeException
   *           if there is no SDCard or the directory exists as a non directory
   */
  public static void createODKDirs(String appName) throws RuntimeException {

    ODKFileUtils.verifyExternalStorageAvailability();

    ODKFileUtils.assertDirectoryStructure(appName);
  }

  @SuppressLint("NewApi")
  @Override
  public void onCreate() {
    singleton = this;
    PropertyManager propertyManager = new PropertyManager(getApplicationContext());

    super.onCreate();

    if (Build.VERSION.SDK_INT >= 19) {
      WebView.setWebContentsDebuggingEnabled(true);
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Log.i(t, "onConfigurationChanged");
  }

  @Override
  public void onTerminate() {
    super.onTerminate();
    Log.i(t, "onTerminate");
  }

}
