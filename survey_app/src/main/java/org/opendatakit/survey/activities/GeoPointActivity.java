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

package org.opendatakit.survey.activities;

import java.text.DecimalFormat;
import java.util.List;

import org.opendatakit.consts.IntentConsts;
import org.opendatakit.activities.BaseActivity;
import org.opendatakit.utilities.ODKFileUtils;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.survey.R;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.widget.Toast;

public class GeoPointActivity extends BaseActivity implements LocationListener {
  private static final String t = "GeoPointActivity";

  // default location accuracy
  private static final double LOCATION_ACCURACY = 5;

  private ProgressDialog mLocationDialog;
  private LocationManager mLocationManager;
  private Location mLocation;
  private boolean mGPSOn = false;
  private boolean mNetworkOn = false;
  private String mAppName;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mAppName = this.getIntent().getStringExtra(IntentConsts.INTENT_KEY_APP_NAME);
    if ( mAppName == null || mAppName.length() == 0 ) {
      mAppName = ODKFileUtils.getOdkDefaultAppName();
    }
    WebLogger.getLogger(mAppName).i(t, t + ".onCreate appName=" + mAppName);

    setTitle(mAppName + " > " + getString(R.string.get_location));

    mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

    // make sure we have a good location provider before continuing
    List<String> providers = mLocationManager.getProviders(true);
    for (String provider : providers) {
      if (provider.equalsIgnoreCase(LocationManager.GPS_PROVIDER)) {
        mGPSOn = true;
      }
      if (provider.equalsIgnoreCase(LocationManager.NETWORK_PROVIDER)) {
        mNetworkOn = true;
      }
    }
    if (!mGPSOn && !mNetworkOn) {
      Toast.makeText(this, getString(R.string.provider_disabled_error),
          Toast.LENGTH_SHORT).show();
      finish();
    }

    setupLocationDialog();

  }
  
  @Override
  public String getAppName() {
    return mAppName;
  }

  @Override
  protected void onPause() {
    super.onPause();

    // stops the GPS. Note that this will turn off the GPS if the screen
    // goes to sleep.
    mLocationManager.removeUpdates(this);

    // We're not using managed dialogs, so we have to dismiss the dialog to
    // prevent it from
    // leaking memory.
    if (mLocationDialog != null && mLocationDialog.isShowing())
      mLocationDialog.dismiss();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (mGPSOn) {
      mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }
    if (mNetworkOn) {
      mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
    }
    mLocationDialog.show();
  }

  /**
   * Sets up the look and actions for the progress dialog while the GPS is
   * searching.
   */
  private void setupLocationDialog() {
    // dialog displayed while fetching gps location
    mLocationDialog = new ProgressDialog(this);
    DialogInterface.OnClickListener geopointButtonListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        switch (which) {
        case DialogInterface.BUTTON_POSITIVE:
          returnLocation();
          break;
        case DialogInterface.BUTTON_NEGATIVE:
          mLocation = null;
          finish();
          break;
        default:
          // do nothing
        }
      }
    };

    // back button doesn't cancel
    mLocationDialog.setCancelable(false);
    mLocationDialog.setIndeterminate(true);
    mLocationDialog.setIcon(android.R.drawable.ic_dialog_info);
    mLocationDialog.setTitle(getString(R.string.getting_location));
    mLocationDialog.setMessage(getString(R.string.please_wait_long));
    mLocationDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.accept_location),
        geopointButtonListener);
    mLocationDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel_location),
        geopointButtonListener);
  }

  private void returnLocation() {
    if (mLocation != null) {
      Intent i = new Intent();
      i.putExtra(MainMenuActivity.LOCATION_LATITUDE_RESULT, mLocation.getLatitude());
      i.putExtra(MainMenuActivity.LOCATION_LONGITUDE_RESULT, mLocation.getLongitude());
      i.putExtra(MainMenuActivity.LOCATION_ALTITUDE_RESULT, mLocation.getAltitude());
      i.putExtra(MainMenuActivity.LOCATION_ACCURACY_RESULT, Double.valueOf(mLocation.getAccuracy()));
      setResult(RESULT_OK, i);
    }
    finish();
  }

  @Override
  public void onLocationChanged(Location location) {
    mLocation = location;
    if (mLocation != null) {
      mLocationDialog.setMessage(getString(R.string.location_provider_accuracy,
          mLocation.getProvider(), truncateDouble(mLocation.getAccuracy())));

      if (mLocation.getAccuracy() <= LOCATION_ACCURACY) {
        returnLocation();
      }
    }
  }

  private static String truncateDouble(float number) {
    DecimalFormat df = new DecimalFormat("#.##");
    return df.format(number);
  }

  @Override
  public void onProviderDisabled(String provider) {

  }

  @Override
  public void onProviderEnabled(String provider) {

  }

  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {
    switch (status) {
    case LocationProvider.AVAILABLE:
      if (mLocation != null) {
        mLocationDialog.setMessage(getString(R.string.location_accuracy, truncateDouble(mLocation.getAccuracy())));
      }
      break;
    case LocationProvider.OUT_OF_SERVICE:
      break;
    case LocationProvider.TEMPORARILY_UNAVAILABLE:
      break;
    }
  }

  @Override
  public void databaseAvailable() {
  }

  @Override
  public void databaseUnavailable() {
  }

}
