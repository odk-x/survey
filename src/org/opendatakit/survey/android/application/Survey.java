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

import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.APKExpansionPolicy;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.util.Base64;
import com.google.android.vending.licensing.util.Base64DecoderException;

import fi.iki.elonen.SimpleWebServer;

/**
 * Extends the Application class to implement
 *
 * @author carlhartung
 *
 */
public class Survey extends Application implements LicenseCheckerCallback {

  private static final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzvIdMy97S9QLTp/gBxp7WUPM"
      + "QWdXu2IDQJbZ3is95cx+WYuLXIdjNyok+VCZo8CxIHb2kuIeDmFYCwagqw9Uclnh2WDWagT+BB4dfyBD3V4IetGNdSZFKh2Y+9KHnysmsY7w"
      + "E4z6NcrGlYbPWyboORjODBRNZ4rWPnLNix8WaNlHDW05uahGnSmto0lNCkdSC2PvmTUE3BimUScoiS+6LxQR/THqnR1RqA1IimjG2JsP58Oo"
      + "n5NjWN4maX08IroXWDBGjhPtdngWOnoR8GoJ96M8k0eAM1LJ84eB/v9LnQZlrZjBdtUEhXlGKVudo41vmp1sC1OpRLYMbshhst7dzQIDAQAB";
  public static final String t = "Survey";

  // these keys are stored in the shared preferences
  // to manage Google play licensing and APK Expansion files
  public static final String KEY_SALT = "licenseSalt";
  public static final String KEY_APK_EXPANSIONS = "apkExpansions";

  // keys for expansion files
  public static final String EXPANSION_FILE_PATH = "path";
  public static final String EXPANSION_FILE_LENGTH = "length";
  public static final String EXPANSION_FILE_URL = "url";

  // private values
  private static final String DEFAULT_FONTSIZE = "21";

  private int versionCode;
  private byte[] mSalt;
  private LicenseChecker mLicenseChecker;
  private APKExpansionPolicy mAPKExpansionPolicy;
  private SimpleWebServer server = null;
  private volatile Thread webServer = null;

  private static final boolean debugAPKExpansion = false;

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

  /**
   * For debugging APK expansion, we check for the existence and process the
   * local file without also confirming that it matches that on the Google Play
   * site.
   *
   * @return true if there is a test APK Expansion file present.
   */
  public static File debugAPKExpansionFile() {
    File f = Survey.getInstance().localExpansionFile();
    if (debugAPKExpansion && f.exists()) {
      return f;
    }
    return null;
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
   * @return true if there are tables present
   * @throws RuntimeException
   *           if there is no SDCard or the directory exists as a non directory
   */
  public static boolean createODKDirs(String appName) throws RuntimeException {

    ODKFileUtils.verifyExternalStorageAvailability();

    ODKFileUtils.assertDirectoryStructure(appName);

    return ODKFileUtils.isConfiguredApp(appName);
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

    SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    String saltString = mSharedPreferences.getString(KEY_SALT, null);
    do {
      if (saltString == null) {
        SecureRandom random = new SecureRandom();
        mSalt = new byte[20];
        random.nextBytes(mSalt);
        saltString = Base64.encode(mSalt);
        mSharedPreferences.edit().putString(KEY_SALT, saltString).commit();
      } else {
        try {
          mSalt = Base64.decode(saltString);
        } catch (Base64DecoderException e) {
          Log.e(t, "Unable to decode saved salt string -- regenerating");
          saltString = null;
          e.printStackTrace();
        }
      }
    } while (saltString == null);

    try {
      PackageInfo pinfo;
      pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
      versionCode = pinfo.versionCode;
      mSalt[0] = (byte) (versionCode % 251);
    } catch (NameNotFoundException e) {
      versionCode = 0;
      e.printStackTrace();
    }

    String deviceId = propertyManager.getSingularProperty(PropertyManager.OR_DEVICE_ID_PROPERTY,
        null);

    // Construct the LicenseChecker with a Policy.
    mAPKExpansionPolicy = new APKExpansionPolicy(this, new AESObfuscator(mSalt, getPackageName(),
        deviceId));
    mLicenseChecker = new LicenseChecker(this, mAPKExpansionPolicy, BASE64_PUBLIC_KEY // Your
                                                                                      // public
                                                                                      // licensing
                                                                                      // key.
    );

    mLicenseChecker.checkAccess(this);
  }

  public byte[] getSalt() {
    return mSalt;
  }

  public String getBase64PublicKey() {
    return BASE64_PUBLIC_KEY;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Log.i(t, "onConfigurationChanged");
  }

  @Override
  public void onTerminate() {
    mLicenseChecker.onDestroy();
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

  @Override
  public void allow(int reason) {
    Log.i(t, "allow: license check succeeded: " + Integer.toString(reason));

    if (mAPKExpansionPolicy.isUpdatedFromServer()) {
      // we got a response from the server, as opposed to a cached entry
      // (a cached entry does not have the expansion info).
      // Gather and retain the expansion file info into the user
      // preferences.
      File f = new File(ODKFileUtils.getAndroidObbFolder(getPackageName()));
      f.mkdirs();

      ArrayList<Map<String, Object>> expansions = new ArrayList<Map<String, Object>>();
      int exps = mAPKExpansionPolicy.getExpansionURLCount();
      for (int i = 0; i < exps; ++i) {
        String name = mAPKExpansionPolicy.getExpansionFileName(i);
        String url = mAPKExpansionPolicy.getExpansionURL(i);
        long len = mAPKExpansionPolicy.getExpansionFileSize(i);
        File ext = new File(f, name);

        Map<String, Object> ex = new HashMap<String, Object>();
        ex.put(EXPANSION_FILE_PATH, ext.getAbsoluteFile());
        ex.put(EXPANSION_FILE_URL, url);
        ex.put(EXPANSION_FILE_LENGTH, Long.valueOf(len).toString());
        expansions.add(ex);
      }

      String expansionDefs;
      try {
        expansionDefs = ODKFileUtils.mapper.writeValueAsString(expansions);
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferences.edit().putString(KEY_APK_EXPANSIONS, expansionDefs).commit();
        Log.i(t, "retained the expansion file list (" + expansions.size() + " expansion files)");
      } catch (JsonGenerationException e) {
        e.printStackTrace();
        Log.e(t, "unable to retain expected APK Expansion information");
      } catch (JsonMappingException e) {
        e.printStackTrace();
        Log.e(t, "unable to retain expected APK Expansion information");
      } catch (IOException e) {
        e.printStackTrace();
        Log.e(t, "unable to retain expected APK Expansion information");
      }
    }
  }

  @SuppressWarnings("unchecked")
  public ArrayList<Map<String, Object>> expansionFiles() {
    File f = new File(ODKFileUtils.getAndroidObbFolder(getPackageName()));
    f.mkdirs();

    SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    String expansionDefs = mSharedPreferences.getString(KEY_APK_EXPANSIONS, null);
    if (expansionDefs != null) {
      try {
        return ODKFileUtils.mapper.readValue(expansionDefs, ArrayList.class);
      } catch (JsonParseException e) {
        e.printStackTrace();
        Log.e(t, "unable to retrieve expected APK Expansion information");
      } catch (JsonMappingException e) {
        e.printStackTrace();
        Log.e(t, "unable to retrieve expected APK Expansion information");
      } catch (IOException e) {
        e.printStackTrace();
        Log.e(t, "unable to retrieve expected APK Expansion information");
      }
    }
    return null;
  }

  public File localExpansionFile() {
    File f = new File(ODKFileUtils.getAndroidObbFolder(getPackageName()));
    f.mkdirs();

    String name = ODKFileUtils.getExpansionAPKFileName(this, true, versionCode);

    return new File(f, name);
  }

  @Override
  public void dontAllow(int reason) {
    Log.e(t, "dontAllow: license check FAILED: " + Integer.toString(reason));
  }

  @Override
  public void applicationError(int errorCode) {
    Log.e(t, "applicationError: license check ERROR: " + Integer.toString(errorCode));
  }

}
