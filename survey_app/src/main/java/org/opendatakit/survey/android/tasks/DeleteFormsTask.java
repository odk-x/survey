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

package org.opendatakit.survey.android.tasks;

import java.util.HashSet;

import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.provider.FormsProviderAPI;
import org.opendatakit.common.android.provider.InstanceProviderAPI;
import org.opendatakit.common.android.provider.TablesProviderAPI;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.listeners.DeleteFormsListener;

import android.app.Application;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

/**
 * Task responsible for deleting selected forms.
 *
 * @author norman86@gmail.com
 * @author mitchellsundt@gmail.com
 *
 */
public class DeleteFormsTask extends AsyncTask<Uri, Void, Integer> {
  private static final String t = "DeleteFormsTask";

  private Application appContext;
  private DeleteFormsListener dl;
  private String appName;
  private boolean deleteFormData = false;

  private int successCount = 0;

  @Override
  protected Integer doInBackground(Uri... params) {
    int deleted = 0;

    if (params == null || appContext == null || dl == null) {
      return deleted;
    }

    // delete files from database and then from file system
    for (int i = 0; i < params.length; i++) {
      if (isCancelled()) {
        break;
      }
      try {
        Uri deleteForm = params[i];

        if (deleteFormData) {
          HashSet<String> tableIds = new HashSet<String>();
          
          Cursor c = null;
          try {
            c = appContext.getContentResolver().query(deleteForm, new String[] { FormsColumns.TABLE_ID }, 
              null, null, null );
            if ( c == null ) {
              throw new IllegalStateException("Unexpected null value");
            }
            if ( c.moveToFirst() ) {
              tableIds.add(c.getString(c.getColumnIndex(FormsColumns.TABLE_ID)));
            }
            c.close();
          } finally {
            if ( c != null && !c.isClosed() ) {
              c.close();
            }
          }

          int uploadDel = 0;
          int tableDel = 0;
          for ( String tableId : tableIds ) {
            
            // delete ALL forms for this tableId
            deleted += appContext.getContentResolver().delete(
                Uri.withAppendedPath(
                    Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName), tableId)
                , null, null);
            
            // Delete all information related to the submission of these rows
            // through the legacy pathway.
            uploadDel += appContext.getContentResolver().delete( 
                Uri.withAppendedPath(
                    Uri.withAppendedPath(InstanceProviderAPI.CONTENT_URI, appName), tableId ) 
                , null, null);
            // Delete all table-level files and all data rows and attachments for this tableId. 
            tableDel += appContext.getContentResolver().delete( 
                Uri.withAppendedPath(
                    Uri.withAppendedPath(TablesProviderAPI.CONTENT_URI, appName), tableId ) 
                , null, null);
          }
        } else {
          deleted += appContext.getContentResolver().delete(deleteForm, null, null);
        }

      } catch (Exception ex) {
        WebLogger.getLogger(appName).e(t,
            "Exception during delete of: " + params[i] + " exception: " + ex.toString());
      }
    }
    successCount = deleted;
    return deleted;
  }

  @Override
  protected void onPostExecute(Integer result) {
    appContext = null;
    if (dl != null) {
      dl.deleteFormsComplete(result, deleteFormData);
    }
  }

  @Override
  protected void onCancelled(Integer result) {
    appContext = null;
    // can be null if cancelled before task executes
    if (result == null) {
      successCount = 0;
    }
    if (dl != null) {
      dl.deleteFormsComplete(successCount, deleteFormData);
    }
  }

  public int getDeleteCount() {
    return successCount;
  }

  public void setDeleteListener(DeleteFormsListener listener) {
    dl = listener;
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

  public void setDeleteFormData(boolean shouldFormDataBeDeleted) {
    synchronized (this) {
      this.deleteFormData = shouldFormDataBeDeleted;
    }
  }

  public boolean getDeleteFormData() {
    return deleteFormData;
  }
}
