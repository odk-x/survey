/*
 * Copyright (C) 2015 University of Washington
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

import java.util.TreeMap;

import org.opendatakit.common.android.logic.CommonToolProperties;
import org.opendatakit.common.android.logic.PropertiesSingleton;
import org.opendatakit.common.android.logic.PropertiesSingletonFactory;
import org.opendatakit.survey.android.R;

import android.content.Context;

public class SurveyToolProperties {

  /****************************************************************
   * CommonToolPropertiesSingletonFactory (opendatakit.properties)
   */
  
  /*******************
   * General Settings
   */

  public static final String KEY_PROTOCOL = "legacy.protocol";

  public static final String KEY_FORMLIST_URL = "legacy.formlist_url";
  public static final String KEY_SUBMISSION_URL = "legacy.submission_url";

  public static final String KEY_SHOW_SPLASH = "survey.showSplash";
  public static final String KEY_SPLASH_PATH = "survey.splashPath";

  public static final String PROTOCOL_ODK_DEFAULT = "odk_default";
  public static final String PROTOCOL_OTHER = "";

  /*******************
   * Admin Settings 
   */

  // keys for each preference
  // main menu
  public static final String KEY_EDIT_SAVED = "survey.edit_saved";
  public static final String KEY_SEND_FINALIZED = "survey.send_finalized";
  public static final String KEY_GET_BLANK = "survey.get_blank";
  public static final String KEY_MANAGE_FORMS = "survey.delete_saved";
  // client
  public static final String KEY_SHOW_SPLASH_SCREEN = "survey.show_splash_screen";
  public static final String KEY_SELECT_SPLASH_SCREEN = "survey.select_splash_screen";
  // form entry
  public static final String KEY_ACCESS_SETTINGS = "survey.access_settings";
  public static final String KEY_CHANGE_LANGUAGE = "survey.change_language";
  public static final String KEY_JUMP_TO = "survey.jump_to";

  /***********************************************************************
   * Secure properties (always move into appName-secure location).
   * e.g., authentication codes and passwords.
   */

  /*******************
   * General Settings
   */
  
  public static final String KEY_LAST_VERSION = "survey.lastVersion";
  public static final String KEY_FIRST_RUN = "survey.firstRun";

  
  public static void accumulateProperties( Context context, 
      TreeMap<String,String> plainProperties, TreeMap<String,String> secureProperties) {
    
    // Set default values as necessary
    
    // the properties managed through the general settings pages.
    // no defaults for these:
    // PreferencesActivity.KEY_USERNAME;
    // PreferencesActivity.KEY_PASSWORD;
    plainProperties.put(KEY_SHOW_SPLASH, "false");
    plainProperties.put(KEY_SPLASH_PATH,
        context.getString(R.string.default_splash_path));
    plainProperties.put(KEY_FORMLIST_URL,
        context.getString(R.string.default_odk_formlist));
    plainProperties.put(KEY_SUBMISSION_URL,
        context.getString(R.string.default_odk_submission));

    // the properties that are managed through the admin settings pages.

    plainProperties.put(KEY_GET_BLANK, "true");
    plainProperties.put(KEY_SEND_FINALIZED, "true");
    plainProperties.put(KEY_MANAGE_FORMS, "true");
    plainProperties.put(KEY_ACCESS_SETTINGS, "true");
    plainProperties.put(KEY_SELECT_SPLASH_SCREEN, "true");
    plainProperties.put(KEY_SHOW_SPLASH_SCREEN, "true");

    // handle the secure properties. If these are in the incoming property file,
    // remove them and move them into the secure properties area.
    //

    secureProperties.put(KEY_LAST_VERSION, "");
    secureProperties.put(KEY_FIRST_RUN, "true");
    
    /////
  }

  private static class SurveyPropertiesSingletonFactory extends PropertiesSingletonFactory {

    private SurveyPropertiesSingletonFactory(TreeMap<String,String> generalDefaults, TreeMap<String,String> adminDefaults) {
      super(generalDefaults, adminDefaults);
    }
  }
  
  private static SurveyPropertiesSingletonFactory factory = null;
  
  public static synchronized PropertiesSingleton get(Context context, String appName) {
    if ( factory == null ) {
      TreeMap<String,String> plainProperties = new TreeMap<String,String>();
      TreeMap<String,String> secureProperties = new TreeMap<String,String>();
      
      CommonToolProperties.accumulateProperties(context, plainProperties, secureProperties);
      SurveyToolProperties.accumulateProperties(context, plainProperties, secureProperties);
      
      factory = new SurveyPropertiesSingletonFactory(plainProperties, secureProperties);
    }
    return factory.getSingleton(context, appName);
  }

}
