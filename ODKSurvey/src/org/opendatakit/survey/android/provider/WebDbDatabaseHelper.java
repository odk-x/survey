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

import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.database.ODKSQLiteOpenHelper;
import org.opendatakit.survey.android.utilities.ODKFileUtils;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/**
 * Opens and manages the Databases.db database used by WebSQL and Java app.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class WebDbDatabaseHelper extends ODKSQLiteOpenHelper {

	// private static final String t = "WebDbDatabaseHelper";

	static String WEBDB_DATABASE_NAME = "Databases.db";
	static String WEBDB_DATABASES_TABLE = "Databases";
	static String WEBDB_ORIGINS_TABLE = "Origins";

	public static String WEBDB_INSTANCE_DB_SHORT_NAME = "odk";
	public static String WEBDB_INSTANCE_DB_DISPLAY_NAME = "ODK Instances Database";
	public static Integer WEBDB_INSTANCE_DB_ESTIMATED_SIZE = 65536;
	public static Integer WEBDB_INSTANCE_DB_VERSION = 1;
	static int WEBDB_VERSION = 1;

	static final String COMMON_ORIGIN = "origin";

	static final String DATABASES_GUID = "guid";
	static final String DATABASES_NAME = "name";
	static final String DATABASES_DISPLAY_NAME = "displayName";
	static final String DATABASES_ESTIMATED_SIZE = "estimatedSize";
	static final String DATABASES_PATH = "path";

	static final String ORIGINS_QUOTA = "quota";

	public static String dbPath() {
		String path = Survey.WEBDB_PATH;
		ODKFileUtils.createFolder(path);
		return path;
	}

	public WebDbDatabaseHelper() {
		super(dbPath(), WEBDB_DATABASE_NAME, null, WEBDB_VERSION);
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
		db.execSQL("CREATE TABLE IF NOT EXISTS " + WEBDB_ORIGINS_TABLE + " ("
				+ COMMON_ORIGIN + " text, " + ORIGINS_QUOTA + " integer );");

		db.execSQL("CREATE TABLE IF NOT EXISTS " + WEBDB_DATABASES_TABLE + " ("
				+ DATABASES_GUID + " integer primary key, " + COMMON_ORIGIN
				+ " text, " + DATABASES_NAME + " text, "
				+ DATABASES_DISPLAY_NAME + " text, " + DATABASES_ESTIMATED_SIZE
				+ " integer, " + DATABASES_PATH + " text );");

		ContentValues ov = new ContentValues();
		ov.put(COMMON_ORIGIN, FileProvider.getFormOriginString());
		ov.put(ORIGINS_QUOTA, 262144);
		db.insert(WEBDB_ORIGINS_TABLE, null, ov);

		ContentValues v = new ContentValues();
		v.put(DATABASES_GUID, 1);
		v.put(COMMON_ORIGIN, FileProvider.getFormOriginString());
		v.put(DATABASES_NAME, WEBDB_INSTANCE_DB_SHORT_NAME);
		v.put(DATABASES_DISPLAY_NAME, WEBDB_INSTANCE_DB_DISPLAY_NAME);
		v.put(DATABASES_ESTIMATED_SIZE, WEBDB_INSTANCE_DB_ESTIMATED_SIZE);
		v.put(DATABASES_PATH,
				String.format("%1$016d.db", WEBDB_INSTANCE_DB_VERSION));
		db.insert("Databases", null, v);

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
}