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
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.provider.FormsProviderAPI.FormsColumns;

import android.content.Context;
import android.database.Cursor;

/**
 * Class to hold information about a form. This holds the data fields that are available in
 * the Forms database as well as, if requested, the parsed formDef object (via Jackson).
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class FormInfo {
    static final ObjectMapper mapper = new ObjectMapper();

	public final int id;
	public final String formPath;
	public final String formFilePath;
	public final String formMediaPath;
	public final long lastModificationDate;
	public final String formId;
	public final String formVersion;
	public final String tableId;
	public final String formTitle;
	public final String description;
	public final String displaySubtext;
	public final String language;
	public final String xmlSubmissionUrl;
	public final String xmlBase64RsaPublicKey;
	public final String xmlDeviceIdPropertyName;
	public final String xmlUserIdPropertyName;
	public final String xmlRootElementName;

	// formDef.json file...
	public final File formDefFile;
	// the entire formDef, parsed using Jackson...
	public final HashMap<String,Object> formDef;

	static final String FORMDEF_VALUE = "value";

	static final String FORMDEF_XML_ROOT_ELEMENT_NAME = "xml_root_element_name";

	static final String FORMDEF_XML_DEVICE_ID_PROPERTY_NAME = "xml_device_id_property_name";

	static final String FORMDEF_XML_USER_ID_PROPERTY_NAME = "xml_user_id_property_name";

	static final String FORMDEF_BASE64_RSA_PUBLIC_KEY = "xml_base64_rsa_public_key";

	static final String FORMDEF_XML_SUBMISSION_URL = "xml_submission_url";

	static final String FORMDEF_DEFAULT_LOCALE = "default_locale";

	static final String FORMDEF_FORM_TITLE = "form_title";

	static final String FORMDEF_TABLE_ID = "table_id";

	static final String FORMDEF_FORM_VERSION = "form_version";

	static final String FORMDEF_FORM_ID = "form_id";

	/**
	 * Return an array of string values. Useful for passing as selectionArgs to SQLite.
	 * Or for iterating over and populating a ContentValues array.
	 *
	 * @param projection -- array of FormsColumns names to declare values of
	 * @return
	 */
	public String[] asRowValues(String[] projection) {

		if ( projection == null ) {
			projection = FormsColumns.formsDataColumnNames;
		}

		String[] ret = new String[projection.length ];
		for ( int i = 0 ; i < projection.length ; ++i ) {
			String s = projection[i];

			if ( FormsColumns._ID.equals(s) ) {
				ret[i] = Integer.toString(id);
			} else if ( FormsColumns.DISPLAY_NAME.equals(s) ) {
				ret[i] = formTitle;
			} else if ( FormsColumns.DISPLAY_SUBTEXT.equals(s) ) {
				ret[i] = displaySubtext;
			} else if ( FormsColumns.DESCRIPTION.equals(s) ) {
				ret[i] = description;
			} else if ( FormsColumns.TABLE_ID.equals(s) ) {
				ret[i] = tableId;
			} else if ( FormsColumns.FORM_ID.equals(s) ) {
				ret[i] = formId;
			} else if ( FormsColumns.FORM_VERSION.equals(s) ) {
				ret[i] = formVersion;
			} else if ( FormsColumns.FORM_FILE_PATH.equals(s) ) {
				ret[i] = formFilePath;
			} else if ( FormsColumns.FORM_MEDIA_PATH.equals(s) ) {
				ret[i] = formMediaPath;
			} else if ( FormsColumns.FORM_PATH.equals(s) ) {
				ret[i] = formPath;
			} else if ( FormsColumns.MD5_HASH.equals(s) ) {
				ret[i] = "-placeholder-"; // removed by FormsProvider
			} else if ( FormsColumns.DATE.equals(s) ) {
				ret[i] = Long.toString(lastModificationDate);
			} else if ( FormsColumns.DEFAULT_FORM_LOCALE.equals(s) ) {
				ret[i] = language;
			} else if ( FormsColumns.XML_SUBMISSION_URL.equals(s) ) {
				ret[i] = xmlSubmissionUrl;
			} else if ( FormsColumns.XML_BASE64_RSA_PUBLIC_KEY.equals(s) ) {
				ret[i] = xmlBase64RsaPublicKey;
			} else if ( FormsColumns.XML_ROOT_ELEMENT_NAME.equals(s) ) {
				ret[i] = xmlRootElementName;
			} else if ( FormsColumns.XML_DEVICE_ID_PROPERTY_NAME.equals(s) ) {
				ret[i] = xmlDeviceIdPropertyName;
			} else if ( FormsColumns.XML_USER_ID_PROPERTY_NAME.equals(s) ) {
				ret[i] = xmlUserIdPropertyName;
    		}
		}
		return ret;
	}

	private Map<String,Object> getSetting(String settingName, ArrayList<Map<String,Object>> settings) {
		for ( Map<String,Object> o : settings) {
			if ( settingName.equals(o.get("setting")) ) {
				return o;
			}
		}
		return null;
	}

	/**
	 * Given a Cursor pointing at a valid Forms database row, extract the values from that cursor.
	 * If parseFormDef is true, read and parse the formDef.json file.
	 *
	 * @param c -- cursor pointing at a valid Forms database row.
	 * @param parseFormDef -- true if the formDef.json file should be opened.
	 */
	@SuppressWarnings("unchecked")
	public FormInfo(Cursor c, boolean parseFormDef) {
    	id = c.getInt(c.getColumnIndex(FormsColumns._ID));
    	formPath = c.getString(c.getColumnIndex(FormsColumns.FORM_PATH));
    	formMediaPath = c.getString(c.getColumnIndex(FormsColumns.FORM_MEDIA_PATH));
    	formFilePath = c.getString(c.getColumnIndex(FormsColumns.FORM_FILE_PATH));

    	formDefFile = new File(formMediaPath + File.separator + "formDef.json");

    	lastModificationDate = c.getLong(c.getColumnIndex(FormsColumns.DATE));
    	formId = c.getString(c.getColumnIndex(FormsColumns.FORM_ID));
    	formVersion = c.getString(c.getColumnIndex(FormsColumns.FORM_VERSION));
    	tableId = c.getString(c.getColumnIndex(FormsColumns.TABLE_ID));
    	formTitle = c.getString(c.getColumnIndex(FormsColumns.DISPLAY_NAME));
    	description = c.getString(c.getColumnIndex(FormsColumns.DESCRIPTION));
    	displaySubtext = c.getString(c.getColumnIndex(FormsColumns.DISPLAY_SUBTEXT));
    	language = c.getString(c.getColumnIndex(FormsColumns.DEFAULT_FORM_LOCALE));
    	xmlSubmissionUrl = c.getString(c.getColumnIndex(FormsColumns.XML_SUBMISSION_URL));
    	xmlBase64RsaPublicKey = c.getString(c.getColumnIndex(FormsColumns.XML_BASE64_RSA_PUBLIC_KEY));
    	xmlDeviceIdPropertyName = c.getString(c.getColumnIndex(FormsColumns.XML_DEVICE_ID_PROPERTY_NAME));
    	xmlUserIdPropertyName = c.getString(c.getColumnIndex(FormsColumns.XML_USER_ID_PROPERTY_NAME));
    	xmlRootElementName = c.getString(c.getColumnIndex(FormsColumns.XML_ROOT_ELEMENT_NAME));

    	if ( parseFormDef && !formDefFile.exists() ) {
			throw new IllegalArgumentException("File does not exist! " + formDefFile.getAbsolutePath());
    	}

    	if ( !parseFormDef ) {
    		formDef = null;
    	} else {

			// OK -- parse the formDef file.
			HashMap<String,Object> om = null;
			try {
				om = mapper.readValue(formDefFile, HashMap.class);
			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			formDef = om;
			if ( formDef == null ) {
				throw new IllegalArgumentException("File is not a json file! " + formDefFile.getAbsolutePath());
			}
    	}

	}

	/**
	 *
	 * @param formDefFile
	 */
	@SuppressWarnings("unchecked")
	public FormInfo(File formDefFile) {
		id = -1;

		// save the File of the formDef...
		this.formDefFile = formDefFile;

		// these are not read/saved at the moment...
		description = null;

		// LEGACY
		File parentFile = formDefFile.getParentFile();
		formFilePath = parentFile.getAbsolutePath() + File.separator + parentFile.getName()+".xml";
		formMediaPath = parentFile.getAbsolutePath();

		// OK -- parse the formDef file.
		HashMap<String,Object> om = null;
		try {
			om = mapper.readValue(formDefFile, HashMap.class);
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		formDef = om;
		if ( formDef == null ) {
			throw new IllegalArgumentException("File is not a json file! " + formDefFile.getAbsolutePath());
		}

		///////////////////////////////////////////////////
		// TODO: DEPENDENCY ALERT!!!
		// TODO: DEPENDENCY ALERT!!!
		// TODO: DEPENDENCY ALERT!!!
		// TODO: DEPENDENCY ALERT!!!
		// THIS ASSUMES A CERTAIN STRUCTURE FOR THE formDef.json
		// file...

		ArrayList<Map<String,Object>> settings = (ArrayList<Map<String,Object>>) formDef.get("settings");
		if ( settings == null ) {
			throw new IllegalArgumentException("File is not a formdef json file! " + formDefFile.getAbsolutePath());

		}
		Map<String,Object> setting = null;

		setting = getSetting(FORMDEF_FORM_ID, settings);
		if ( setting != null ) {
			Object o = setting.get(FORMDEF_VALUE);
			if ( o == null || !(o instanceof String) ) {
    			throw new IllegalArgumentException("formId is not specified or invalid in the formdef json file! " + formDefFile.getAbsolutePath());
			}
			formId = (String) o;
		} else {
			throw new IllegalArgumentException("formId is not specified in the formdef json file! " + formDefFile.getAbsolutePath());
		}

		String fallbackLanguage = "default";
		String defaultLanguage;
		setting = getSetting(FORMDEF_DEFAULT_LOCALE, settings);
		if ( setting != null ) {
			Object o = setting.get(FORMDEF_VALUE);
			if ( o == null ) {
				defaultLanguage = null;
			} else if ( o instanceof String ) {
				defaultLanguage = (String) o;
			} else {
    			throw new IllegalArgumentException("defaultLocale is invalid in the formdef json file! " + formDefFile.getAbsolutePath());
			}
		} else {
			defaultLanguage = null;
		}

		Map<String,Object> formDefStruct = null;
		setting = getSetting(FORMDEF_FORM_TITLE, settings);
		if ( setting != null ) {
			Object o = setting.get(FORMDEF_VALUE);
			if ( o == null ) {
    			throw new IllegalArgumentException("formTitle is not specified in the formdef json file! " + formDefFile.getAbsolutePath());
			}
			if ( o instanceof String ) {
				language = (defaultLanguage != null) ? defaultLanguage : fallbackLanguage;
				formTitle = (String) o;
			} else {
				try {
					formDefStruct = (Map<String,Object>) o;

					if ( formDefStruct == null || formDefStruct.size() == 0 ) {
		    			throw new IllegalArgumentException("formTitle is not specified in the formdef json file! " + formDefFile.getAbsolutePath());
					}

					if ( !formDefStruct.containsKey(defaultLanguage) ) {
						String[] values = formDefStruct.keySet().toArray(new String[formDefStruct.size()]);
						Arrays.sort(values,0,values.length);
						defaultLanguage = values[0];
					}

					language = (defaultLanguage != null) ? defaultLanguage : fallbackLanguage;
					// just get the one title string from the file...
					formTitle = (String) formDefStruct.get(language);
				} catch (ClassCastException e) {
					e.printStackTrace();
	    			throw new IllegalArgumentException("formTitle is invalid in the formdef json file! " + formDefFile.getAbsolutePath());
				}
			}
		} else {
			throw new IllegalArgumentException("formTitle is not specified in the formdef json file! " + formDefFile.getAbsolutePath());
		}

		setting = getSetting(FORMDEF_FORM_VERSION, settings);
		if ( setting != null ) {
			Object o = setting.get(FORMDEF_VALUE);
			if ( o == null ) {
				formVersion = null;
			} else if ( o instanceof String ) {
				formVersion = (String) o;
			} else {
				formVersion = o.toString();
			}
		} else {
			formVersion = null;
		}

		setting = getSetting(FORMDEF_TABLE_ID, settings);
		if ( setting != null ) {
			Object o = setting.get(FORMDEF_VALUE);
			if ( o == null ) {
				tableId = formId;
			} else if ( o instanceof String ) {
				tableId = (String) o;
			} else {
				tableId = o.toString();
			}
		} else {
			tableId = formId;
		}

		setting = getSetting(FORMDEF_XML_SUBMISSION_URL, settings);
		if ( setting != null ) {
			Object o = setting.get(FORMDEF_VALUE);
			if ( o == null ) {
				xmlSubmissionUrl = null;
			} else if ( o instanceof String ) {
				xmlSubmissionUrl = (String) o;
			} else {
				throw new IllegalArgumentException("Invalid value for " + FORMDEF_XML_SUBMISSION_URL);
			}
		} else {
			xmlSubmissionUrl = null;
		}


		setting = getSetting(FORMDEF_BASE64_RSA_PUBLIC_KEY, settings);
		if ( setting != null ) {
			Object o = setting.get(FORMDEF_VALUE);
			if ( o == null ) {
				xmlBase64RsaPublicKey = null;
			} else if ( o instanceof String ) {
				xmlBase64RsaPublicKey = (String) o;
			} else {
				throw new IllegalArgumentException("Invalid value for " + FORMDEF_BASE64_RSA_PUBLIC_KEY);
			}
		} else {
			xmlBase64RsaPublicKey = null;
		}


		setting = getSetting(FORMDEF_XML_ROOT_ELEMENT_NAME, settings);
		if ( setting != null ) {
			Object o = setting.get(FORMDEF_VALUE);
			if ( o == null ) {
				xmlRootElementName = "data";
			} else if ( o instanceof String ) {
				xmlRootElementName = (String) o;
			} else {
				throw new IllegalArgumentException("Invalid value for " + FORMDEF_XML_ROOT_ELEMENT_NAME);
			}
		} else {
			xmlRootElementName = "data";
		}

		setting = getSetting(FORMDEF_XML_DEVICE_ID_PROPERTY_NAME, settings);
		if ( setting != null ) {
			Object o = setting.get(FORMDEF_VALUE);
			if ( o == null ) {
				xmlDeviceIdPropertyName = null;
			} else if ( o instanceof String ) {
				xmlDeviceIdPropertyName = (String) o;
			} else {
				throw new IllegalArgumentException("Invalid value for " + FORMDEF_XML_DEVICE_ID_PROPERTY_NAME);
			}
		} else {
			xmlDeviceIdPropertyName = null;
		}

		setting = getSetting(FORMDEF_XML_USER_ID_PROPERTY_NAME, settings);
		if ( setting != null ) {
			Object o = setting.get(FORMDEF_VALUE);
			if ( o == null ) {
				xmlUserIdPropertyName = null;
			} else if ( o instanceof String ) {
				xmlUserIdPropertyName = (String) o;
			} else {
				throw new IllegalArgumentException("Invalid value for " + FORMDEF_XML_USER_ID_PROPERTY_NAME);
			}
		} else {
			xmlUserIdPropertyName = null;
		}

		lastModificationDate = formDefFile.lastModified();

		File parentDir = new File(Survey.FORMS_PATH);

		ArrayList<String> pathElements = new ArrayList<String>();

		formDefFile = formDefFile.getParentFile();

		while ( formDefFile != null && !formDefFile.equals(parentDir) ) {
			pathElements.add(formDefFile.getName());
			formDefFile = formDefFile.getParentFile();
		}

		StringBuilder b = new StringBuilder();
		b.append("..");
		b.append(File.separator);
		for ( int i = pathElements.size()-1 ; i >= 0 ; --i ) {
			String element = pathElements.get(i);
			b.append(element);
			b.append(File.separator);
		}
		formPath = b.toString();

		Context c = Survey.getInstance().getApplicationContext();
        String ts = new SimpleDateFormat(c.getString(R.string.added_on_date_at_time), Locale.getDefault()).format(lastModificationDate);
        displaySubtext = ts;
	}

}