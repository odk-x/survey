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

import org.opendatakit.survey.android.database.ODKSQLiteOpenHelper;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

/**
 * Opens and manages the WebKit database that holds all of our tables...
 *
 * @author mitchellsundt@gmail.com
 *
 */
public abstract class WebKitDatabaseInfoHelper extends ODKSQLiteOpenHelper {

	// private static final String t = "WebKitDatabaseInfoHelper";

	private static final String WEBKIT_INFO_TABLE = "__WebKitDatabaseInfoTable__";
	private static final String WEBKIT_INFO_KEY = "key";
	private static final String WEBKIT_INFO_VALUE = "value";

	static int WEBKIT_VERSION = 1;

	private static final String WEBKIT_APP_VERSION_TABLE = "_AppVersionTable_";
	private static final String WEBKIT_APP_KEY = "appkey";
	private static final String WEBKIT_APP_VERSION = "version";

	private String appKey;
	private int version;

	public WebKitDatabaseInfoHelper(String path, String name,
			CursorFactory factory, String appKey, int version) {
		super(path, name, factory, WEBKIT_VERSION);
		this.appKey = appKey;
		this.version = version;
	}

	/**
	 * Called when the database is created for the first time. This is where the
	 * creation of tables and the initial population of the tables should
	 * happen.
	 *
	 * @param db
	 *            The database.
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE IF NOT EXISTS " + WEBKIT_INFO_TABLE + " ("
				+ WEBKIT_INFO_KEY + " text, " + WEBKIT_INFO_VALUE + " text );");

		// insert if missing...

		Cursor c = null;
		try {
			c = db.query(WEBKIT_INFO_TABLE, null, null, null, null, null, null);

			if (c.getCount() == 0) {
				c.close();
				c = null;

				ContentValues ov = new ContentValues();
				ov.put(WEBKIT_INFO_KEY, "WebKitDatabaseVersionKey");
				ov.put(WEBKIT_INFO_VALUE, "1");
				db.insert(WEBKIT_INFO_TABLE, null, ov);
			}
		} finally {
			if (c != null && !c.isClosed()) {
				c.close();
			}
		}

		db.execSQL("CREATE TABLE IF NOT EXISTS " + WEBKIT_APP_VERSION_TABLE
				+ " (" + WEBKIT_APP_KEY + " text, " + WEBKIT_APP_VERSION
				+ " integer );");
	}

	/**
	 * Called when the database needs to be upgraded. The implementation should
	 * use this method to drop tables, add tables, or do anything else it needs
	 * to upgrade to the new schema version.
	 * <p>
	 * The SQLite ALTER TABLE documentation can be found <a
	 * href="http://sqlite.org/lang_altertable.html">here</a>. If you add new
	 * columns you can use ALTER TABLE to insert them into a live table. If you
	 * rename or remove columns you can use ALTER TABLE to rename the old table,
	 * then create the new table and then populate the new table with the
	 * contents of the old table.
	 *
	 * @param db
	 *            The database.
	 * @param oldVersion
	 *            The old database version.
	 * @param newVersion
	 *            The new database version.
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	public abstract void onCreateAppVersion(SQLiteDatabase db);

	public abstract void onUpgradeAppVersion(SQLiteDatabase db, int oldVersion,
			int newVersion);

	private void assertAppVersion(SQLiteDatabase db, String appKey, int version) {

		Integer actualVersion = null;

		Cursor c = null;
		try {
			c = db.query(WEBKIT_APP_VERSION_TABLE, null, WEBKIT_APP_KEY + "=?",
					new String[] { appKey }, null, null, WEBKIT_APP_VERSION
							+ " DESC");
			int appVersionIdx = c.getColumnIndex(WEBKIT_APP_VERSION);

			if (c.getCount() > 0 && c.moveToFirst()) {
				actualVersion = c.getInt(appVersionIdx);
			}
		} finally {
			if (c != null && !c.isClosed()) {
				c.close();
			}
		}

		if (actualVersion == null || actualVersion != version) {
			db.beginTransaction();
			try {
				if (actualVersion == null) {
					onCreateAppVersion(db);
				} else {
					onUpgradeAppVersion(db, actualVersion, version);
				}
				db.delete(WEBKIT_APP_VERSION_TABLE, WEBKIT_APP_KEY + "=?",
						new String[] { appKey });
				ContentValues v = new ContentValues();
				v.put(WEBKIT_APP_KEY, appKey);
				v.put(WEBKIT_APP_VERSION, version);
				db.insert(WEBKIT_APP_VERSION_TABLE, null, v);
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}
		}
	}

	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
		assertAppVersion(db, appKey, version);
	}
}