/*
 * Copyright (C) 2012 University of Washington
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
import java.io.Serializable;
import java.util.Date;

import android.net.Uri;

/**
 * Basic definitions of the current form being processed.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class FormIdStruct implements Serializable {
	/**
	 *
	 */
	private static final long serialVersionUID = -525417323683147800L;

	public final Uri formUri;
	public final File formDefFile;
	public final String formPath;
	public final String formId;
	public final String tableId;
	public final String formVersion;
	public final Date lastDownloadDate;

	public FormIdStruct(Uri formUri, File formDefFile, String formPath, String formId, String formVersion, String tableId, Date lastModifiedDate) {
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
		this.formVersion = original.formVersion;
		this.lastDownloadDate = original.lastDownloadDate;
	}
}