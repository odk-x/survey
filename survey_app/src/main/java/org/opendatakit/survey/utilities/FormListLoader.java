/*
 * Copyright (C) 2015 University of Washington
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

package org.opendatakit.survey.utilities;

import androidx.loader.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.opendatakit.application.CommonApplication;
import org.opendatakit.database.data.OrderedColumns;
import org.opendatakit.database.data.UserTable;
import org.opendatakit.database.service.DbHandle;
import org.opendatakit.database.service.UserDbInterface;
import org.opendatakit.database.utilities.QueryUtil;
import org.opendatakit.exception.ServicesAvailabilityException;
import org.opendatakit.properties.CommonToolProperties;
import org.opendatakit.properties.PropertiesSingleton;
import org.opendatakit.provider.FormsColumns;
import org.opendatakit.provider.FormsProviderAPI;
import org.opendatakit.survey.R;
import org.opendatakit.survey.application.Survey;
import org.opendatakit.utilities.LocalizationUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * @author mitchellsundt@gmail.com
 */
public class FormListLoader extends AsyncTaskLoader<ArrayList<FormInfo>> {

  private final String appName;


  public FormListLoader(Context context, String appName) {
    super(context);
    this.appName = appName;
  }

  @Override public ArrayList<FormInfo> loadInBackground() {
    // This is called when a new Loader needs to be created. This
    // sample only has one Loader, so we don't care about the ID.
    // First, pick the base URI to use depending on whether we are
    // currently filtering.
    Uri baseUri = Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName);
    PropertiesSingleton props = CommonToolProperties.get(getContext(), appName);

    ArrayList<FormInfo> forms = new ArrayList<FormInfo>();

    Cursor c = null;
    try {

      c = getContext().getContentResolver().query(baseUri, null, null, null, null);

      if ( c != null && c.moveToFirst() ) {
        int idxTableId = c.getColumnIndex(FormsColumns.TABLE_ID);
        int idxFormId = c.getColumnIndex(FormsColumns.FORM_ID);
        int idxFormTitle = c.getColumnIndex(FormsColumns.DISPLAY_NAME);
        int idxLastUpdateDate = c.getColumnIndex(FormsColumns.DATE);
        int idxFormVersion = c.getColumnIndex(FormsColumns.FORM_VERSION);
        UserDbInterface dbInterface =Survey.getInstance().getDatabase();
        SimpleDateFormat formatter = new SimpleDateFormat(getContext().getString(R.string
            .last_updated_on_date_at_time), Locale.getDefault());

        do {
          String tableId = c.getString(idxTableId);
          String formVersion = c.isNull(idxFormVersion) ? null :
              c.getString(idxFormVersion);
          long timestamp = c.getLong(idxLastUpdateDate);
          Date lastModificationDate = new Date(timestamp);
          String formTitle = c.getString(idxFormTitle);

          OrderedColumns columnDefinitions = getColumnDefinitions(appName, tableId);
          int tableIncompleteCount = loadInfoAboutForms(tableId , dbInterface , columnDefinitions);

          FormInfo info = new FormInfo(
              tableId,
              c.getString(idxFormId),
              formVersion,
              LocalizationUtils.getLocalizedDisplayName(appName, tableId,
                  props.getUserSelectedDefaultLocale(),
                  formTitle),
              formatter.format(lastModificationDate) ,
                  tableIncompleteCount );
          forms.add(info);
        } while ( c.moveToNext());
      }
    } finally {
      if ( c != null && !c.isClosed() ) {
        c.close();
      }
    }


    return forms;
  }

  @Override protected void onStartLoading() {
    super.onStartLoading();
    forceLoad();
  }


  public synchronized OrderedColumns getColumnDefinitions(String appName, String tableId) {

    OrderedColumns mColumnDefinitions = null;
    CommonApplication app = Survey.getInstance();
    if (app.getDatabase() != null)
    {
      DbHandle db = null;
      try
      {
        db = app.getDatabase().openDatabase( appName );
        mColumnDefinitions = app.getDatabase()
                .getUserDefinedColumns( appName , db, tableId);
      } catch (ServicesAvailabilityException e)
      {

        throw new IllegalStateException("database went down -- handle this! " + e);
      } finally
      {
        if (db != null) {
          try
          {
            app.getDatabase().closeDatabase(appName, db);
          } catch (ServicesAvailabilityException e)
          {

          }
        }
      }
    }
    return mColumnDefinitions;
  }

  private int loadInfoAboutForms(String iTableId , UserDbInterface dbInterface , OrderedColumns columnDefs)
  {
    DbHandle db = null;
    String[] emptyArray = {};
    int countOfIncompleteRows = 0;
    try {
      db = dbInterface.openDatabase(appName);
      SQLQueryStruct sqlQueryStruct = new SQLQueryStruct("_savepoint_type = 'INCOMPLETE' ", null, new String[]{},
                "", "", "");
      UserTable userTable = dbInterface.simpleQuery(appName, db, iTableId, columnDefs,
                sqlQueryStruct.whereClause, sqlQueryStruct.selectionArgs,
                sqlQueryStruct.groupBy == null ? emptyArray : sqlQueryStruct.groupBy,
                sqlQueryStruct.having,
                QueryUtil.convertStringToArray(sqlQueryStruct.orderByElementKey),
                QueryUtil.convertStringToArray(sqlQueryStruct.orderByDirection),
                null, null);
      countOfIncompleteRows = userTable.getNumberOfRows();
    }
    catch(ServicesAvailabilityException sae) {
      throw new IllegalStateException("Database open -- while fetching incomplete stats count! " + sae);
    }
    finally {
      try {
        if (db != null)
          dbInterface.closeDatabase(appName, db);
        }
        catch (ServicesAvailabilityException sae) {}
    }
    return countOfIncompleteRows;
  }
}
