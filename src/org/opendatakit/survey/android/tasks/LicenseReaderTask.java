package org.opendatakit.survey.android.tasks;


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.opendatakit.survey.android.listeners.LicenseReaderListener;

import org.opendatakit.common.android.R;

import android.app.Application;
import android.os.AsyncTask;

public class LicenseReaderTask extends AsyncTask<Void, Integer, String> {

  private Application appContext;
  private LicenseReaderListener lrl;
  private String appName;
  private String mResult;

  protected String doInBackground(Void... arg0) {
    
    String result = null;
    StringBuilder interimResult  = null;

    try {
      InputStream licenseInputStream = appContext.getResources().openRawResource(R.raw.license);
      InputStreamReader licenseInputStreamReader = new InputStreamReader(licenseInputStream);
      BufferedReader r = new BufferedReader(licenseInputStreamReader);
      interimResult = new StringBuilder();
      String line;
      while ((line = r.readLine()) != null) {
        interimResult.append(line);
        interimResult.append("\n");
      }
      r.close();
      licenseInputStreamReader.close();
      licenseInputStream.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
    result = interimResult.toString();
    return result;
  }

  @Override
  protected void onPostExecute(String result) {
    synchronized (this) {
      mResult = result;
      appContext = null;
      if (lrl != null) {
        lrl.readLicenseComplete(result);
      }
    }
  }
  
  @Override
  protected void onCancelled(String result) {
    synchronized (this) {
      mResult = result;
      appContext = null;
      // can be null if cancelled before task executes
      if (lrl != null) {
        lrl.readLicenseComplete(result);
      }
    }
  }

  public String getResult() {
    return mResult;
  }

  public void setLicenseReaderListener(LicenseReaderListener listener) {
    lrl = listener;
  }

  public void setAppName(String appName) {
    synchronized (this) {
      this.appName = appName;
    }
  }

  public String getAppName() {
    return appName;
  }

  public void setApplication(Application appContext) {
    synchronized (this) {
      this.appContext = appContext;
    }
  }

  public Application getApplication() {
    return appContext;
  }
}

