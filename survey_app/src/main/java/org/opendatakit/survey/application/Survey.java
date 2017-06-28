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
package org.opendatakit.survey.application;

import android.app.Activity;
import android.app.Application;
import org.opendatakit.application.CommonApplication;
import org.opendatakit.survey.R;

/**
 * Extends the Application class to implement
 *
 * @author carlhartung
 *
 */
public class Survey extends CommonApplication {
  /**
   * Used for logging
   */
  @SuppressWarnings("unused")
  public static final String TAG = Survey.class.getSimpleName();

  /**
   * Gets the application singleton that can be used to get the database interface
   * @param act an activity to pull the application from
   * @return act.getApplication() if possible
   */
  public static CommonApplication getInstance(Activity act) {
    Application app = act.getApplication();
    if (app instanceof CommonApplication) {
      return (CommonApplication) app;
    }
    throw new IllegalArgumentException("Bad application");
  }

  @Override
  public int getApkDisplayNameResourceId() {
    return R.string.app_name;
  }

  @Override
  public int getConfigZipResourceId() {
    return R.raw.configzip;
  }

  @Override
  public int getSystemZipResourceId() {
    return R.raw.systemzip;
  }

  @Override
  public int getWebKitResourceId() {
    return R.id.webkit;
  }
}
