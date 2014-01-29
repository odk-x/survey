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

package org.opendatakit.survey.android.preferences;

import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.logic.PropertiesSingleton;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;

public class AdminPreferencesActivity extends PreferenceActivity implements OnPreferenceChangeListener{

  public static final String ADMIN_PREFERENCES = "admin_prefs";

  // key for this preference screen
  public static final String KEY_ADMIN_PW = "admin_pw";

  // keys for each preference
  // main menu
  public static final String KEY_EDIT_SAVED = "edit_saved";
  public static final String KEY_SEND_FINALIZED = "send_finalized";
  public static final String KEY_GET_BLANK = "get_blank";
  public static final String KEY_MANAGE_FORMS = "delete_saved";
  // server
  public static final String KEY_CHANGE_SERVER = "change_server";
  public static final String KEY_CHANGE_USERNAME = "change_username";
  public static final String KEY_CHANGE_PASSWORD = "change_password";
  public static final String KEY_CHANGE_GOOGLE_ACCOUNT = "change_google_account";
  // client
  public static final String KEY_CHANGE_FONT_SIZE = "change_font_size";
  public static final String KEY_SHOW_SPLASH_SCREEN = "show_splash_screen";
  public static final String KEY_SELECT_SPLASH_SCREEN = "select_splash_screen";
  // form entry
  public static final String KEY_ACCESS_SETTINGS = "access_settings";
  public static final String KEY_CHANGE_LANGUAGE = "change_language";
  public static final String KEY_JUMP_TO = "jump_to";
  

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTitle(getString(R.string.app_name) + " > " + getString(R.string.admin_preferences));

    addPreferencesFromResource(R.xml.admin_preferences);
    
    PreferenceScreen prefScreen = this.getPreferenceScreen();
    
    initializeCheckBoxPreference(prefScreen);
  }
  
  protected void initializeCheckBoxPreference(PreferenceGroup prefGroup) {
    PropertiesSingleton propSingleton = PropertiesSingleton.INSTANCE;
  	
    for ( int i = 0; i < prefGroup.getPreferenceCount(); i++ ) {
      Preference pref = prefGroup.getPreference(i);
      Class c = pref.getClass();
      if (c == CheckBoxPreference.class) {
        CheckBoxPreference checkBoxPref = (CheckBoxPreference)pref;
        if (propSingleton.containsKey(checkBoxPref.getKey())) {
          String checked = propSingleton.getProperty(checkBoxPref.getKey());
          if (checked.equals("true")) {
            checkBoxPref.setChecked(true);
          } else {
            checkBoxPref.setChecked(false);
          }
        }	
        // Set the listener
        checkBoxPref.setOnPreferenceChangeListener(this);
      } else if (c == PreferenceCategory.class) {
        // Find CheckBoxPreferences in this category
        PreferenceCategory prefCat = (PreferenceCategory)pref;
        initializeCheckBoxPreference(prefCat);
      }
    }
  }

  /**
   * Generic listener that sets the summary to the newly selected/entered value
   */
  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    PropertiesSingleton propSingleton = PropertiesSingleton.INSTANCE;
    propSingleton.setProperty(preference.getKey(), newValue.toString());
    return true;
  }
  
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    PropertiesSingleton propSingleton = PropertiesSingleton.INSTANCE;
    propSingleton.writeProperties();
  }
  
  @Override
  public void finish() {
    PropertiesSingleton propSingleton = PropertiesSingleton.INSTANCE;
    propSingleton.writeProperties();
    super.finish();
  }
}