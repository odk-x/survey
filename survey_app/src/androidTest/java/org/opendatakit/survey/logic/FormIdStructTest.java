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

package org.opendatakit.survey.logic;

import java.io.File;
import java.util.Date;

import org.opendatakit.provider.FormsProviderAPI;
import org.opendatakit.utilities.ODKFileUtils;

import android.net.Uri;
import android.test.AndroidTestCase;

public class FormIdStructTest extends AndroidTestCase {
  private static final String FORM_PATH = "tables/myTable/forms/formName";
  private static final String FORM_ID = "formName";
  private static final String TABLE_ID = "myTable";
  private static final String FORM_VERSION = "12";
  private static final String APP_NAME = "survey.test";

  public void testValuesMatch() {
	Uri formUri = Uri.parse("content://" + FormsProviderAPI.AUTHORITY + "/" + APP_NAME + "/" + TABLE_ID + "/1");
	File formDefFile = ODKFileUtils.asAppFile(APP_NAME,FORM_PATH);
	Date now = new Date();
	
	FormIdStruct fis = new FormIdStruct(formUri, formDefFile, FORM_PATH, 
		FORM_ID, FORM_VERSION, TABLE_ID, now);
		
	assertEquals(fis.lastDownloadDate, now);
	assertEquals(fis.tableId, TABLE_ID);
	assertEquals(fis.formVersion, FORM_VERSION);
	assertEquals(fis.formId, FORM_ID);
	assertEquals(fis.formPath, FORM_PATH);
	assertEquals(fis.formDefFile, formDefFile);
	assertEquals(fis.formUri, formUri);
	assertEquals(fis.appName, APP_NAME);
  }

}
