/*
 * Copyright (C) 2014 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.survey.android.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.opendatakit.aggregate.odktables.rest.KeyValueStoreConstants;
import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.utilities.ODKDataUtils;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.database.service.KeyValueStoreEntry;
import org.opendatakit.database.service.OdkDbHandle;
import org.opendatakit.survey.android.application.Survey;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.os.RemoteException;

public class TableSetLoader extends AsyncTaskLoader<List<TableSetLoader.TableSetEntry>>{

  public static final class TableSetEntry {
    public final String tableId;
    public final String displayName;

    TableSetEntry(String tableId, String displayName) {
      this.tableId = tableId;
      this.displayName = displayName;
    }
  }

  private String appName;

  public TableSetLoader(Context context, String appName) {
    super(context);
    this.appName = appName;
  }

  @Override
  public List<TableSetEntry> loadInBackground() {

    List<KeyValueStoreEntry> kvsEntries;
    OdkDbHandle dbHandle = null;
    try {
      dbHandle = Survey.getInstance().getDatabase().openDatabase(appName);
      kvsEntries = Survey.getInstance().getDatabase().getDBTableMetadata(appName, dbHandle, null, 
        KeyValueStoreConstants.PARTITION_TABLE,
        KeyValueStoreConstants.ASPECT_DEFAULT, 
        KeyValueStoreConstants.TABLE_DISPLAY_NAME);
    } catch (RemoteException e) {
      WebLogger.getLogger(appName).printStackTrace(e);
      throw new IllegalStateException("unable to obtain data from remote service");
    } finally {
      try {
        if ( dbHandle != null ) {
          Survey.getInstance().getDatabase().closeDatabase(appName, dbHandle);
        }
      } catch (RemoteException e) {
        WebLogger.getLogger(appName).printStackTrace(e);
      }
    }
    
    ArrayList<TableSetEntry> entries = new ArrayList<TableSetEntry>();
    for ( KeyValueStoreEntry kvs : kvsEntries ) {
      if ( kvs.tableId.equals(FormsColumns.COMMON_BASE_FORM_ID) ) {
        continue;
      }
      String tableId = kvs.tableId;
      String value = kvs.value;
      String displayName = ODKDataUtils.getLocalizedDisplayName(value);
      if ( displayName == null ) {
        displayName = tableId;
      }
      entries.add( new TableSetEntry(tableId, displayName));
    }

    Collections.sort(entries, new Comparator<TableSetEntry>() {

      @Override
      public int compare(TableSetEntry lhs, TableSetEntry rhs) {
        return lhs.displayName.compareTo(rhs.displayName);
      }});

    return entries;
  }

  /**
   * Handles a request to start the Loader.
   */
  @Override protected void onStartLoading() {
    forceLoad();
  }

  /**
   * Handles a request to stop the Loader.
   */
  @Override protected void onStopLoading() {
      // Attempt to cancel the current load task if possible.
      cancelLoad();
  }

  /**
   * Handles a request to cancel a load.
   */
  @Override public void onCanceled(List<TableSetEntry> tables) {
      super.onCanceled(tables);
  }

  /**
   * Handles a request to completely reset the Loader.
   */
  @Override protected void onReset() {
      super.onReset();

      // Ensure the loader is stopped
      onStopLoading();
  }

}
