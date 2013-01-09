/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2011-2013 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opendatakit.survey.android.provider;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Convenience definitions for NotePadProvider
 */
public final class FormsProviderAPI {
	public static final String AUTHORITY = "org.opendatakit.survey.android.provider.forms";
	public static final String FILENAME_XFORMS_XML = "xforms.xml";
	public static final String MD5_COLON_PREFIX = "md5:";

	// This class cannot be instantiated
	private FormsProviderAPI() {
	}

	/**
	 * Notes table
	 */
	public static final class FormsColumns implements BaseColumns {
		// This class cannot be instantiated
		private FormsColumns() {
		}

		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ AUTHORITY + "/forms");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.opendatakit.form";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.opendatakit.form";

		// These are the only things needed for an insert
		public static final String TABLE_ID = "tableId"; // for Tables linkage
		public static final String FORM_ID = "formId";
		public static final String FORM_VERSION = "formVersion"; // can be null
		public static final String DISPLAY_NAME = "displayName";
		public static final String DESCRIPTION = "description"; // can be null
		public static final String FORM_FILE_PATH = "formFilePath"; // ODK2:
																	// within
																	// the media
																	// directory
		public static final String FORM_MEDIA_PATH = "formMediaPath"; // directory
																		// containing
																		// formDef.json
		public static final String FORM_PATH = "formPath"; // relative path for
															// WebKit
		public static final String DEFAULT_FORM_LOCALE = "defaultFormLocale";
		public static final String XML_SUBMISSION_URL = "xmlSubmissionUrl"; // ODK1
																			// support
																			// -
																			// can
																			// be
																			// null
		public static final String XML_BASE64_RSA_PUBLIC_KEY = "xmlBase64RsaPublicKey"; // ODK1
																						// support
																						// -
																						// can
																						// be
																						// null
		public static final String XML_ROOT_ELEMENT_NAME = "xmlRootElementName"; // ODK1
																					// support
																					// -
																					// can
																					// be
																					// null
		public static final String XML_DEVICE_ID_PROPERTY_NAME = "xmlDeviceIdPropertyName";
		public static final String XML_USER_ID_PROPERTY_NAME = "xmlUserIdPropertyName";

		// these are generated for you (but you can insert something else if you
		// want)
		public static final String DISPLAY_SUBTEXT = "displaySubtext";
		public static final String MD5_HASH = "md5Hash";
		public static final String DATE = "date"; // last modification date

		// NOTE: this omits _ID (the primary key)
		public static final String[] formsDataColumnNames = { DISPLAY_NAME,
				DISPLAY_SUBTEXT, DESCRIPTION, TABLE_ID, FORM_ID, FORM_VERSION,
				FORM_FILE_PATH, FORM_MEDIA_PATH, FORM_PATH, MD5_HASH, DATE,
				DEFAULT_FORM_LOCALE, XML_SUBMISSION_URL,
				XML_BASE64_RSA_PUBLIC_KEY, XML_DEVICE_ID_PROPERTY_NAME,
				XML_USER_ID_PROPERTY_NAME, XML_ROOT_ELEMENT_NAME };

	}
}
