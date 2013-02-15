/*
 * Copyright (C) 2009-2013 University of Washington
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

import org.opendatakit.common.android.logic.PropertyManagerImpl;
import org.opendatakit.survey.android.preferences.PreferencesActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Used to return device properties to JavaRosa
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 * @author mitchellsundt@gmail.com
 */

public class PropertyManager extends PropertyManagerImpl {
	public static final DynamicPropertiesInterface callback = new DynamicPropertiesInterface() {

		@Override
		public String getUsername(Context c) {
			// Get the user name from the settings
			SharedPreferences settings = PreferenceManager
					.getDefaultSharedPreferences(c);
			return settings.getString(PreferencesActivity.KEY_USERNAME, null);
		}

		@Override
		public String getUserEmail(Context c) {
			// Get the user email from the settings
			SharedPreferences settings = PreferenceManager
					.getDefaultSharedPreferences(c);
			return settings.getString(PreferencesActivity.KEY_ACCOUNT, null);
		}
	};

	/**
	 * Constructor used within the Application object to create a singleton of
	 * the property manager. Access it through
	 * Survey.getInstance().getPropertyManager()
	 *
	 * @param context
	 */
	public PropertyManager(Context context) {
		super(context,callback);
	}
}
