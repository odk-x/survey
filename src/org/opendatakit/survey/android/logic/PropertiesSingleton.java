/*
 * Copyright (C) 2013-2014 University of Washington
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

package org.opendatakit.survey.android.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.lang3.CharEncoding;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.preferences.AdminPreferencesActivity;
import org.opendatakit.survey.android.preferences.PreferencesActivity;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class PropertiesSingleton {

  private static final String t = "PropertiesSingleton";

  private static final Map<String, PropertiesSingleton> singletons = new HashMap<String, PropertiesSingleton>();

  private static synchronized PropertiesSingleton getSingleton(String appName) {
    if (appName == null || appName.length() == 0) {
      throw new IllegalArgumentException("Unexpectedly null or empty appName");
    }
    PropertiesSingleton s = singletons.get(appName);
    if (s == null) {
      s = new PropertiesSingleton(appName);
      singletons.put(appName, s);
    }
    return s;
  }

  private static boolean isSecureProperty(String propertyName) {
    return PreferencesActivity.KEY_AUTH.equals(propertyName) ||
        AdminPreferencesActivity.KEY_ADMIN_PW.equals(propertyName);
  }

  public static boolean containsKey(String appName, String propertyName) {
    if (isSecureProperty(propertyName)) {
      // this needs to be stored in a protected area
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Survey
          .getInstance().getApplicationContext());
      return sharedPreferences.contains(appName + "_" + propertyName);
    } else {
      PropertiesSingleton s = getSingleton(appName);
      return s.containsKey(propertyName);
    }
  }

  public static String getProperty(String appName, String propertyName) {
    if (isSecureProperty(propertyName)) {
      // this needs to be stored in a protected area
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Survey
          .getInstance().getApplicationContext());
      return sharedPreferences.getString(appName + "_" + propertyName, null);
    } else {
      PropertiesSingleton s = getSingleton(appName);
      return s.getProperty(propertyName);
    }
  }

  public static void removeProperty(String appName, String propertyName) {
    if (isSecureProperty(propertyName)) {
      // this needs to be stored in a protected area
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Survey
          .getInstance().getApplicationContext());
      sharedPreferences.edit().remove(appName + "_" + propertyName).commit();
    } else {
      PropertiesSingleton s = getSingleton(appName);
      s.removeProperty(propertyName);
    }
  }

  public static void setProperty(String appName, String propertyName, String value) {
    if (isSecureProperty(propertyName)) {
      // this needs to be stored in a protected area
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Survey
          .getInstance().getApplicationContext());
      sharedPreferences.edit().putString(appName + "_" + propertyName, value).commit();
    } else {
      PropertiesSingleton s = getSingleton(appName);
      s.setProperty(propertyName, value);
    }
  }

  public static void writeProperties(String appName) {
    PropertiesSingleton s = getSingleton(appName);
    s.writeProperties();
  }

  private String mAppName;
  private Properties mProps;
  private File mConfigFile;
  private File mTempConfigFile;

  private PropertiesSingleton(String appName) {
    mAppName = appName;
    mProps = new Properties();

    // Set default values as necessary
    Properties defaults = new Properties();
    defaults.setProperty(PreferencesActivity.KEY_LAST_VERSION, "0");
    defaults.setProperty(PreferencesActivity.KEY_FIRST_RUN, "true");
    defaults.setProperty(PreferencesActivity.KEY_ACCOUNT, "");
    defaults.setProperty(AdminPreferencesActivity.KEY_GET_BLANK, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_SEND_FINALIZED, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_MANAGE_FORMS, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_ACCESS_SETTINGS, "true");
    defaults.setProperty(PreferencesActivity.KEY_SHOW_SPLASH, "false");
    defaults.setProperty(PreferencesActivity.KEY_SPLASH_PATH,
        Survey.getInstance().getString(R.string.default_splash_path));
    defaults.setProperty(PreferencesActivity.KEY_FONT_SIZE, "21");
    defaults.setProperty(PreferencesActivity.KEY_SERVER_URL,
        Survey.getInstance().getString(R.string.default_server_url));
    defaults.setProperty(PreferencesActivity.KEY_FORMLIST_URL,
        Survey.getInstance().getString(R.string.default_odk_formlist));
    defaults.setProperty(AdminPreferencesActivity.KEY_CHANGE_SERVER, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_CHANGE_USERNAME, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_CHANGE_PASSWORD, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_CHANGE_GOOGLE_ACCOUNT, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_CHANGE_FONT_SIZE, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_SELECT_SPLASH_SCREEN, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_SHOW_SPLASH_SCREEN, "true");
    defaults.setProperty(PreferencesActivity.KEY_SUBMISSION_URL,
        Survey.getInstance().getString(R.string.default_odk_submission));

    readProperties();

    boolean dirtyProps = false;
    for (Entry<Object, Object> entry : defaults.entrySet()) {
      if (mProps.containsKey(entry.getKey().toString()) == false) {
        mProps.setProperty(entry.getKey().toString(), entry.getValue().toString());
        dirtyProps = true;
      }
    }

    // strip out the admin password and store it in the app layer.
    if (mProps.containsKey(AdminPreferencesActivity.KEY_ADMIN_PW)) {
      defaults.setProperty(AdminPreferencesActivity.KEY_ADMIN_PW, "");
      String adminPW = mProps.getProperty(AdminPreferencesActivity.KEY_ADMIN_PW);
      // NOTE: can't use the static methods because this object is not yet fully created
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Survey
          .getInstance().getApplicationContext());
      sharedPreferences.edit()
          .putString(mAppName + "_" + AdminPreferencesActivity.KEY_ADMIN_PW, adminPW).commit();
      mProps.remove(AdminPreferencesActivity.KEY_ADMIN_PW);
      dirtyProps = true;
    }

    // strip out the auth key
    if (mProps.containsKey(PreferencesActivity.KEY_AUTH)) {
      mProps.remove(PreferencesActivity.KEY_AUTH);
      dirtyProps = true;
    }

    if (dirtyProps) {
      writeProperties();
    }

  }

  private boolean containsKey(String key) {
    return mProps.containsKey(key);
  }

  private String getProperty(String key) {
    String retString = null;

    if (mProps.containsKey(key)) {
      retString = mProps.getProperty(key);
    }
    return retString;
  }

  private void removeProperty(String key) {
    if (key != null) {
      mProps.remove(key);
    }
  }

  private void setProperty(String key, String value) {
    mProps.setProperty(key, value);
  }

  private void readProperties() {
    try {
      if (mConfigFile == null) {
        mConfigFile = new File(ODKFileUtils.getSurveyConfigurationFile(mAppName));
      }

      FileInputStream configFileInputStream = new FileInputStream(mConfigFile);

      mProps.loadFromXML(configFileInputStream);
      configFileInputStream.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void writeProperties() {
    try {
      if (mTempConfigFile == null) {
        mTempConfigFile = new File(ODKFileUtils.getSurveyTempConfigurationFile(mAppName));
      }
      FileOutputStream configFileOutputStream = new FileOutputStream(mTempConfigFile, false);

      mProps.storeToXML(configFileOutputStream, null, CharEncoding.UTF_8);
      configFileOutputStream.close();

      if (mConfigFile == null) {
        mConfigFile = new File(ODKFileUtils.getSurveyConfigurationFile(mAppName));
      }

      boolean fileSuccess = mTempConfigFile.renameTo(mConfigFile);

      if (!fileSuccess) {
        Log.i(t, "Temporary Config File Rename Failed!");
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
