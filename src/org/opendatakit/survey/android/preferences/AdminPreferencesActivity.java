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

import org.opendatakit.IntentConsts;
import org.opendatakit.common.android.logic.PropertiesSingleton;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.logic.SurveyToolProperties;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

public class AdminPreferencesActivity extends PreferenceActivity implements OnPreferenceChangeListener{

  public static final String ADMIN_PREFERENCES = "admin_prefs";

  private String mAppName;
  
  private PropertiesSingleton mProps;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mAppName = this.getIntent().getStringExtra(IntentConsts.INTENT_KEY_APP_NAME);
    if ( mAppName == null || mAppName.length() == 0 ) {
    	mAppName = "survey";
    }

    setTitle(mAppName + " > " + getString(R.string.admin_preferences));
    
    mProps = SurveyToolProperties.get(this, mAppName);

    addPreferencesFromResource(R.xml.admin_preferences);

    PreferenceScreen prefScreen = this.getPreferenceScreen();

    initializeCheckBoxPreference(prefScreen);
  }

  protected void initializeCheckBoxPreference(PreferenceGroup prefGroup) {

    for ( int i = 0; i < prefGroup.getPreferenceCount(); i++ ) {
      Preference pref = prefGroup.getPreference(i);
      Class c = pref.getClass();
      if (c == CheckBoxPreference.class) {
        CheckBoxPreference checkBoxPref = (CheckBoxPreference)pref;
        if (mProps.containsKey(checkBoxPref.getKey())) {
          String checked = mProps.getProperty(checkBoxPref.getKey());
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
    mProps.setProperty(preference.getKey(), newValue.toString());
    return true;
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mProps.writeProperties();
  }

  @Override
  public void finish() {
    mProps.writeProperties();
    super.finish();
  }
}