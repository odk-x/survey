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

package org.opendatakit.survey.android.utilities;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.provider.FormsProviderAPI;
import org.opendatakit.common.android.utilities.LocalizationUtils;
import org.opendatakit.survey.android.R;

import java.text.SimpleDateFormat;
import java.util.*;

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

    ArrayList<FormInfo> forms = new ArrayList<FormInfo>();

    Cursor c = null;
    try {
      c = getContext().getContentResolver().query(baseUri, null, null, null, null);

      if ( c.moveToFirst() ) {
        int idxTableId = c.getColumnIndex(FormsColumns.TABLE_ID);
        int idxFormId = c.getColumnIndex(FormsColumns.FORM_ID);
        int idxFormTitle = c.getColumnIndex(FormsColumns.DISPLAY_NAME);
        int idxLastUpdateDate = c.getColumnIndex(FormsColumns.DATE);
        int idxFormVersion = c.getColumnIndex(FormsColumns.FORM_VERSION);

        SimpleDateFormat formatter = new SimpleDateFormat(getContext().getString(R.string
            .last_updated_on_date_at_time), Locale.getDefault());

        do {
          String formVersion = c.isNull(idxFormVersion) ? null :
              c.getString(idxFormVersion);
          long timestamp = c.getLong(idxLastUpdateDate);
          Date lastModificationDate = new Date(timestamp);
          String formTitle = c.getString(idxFormTitle);

          FormInfo info = new FormInfo(
              c.getString(idxTableId),
              c.getString(idxFormId),
              formVersion,
              LocalizationUtils.getLocalizedDisplayName(formTitle),
              formatter.format(lastModificationDate));
          forms.add(info);
        } while ( c.moveToNext());
      }
    } finally {
      if ( c != null && !c.isClosed() ) {
        c.close();
      }
    }

    // order this by the localized display name
    Collections.sort(forms, new Comparator<FormInfo>() {
      @Override public int compare(FormInfo lhs, FormInfo rhs) {
        int cmp = lhs.formDisplayName.compareTo(rhs.formDisplayName);
        if ( cmp != 0 ) {
          return cmp;
        }
        cmp = lhs.tableId.compareTo(rhs.tableId);
        if ( cmp != 0 ) {
          return cmp;
        }
        cmp = lhs.formId.compareTo(rhs.formId);
        if ( cmp != 0 ) {
          return cmp;
        }
        cmp = lhs.formVersion.compareTo(rhs.formVersion);
        if ( cmp != 0 ) {
          return cmp;
        }
        cmp = lhs.formDisplaySubtext.compareTo(rhs.formDisplaySubtext);
        return cmp;
      }
    });

    return forms;
  }

  @Override protected void onStartLoading() {
    super.onStartLoading();
    forceLoad();
  }
}
