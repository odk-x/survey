package org.opendatakit.survey.android.logic;

import java.io.File;
import java.io.FileFilter;

import org.opendatakit.common.android.logic.PropertyManager.DynamicPropertiesInterface;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.preferences.PreferencesActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Implements property access methods that return dynamic values
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class DynamicPropertiesCallback implements DynamicPropertiesInterface {

  Context ctxt;
  String appName;
  String tableId;
  String instanceId;

  public DynamicPropertiesCallback(Context ctxt, String appName, String tableId, String instanceId) {
    this.ctxt = ctxt;
    this.appName = appName;
    this.tableId = tableId;
    this.instanceId = instanceId;
  }

  @Override
  public String getUsername() {
    // Get the user name from the settings
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctxt);
    return settings.getString(PreferencesActivity.KEY_USERNAME, null);
  }

  @Override
  public String getUserEmail() {
    // Get the user email from the settings
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctxt);
    return settings.getString(PreferencesActivity.KEY_ACCOUNT, null);
  }

  @Override
  public String getInstanceDirectory() {
    String mediaPath = ODKFileUtils.getInstanceFolder(appName, tableId, instanceId);
    return mediaPath;
  }

  @Override
  public String getNewInstanceFile(String extension) {
    String mediaPath = ODKFileUtils.getInstanceFolder(appName, tableId, instanceId);
    File f = new File(mediaPath);
    String chosenFileName;
    for (;;) {
      final String fileName = Long.toString(System.currentTimeMillis());
      chosenFileName = fileName;
      // see if there are any files with this fileName, with or without file extensions
      File[] files = f.listFiles(new FileFilter() {

        @Override
        public boolean accept(File pathname) {
          String name = pathname.getName();
          if ( !name.startsWith(fileName) ) {
            return false;
          }
          // strip of any extension...
          int idx = name.indexOf('.');
          if ( idx != -1 ) {
            String  firstPart = name.substring(0,idx);
            if (firstPart.equals(fileName)) {
              return true;
            }
          } else if (name.equals(fileName)) {
            return true;
          }
          return false;
        }
      });
      if (files == null || files.length == 0) {
        break;
      }
    }
    String filePath = mediaPath + File.separator + chosenFileName
        + ((extension != null && extension.length() > 0) ? ("." + extension) : "");
    return filePath;
  }
}