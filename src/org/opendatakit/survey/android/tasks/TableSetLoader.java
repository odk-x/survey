package org.opendatakit.survey.android.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.opendatakit.aggregate.odktables.rest.KeyValueStoreConstants;
import org.opendatakit.common.android.database.DatabaseFactory;
import org.opendatakit.common.android.database.DatabaseConstants;
import org.opendatakit.common.android.provider.KeyValueStoreColumns;
import org.opendatakit.common.android.utilities.ODKDataUtils;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class TableSetLoader extends AsyncTaskLoader<List<TableSetLoader.TableSetEntry>>{

  private static final String COMMON_JAVASCRIPT_FRAMEWORK = "\"Common Javascript Framework\"";
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

    SQLiteDatabase db = null;
    Cursor c = null;
    try {
      db = DatabaseFactory.get().getDatabase(getContext(), appName);
      c = db.query(DatabaseConstants.KEY_VALUE_STORE_ACTIVE_TABLE_NAME, null,
            KeyValueStoreColumns.PARTITION + "=? AND " +
            KeyValueStoreColumns.ASPECT + "=? AND " +
            KeyValueStoreColumns.KEY + "=? AND " +
            KeyValueStoreColumns.VALUE + "<>?",
        new String[] { KeyValueStoreConstants.PARTITION_TABLE,
                       KeyValueStoreConstants.ASPECT_DEFAULT,
                       KeyValueStoreConstants.TABLE_DISPLAY_NAME,
                       COMMON_JAVASCRIPT_FRAMEWORK}, null, null, null);

      if ( c != null && c.getCount() > 0) {
        List<TableSetEntry> entries = new ArrayList<TableSetEntry>();
  
        c.moveToFirst();
        int idxTableId = c.getColumnIndex(KeyValueStoreColumns.TABLE_ID);
        int idxValue = c.getColumnIndex(KeyValueStoreColumns.VALUE);
  
        do {
          String tableId = c.getString(idxTableId);
          String value = c.getString(idxValue);
          String displayName = ODKDataUtils.getLocalizedDisplayName(value);
          if ( displayName == null ) {
            displayName = tableId;
          }
          entries.add( new TableSetEntry(tableId, displayName));
        } while ( c.moveToNext() );
  
        Collections.sort(entries, new Comparator<TableSetEntry>() {
  
          @Override
          public int compare(TableSetEntry lhs, TableSetEntry rhs) {
            return lhs.displayName.compareTo(rhs.displayName);
          }});
  
        return entries;
      }
    } finally {
      if ( c != null && !c.isClosed() ) {
        c.close();
      }
      if ( db != null ) {
        db.close();
      }
    }

    return new ArrayList<TableSetEntry>();
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
