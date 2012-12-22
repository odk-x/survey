/*
 * Copyright (C) 2007 The Android Open Source Project
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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

/**
 *
 */
public class InstanceProvider extends ContentProvider {

    // private static final String t = "InstancesProvider";

    private static final int APP_VERSION = 1;
    private static final String APP_KEY = "org.opendatakit.survey";
    private static final String UPLOADS_TABLE_NAME = "uploads";

    private static final String TABLE_DEFS_TABLE_NAME = "table_definitions";
    private static final String TABLE_DEFS_TABLE_ID = "table_id";
    private static final String TABLE_DEFS_DB_TABLE_NAME = "db_table_name";

    private static final String COLUMN_DEFINITIONS_TABLE_NAME = "column_definitions";

    private static final String DATA_TABLE_ID_COLUMN = InstanceColumns.DATA_TABLE_INSTANCE_ID;
    private static final String DATA_TABLE_TIMESTAMP_COLUMN = "timestamp";
    private static final String DATA_TABLE_INSTANCE_NAME_COLUMN = "instance_name";

    private static HashMap<String, String> sInstancesProjectionMap;

    private static final int INSTANCES = 1;
    private static final int INSTANCE_ID = 2;

    private static final UriMatcher sUriMatcher;

    /*
     * 				String dbFile = defn.dbFile.getParent();
				defn.dbFile.getParentFile().mkdirs();
				mKitHelper = new WebKitDatabaseInfoHelper(dbFile, defn.dbFile.getName());
			    return mKitHelper;

     */
    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends WebKitDatabaseInfoHelper {

        DatabaseHelper(String dbPath, String databaseName) {
            super(dbPath, databaseName, null, APP_KEY, APP_VERSION);
        }


        @Override
        public void onCreateAppVersion(SQLiteDatabase db) {
           db.execSQL("CREATE TABLE " + UPLOADS_TABLE_NAME + " ("
        	   + InstanceColumns._ID + " integer primary key, "
        	   + InstanceColumns.DATA_TABLE_INSTANCE_ID + " text unique, "
        	   + InstanceColumns.XML_PUBLISH_TIMESTAMP + " integer, "
        	   + InstanceColumns.XML_PUBLISH_STATUS + " text, "
        	   + InstanceColumns.DISPLAY_SUBTEXT + " text)");
        }


        @Override
        public void onUpgradeAppVersion(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    private WebSqlDatabaseHelper h;
    private DatabaseHelper mDbHelper;


    @Override
    public boolean onCreate() {

    	h = new WebSqlDatabaseHelper();
    	WebDbDefinition defn = h.getWebKitDatabaseInfoHelper();
    	if ( defn != null ) {
    		mDbHelper = new DatabaseHelper(defn.dbFile.getParent(), defn.dbFile.getName());
    	}
        return true;
    }

    private String getDbTableName(SQLiteDatabase db, String tableId) {
    	Cursor c = null;
    	try {
    		c = db.query(TABLE_DEFS_TABLE_NAME, new String[] { TABLE_DEFS_DB_TABLE_NAME },
        		TABLE_DEFS_TABLE_ID + "=?", new String[] { tableId }, null, null, null);

    		if ( c.moveToFirst() ) {
    			int idx = c.getColumnIndex(TABLE_DEFS_DB_TABLE_NAME);
    			return c.getString(idx);
    		}
    	} finally {
    		if ( c != null && !c.isClosed() ) {
    			c.close();
    		}
    	}
    	return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
    	List<String> segments = uri.getPathSegments();

    	if ( segments.size() < 1 || segments.size() > 2 ) {
    		throw new IllegalArgumentException("Unknown URI (too many segments!) " + uri);
    	}

    	String tableId = segments.get(0);
    	String instanceId = (segments.size() == 2 ? segments.get(1) : null);

    	SQLiteDatabase db = mDbHelper.getReadableDatabase();

    	String dbTableName = getDbTableName(db, tableId);
    	if ( dbTableName == null ) {
    		throw new IllegalArgumentException("Unknown URI (no matching tableId) " + uri);
    	}

    	dbTableName = "\"" + dbTableName + "\"";

    	// ARGH! we must ensure that we have records in our UPLOADS_TABLE_NAME for every
    	// distinct instance in the data table.
    	StringBuilder b = new StringBuilder();
    	b.append("INSERT INTO ").append(UPLOADS_TABLE_NAME).append("(").append(InstanceColumns.DATA_TABLE_INSTANCE_ID).append(") ")
    	 .append("SELECT ").append(InstanceColumns.DATA_TABLE_INSTANCE_ID).append(" FROM (")
    	    .append("SELECT DISTINCT ").append(DATA_TABLE_ID_COLUMN).append(" as ").append(InstanceColumns.DATA_TABLE_INSTANCE_ID).append(" FROM ").append(dbTableName)
    	    .append(" EXCEPT SELECT DISTINCT ").append(InstanceColumns.DATA_TABLE_INSTANCE_ID).append(" FROM ").append(UPLOADS_TABLE_NAME).append(")");

    	db.execSQL(b.toString());

    	// First, ensure that

    	b.setLength(0);
    	b.append("SELECT ");
    	b.append(UPLOADS_TABLE_NAME).append(".").append(InstanceColumns._ID).append(",")
    	 .append(dbTableName).append(".").append("*").append(",");
    	// b.append(dbTableName).append(".").append(InstanceColumns._ID).append(",");
    	b.append("CASE WHEN ").append(DATA_TABLE_TIMESTAMP_COLUMN).append(" IS NULL THEN null")
	   	 .append(" WHEN ").append(InstanceColumns.XML_PUBLISH_TIMESTAMP).append(" IS NULL THEN null")
	   	 .append(" WHEN ").append(DATA_TABLE_TIMESTAMP_COLUMN).append(" > ")
	   	                  .append(InstanceColumns.XML_PUBLISH_TIMESTAMP).append(" THEN null")
	   	 .append(" ELSE ").append(InstanceColumns.XML_PUBLISH_TIMESTAMP)
	   	 .append(" END as ").append(InstanceColumns.XML_PUBLISH_TIMESTAMP).append(",");
    	b.append("CASE WHEN ").append(DATA_TABLE_TIMESTAMP_COLUMN).append(" IS NULL THEN null")
    	 .append(" WHEN ").append(InstanceColumns.XML_PUBLISH_TIMESTAMP).append(" IS NULL THEN null")
    	 .append(" WHEN ").append(DATA_TABLE_TIMESTAMP_COLUMN).append(" > ")
    	                  .append(InstanceColumns.XML_PUBLISH_TIMESTAMP).append(" THEN null")
    	 .append(" ELSE ").append(InstanceColumns.XML_PUBLISH_STATUS)
    	 .append(" END as ").append(InstanceColumns.XML_PUBLISH_STATUS).append(",");
    	b.append("CASE WHEN ").append(DATA_TABLE_TIMESTAMP_COLUMN).append(" IS NULL THEN null")
	   	 .append(" WHEN ").append(InstanceColumns.XML_PUBLISH_TIMESTAMP).append(" IS NULL THEN null")
	   	 .append(" WHEN ").append(DATA_TABLE_TIMESTAMP_COLUMN).append(" > ")
	   	                  .append(InstanceColumns.XML_PUBLISH_TIMESTAMP).append(" THEN null")
	   	 .append(" ELSE ").append(InstanceColumns.DISPLAY_SUBTEXT)
	   	 .append(" END as ").append(InstanceColumns.DISPLAY_SUBTEXT).append(",");
    	b.append(DATA_TABLE_INSTANCE_NAME_COLUMN)
    	 .append(" as ").append(InstanceColumns.DISPLAY_NAME);
    	b.append(" FROM ").append(dbTableName);
    	b.append(" LEFT JOIN ").append(UPLOADS_TABLE_NAME)
    	 .append(" ON ").append(dbTableName).append(".").append(DATA_TABLE_ID_COLUMN)
    	 .append("=").append(UPLOADS_TABLE_NAME).append(".").append(InstanceColumns.DATA_TABLE_INSTANCE_ID);

    	if ( selection != null || instanceId != null) {
    		b.append(" WHERE ");
    		if ( selection != null ) {
    			b.append("(").append(selection).append(")");
    		}
    		if ( instanceId != null ) {
    			if ( selection != null ) {
    				b.append(" AND ");
    				if ( selectionArgs != null ) {
        				String[] args = new String[selectionArgs.length];
        				for ( int i = 0 ; i < selectionArgs.length ; ++i ) {
        					args[i] = selectionArgs[i];
        				}
        				args[selectionArgs.length] = instanceId;
        				selectionArgs = args;
    				} else {
    					selectionArgs = new String[] { instanceId };
    				}
    			} else {
    				selectionArgs = new String[] { instanceId };
    			}
    			b.append(dbTableName).append(".").append(DATA_TABLE_ID_COLUMN).append("=?");
    		}
    	}
    	if ( sortOrder != null ) {
    		b.append(" ORDER BY ").append(sortOrder);
    	}
    	Cursor c = db.rawQuery(b.toString(), selectionArgs);
        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }


    @Override
    public String getType(Uri uri) {
    	// don't see the point of trying to implement this call...
    	return null;
//        switch (sUriMatcher.match(uri)) {
//            case INSTANCES:
//                return InstanceColumns.CONTENT_TYPE;
//
//            case INSTANCE_ID:
//                return InstanceColumns.CONTENT_ITEM_TYPE;
//
//            default:
//                throw new IllegalArgumentException("Unknown URI " + uri);
//        }
    }


    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        throw new IllegalArgumentException("Insert not implemented!");
    }

    private String getDisplaySubtext(String xmlPublishStatus, Date xmlPublishDate) {
        if ( xmlPublishDate == null ) {
        	return getContext().getString(R.string.not_yet_sent);
    	} else if (InstanceProviderAPI.STATUS_SUBMITTED.equalsIgnoreCase(xmlPublishStatus)) {
    		return new SimpleDateFormat(getContext().getString(R.string.sent_on_date_at_time)).format(xmlPublishDate);
    	} else if (InstanceProviderAPI.STATUS_SUBMISSION_FAILED.equalsIgnoreCase(xmlPublishStatus)) {
    		return new SimpleDateFormat(getContext().getString(R.string.sending_failed_on_date_at_time)).format(xmlPublishDate);
    	} else {
    		throw new IllegalStateException("Unrecognized xmlPublishStatus: " + xmlPublishStatus);
        }
    }

    /**
     * This method removes the entry from the content provider, and also removes any associated files.
     * files:  form.xml, [formmd5].formdef, formname {directory}
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
    	List<String> segments = uri.getPathSegments();

    	if ( segments.size() < 1 || segments.size() > 2 ) {
    		throw new IllegalArgumentException("Unknown URI (too many segments!) " + uri);
    	}

    	String tableId = segments.get(0);
    	String instanceId = (segments.size() == 2 ? segments.get(1) : null);

    	SQLiteDatabase db = mDbHelper.getWritableDatabase();

    	String dbTableName = getDbTableName(db, tableId);
    	if ( dbTableName == null ) {
    		return 0; // not known...
    	}

    	dbTableName = "\"" + dbTableName + "\"";

        if ( segments.size() == 2 ) {
        	where = "(" + where + ") AND (" + InstanceColumns.DATA_TABLE_INSTANCE_ID + "=? )";
        	if ( whereArgs != null ) {
        		String[] args = new String[whereArgs.length+1];
        		for ( int i = 0 ; i < whereArgs.length ; ++i ) {
        			args[i] = whereArgs[i];
        		}
        		args[whereArgs.length] = instanceId;
        		whereArgs = args;
        	} else {
        		whereArgs = new String[] { instanceId };
        	}
        }

        List<String> ids = new ArrayList<String>();
        Cursor del = null;
        try {
	    	del = this.query(uri, null, where, whereArgs, null);
	        del.moveToPosition(-1);
	        while (del.moveToNext()) {
	            String iId = del.getString(del.getColumnIndex(InstanceColumns.DATA_TABLE_INSTANCE_ID));
	        	ids.add(iId);
	        	File f = new File(new File(new File(Survey.INSTANCES_PATH), tableId), iId);
	        	if ( f.exists() ) {
	        		if ( f.isDirectory() ) {
	        			FileUtils.deleteDirectory(f);
	        		} else {
	        			f.delete();
	        		}
	        	}

	        }
        } catch (IOException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Unable to delete instance directory: " + e.toString());
		} finally {
        	if ( del != null ) {
        		del.close();
        	}
        }

        for ( String id : ids ) {
	        db.delete(UPLOADS_TABLE_NAME, InstanceColumns.DATA_TABLE_INSTANCE_ID + "=?",
	        			new String[] { id } );
	        db.delete(dbTableName, DATA_TABLE_ID_COLUMN + "=?",
	        			new String[] { id } );
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return ids.size();
    }


    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
    	List<String> segments = uri.getPathSegments();

    	if ( segments.size() !=2 ) {
    		throw new IllegalArgumentException("Unknown URI (does not specify instance!) " + uri);
    	}

    	String tableId = segments.get(0);

    	SQLiteDatabase db = mDbHelper.getWritableDatabase();

    	String dbTableName = getDbTableName(db, tableId);
    	if ( dbTableName == null ) {
    		throw new IllegalArgumentException("Unknown URI (no matching tableId) " + uri);
    	}

    	dbTableName = "\"" + dbTableName + "\"";

    	// run the query to get all the ids...
    	List<String> ids = new ArrayList<String>();
        Cursor ref = null;
        try {
        	// use this provider's query interface to get the set of ids that match (if any)
        	ref = this.query(uri, null, where, whereArgs, null);
        	ref.moveToPosition(-1);
	        while (ref.moveToNext()) {
	            String iId = ref.getString(ref.getColumnIndex(InstanceColumns.DATA_TABLE_INSTANCE_ID));
	        	ids.add(iId);
	        }
		} finally {
        	if ( ref != null ) {
        		ref.close();
        	}
        }

        // update the values string...
        if (values.containsKey(InstanceColumns.XML_PUBLISH_STATUS)) {
        	Date xmlPublishDate = new Date();
            values.put(InstanceColumns.XML_PUBLISH_TIMESTAMP, xmlPublishDate.getTime());
        	String xmlPublishStatus = values.getAsString(InstanceColumns.XML_PUBLISH_STATUS);
            if (values.containsKey(InstanceColumns.DISPLAY_SUBTEXT) == false) {
                String text = getDisplaySubtext(xmlPublishStatus, xmlPublishDate);
                values.put(InstanceColumns.DISPLAY_SUBTEXT, text);
            }
        }

        int count = 0;
        for ( String id : ids ) {
        	values.put(InstanceColumns.DATA_TABLE_INSTANCE_ID, id);
        	count += (db.replace(UPLOADS_TABLE_NAME, null, values) != -1) ? 1 : 0;
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(InstanceProviderAPI.AUTHORITY, "instances", INSTANCES);
        sUriMatcher.addURI(InstanceProviderAPI.AUTHORITY, "instances/#", INSTANCE_ID);

        sInstancesProjectionMap = new HashMap<String, String>();
        sInstancesProjectionMap.put(InstanceColumns._ID, InstanceColumns._ID);
        sInstancesProjectionMap.put(InstanceColumns.DATA_TABLE_INSTANCE_ID, InstanceColumns.DATA_TABLE_INSTANCE_ID);
        sInstancesProjectionMap.put(InstanceColumns.XML_PUBLISH_TIMESTAMP, InstanceColumns.XML_PUBLISH_TIMESTAMP);
        sInstancesProjectionMap.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.XML_PUBLISH_STATUS);
    }

}
