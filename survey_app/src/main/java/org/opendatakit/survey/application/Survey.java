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

import org.opendatakit.application.CommonApplication;
import org.opendatakit.properties.CommonToolProperties;
import org.opendatakit.properties.PropertiesSingleton;
import org.opendatakit.survey.R;

import com.google.firebase.analytics.FirebaseAnalytics ;

import android.content.Context;
import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;

import android.annotation.SuppressLint;

/**
 * Extends the Application class to implement
 *
 * @author carlhartung
 *
 */
public class Survey extends CommonApplication {
  public static final String t = "Survey";

  private FirebaseAnalytics analytics;

  private static Survey singleton = null;

  public static Survey getInstance() {
    return singleton;
  }

  @SuppressLint("NewApi")
  @Override
  public void onCreate() {
    singleton = this;

    super.onCreate();

    Fabric.with(this, new Crashlytics());
    analytics = FirebaseAnalytics.getInstance(this);
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
