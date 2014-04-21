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

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.opendatakit.common.android.logic.PropertyManager;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.logic.PropertiesSingleton;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.util.Log;

import fi.iki.elonen.SimpleWebServer;

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

  private SimpleWebServer server = null;
  private volatile Thread webServer = null;

  private static Survey singleton = null;

  public static Survey getInstance() {
    return singleton;
  }

  private synchronized void startServer() {
    if (server == null || !server.isAlive()) {
      SimpleWebServer testing = new SimpleWebServer();
      try {
        testing.start();
        server = testing;
      } catch (IOException e) {
        Log.e("Survey.Thread.WebServer", "Exception: " + e.toString());
      }
    }
  }

  private synchronized void stopServer() {
    if (server != null) {
      try {
        server.stop();
      } catch (Exception e) {
        // ignore...
      }
      server = null;
    }
  }

  public static int getQuestionFontsize(String appName) {
    String question_font = PropertiesSingleton.getProperty(appName, PreferencesActivity.KEY_FONT_SIZE);
    int questionFontsize = Integer.valueOf(question_font);
    return questionFontsize;
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
      versionDetail = " " + versionName + "(" + versionNumber + ")";
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

  @Override
  public void onCreate() {
    singleton = this;
    PropertyManager propertyManager = new PropertyManager(getApplicationContext());

    super.onCreate();

    webServer = new Thread(null, new Runnable() {
      @Override
      public void run() {
        Thread mySelf = Thread.currentThread();
        int retryCount = 0;
        for (;webServer == mySelf;) {
          startServer();
          try {
            retryCount++;
            Thread.sleep(1000);
            if ( retryCount % 60 == 0 ) {
              Log.d(t,"Survey.Thread.WebServer -- waking to confirm webserver is still working");
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        stopServer();
      }}, "WebServer");
    webServer.start();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Log.i(t, "onConfigurationChanged");
  }

  @Override
  public void onTerminate() {
    Thread tmpThread = webServer;
    webServer = null;
    tmpThread.interrupt();
    try {
      // give it time to drain...
      Thread.sleep(200);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    super.onTerminate();
    Log.i(t, "onTerminate");
  }

}
