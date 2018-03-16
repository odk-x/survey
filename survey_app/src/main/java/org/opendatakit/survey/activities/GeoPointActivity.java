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

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;
import org.opendatakit.activities.BaseActivity;
import org.opendatakit.consts.IntentConsts;
import org.opendatakit.fragment.ProgressDialogFragment;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.survey.R;
import org.opendatakit.utilities.ODKFileUtils;
import org.opendatakit.utilities.RuntimePermissionUtils;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;

public class GeoPointActivity extends BaseActivity implements LocationListener, ActivityCompat
    .OnRequestPermissionsResultCallback, ProgressDialogFragment.ProgressDialogListener {
  private static final String t = GeoPointActivity.class.getSimpleName();

  private static final String PROGRESS_DIALOG_TAG = "progressDialogGeoPoint";

  // default location accuracy
  private static final double LOCATION_ACCURACY = 5;

  private ProgressDialogFragment mLocationProgressDialog;
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
      checkAndEnableProvider(LocationManager.NETWORK_PROVIDER);

      if (mEnabledProviders.size() < 1) {
        Toast
            .makeText(this, getString(R.string.provider_disabled_error), Toast.LENGTH_SHORT)
            .show();
      }
    }

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

  }

  @SuppressLint("MissingPermission") // checked
  @Override
  protected void onResume() {
    super.onResume();

    if (mEnabledProviders.size() > 0) {
      for (String provider : mEnabledProviders) {
        Log.i(t, "requesting location updates from " + provider);
        mLocationManager.requestLocationUpdates(provider, 0, 0, this);
      }

      // show location dialog only if we have at least 1 location provider
      showLocationProgressDialog();
    }
  }


  private void showLocationProgressDialog() {

    mLocationProgressDialog = ProgressDialogFragment.eitherReuseOrCreateNew(
        PROGRESS_DIALOG_TAG, mLocationProgressDialog, getFragmentManager(), getString(R.string
            .getting_location),
        getString(R.string.please_wait_long), true, getString(R.string.accept_location),
        getString(R.string.cancel_location), null);

    if(!mLocationProgressDialog.isAdded()) {
      mLocationProgressDialog.show(getFragmentManager(), PROGRESS_DIALOG_TAG);
    }
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
      mLocationProgressDialog.setMessage(getString(R.string.location_provider_accuracy,
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
    Log.w(t, provider + " disabled");
  }

  @Override
  public void onProviderEnabled(String provider) {
    Log.i(t, provider + " enabled");
  }

  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {
    switch (status) {
    case LocationProvider.AVAILABLE:
      if (mLocation != null) {
        mLocationProgressDialog.setMessage(getString(R.string.location_accuracy, truncateDouble(mLocation.getAccuracy())));
      }
      break;
    case LocationProvider.OUT_OF_SERVICE:
      Log.w(t, provider + " out of service");
      break;
    case LocationProvider.TEMPORARILY_UNAVAILABLE:
      Log.w(t, provider + " temporarily unavailable");
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
        Log.i(t, "requesting location updates from " + provider);
        mLocationManager.requestLocationUpdates(provider, 0, 0, this);
      }

      showLocationProgressDialog();
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

  /**
   * Callback from progressDialogFragment
   * @param dialog the progressDialogFragment that was pressed
   */
  public void onProgressDialogPositiveButtonClick(ProgressDialogFragment dialog) {
    // take location
    returnLocation();
  }

  /**
   * Callback from progressDialogFragment
   * @param dialog the progressDialogFragment that was pressed
   */
  public void onProgressDialogNegativeButtonClick(ProgressDialogFragment dialog) {
    // cancel
    mLocation = null;
    finish();
  }

  /**
   * Callback from progressDialogFragment
   * @param dialog the progressDialogFragment that was pressed
   */
  public void onProgressDialogNeutralButtonClick(ProgressDialogFragment dialog) {
    // do nothing as no button specified
  }
}
