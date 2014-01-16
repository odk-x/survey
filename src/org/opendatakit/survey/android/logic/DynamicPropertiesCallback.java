/*
 * Copyright (C) 2013 University of Washington
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
import java.io.FileFilter;

import org.opendatakit.common.android.logic.PropertyManager.DynamicPropertiesInterface;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.preferences.PreferencesActivity;

import android.content.Context;

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
    // Get the user name
    PropertiesSingleton propSingleton = PropertiesSingleton.INSTANCE;
    return propSingleton.getProperty(PreferencesActivity.KEY_USERNAME);
  }

  @Override
  public String getUserEmail() {
    // Get the user email
    PropertiesSingleton propSingleton = PropertiesSingleton.INSTANCE;
    return propSingleton.getProperty(PreferencesActivity.KEY_ACCOUNT);
  }

  @Override
  public String getAppName() {
    return appName;
  }

  @Override
  public String getInstanceDirectory() {
    String mediaPath = ODKFileUtils.getInstanceFolder(appName, tableId, instanceId);
    return mediaPath;
  }

  @Override
  public String getUriFragmentNewInstanceFile(String uriDeviceId, String extension) {
    String mediaPath = ODKFileUtils.getInstanceFolder(appName, tableId, instanceId);
    File f = new File(mediaPath);
    f.mkdirs();
    String chosenFileName;
    for (;;) {
      String candidate = Long.toString(System.currentTimeMillis()) + "_" + uriDeviceId;
      final String fileName = candidate.replaceAll("[\\p{Punct}\\p{Space}]", "_");
      chosenFileName = fileName;
      // see if there are any files with this fileName, with or without file
      // extensions
      File[] files = f.listFiles(new FileFilter() {

        @Override
        public boolean accept(File pathname) {
          String name = pathname.getName();
          if (!name.startsWith(fileName)) {
            return false;
          }
          // strip of any extension...
          int idx = name.indexOf('.');
          if (idx != -1) {
            String firstPart = name.substring(0, idx);
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
    return ODKFileUtils.asUriFragment(appName, new File(filePath));
  }
}