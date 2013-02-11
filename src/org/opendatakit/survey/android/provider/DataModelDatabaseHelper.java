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

package org.opendatakit.survey.android.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.opendatakit.survey.android.provider.FormsProviderAPI.FormsColumns;
import org.opendatakit.survey.android.provider.InstanceProviderAPI.InstanceColumns;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * This class helps open, create, and upgrade the database file.
 */
public class DataModelDatabaseHelper extends WebKitDatabaseInfoHelper {

	static final ObjectMapper mapper = new ObjectMapper();
	static final String APP_KEY = "org.opendatakit.survey";
	static final int APP_VERSION = 1;

	public static final String DATA_TABLE_ID_COLUMN = "id";
	public static final String DATA_TABLE_SAVED_COLUMN = "saved";
	public static final String DATA_TABLE_TIMESTAMP_COLUMN = "timestamp";
	public static final String DATA_TABLE_INSTANCE_NAME_COLUMN = "instance_name";

	private static final String TABLE_DEFS_TABLE_NAME = "table_definitions";
	private static final String TABLE_DEFS_TABLE_ID = "table_id";
	private static final String TABLE_DEFS_DB_TABLE_NAME = "db_table_name";

	private static final String COLUMN_DEFINITIONS_TABLE_NAME = "column_definitions";
	private static final String COLUMN_DEFINITIONS_TABLE_ID = "table_id"; // not
																			// null
	private static final String COLUMN_DEFINITIONS_ELEMENT_KEY = "element_key"; // not
																				// null
	private static final String COLUMN_DEFINITIONS_ELEMENT_NAME = "element_name"; // not
																					// null
	private static final String COLUMN_DEFINITIONS_ELEMENT_TYPE = "element_type";
	private static final String COLUMN_DEFINITIONS_LIST_CHILD_ELEMENT_KEYS = "list_child_element_keys"; // json
																										// array
																										// [element_key]
	private static final String COLUMN_DEFINITIONS_IS_PERSISTED = "is_persisted"; // not
																					// null
																					// integer
																					// as
																					// boolean
	private static final String COLUMN_DEFINITIONS_JOINS = "joins"; // json
																	// array
																	// [{table_id:,element_key:}...]

	DataModelDatabaseHelper(String dbPath, String databaseName) {
		super(dbPath, databaseName, null, APP_KEY, APP_VERSION);
	}

	private void commonTableDefn(SQLiteDatabase db, String uploadsTableName,
			String formsTableName) {
		db.execSQL("CREATE TABLE IF NOT EXISTS " + uploadsTableName + " ("
				+ InstanceColumns._ID + " integer primary key, "
				+ InstanceColumns.DATA_TABLE_INSTANCE_ID + " text unique, "
				+ InstanceColumns.XML_PUBLISH_TIMESTAMP + " integer, "
				+ InstanceColumns.XML_PUBLISH_STATUS + " text, "
				+ InstanceColumns.DISPLAY_SUBTEXT + " text)");

		db.execSQL("CREATE TABLE IF NOT EXISTS " + formsTableName + " ("
				+ FormsColumns._ID + " integer primary key, "
				+ FormsColumns.DISPLAY_NAME + " text not null, "
				+ FormsColumns.DISPLAY_SUBTEXT + " text not null, "
				+ FormsColumns.DESCRIPTION + " text, " + FormsColumns.TABLE_ID
				+ " text not null, " + FormsColumns.FORM_ID
				+ " text not null, " + FormsColumns.FORM_VERSION + " text, "
				+ FormsColumns.FORM_FILE_PATH
				+ " text null, "
				+ FormsColumns.FORM_MEDIA_PATH
				+ " text not null, "
				+ FormsColumns.FORM_PATH
				+ " text not null, "
				+ FormsColumns.MD5_HASH
				+ " text not null, "
				+ FormsColumns.DATE
				+ " integer not null, " // milliseconds
				+ FormsColumns.DEFAULT_FORM_LOCALE + " text, "
				+ FormsColumns.XML_SUBMISSION_URL + " text, "
				+ FormsColumns.XML_BASE64_RSA_PUBLIC_KEY + " text, "
				+ FormsColumns.XML_ROOT_ELEMENT_NAME + " text, "
				+ FormsColumns.XML_DEVICE_ID_PROPERTY_NAME + " text, "
				+ FormsColumns.XML_USER_ID_PROPERTY_NAME + " text );");
	}

	@Override
	public void onCreateAppVersion(SQLiteDatabase db) {
		commonTableDefn(db, InstanceProvider.UPLOADS_TABLE_NAME,
				FormsProvider.FORMS_TABLE_NAME);
	}

	@Override
	public void onUpgradeAppVersion(SQLiteDatabase db, int oldVersion,
			int newVersion) {
		// for now, upgrade and creation use the same codepath...
		commonTableDefn(db, InstanceProvider.UPLOADS_TABLE_NAME,
				FormsProvider.FORMS_TABLE_NAME);
	}

	/**
	 * Accessor to retrieve the database table name given the tableId
	 *
	 * @param db
	 * @param tableId
	 * @return
	 */
	public static String getDbTableName(SQLiteDatabase db, String tableId) {
		Cursor c = null;
		try {
			c = db.query(TABLE_DEFS_TABLE_NAME,
					new String[] { TABLE_DEFS_DB_TABLE_NAME },
					TABLE_DEFS_TABLE_ID + "=?", new String[] { tableId }, null,
					null, null);

			if (c.moveToFirst()) {
				int idx = c.getColumnIndex(TABLE_DEFS_DB_TABLE_NAME);
				return c.getString(idx);
			}
		} finally {
			if (c != null && !c.isClosed()) {
				c.close();
			}
		}
		return null;
	}

	public static class Join {
		public final String tableId;
		public final String elementKey;

		Join(String tableId, String elementKey) {
			this.tableId = tableId;
			this.elementKey = elementKey;
		}
	};

	public static class ColumnDefinition {
		public final String elementKey;
		public final String elementName;
		public final String elementType;
		public final boolean isPersisted;

		public final ArrayList<ColumnDefinition> children = new ArrayList<ColumnDefinition>();
		public final ArrayList<Join> joins = new ArrayList<Join>();
		public ColumnDefinition parent = null;

		ColumnDefinition(String elementKey, String elementName,
				String elementType, boolean isPersisted) {
			this.elementKey = elementKey;
			this.elementName = elementName;
			this.elementType = elementType;
			this.isPersisted = isPersisted;
		}

		private void setParent(ColumnDefinition parent) {
			this.parent = parent;
		}

		void addJoin(Join j) {
			joins.add(j);
		}

		void addChild(ColumnDefinition child) {
			child.setParent(this);
			children.add(child);
		}
	};

	private static class ColumnContainer {
		public ColumnDefinition defn = null;
		public ArrayList<String> children = null;
	};

	/**
	 * Covert the ColumnDefinition map into a JSON schema.
	 *
	 * @param defns
	 * @return
	 */
	public static TreeMap<String, Object> getDataModel(
			Map<String, ColumnDefinition> defns) {
		TreeMap<String, Object> model = new TreeMap<String, Object>();

		for (ColumnDefinition c : defns.values()) {
			if (c.parent == null) {
				model.put(c.elementName, new TreeMap<String, Object>());
				@SuppressWarnings("unchecked")
				TreeMap<String, Object> jsonSchema = (TreeMap<String, Object>) model
						.get(c.elementName);
				getDataModelHelper(jsonSchema, c);
			}
		}
		return model;
	}

	private static void getDataModelHelper(TreeMap<String, Object> jsonSchema,
			ColumnDefinition c) {
		if (c.elementType.equals("string")) {
			jsonSchema.put("type", "string");
			jsonSchema.put("elementKey", c.elementKey);
			jsonSchema.put("isPersisted", c.isPersisted);
		} else if (c.elementType.equals("number")) {
			jsonSchema.put("type", "number");
			jsonSchema.put("elementKey", c.elementKey);
			jsonSchema.put("isPersisted", c.isPersisted);
		} else if (c.elementType.equals("integer")) {
			jsonSchema.put("type", "integer");
			jsonSchema.put("elementKey", c.elementKey);
			jsonSchema.put("isPersisted", c.isPersisted);
		} else if (c.elementType.equals("boolean")) {
			jsonSchema.put("type", "boolean");
			jsonSchema.put("elementKey", c.elementKey);
			jsonSchema.put("isPersisted", c.isPersisted);
		} else if (c.elementType.equals("array")) {
			jsonSchema.put("type", "array");
			jsonSchema.put("elementKey", c.elementKey);
			jsonSchema.put("isPersisted", c.isPersisted);
			ColumnDefinition ch = c.children.get(0);
			jsonSchema.put("items", new TreeMap<String, Object>());
			@SuppressWarnings("unchecked")
			TreeMap<String, Object> itemSchema = (TreeMap<String, Object>) jsonSchema
					.get("items");
			getDataModelHelper(itemSchema, ch); // recursion...
		} else {
			jsonSchema.put("type", "object");
			if (!c.elementType.equals("object")) {
				jsonSchema.put("elementType", c.elementType);
			}
			jsonSchema.put("elementKey", c.elementKey);
			jsonSchema.put("isPersisted", c.isPersisted);
			jsonSchema.put("properties", new TreeMap<String, Object>());
			@SuppressWarnings("unchecked")
			TreeMap<String, Object> propertiesSchema = (TreeMap<String, Object>) jsonSchema
					.get("properties");
			for (ColumnDefinition ch : c.children) {
				propertiesSchema.put(c.elementName,
						new TreeMap<String, Object>());
				@SuppressWarnings("unchecked")
				TreeMap<String, Object> itemSchema = (TreeMap<String, Object>) propertiesSchema
						.get(c.elementName);
				getDataModelHelper(itemSchema, ch); // recursion...
			}
		}
	}

	/**
	 * Return a map of (elementKey -> ColumnDefinition)
	 *
	 * @param db
	 * @param tableId
	 * @return
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	public static Map<String, ColumnDefinition> getColumnDefinitions(
			SQLiteDatabase db, String tableId) throws JsonParseException,
			JsonMappingException, IOException {
		Map<String, ColumnDefinition> defn = new HashMap<String, ColumnDefinition>();

		Cursor c = null;
		try {
			c = db.query(COLUMN_DEFINITIONS_TABLE_NAME, null,
					COLUMN_DEFINITIONS_TABLE_ID + "=?",
					new String[] { tableId }, null, null, null);

			if (c.moveToFirst()) {
				int idxEK = c.getColumnIndex(COLUMN_DEFINITIONS_ELEMENT_KEY);
				int idxEN = c.getColumnIndex(COLUMN_DEFINITIONS_ELEMENT_NAME);
				int idxET = c.getColumnIndex(COLUMN_DEFINITIONS_ELEMENT_TYPE);
				int idxIP = c.getColumnIndex(COLUMN_DEFINITIONS_IS_PERSISTED);
				int idxLIST = c
						.getColumnIndex(COLUMN_DEFINITIONS_LIST_CHILD_ELEMENT_KEYS);
				int idxJOINS = c.getColumnIndex(COLUMN_DEFINITIONS_JOINS);
				HashMap<String, ColumnContainer> ref = new HashMap<String, ColumnContainer>();

				do {
					String elementKey = c.getString(idxEK);
					String elementName = c.getString(idxEN);
					String elementType = c.getString(idxET);
					boolean isPersisted = (c.getInt(idxIP) != 0);
					String childrenString = c.isNull(idxLIST) ? null : c
							.getString(idxLIST);
					String joinsString = c.isNull(idxJOINS) ? null : c
							.getString(idxJOINS);
					ColumnContainer ctn = new ColumnContainer();
					ctn.defn = new ColumnDefinition(elementKey, elementName,
							elementType, isPersisted);

					if (childrenString != null) {
						@SuppressWarnings("unchecked")
						ArrayList<String> l = mapper.readValue(childrenString,
								ArrayList.class);
						ctn.children = l;
					}

					if (joinsString != null) {
						@SuppressWarnings("unchecked")
						ArrayList<Object> joins = mapper.readValue(joinsString,
								ArrayList.class);
						for (Object o : joins) {
							@SuppressWarnings("unchecked")
							Map<String, Object> m = (Map<String, Object>) o;
							String tId = (String) m.get("table_id");
							String tEK = (String) m.get("element_key");

							Join j = new Join(tId, tEK);
							ctn.defn.addJoin(j);
						}
					}

					ref.put(elementKey, ctn);
				} while (c.moveToNext());

				// OK now connect all the children...

				for (ColumnContainer ctn : ref.values()) {
					if (ctn.children != null) {
						for (String ek : ctn.children) {
							ColumnContainer child = ref.get(ek);
							if (child == null) {
								throw new IllegalArgumentException(
										"Unexpected missing child element: "
												+ ek);
							}
							ctn.defn.addChild(child.defn);
						}
					}
				}

				// and construct the list of entries...
				for (ColumnContainer ctn : ref.values()) {
					defn.put(ctn.defn.elementKey, ctn.defn);
				}
				return defn;
			}
		} finally {
			if (c != null && !c.isClosed()) {
				c.close();
			}
		}
		return null;
	}
}