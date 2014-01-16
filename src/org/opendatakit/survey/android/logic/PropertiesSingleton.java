package org.opendatakit.survey.android.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map.Entry;
import java.util.Properties;

import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.preferences.AdminPreferencesActivity;
import org.opendatakit.survey.android.preferences.PreferencesActivity;

import android.util.Log;

public class PropertiesSingleton {
  public final static PropertiesSingleton INSTANCE = new PropertiesSingleton();

  private static final String ODK_SURVEY_CONFIG_PROPERTIES_FILENAME = "config.properties";

  private static final String ODK_SURVEY_TEMP_CONFIG_PROPERTIES_FILENAME = "config.temp";

  public static final String t = "PropertiesSingleton";

  private Properties mProps;
  private File mConfigFile;
  private File mTempConfigFile;

  private PropertiesSingleton() {
    mProps = new Properties();

    // Set default values as necessary
    Properties defaults = new Properties();
    defaults.setProperty(PreferencesActivity.KEY_ACCOUNT, "");
    defaults.setProperty(AdminPreferencesActivity.KEY_GET_BLANK, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_SEND_FINALIZED, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_MANAGE_FORMS, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_ACCESS_SETTINGS, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_ADMIN_PW, "");
    // defaults.setProperty(PreferencesActivity.KEY_FIRST_RUN, "true");
    defaults.setProperty(PreferencesActivity.KEY_SHOW_SPLASH, "false");
    defaults.setProperty(PreferencesActivity.KEY_SPLASH_PATH, "ODK Default");
    // defaults.setProperty(PreferencesActivity.KEY_LAST_VERSION, "0");
    defaults.setProperty(PreferencesActivity.KEY_FONT_SIZE, "21");
    defaults.setProperty(PreferencesActivity.KEY_SERVER_URL, "https://opendatakit-2.appspot.com");
    defaults.setProperty(PreferencesActivity.KEY_FORMLIST_URL, "/formList");
    defaults.setProperty(AdminPreferencesActivity.KEY_CHANGE_SERVER, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_CHANGE_USERNAME, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_CHANGE_PASSWORD, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_CHANGE_GOOGLE_ACCOUNT, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_CHANGE_FONT_SIZE, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_SELECT_SPLASH_SCREEN, "true");
    defaults.setProperty(AdminPreferencesActivity.KEY_SHOW_SPLASH_SCREEN, "true");
    defaults.setProperty(PreferencesActivity.KEY_AUTH, "");
    defaults.setProperty(PreferencesActivity.KEY_SUBMISSION_URL, "/submission");

    readProperties();

    boolean dirtyProps = false;
    for (Entry<Object, Object> entry : defaults.entrySet()) {
      if (mProps.containsKey(entry.getKey().toString()) == false) {
        mProps.setProperty(entry.getKey().toString(), entry.getValue().toString());
        dirtyProps = true;
      }
    }

    if (dirtyProps) {
      writeProperties();
    }

  }

  public String getProperty(String key) {
    String retString = null;

    if (mProps.containsKey(key)) {
      retString = mProps.getProperty(key);
    }
    return retString;
  }

  /*
   * public String getPropertyOrDefault(String key, String defaultStr) { String
   * retString = defaultStr;
   * 
   * if (mProps.containsKey(key)) { retString = mProps.getProperty(key); }
   * return retString; }
   */

  public void setProperty(String key, String value) {
    mProps.setProperty(key, value);
  }

  public void removeProperty(String key) {
    if (key != null) {
      mProps.remove(key);
    }
  }

  public boolean containsKey(String key) {
    return mProps.containsKey(key);
  }

  private void readProperties() {
    try {
      if (mConfigFile == null) {
        mConfigFile = new File(ODKFileUtils.getAppFolder("survey"),
            ODK_SURVEY_CONFIG_PROPERTIES_FILENAME);
      }

      FileInputStream configFileInputStream = new FileInputStream(mConfigFile);

      mProps.load(configFileInputStream);
      configFileInputStream.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void writeProperties() {
    try {
      if (mTempConfigFile == null) {
        mTempConfigFile = new File(ODKFileUtils.getAppFolder("survey"),
            ODK_SURVEY_TEMP_CONFIG_PROPERTIES_FILENAME);
      }
      FileOutputStream configFileOutputStream = new FileOutputStream(mTempConfigFile, false);

      mProps.store(configFileOutputStream, null);
      configFileOutputStream.close();

      if (mConfigFile == null) {
        mConfigFile = new File(ODKFileUtils.getAppFolder("survey"),
            ODK_SURVEY_CONFIG_PROPERTIES_FILENAME);
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
