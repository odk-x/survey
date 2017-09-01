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
import java.util.HashSet;
import java.util.Set;

import org.opendatakit.consts.IntentConsts;
import org.opendatakit.activities.BaseActivity;
import org.opendatakit.utilities.ODKFileUtils;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.survey.R;
import org.opendatakit.utilities.RuntimePermissionUtils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v13.app.ActivityCompat;
import android.widget.Toast;

public class GeoPointActivity extends BaseActivity implements LocationListener, ActivityCompat.OnRequestPermissionsResultCallback {
  private static final String t = "GeoPointActivity";

  // default location accuracy
  private static final double LOCATION_ACCURACY = 5;

  private ProgressDialog mLocationDialog;
  private LocationManager mLocationManager;
  private Location mLocation;
  private Set<String> mEnabledProviders;
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
    mEnabledProviders = new HashSet<>();

    // only check for fine location
    // but request coarse and fine in case we can only get coarse
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(
          this,
          new String[] {
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION
          },
          0
      );
    } else {
      checkAndEnableProvider(LocationManager.GPS_PROVIDER);
      checkAndEnableProvider(LocationManager.GPS_PROVIDER);

      if (mEnabledProviders.size() < 1) {
        Toast
            .makeText(this, getString(R.string.provider_disabled_error), Toast.LENGTH_SHORT)
            .show();
      }
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

  @SuppressLint("MissingPermission") // checked
  @Override
  protected void onResume() {
    super.onResume();

    if (mEnabledProviders.size() > 0) {
      for (String provider : mEnabledProviders) {
        mLocationManager.requestLocationUpdates(provider, 0, 0, this);
      }

      mLocationDialog.show();
    }
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

  private String truncateDouble(float number) {
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

  @SuppressLint("MissingPermission") // checked in helper method
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    checkAndEnableProvider(LocationManager.GPS_PROVIDER);
    checkAndEnableProvider(LocationManager.NETWORK_PROVIDER);

    if (mEnabledProviders.size() > 0) {
      for (String provider : mEnabledProviders) {
        mLocationManager.requestLocationUpdates(provider, 0, 0, this);
      }

      mLocationDialog.show();
      return;
    }

    if (RuntimePermissionUtils.shouldShowAnyPermissionRationale(this, permissions)) {
      RuntimePermissionUtils.createPermissionRationaleDialog(this, requestCode, permissions)
          .setMessage(R.string.location_permission_rationale)
          .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              dialog.cancel();
              finish();
            }
          })
          .show();
    } else {
      Toast
          .makeText(GeoPointActivity.this, R.string.location_permission_perm_denied, Toast.LENGTH_LONG)
          .show();
      finish();
    }
  }

  private void checkAndEnableProvider(String provider) {
    if (mLocationManager == null) {
      return;
    }

    switch (provider) {
      case LocationManager.GPS_PROVIDER:
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED &&
            mLocationManager.isProviderEnabled(provider)) {
          mEnabledProviders.add(provider);
        }
        break;

      case LocationManager.NETWORK_PROVIDER:
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED &&
            mLocationManager.isProviderEnabled(provider)) {
          mEnabledProviders.add(provider);
        }
        break;

      default:
        break;
    }
  }
}
