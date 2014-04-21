/*
 * Copyright (C) 2013 University of Washington
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

package org.opendatakit.survey.android.tasks;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.listeners.CopyExpansionFilesListener;

import android.app.Application;
import android.content.res.AssetFileDescriptor;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Background task for exploding the built-in zipfile resource into the framework
 * directory of the application.
 *
 * @author mitchellsundt@gmail.com
 */
public class CopyExpansionFilesTask extends AsyncTask<Void, String, ArrayList<String>> {

  private static final String t = "CopyExpansionFilesTask";

  private Application appContext;
  private CopyExpansionFilesListener mStateListener;
  private String appName;

  private boolean mSuccess = false;
  private ArrayList<String> mResult = new ArrayList<String>();

  private boolean mPendingSuccess = false;

  @Override
  protected ArrayList<String> doInBackground(Void... values) {
    mPendingSuccess = true;

    String message = null;
    ArrayList<String> result = new ArrayList<String>();

    AssetFileDescriptor fd = null;
    try {
      fd = appContext.getResources().openRawResourceFd(R.raw.zipfile);
      long size = fd.getLength();
      InputStream rawInputStream = null;
      try {
        rawInputStream = fd.createInputStream();
        ZipInputStream zipInputStream = null;
        ZipEntry entry = null;
        try {
          zipInputStream = new ZipInputStream(rawInputStream);
          int nFiles = 0;
          while ((entry = zipInputStream.getNextEntry()) != null) {
            message = null;
            if (isCancelled()) {
              message = "cancelled";
              result.add(entry.getName() + " " + message);
              break;
            }
            ++nFiles;
            File tempFile = new File(ODKFileUtils.getAppFolder(appName), entry.getName());
            String formattedString = appContext.getString(R.string.expansion_unzipping,
                entry.getName());
            publishProgress(formattedString, Integer.valueOf(nFiles).toString(),
                Long.toString(size));
            if (entry.isDirectory()) {
              tempFile.mkdirs();
            } else {
              int bufferSize = 8192;
              OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile, false),
                  bufferSize);
              byte buffer[] = new byte[bufferSize];
              long count = 0L;
              int bread;
              while ((bread = zipInputStream.read(buffer)) != -1) {
                count += bread;
                out.write(buffer, 0, bread);
              }
              out.flush();
              out.close();

              publishProgress(appContext.getString(R.string.expanding_file, tempFile.getAbsolutePath()),
                  Long.toString(count), Long.toString(size));

            }
            Log.i(t, "Extracted ZipEntry: " + entry.getName());

            message = appContext.getString(R.string.success);
            result.add(entry.getName() + " " + message);
          }
        } catch (IOException e) {
          e.printStackTrace();
          mPendingSuccess = false;
          if (e.getCause() != null) {
            message = e.getCause().getMessage();
          } else {
            message = e.getMessage();
          }
          if ( entry != null ) {
            result.add(entry.getName() + " " + message);
          } else {
            result.add("Error accessing zipfile resource " + message);
          }
        } finally {
          if (zipInputStream != null) {
            try {
              zipInputStream.close();
            } catch (IOException e) {
              e.printStackTrace();
              Log.e(t, "Closing of ZipFile failed: " + e.toString());
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        mPendingSuccess = false;
        if (e.getCause() != null) {
          message = e.getCause().getMessage();
        } else {
          message = e.getMessage();
        }
        result.add("Error accessing zipfile resource " + message);
      } finally {
        if ( rawInputStream != null) {
          try {
            rawInputStream.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    } finally {
      if ( fd != null ) {
        try {
          fd.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else {
        result.add("Error accessing zipfile resource.");
      }
    }

    return result;
  }

  @Override
  protected void onPostExecute(ArrayList<String> result) {
    synchronized (this) {
      mResult = result;
      mSuccess = mPendingSuccess;
      if (mStateListener != null) {
        mStateListener.copyExpansionFilesComplete(mSuccess, mResult);
      }
    }
  }

  @Override
  protected void onCancelled(ArrayList<String> result) {
    synchronized (this) {
      // can be null if cancelled before task executes
      mResult = (result == null) ? new ArrayList<String>() : result;
      mSuccess = false;
      if (mStateListener != null) {
        mStateListener.copyExpansionFilesComplete(mSuccess, mResult);
      }
    }
  }

  @Override
  protected void onProgressUpdate(String... values) {
    synchronized (this) {
      if (mStateListener != null) {
        // update progress and total
        mStateListener.copyProgressUpdate(values[0], Integer.valueOf(values[1]),
            Integer.valueOf(values[2]));
      }
    }

  }

  public boolean getOverallSuccess() {
    return mSuccess;
  }

  public ArrayList<String> getResult() {
    return mResult;
  }

  public void setCopyExpansionFilesListener(CopyExpansionFilesListener sl) {
    synchronized (this) {
      mStateListener = sl;
    }
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
