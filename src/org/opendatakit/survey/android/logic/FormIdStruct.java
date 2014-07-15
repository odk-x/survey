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

package org.opendatakit.survey.android.logic;

import java.io.File;
import java.util.Date;

import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.utilities.ODKDatabaseUtils;
import org.opendatakit.common.android.utilities.ODKFileUtils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

/**
 * Basic definitions of the current form being processed.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class FormIdStruct {
  public final Uri formUri;
  public final File formDefFile;
  public final String formPath;
  public final String formId;
  public final String tableId;
  public final String appName;
  public final String formVersion;
  public final Date lastDownloadDate;

  public FormIdStruct(Uri formUri, File formDefFile, String formPath, String formId,
      String formVersion, String tableId, Date lastModifiedDate) {
    this.appName = FormsColumns.extractAppNameFromFormsUri(formUri);
    this.formUri = formUri;
    this.formDefFile = formDefFile;
    this.formPath = formPath;
    this.formId = formId;
    this.formVersion = formVersion;
    this.tableId = tableId;
    this.lastDownloadDate = lastModifiedDate;
  }

  public FormIdStruct(FormIdStruct original) {
    this.formUri = original.formUri;
    this.formDefFile = original.formDefFile;
    this.formPath = original.formPath;
    this.formId = original.formId;
    this.tableId = original.tableId;
    this.appName = original.appName;
    this.formVersion = original.formVersion;
    this.lastDownloadDate = original.lastDownloadDate;
  }

  public static final FormIdStruct retrieveFormIdStruct(ContentResolver resolver, Uri formUri) {
    if (formUri == null) {
      return null;
    }
    String appName = FormsColumns.extractAppNameFromFormsUri(formUri);
    Cursor c = null;
    try {
      c = resolver.query(formUri, null, null, null, null);
      if (c != null && c.getCount() == 1) {
        int appRelativeFormMedia = c.getColumnIndex(FormsColumns.APP_RELATIVE_FORM_MEDIA_PATH);
        int formPath = c.getColumnIndex(FormsColumns.FORM_PATH);
        int formId = c.getColumnIndex(FormsColumns.FORM_ID);
        int formVersion = c.getColumnIndex(FormsColumns.FORM_VERSION);
        int tableId = c.getColumnIndex(FormsColumns.TABLE_ID);
        int date = c.getColumnIndex(FormsColumns.DATE);

        c.moveToFirst();

        File formMediaDirectory = ODKFileUtils.asAppFile(appName, ODKDatabaseUtils.getIndexAsString(c, appRelativeFormMedia));
        File formDefJsonFile = new File(formMediaDirectory, ODKFileUtils.FORMDEF_JSON_FILENAME);

        Long timestamp = ODKDatabaseUtils.getIndexAsType(c, Long.class, date);
        FormIdStruct newForm = new FormIdStruct(formUri, formDefJsonFile,
            ODKFileUtils.getRelativeFormPath(appName, formDefJsonFile), ODKDatabaseUtils.getIndexAsString(c, formId),
            ODKDatabaseUtils.getIndexAsString(c, formVersion), ODKDatabaseUtils.getIndexAsString(c, tableId),
            (timestamp == null) ? null : new Date(timestamp));
        return newForm;
      }
    } finally {
      if (c != null) {
        c.close();
      }
    }
    return null;
  }
}