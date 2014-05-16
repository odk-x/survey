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
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.opendatakit.common.android.logic.FormInfo;
import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.listeners.InitializationListener;
import org.opendatakit.survey.android.provider.FormsProviderAPI;

import android.app.Application;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Background task for exploding the built-in zipfile resource into the framework
 * directory of the application and doing forms discovery on this appName.
 *
 * @author mitchellsundt@gmail.com
 */
public class InitializationTask extends AsyncTask<Void, String, ArrayList<String>> {

  private static final String t = "InitializationTask";

  private Application appContext;
  private InitializationListener mStateListener;
  private String appName;

  private boolean mSuccess = false;
  private ArrayList<String> mResult = new ArrayList<String>();

  private boolean mPendingSuccess = false;

  @Override
  protected ArrayList<String> doInBackground(Void... values) {
    mPendingSuccess = true;

    String message = null;
    ArrayList<String> result = new ArrayList<String>();

    /////////////////////////////////////////////////
    // check that the framework zip has been exploded
    if (!ODKFileUtils.isConfiguredTablesApp(appName, Survey.getInstance().getVersionCodeString()) ) {
      publishProgress(appContext.getString(R.string.expansion_unzipping_begins), null);

      extractFromRawZip(R.raw.frameworkzip, true, result);
      extractFromRawZip(R.raw.assetszip, false, result);

      ODKFileUtils.assertConfiguredTablesApp(appName, Survey.getInstance().getVersionCodeString());
    }

    ///////////////////////////////////////////
    ///////////////////////////////////////////
    ///////////////////////////////////////////
    // register the framework form
    // TODO: make this go away!
    updateFormDir(new File(ODKFileUtils.getFrameworkFolder(appName)), false,
                  ODKFileUtils.getStaleFrameworkFolder(appName) + File.separator);

    // and now scan for new forms...

    String completionString = appContext.getString(R.string.searching_for_form_defs);
    publishProgress(completionString, null);

    File tablesDir = new File(ODKFileUtils.getTablesFolder(appName));

    File[] tableIdDirs = tablesDir.listFiles(new FileFilter() {

      @Override
      public boolean accept(File pathname) {
        return pathname.isDirectory();
      }});

    List<File> formDirs = new ArrayList<File>();
    for ( File tableIdDir : tableIdDirs ) {
      String tableId = tableIdDir.getName();

      File formDir = new File(ODKFileUtils.getFormsFolder(appName, tableId));
      File[] formIdDirs = formDir.listFiles(new FileFilter() {

        @Override
        public boolean accept(File pathname) {
          File formDef = new File(pathname, ODKFileUtils.FORMDEF_JSON_FILENAME);
          return pathname.isDirectory() && formDef.exists() && formDef.isFile();
        }});

      if ( formIdDirs != null ) {
        formDirs.addAll(Arrays.asList(formIdDirs));
      }
    }

    ///////////////////////////////////////////
    // remove forms that no longer exist
    // remove the forms that haven't changed
    // from the discovered list
    removeStaleFormInfo(formDirs);

    // this is the complete list of forms we need to scan and possibly add
    // to the FormsProvider
    for ( int i = 0 ; i < formDirs.size() ; ++i ) {
      File formDir = formDirs.get(i);
      // specifically target this form...
      Log.i(t, "updateFormInfo: form: " + formDir.getAbsolutePath());

      String examString = appContext.getString(R.string.updating_form_information, formDir.getName(), i, formDirs.size());
      publishProgress(examString, null);

      updateFormDir(formDir, true, ODKFileUtils.getStaleFormsFolder(appName) + File.separator);
    }

    return result;
  }

  private final void extractFromRawZip(int resourceId, boolean overwrite, ArrayList<String> result) {
    String message = null;
    AssetFileDescriptor fd = null;
    try {
      fd = appContext.getResources().openRawResourceFd(resourceId);
      final long size = fd.getLength(); // apparently over-counts by 2x?
      InputStream rawInputStream = null;
      try {
        rawInputStream = fd.createInputStream();
        ZipInputStream zipInputStream = null;
        ZipEntry entry = null;
        try {

          // count the number of files in the zip
          zipInputStream = new ZipInputStream(rawInputStream);
          int totalFiles = 0;
          while ((entry = zipInputStream.getNextEntry()) != null) {
            message = null;
            if (isCancelled()) {
              message = "cancelled";
              result.add(entry.getName() + " " + message);
              break;
            }
            ++totalFiles;
          }
          zipInputStream.close();

          // and re-open the stream, reading it this time...
          fd = appContext.getResources().openRawResourceFd(resourceId);
          rawInputStream = fd.createInputStream();
          zipInputStream = new ZipInputStream(rawInputStream);

          long bytesProcessed = 0L;
          long lastBytesProcessedThousands = 0L;
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
            String formattedString = appContext.getString(R.string.expansion_unzipping_without_detail,
                entry.getName(), nFiles, totalFiles);
            String detail;
            if (entry.isDirectory()) {
              detail = appContext.getString(R.string.expansion_create_dir_detail);
              publishProgress(formattedString, detail);
              tempFile.mkdirs();
            } else if ( overwrite || !tempFile.exists() ) {
              int bufferSize = 8192;
              OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile, false),
                  bufferSize);
              byte buffer[] = new byte[bufferSize];
              int bread;
              while ((bread = zipInputStream.read(buffer)) != -1) {
                bytesProcessed += bread;
                long curThousands = (bytesProcessed / 1000L);
                if ( curThousands != lastBytesProcessedThousands ) {
                  detail = appContext.getString(R.string.expansion_unzipping_detail, bytesProcessed, size);
                  publishProgress(formattedString, detail);
                  lastBytesProcessedThousands = curThousands;
                }
                out.write(buffer, 0, bread);
              }
              out.flush();
              out.close();

              detail = appContext.getString(R.string.expansion_unzipping_detail, bytesProcessed, size);
              publishProgress(formattedString, detail);
            }
            Log.i(t, "Extracted ZipEntry: " + entry.getName());

            message = appContext.getString(R.string.success);
            result.add(entry.getName() + " " + message);
          }

          String completionString = appContext.getString(R.string.expansion_unzipping_complete, totalFiles);
          publishProgress(completionString, null);
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
  }

  /**
   * Remove definitions from the Forms database that are no longer present on
   * disk.
   */
  private final void removeStaleFormInfo(List<File> discoveredFormDefDirs) {
    Uri formsProviderContentUri = Uri.parse("content://" + FormsProviderAPI.AUTHORITY);

    String completionString = appContext.getString(R.string.searching_for_deleted_forms);
    publishProgress(completionString, null);


    Log.i(t, "removeStaleFormInfo " + appName + " begin");
    ArrayList<Uri> badEntries = new ArrayList<Uri>();
    Cursor c = null;
    try {
      c = appContext.getContentResolver().query(
          Uri.withAppendedPath(formsProviderContentUri, appName), null, null, null, null);

      if (c == null) {
        Log.w(t, "removeStaleFormInfo " + appName
            + " null cursor returned from query.");
        return;
      }

      if (c.moveToFirst()) {
        do {
          String id = c.getString(c.getColumnIndex(FormsColumns.FORM_ID));
          Uri otherUri = Uri.withAppendedPath(
              Uri.withAppendedPath(formsProviderContentUri, appName), id);

          String examString = appContext.getString(R.string.examining_form, id);
          publishProgress(examString, null);

          int appRelativeFormMediaPathIdx = c
              .getColumnIndex(FormsColumns.APP_RELATIVE_FORM_MEDIA_PATH);
          if (appRelativeFormMediaPathIdx == -1) {
            throw new IllegalStateException("Column " + FormsColumns.APP_RELATIVE_FORM_MEDIA_PATH
                + " missing from database table. Incompatible versions?");
          }
          String appRelativeFormMediaPath = c.getString(appRelativeFormMediaPathIdx);
          File f = ODKFileUtils.asAppFile(appName, appRelativeFormMediaPath);
          File formDefJson = new File(f, ODKFileUtils.FORMDEF_JSON_FILENAME);
          if (!f.exists() || !f.isDirectory() || !formDefJson.exists() || !formDefJson.isFile()) {
            // the form definition does not exist
            badEntries.add(otherUri);
            continue;
          } else {
            //////////////////////////////////
            // formdef.json exists. See if it is
            // unchanged...
            String json_md5 = c.getString(c.getColumnIndex(FormsColumns.JSON_MD5_HASH));
            String fileMd5 = ODKFileUtils.getMd5Hash(formDefJson);
            if ( json_md5.equals(fileMd5) ) {
              // it is unchanged -- no need to rescan it
              discoveredFormDefDirs.remove(f);
            }
          }
        } while (c.moveToNext());
      }
    } catch (Exception e) {
      Log.e(
          t,
          "removeStaleFormInfo " + appName + " exception: "
              + e.toString());
      e.printStackTrace();
    } finally {
      if (c != null && !c.isClosed()) {
        c.close();
      }
    }

    // delete the other entries (and directories)
    for (Uri badUri : badEntries) {
      Log.i(
          t,
          "removeStaleFormInfo: " + appName + " deleting: "
              + badUri.toString());
      try {
        appContext.getContentResolver().delete(badUri, null, null);
      } catch (Exception e) {
        Log.e(
            t,
            "removeStaleFormInfo " + appName + " exception: "
                + e.toString());
        e.printStackTrace();
        // and continue -- don't throw an error
      }
    }
    Log.i(t, "removeStaleFormInfo " + appName + " end");
  }

  /**
   * Construct a directory name that is unused in the stale path and move
   * mediaPath there.
   *
   * @param mediaPath
   * @param baseStaleMediaPath
   *          -- the stale directory corresponding to the mediaPath container
   * @return the directory within the stale directory that the mediaPath was
   *         renamed to.
   * @throws IOException
   */
  private final File moveToStaleDirectory(File mediaPath, String baseStaleMediaPath)
      throws IOException {
    // we have a 'framework' form in the forms directory.
    // Move it to the stale directory.
    // Delete all records referring to this directory.
    int i = 0;
    File tempMediaPath = new File(baseStaleMediaPath + mediaPath.getName() + "_"
        + Integer.toString(i));
    while (tempMediaPath.exists()) {
      ++i;
      tempMediaPath = new File(baseStaleMediaPath + mediaPath.getName() + "_" + Integer.toString(i));
    }
    FileUtils.moveDirectory(mediaPath, tempMediaPath);
    return tempMediaPath;
  }

  /**
   * Scan the given formDir and update the Forms database. If it is the
   * formsFolder, then any 'framework' forms should be forbidden. If it is not
   * the
   * formsFolder, only 'framework' forms should be allowed
   *
   * @param mediaPath
   *          -- full formDir
   * @param isFormsFolder
   * @param baseStaleMediaPath
   *          -- path prefix to the stale forms/framework directory.
   */
  private final void updateFormDir(File formDir, boolean isFormsFolder, String baseStaleMediaPath) {
    Uri formsProviderContentUri = Uri.parse("content://" + FormsProviderAPI.AUTHORITY);
    String formDirectoryPath = formDir.getAbsolutePath();
    Log.i(t, "updateFormDir: " + formDirectoryPath);

    boolean needUpdate = true;
    FormInfo fi = null;
    Uri uri = null;
    Cursor c = null;
    try {
      File formDef = new File(formDir, ODKFileUtils.FORMDEF_JSON_FILENAME);

      String selection = FormsColumns.APP_RELATIVE_FORM_MEDIA_PATH + "=?";
      String[] selectionArgs = { ODKFileUtils.asRelativePath(appName, formDir)
      };
      c = appContext.getContentResolver().query(
          Uri.withAppendedPath(formsProviderContentUri, appName), null, selection, selectionArgs,
          null);

      if (c == null) {
        Log.w(t, "updateFormDir: " + formDirectoryPath
            + " null cursor -- cannot update!");
        return;
      }

      if (c.getCount() > 1) {
        c.close();
        Log.w(t, "updateFormDir: " + formDirectoryPath
            + " multiple records from cursor -- delete all and restore!");
        // we have multiple records for this one directory.
        // Rename the directory. Delete the records, and move the
        // directory back.
        File tempMediaPath = moveToStaleDirectory(formDir, baseStaleMediaPath);
        appContext.getContentResolver().delete(Uri.withAppendedPath(formsProviderContentUri, appName),
            selection, selectionArgs);
        FileUtils.moveDirectory(tempMediaPath, formDir);
        // we don't know which of the above records was correct, so
        // reparse this to get ground truth...
        fi = new FormInfo(appContext, appName, formDef);
      } else if (c.getCount() == 1) {
        c.moveToFirst();
        String id = c.getString(c.getColumnIndex(FormsColumns.FORM_ID));
        uri = Uri.withAppendedPath(Uri.withAppendedPath(formsProviderContentUri, appName), id);
        Long lastModificationDate = c.getLong(c.getColumnIndex(FormsColumns.DATE));
        Long formDefModified = ODKFileUtils.getMostRecentlyModifiedDate(formDir);
        if (lastModificationDate.compareTo(formDefModified) == 0) {
          Log.i(t, "updateFormDir: " + formDirectoryPath
              + " formDef unchanged");
          fi = new FormInfo(appName, c, false);
          needUpdate = false;
        } else {
          Log.i(t, "updateFormDir: " + formDirectoryPath
              + " formDef revised");
          fi = new FormInfo(appContext, appName, formDef);
          needUpdate = true;
        }
      } else if (c.getCount() == 0) {
        // it should be new, try to parse it...
        fi = new FormInfo(appContext, appName, formDef);
      }

      // Enforce that a formId == FormsColumns.COMMON_BASE_FORM_ID can only be
      // in the Framework directory
      // and that no other formIds can be in that directory. If this is not the
      // case, ensure that
      // this record is moved to the stale directory.

      if (fi.formId.equals(FormsColumns.COMMON_BASE_FORM_ID)) {
        if (isFormsFolder) {
          // we have a 'framework' form in the forms directory.
          // Move it to the stale directory.
          // Delete all records referring to this directory.
          moveToStaleDirectory(formDir, baseStaleMediaPath);
          appContext.getContentResolver().delete(
              Uri.withAppendedPath(formsProviderContentUri, appName), selection, selectionArgs);
          return;
        }
      } else {
        if (!isFormsFolder) {
          // we have a non-'framework' form in the framework directory.
          // Move it to the stale directory.
          // Delete all records referring to this directory.
          moveToStaleDirectory(formDir, baseStaleMediaPath);
          appContext.getContentResolver().delete(
              Uri.withAppendedPath(formsProviderContentUri, appName), selection, selectionArgs);
          return;
        }
      }
    } catch (SQLiteException e) {
      e.printStackTrace();
      Log.e(
          t,
          "updateFormDir: " + formDirectoryPath + " exception: "
              + e.toString());
      return;
    } catch (IOException e) {
      e.printStackTrace();
      Log.e(
          t,
          "updateFormDir: " + formDirectoryPath + " exception: "
              + e.toString());
      return;
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
      Log.e(
          t,
          "updateFormDir: " + formDirectoryPath + " exception: "
              + e.toString());
      try {
        FileUtils.deleteDirectory(formDir);
        Log.i(t, "updateFormDir: " + formDirectoryPath
            + " Removing -- unable to parse formDef file: " + e.toString());
      } catch (IOException e1) {
        e1.printStackTrace();
        Log.i(t,
            "updateFormDir: " + formDirectoryPath
                + " Removing -- unable to delete form directory: " + formDir.getName() + " error: "
                + e.toString());
      }
      return;
    } finally {
      if (c != null && !c.isClosed()) {
        c.close();
      }
    }

    // Delete any entries matching this FORM_ID, but not the same directory and
    // which have a version that is equal to or older than this version.
    String selection;
    String[] selectionArgs;
    if (fi.formVersion == null) {
      selection = FormsColumns.APP_RELATIVE_FORM_MEDIA_PATH + "!=? AND " + FormsColumns.FORM_ID
          + "=? AND " + FormsColumns.FORM_VERSION + " IS NULL";
      String[] temp = { ODKFileUtils.asRelativePath(appName, formDir), fi.formId
      };
      selectionArgs = temp;
    } else {
      selection = FormsColumns.APP_RELATIVE_FORM_MEDIA_PATH + "!=? AND " + FormsColumns.FORM_ID
          + "=? AND " + "( " + FormsColumns.FORM_VERSION + " IS NULL" + " OR "
          + FormsColumns.FORM_VERSION + " <=?" + " )";
      String[] temp = { ODKFileUtils.asRelativePath(appName, formDir), fi.formId, fi.formVersion
      };
      selectionArgs = temp;
    }

    try {
      appContext.getContentResolver().delete(Uri.withAppendedPath(formsProviderContentUri, appName),
          selection, selectionArgs);
    } catch (SQLiteException e) {
      e.printStackTrace();
      Log.e(
          t,
          "updateFormDir: " + formDirectoryPath + " exception: "
              + e.toString());
      return;
    } catch (Exception e) {
      e.printStackTrace();
      Log.e(
          t,
          "updateFormDir: " + formDirectoryPath + " exception: "
              + e.toString());
      return;
    }

    // See if we have any newer versions already present...
    if (fi.formVersion == null) {
      selection = FormsColumns.APP_RELATIVE_FORM_MEDIA_PATH + "!=? AND " + FormsColumns.FORM_ID
          + "=? AND " + FormsColumns.FORM_VERSION + " IS NOT NULL";
      String[] temp = { ODKFileUtils.asRelativePath(appName, formDir), fi.formId
      };
      selectionArgs = temp;
    } else {
      selection = FormsColumns.APP_RELATIVE_FORM_MEDIA_PATH + "!=? AND " + FormsColumns.FORM_ID
          + "=? AND " + FormsColumns.FORM_VERSION + " >?";
      String[] temp = { ODKFileUtils.asRelativePath(appName, formDir), fi.formId, fi.formVersion
      };
      selectionArgs = temp;
    }

    try {
      Uri uriApp = Uri.withAppendedPath(formsProviderContentUri, appName);
      c = appContext.getContentResolver().query(uriApp, null, selection, selectionArgs, null);

      if (c == null) {
        Log.w(t, "updateFormDir: " + uriApp.toString()
            + " null cursor -- cannot update!");
        return;
      }

      if (c.moveToFirst()) {
        // the directory we are processing is stale -- move it to stale
        // directory
        moveToStaleDirectory(formDir, baseStaleMediaPath);
        return;
      }
    } catch (SQLiteException e) {
      e.printStackTrace();
      Log.e(
          t,
          "updateFormDir: " + formDirectoryPath + " exception: "
              + e.toString());
      return;
    } catch (IOException e) {
      e.printStackTrace();
      Log.e(
          t,
          "updateFormDir: " + formDirectoryPath + " exception: "
              + e.toString());
      return;
    } finally {
      if (c != null && !c.isClosed()) {
        c.close();
      }
    }

    if (!needUpdate) {
      // no change...
      return;
    }

    try {
      // Now insert or update the record...
      ContentValues v = new ContentValues();
      String[] values = fi.asRowValues(FormsColumns.formsDataColumnNames);
      for (int i = 0; i < values.length; ++i) {
        v.put(FormsColumns.formsDataColumnNames[i], values[i]);
      }

      if (uri != null) {
        int count = appContext.getContentResolver().update(uri, v, null, null);
        Log.i(t, "updateFormDir: " + formDirectoryPath + " " + count
            + " records successfully updated");
      } else {
        appContext.getContentResolver().insert(Uri.withAppendedPath(formsProviderContentUri, appName),
            v);
        Log.i(t, "updateFormDir: " + formDirectoryPath
            + " one record successfully inserted");
      }

    } catch (SQLiteException ex) {
      ex.printStackTrace();
      Log.e(t, "updateFormDir: " + formDirectoryPath + " exception: "
          + ex.toString());
      return;
    }
  }

  @Override
  protected void onPostExecute(ArrayList<String> result) {
    synchronized (this) {
      mResult = result;
      mSuccess = mPendingSuccess;
      if (mStateListener != null) {
        mStateListener.initializationComplete(mSuccess, mResult);
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
        mStateListener.initializationComplete(mSuccess, mResult);
      }
    }
  }

  @Override
  protected void onProgressUpdate(String... values) {
    synchronized (this) {
      if (mStateListener != null) {
        // update progress and total
        mStateListener.initializationProgressUpdate(values[0] + (( values[1] != null ) ? "\n(" + values[1] + ")" : ""));
      }
    }

  }

  public boolean getOverallSuccess() {
    return mSuccess;
  }

  public ArrayList<String> getResult() {
    return mResult;
  }

  public void setInitializationListener(InitializationListener sl) {
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
