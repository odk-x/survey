/*
 * Copyright (C) 2012-2013 University of Washington
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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import org.opendatakit.activities.BaseActivity;
import org.opendatakit.application.ToolAwareApplication;
import org.opendatakit.consts.IntentConsts;
import org.opendatakit.dependencies.DependencyChecker;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.properties.CommonToolProperties;
import org.opendatakit.properties.PropertiesSingleton;
import org.opendatakit.provider.FormsProviderAPI;
import org.opendatakit.survey.R;
import org.opendatakit.utilities.ODKFileUtils;
import org.opendatakit.webkitserver.utilities.UrlUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Code to display a splash screen
 *
 * @author Carl Hartung
 */
public class SplashScreenActivity extends BaseActivity {

  private static final String TAG = SplashScreenActivity.class.getSimpleName();
  private static final boolean EXIT = true;
  private static final int ACTION_CODE = 1;
  private int mImageMaxWidth;
  //private int mSplashTimeout = 10000; // milliseconds
  //private int mSplashTimeout = 2000;
  private int mSplashTimeout = 0;
  private String appName;
  /**
   * We need this variable here (and we need to save it to the saved instance state) because if
   * we start MainMenuActivity, then get destroyed, then MainMenuActivity finish()-es back to us,
   * it will instantiate a new SplashScreenActivity all over again, and the SplashScreenActivity
   * will think that it's supposed to be starting MainMenuActivity even if we were actually
   * supposed to finish() back to Tables. That was a really hard bug to track down.
   */
  private boolean started = false;

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putBoolean("started", started);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (savedInstanceState != null && savedInstanceState.containsKey("started")) {
      started = savedInstanceState.getBoolean("started");
    }

    // verify that the external SD Card is available.
    try {
      ODKFileUtils.verifyExternalStorageAvailability();
    } catch (RuntimeException e) {
      createErrorDialog(e.getMessage(), EXIT);
      return;
    }

    DisplayMetrics dm = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(dm);
    mImageMaxWidth = dm.widthPixels;

    // this splash screen should be a blank slate
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.splash_screen);

    // external intent
    appName = getIntent().getStringExtra(IntentConsts.INTENT_KEY_APP_NAME);
    if (appName == null) {
      appName = ODKFileUtils.getOdkDefaultAppName();
    }

    Uri uri = getIntent().getData();
    if (uri != null) {
      // initialize to the URI, then we will customize further based upon the
      // savedInstanceState...
      final Uri uriFormsProvider = FormsProviderAPI.CONTENT_URI;
      final Uri uriWebView = UrlUtils.getWebViewContentUri(this);
      if (uri.getScheme().equalsIgnoreCase(uriFormsProvider.getScheme()) && uri.getAuthority()
          .equalsIgnoreCase(uriFormsProvider.getAuthority())) {
        List<String> segments = uri.getPathSegments();
        if (segments != null && segments.size() >= 1) {
          appName = segments.get(0);
        } else {
          String err = "Invalid " + uri + " uri. Expected two segments.";
          WebLogger.getLogger(appName).e(TAG, err);
          Intent i = new Intent();
          setResult(RESULT_CANCELED, i);
          finish();
          return;
        }
      } else if (uri.getScheme().equals(uriWebView.getScheme()) && uri.getAuthority()
          .equals(uriWebView.getAuthority()) && uri.getPort() == uriWebView.getPort()) {
        List<String> segments = uri.getPathSegments();
        if (segments != null && segments.size() == 1) {
          appName = segments.get(0);
        } else {
          String err =
              "Invalid " + uri + " uri. Expected one segment (the application name).";
          WebLogger.getLogger(appName).e(TAG, err);
          Intent i = new Intent();
          setResult(RESULT_CANCELED, i);
          finish();
          return;
        }
      } else {
        // TODO why in the world is a log message being translated
        String err = getString(R.string.unrecognized_uri, uri.toString(), uriWebView.toString(),
            uriFormsProvider.toString());
        WebLogger.getLogger(appName).e(TAG, err);
        Intent i = new Intent();
        setResult(RESULT_CANCELED, i);
        finish();
        return;
      }
    }
    WebLogger.getLogger(appName).i(TAG, "SplashScreenActivity appName: " + appName);

    // get the package info object with version number
    PackageInfo packageInfo = null;
    try {
      packageInfo = getPackageManager()
          .getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
    } catch (NameNotFoundException e) {
      WebLogger.getLogger(getAppName()).printStackTrace(e);
    }

    boolean dependable = DependencyChecker.checkDependencies(this);
    if (!dependable) { // dependencies missing
      return;
    }

    PropertiesSingleton props = CommonToolProperties.get(getApplicationContext(), appName);

    String toolFirstRunKey = PropertiesSingleton
        .toolFirstRunPropertyName(((ToolAwareApplication) getApplication()).getToolName());

    String toolVersionKey = PropertiesSingleton
        .toolVersionPropertyName(((ToolAwareApplication) getApplication()).getToolName());

    Boolean firstRun = props.getBooleanProperty(toolFirstRunKey);
    Boolean showSplash = props.getBooleanProperty(CommonToolProperties.KEY_SHOW_SPLASH);

    String splashPath = props.getProperty(CommonToolProperties.KEY_SPLASH_PATH);

    // if you've increased version code, then update the version number and set firstRun to true
    String sKeyLastVer = props.getProperty(toolVersionKey);
    long keyLastVer =
        sKeyLastVer == null || sKeyLastVer.isEmpty() ? -1L : Long.valueOf(sKeyLastVer);
    if (packageInfo != null && keyLastVer < packageInfo.versionCode) {
      props.setProperties(
          Collections.singletonMap(toolVersionKey, Integer.toString(packageInfo.versionCode)));

      firstRun = true;
    }

    // do all the first run things
    if ((firstRun == null ? true : firstRun) && (showSplash == null ? false : showSplash)) {
      props.setProperties(Collections.singletonMap(toolFirstRunKey, Boolean.toString(false)));
      startSplashScreen(splashPath);
    } else {
      endSplashScreen();
    }
  }

  @Override
  public String getAppName() {
    return appName;
  }

  private void endSplashScreen() {

    // launch new activity and close splash screen
    Uri data = getIntent().getData();
    Bundle extras = getIntent().getExtras();

    Intent i = new Intent(this, MainMenuActivity.class);
    if (data != null) {
      i.setData(data);
    }
    if (extras != null) {
      i.putExtras(extras);
    }
    i.putExtra(IntentConsts.INTENT_KEY_APP_NAME, appName);
    if (!started) {
      started = true;
      startActivityForResult(i, ACTION_CODE);
    } else {
      WebLogger.getLogger(appName).i(TAG, "Don't know what to do here");
      // Don't need to finish() because that's handled by onActivityResult
    }
  }

  // decodes image and scales it to reduce memory consumption
  private Bitmap decodeFile(File f) {
    Bitmap b = null;
    try {
      // Decode image size
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inJustDecodeBounds = true;

      FileInputStream fis = new FileInputStream(f);
      BitmapFactory.decodeStream(fis, null, o);
      try {
        fis.close();
      } catch (IOException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
      }

      long scale = 1;
      if (o.outHeight > mImageMaxWidth || o.outWidth > mImageMaxWidth) {
        scale = Math.round(Math.pow(2, Math.round(
            Math.log(mImageMaxWidth / (double) Math.max(o.outHeight, o.outWidth)) / Math
                .log(0.5))));
      }

      // Decode with inSampleSize
      BitmapFactory.Options o2 = new BitmapFactory.Options();
      if (scale > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Image too large");
      }
      //noinspection NumericCastThatLosesPrecision
      o2.inSampleSize = (int) scale;
      fis = new FileInputStream(f);
      b = BitmapFactory.decodeStream(fis, null, o2);
      try {
        fis.close();
      } catch (IOException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
      }
    } catch (FileNotFoundException e) {
      WebLogger.getLogger(getAppName()).printStackTrace(e);
    }
    return b;
  }

  private void startSplashScreen(String path) {

    // add items to the splash screen here. makes things less distracting.
    ImageView iv = (ImageView) findViewById(R.id.splash);
    View ll = findViewById(R.id.splash_default);

    File f = new File(path);
    if (f.exists()) {
      iv.setImageBitmap(decodeFile(f));
      ll.setVisibility(View.GONE);
      iv.setVisibility(View.VISIBLE);
    }

    // create a thread that counts up to the timeout
    Thread t = new Thread() {
      @Override
      public void run() {
        int count = 0;
        try {
          super.run();
          while (count <= mSplashTimeout) {
            sleep(100);
            count += 100;
          }
        } catch (Exception e) {
          WebLogger.getLogger(getAppName()).printStackTrace(e);
        } finally {
          endSplashScreen();
        }
      }
    };
    t.start();
  }

  private void createErrorDialog(CharSequence errorMsg, final boolean shouldExit) {
    AlertDialog mAlertDialog = new AlertDialog.Builder(this).create();
    mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
    mAlertDialog.setMessage(errorMsg);
    DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int i) {
        if (shouldExit && i == DialogInterface.BUTTON_POSITIVE) {
          finish();
        }
      }
    };
    mAlertDialog.setCancelable(false);
    mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), errorListener);
    mAlertDialog.show();
  }

  @Override
  public void databaseAvailable() {
  }

  @Override
  public void databaseUnavailable() {
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    setResult(resultCode, intent);
    WebLogger.getLogger(appName).i(TAG,
        "MainMenu finish()'d back to SplashScreen: " + (requestCode == ACTION_CODE ?
            "true" :
            "THIS SHOULDN'T HAPPEN"));
    WebLogger.getLogger(appName).i(TAG, "SplashScreenActivity finish()-ing back to " + (
        getCallingActivity() == null ?
            null :
            getCallingActivity().getClassName()));
    finish();
  }
}
