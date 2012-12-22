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
import java.util.HashSet;

import org.apache.commons.io.FileUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.database.ODKSQLiteOpenHelper;
import org.opendatakit.survey.android.provider.FormsProviderAPI.FormsColumns;
import org.opendatakit.survey.android.utilities.ODKFileUtils;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 *
 */
public class FormsProvider extends ContentProvider {

    private static final String t = "FormsProvider";


    private static final String DATABASE_NAME = "forms.db";
    private static final int DATABASE_VERSION = 10;
    private static final String FORMS_TABLE_NAME = "formDefs";

    private static HashMap<String, String> sFormsProjectionMap;

    private static final int FORMS = 1;
    private static final int FORM_ID = 2;

    private static final UriMatcher sUriMatcher;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends ODKSQLiteOpenHelper {

        DatabaseHelper(String databaseName) {
            super(Survey.METADATA_PATH, databaseName, null, DATABASE_VERSION);
        }


        @Override
        public void onCreate(SQLiteDatabase db) {
        	onCreateNamed(db, FORMS_TABLE_NAME);
        }

        private void onCreateNamed(SQLiteDatabase db, String tableName) {
            db.execSQL("CREATE TABLE " + tableName + " ("
            		+ FormsColumns._ID + " integer primary key, "
            		+ FormsColumns.DISPLAY_NAME + " text not null, "
                    + FormsColumns.DISPLAY_SUBTEXT + " text not null, "
                    + FormsColumns.DESCRIPTION + " text, "
                    + FormsColumns.TABLE_ID + " text not null, "
                    + FormsColumns.FORM_ID + " text not null, "
                    + FormsColumns.FORM_VERSION + " text, "
                    + FormsColumns.FORM_FILE_PATH + " text null, "
                    + FormsColumns.FORM_MEDIA_PATH + " text not null, "
                    + FormsColumns.FORM_PATH + " text not null, "
                    + FormsColumns.MD5_HASH + " text not null, "
                    + FormsColumns.DATE + " integer not null, " // milliseconds
                    + FormsColumns.DEFAULT_FORM_LOCALE + " text, "
                    + FormsColumns.XML_SUBMISSION_URL + " text, "
                    + FormsColumns.XML_BASE64_RSA_PUBLIC_KEY + " text, "
                    + FormsColumns.XML_ROOT_ELEMENT_NAME + " text );");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        	if ( oldVersion < 10 ) {
                Log.w(t, "Upgrading database from version " + oldVersion + " to " + newVersion
                        + ", which will destroy all old data");
                db.execSQL("DROP TABLE IF EXISTS " + FORMS_TABLE_NAME);
                onCreate(db);
                return;
        	} else {
            	// int initialVersion = oldVersion;
        		/* retained as an example of how to migrate...
        		// adding BASE64_RSA_PUBLIC_KEY and changing type and name of integer MODEL_VERSION to text VERSION
                db.execSQL("DROP TABLE IF EXISTS " + TEMP_FORMS_TABLE_NAME);
                onCreateNamed(db, TEMP_FORMS_TABLE_NAME);
        		db.execSQL("INSERT INTO " + TEMP_FORMS_TABLE_NAME + " ("
                		+ FormsColumns._ID + ", "
                		+ FormsColumns.DISPLAY_NAME + ", "
                        + FormsColumns.DISPLAY_SUBTEXT + ", "
                        + FormsColumns.DESCRIPTION + ", "
                        + FormsColumns.JR_FORM_ID + ", "
                        + FormsColumns.MD5_HASH + ", "
                        + FormsColumns.DATE + ", " // milliseconds
                        + FormsColumns.FORM_MEDIA_PATH + ", "
                        + FormsColumns.FORM_FILE_PATH + ", "
                        + FormsColumns.LANGUAGE + ", "
                        + FormsColumns.SUBMISSION_URI + ", "
                        + FormsColumns.JR_VERSION + ", "
                        + ((oldVersion != 3) ? "" : (FormsColumns.BASE64_RSA_PUBLIC_KEY + ", "))
                        + FormsColumns.JRCACHE_FILE_PATH + ") SELECT "
                		+ FormsColumns._ID + ", "
                		+ FormsColumns.DISPLAY_NAME + ", "
                        + FormsColumns.DISPLAY_SUBTEXT + ", "
                        + FormsColumns.DESCRIPTION + ", "
                        + FormsColumns.JR_FORM_ID + ", "
                        + FormsColumns.MD5_HASH + ", "
                        + FormsColumns.DATE + ", " // milliseconds
                        + FormsColumns.FORM_MEDIA_PATH + ", "
                        + FormsColumns.FORM_FILE_PATH + ", "
                        + FormsColumns.LANGUAGE + ", "
                        + FormsColumns.SUBMISSION_URI + ", "
                        + "CASE WHEN " + MODEL_VERSION + " IS NOT NULL THEN " +
                        			"CAST(" + MODEL_VERSION + " AS TEXT) ELSE NULL END, "
                        + ((oldVersion != 3) ? "" : (FormsColumns.BASE64_RSA_PUBLIC_KEY + ", "))
                        + FormsColumns.JRCACHE_FILE_PATH + " FROM " + FORMS_TABLE_NAME);

        		// risky failures here...
        		db.execSQL("DROP TABLE IF EXISTS " + FORMS_TABLE_NAME);
        		onCreateNamed(db, FORMS_TABLE_NAME);
        		db.execSQL("INSERT INTO " + FORMS_TABLE_NAME + " ("
                		+ FormsColumns._ID + ", "
                		+ FormsColumns.DISPLAY_NAME + ", "
                        + FormsColumns.DISPLAY_SUBTEXT + ", "
                        + FormsColumns.DESCRIPTION + ", "
                        + FormsColumns.JR_FORM_ID + ", "
                        + FormsColumns.MD5_HASH + ", "
                        + FormsColumns.DATE + ", " // milliseconds
                        + FormsColumns.FORM_MEDIA_PATH + ", "
                        + FormsColumns.FORM_FILE_PATH + ", "
                        + FormsColumns.LANGUAGE + ", "
                        + FormsColumns.SUBMISSION_URI + ", "
                        + FormsColumns.JR_VERSION + ", "
                        + FormsColumns.BASE64_RSA_PUBLIC_KEY + ", "
                        + FormsColumns.JRCACHE_FILE_PATH + ") SELECT "
                		+ FormsColumns._ID + ", "
                		+ FormsColumns.DISPLAY_NAME + ", "
                        + FormsColumns.DISPLAY_SUBTEXT + ", "
                        + FormsColumns.DESCRIPTION + ", "
                        + FormsColumns.JR_FORM_ID + ", "
                        + FormsColumns.MD5_HASH + ", "
                        + FormsColumns.DATE + ", " // milliseconds
                        + FormsColumns.FORM_MEDIA_PATH + ", "
                        + FormsColumns.FORM_FILE_PATH + ", "
                        + FormsColumns.LANGUAGE + ", "
                        + FormsColumns.SUBMISSION_URI + ", "
                        + FormsColumns.JR_VERSION + ", "
                        + FormsColumns.BASE64_RSA_PUBLIC_KEY + ", "
                        + FormsColumns.JRCACHE_FILE_PATH + " FROM " + TEMP_FORMS_TABLE_NAME);
        		db.execSQL("DROP TABLE IF EXISTS " + TEMP_FORMS_TABLE_NAME);

	            Log.w(t, "Successfully upgraded database from version " + initialVersion + " to " + newVersion
	                    + ", without destroying all the old data");
	            */
        	}
        }
    }

    private ODKSQLiteOpenHelper mDbHelper;



    @Override
    public boolean onCreate() {

        // must be at the beginning of any activity that can be called from an external intent
        Survey.createODKDirs();

        mDbHelper = new DatabaseHelper(DATABASE_NAME);
        return true;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(FORMS_TABLE_NAME);

        switch (sUriMatcher.match(uri)) {
            case FORMS:
                qb.setProjectionMap(sFormsProjectionMap);
                break;

            case FORM_ID:
                qb.setProjectionMap(sFormsProjectionMap);
                qb.appendWhere(FormsColumns._ID + "=" + uri.getPathSegments().get(1));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // Get the database and run the query
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }


    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case FORMS:
                return FormsColumns.CONTENT_TYPE;

            case FORM_ID:
                return FormsColumns.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    private void patchUpValues( ContentValues values ) {
        // don't let users put in a manual FORM_FILE_PATH
        if (values.containsKey(FormsColumns.FORM_FILE_PATH)) {
            values.remove(FormsColumns.FORM_FILE_PATH);
        }

        // don't let users put in a manual FORM_PATH
        if (values.containsKey(FormsColumns.FORM_PATH)) {
            values.remove(FormsColumns.FORM_PATH);
        }

        // don't let users put in a manual DATE
        if (values.containsKey(FormsColumns.DATE)) {
            values.remove(FormsColumns.DATE);
        }

        // don't let users put in a manual md5 hash
        if (values.containsKey(FormsColumns.MD5_HASH)) {
            values.remove(FormsColumns.MD5_HASH);
        }

        // if we are not updating FORM_MEDIA_PATH, we don't need to recalc any of the above
        if (!values.containsKey(FormsColumns.FORM_MEDIA_PATH)) {
        	return;
        }

        // Normalize path...
        File mediaPath = new File(values.getAsString(FormsColumns.FORM_MEDIA_PATH));

        // require that the form directory actually exists
        if ( !mediaPath.exists() ) {
        	throw new IllegalArgumentException(FormsColumns.FORM_MEDIA_PATH + " directory does not exist: " + mediaPath.getAbsolutePath());
        }

        values.put(FormsColumns.FORM_MEDIA_PATH, mediaPath.getAbsolutePath());

        // date is the last modification date of the media folder
        Long now = mediaPath.lastModified();
        values.put(FormsColumns.DATE, now);

        // require that it contain a formDef file
        File formDefFile = new File(mediaPath, FormsProviderAPI.FILENAME_FORM_DEF_JSON);
        if ( !formDefFile.exists() ) {
        	throw new IllegalArgumentException( FormsProviderAPI.FILENAME_FORM_DEF_JSON + " does not exist in: " + mediaPath.getAbsolutePath());
        }

        // ODK2: FILENAME_XFORMS_XML may not exist if non-ODK1 fetch path...
        File xformsFile = new File(mediaPath, FormsProviderAPI.FILENAME_XFORMS_XML);
        if ( xformsFile.exists() ) {
        	values.put(FormsColumns.FORM_FILE_PATH, xformsFile.getAbsolutePath());
        }

        // compute FORM_PATH...
		String formPath = relativeFormDefPath(formDefFile);
        values.put(FormsColumns.FORM_PATH, formPath);

        String md5;
        if ( xformsFile.exists() ) {
        	md5 = FormsProviderAPI.MD5_COLON_PREFIX + ODKFileUtils.getMd5Hash(xformsFile);
        } else {
        	md5 = "-none-";
        }
        values.put(FormsColumns.MD5_HASH, md5);
    }

    @Override
    public synchronized Uri insert(Uri uri, ContentValues initialValues) {
        // Validate the requested uri
        if (sUriMatcher.match(uri) != FORMS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        // ODK2: require FORM_MEDIA_PATH (different behavior -- ODK1 and required FORM_FILE_PATH)
        if (!values.containsKey(FormsColumns.FORM_MEDIA_PATH)) {
        	throw new IllegalArgumentException(FormsColumns.FORM_MEDIA_PATH + " must be specified.");
        }

        // Normalize path...
        File mediaPath = new File(values.getAsString(FormsColumns.FORM_MEDIA_PATH));

        // require that the form directory actually exists
        if ( !mediaPath.exists() ) {
        	throw new IllegalArgumentException(FormsColumns.FORM_MEDIA_PATH + " directory does not exist: " + mediaPath.getAbsolutePath());
        }

        patchUpValues(values);

        if (values.containsKey(FormsColumns.DISPLAY_SUBTEXT) == false) {
            Date today = new Date();
            String ts = new SimpleDateFormat(getContext().getString(R.string.added_on_date_at_time)).format(today);
            values.put(FormsColumns.DISPLAY_SUBTEXT, ts);
        }

        if (values.containsKey(FormsColumns.DISPLAY_NAME) == false) {
            values.put(FormsColumns.DISPLAY_NAME, mediaPath.getName());
        }

        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // first try to see if a record with this filename already exists...
        String[] projection = {
                FormsColumns._ID, FormsColumns.FORM_MEDIA_PATH
        };
        String[] selectionArgs = { mediaPath.getAbsolutePath() };
        String selection = FormsColumns.FORM_MEDIA_PATH + "=?";
        Cursor c = null;
        try {
        	c = db.query(FORMS_TABLE_NAME, projection, selection, selectionArgs, null, null, null);
        	if ( c.getCount() > 0 ) {
        		// already exists
        		throw new SQLException("FAILED Insert into " + uri + " -- row already exists for form directory: " + mediaPath.getAbsolutePath());
        	}
        } finally {
        	if ( c != null ) {
        		c.close();
        	}
        }

        long rowId = db.insert(FORMS_TABLE_NAME, null, values);
        if (rowId > 0) {
            Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(formUri, null);
            return formUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }


    private String relativeFormDefPath(File formDefFile) {

	    // compute FORM_PATH...
		File parentDir = new File(Survey.FORMS_PATH);

		ArrayList<String> pathElements = new ArrayList<String>();

		File f = formDefFile.getParentFile();

		while ( f != null && !f.equals(parentDir) ) {
			pathElements.add(f.getName());
			f = f.getParentFile();
		}

		StringBuilder b = new StringBuilder();
		if ( f == null ) {
			// OK we have had to go all the way up to /
			b.append("..");
			b.append(File.separator); // to get from ./default to parentDir

			while ( parentDir != null ) {
				b.append("..");
				b.append(File.separator);
				parentDir = parentDir.getParentFile();
			}

		} else {
			b.append("..");
			b.append(File.separator);
		}

		for ( int i = pathElements.size()-1 ; i >= 0 ; --i ) {
			String element = pathElements.get(i);
			b.append(element);
			b.append(File.separator);
		}
		return b.toString();
    }

    private void moveDirectory(File mediaDirectory) throws IOException {
    	if ( mediaDirectory.getParentFile().getAbsolutePath().equals(Survey.FORMS_PATH) &&
    			mediaDirectory.exists() ) {
    		// it is a directory under our control -- move it to the stale forms path...
    		// otherwise, it is not where we will look for it, so we can ignore it
    		// (once the record is gone from our FormsProvider, we will not accidentally
    		// DiskSync and locate it).
    		String rootName = mediaDirectory.getName();
    		int rev = 2;
	        String staleMediaPathName = Survey.STALE_FORMS_PATH + File.separator + rootName;
	        File staleMediaPath = new File(staleMediaPathName);

	        while (staleMediaPath.exists()) {
				try {
					if ( staleMediaPath.exists() ) {
						FileUtils.deleteDirectory(staleMediaPath);
					}
					Log.i(t, "Successful delete of stale directory: " + staleMediaPathName);
				} catch ( IOException ex ) {
					ex.printStackTrace();
					Log.i(t, "Unable to delete stale directory: " + staleMediaPathName);
				}
	        	staleMediaPathName = Survey.FORMS_PATH + File.separator + rootName + "_" + rev;
	            staleMediaPath = new File(staleMediaPathName);
	            rev++;
	        }
	        FileUtils.moveDirectory(mediaDirectory, staleMediaPath);
    	}
    }

    /**
     * This method removes the entry from the content provider, and also removes any associated
     * files. files: form.xml, [formmd5].formdef, formname {directory}
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        Cursor del = null;
        HashSet<File> mediaDirs = new HashSet<File>();
        try {
        	del = this.query(uri, null, where, whereArgs, null);
            del.moveToPosition(-1);
            while (del.moveToNext()) {
            	File mediaDir = new File(del.getString(del.getColumnIndex(FormsColumns.FORM_MEDIA_PATH)));
            	mediaDirs.add(mediaDir);
            }
		} finally {
        	if ( del != null ) {
        		del.close();
        	}
        }
        int count;
        switch (sUriMatcher.match(uri)) {
            case FORMS:
                count = db.delete(FORMS_TABLE_NAME, where, whereArgs);
                break;

            case FORM_ID:
                String formId = uri.getPathSegments().get(1);
                count =
                    db.delete(FORMS_TABLE_NAME,
                        FormsColumns._ID + "=" + formId
                                + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
                        whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // and attempt to move these directories to the stale forms location
        // so that they do not immediately get rescanned...

        for ( File mediaDir : mediaDirs ) {
	        try {
	        	moveDirectory(mediaDir);
		    } catch (IOException e) {
				e.printStackTrace();
		        Log.e(t, "Unable to move directory " + e.toString());
		    }
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static class FormIdVersion {
		final String formId;
    	final String formVersion;

    	FormIdVersion(String formId, String formVersion) {
    		this.formId = formId;
    		this.formVersion = formVersion;
    	}

    	@Override
		public boolean equals(Object o) {
    		if (!(o instanceof FormIdVersion) ) return false;
    		FormIdVersion that = (FormIdVersion) o;

    		// identical if id and version matches...
			return formId.equals(that.formId) &&
					((formVersion == null) ? (that.formVersion == null)
							: (that.formVersion != null && formVersion.equals(that.formVersion)));
		}
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        /*
         * First, find out what records match this query, and if they refer to two
         * or more (formId,formVersion) tuples, then be sure to remove all FORM_MEDIA_PATH
         * references. Otherwise, if they are all for the same tuple, and the update
         * specifies a FORM_MEDIA_PATH, move all the non-matching directories elsewhere.
         */
        HashSet<File> mediaDirs = new HashSet<File>();
        boolean multiset = false;
        Cursor c = null;
        try {
        	c = this.query(uri, null, where, whereArgs, null);

            if (c.getCount() >= 1) {
            	FormIdVersion ref = null;
                c.moveToPosition(-1);
                while (c.moveToNext()) {
                	String formId = c.getString(c.getColumnIndex(FormsColumns.FORM_ID));
                	String formVersion = c.getString(c.getColumnIndex(FormsColumns.FORM_VERSION));
                	FormIdVersion cur = new FormIdVersion(formId, formVersion);

                	String mediaPath = c.getString(c.getColumnIndex(FormsColumns.FORM_MEDIA_PATH));
                	if ( mediaPath != null ) {
                		mediaDirs.add(new File(mediaPath));
                	}

                	if ( ref != null && !ref.equals(cur) ) {
                		multiset = true;
                		break;
                	} else {
                		ref = cur;
                	}
                }
            }
        } finally {
        	if ( c != null ) {
        		c.close();
        	}
        }

        if ( multiset ) {
            // don't let users manually update media path
        	// we are referring to two or more (formId,formVersion) tuples.
            if (values.containsKey(FormsColumns.FORM_MEDIA_PATH)) {
                values.remove(FormsColumns.FORM_MEDIA_PATH);
            }
        } else if (values.containsKey(FormsColumns.FORM_MEDIA_PATH)) {
        	// we are not a multiset and we are setting the media path
        	// try to move all the existing non-matching media paths to somewhere else...
        	File mediaPath = new File(values.getAsString(FormsColumns.FORM_MEDIA_PATH));
        	for ( File altPath : mediaDirs ) {
        		if ( !altPath.equals(mediaPath) ) {
        			try {
						moveDirectory(altPath);
					} catch (IOException e) {
						e.printStackTrace();
						Log.e(t, "Attempt to move " + altPath.getAbsolutePath() + " failed: " + e.toString());
					}
        		}
        	}
        	// OK. we have moved the existing form definitions elsewhere. We can proceed with update...
        }

        // ensure that all values are correct and ignore some user-supplied values...
        patchUpValues(values);

        // Make sure that the necessary fields are all set
        if (values.containsKey(FormsColumns.DATE) == true) {
            Date today = new Date();
            String ts = new SimpleDateFormat(getContext().getString(R.string.added_on_date_at_time)).format(today);
            values.put(FormsColumns.DISPLAY_SUBTEXT, ts);
        }

        // OK Finally, now do the update...

        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case FORMS:
                count = db.update(FORMS_TABLE_NAME, values, where, whereArgs);
                break;

            case FORM_ID:
                String formId = uri.getPathSegments().get(1);
                // Whenever file paths are updated, delete the old files.

                count = db.update(FORMS_TABLE_NAME, values, FormsColumns._ID + "=" + formId
                                + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
                            whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(FormsProviderAPI.AUTHORITY, "forms", FORMS);
        sUriMatcher.addURI(FormsProviderAPI.AUTHORITY, "forms/#", FORM_ID);

        sFormsProjectionMap = new HashMap<String, String>();
        sFormsProjectionMap.put(FormsColumns._ID, FormsColumns._ID);
        sFormsProjectionMap.put(FormsColumns.DISPLAY_NAME, FormsColumns.DISPLAY_NAME);
        sFormsProjectionMap.put(FormsColumns.DISPLAY_SUBTEXT, FormsColumns.DISPLAY_SUBTEXT);
        sFormsProjectionMap.put(FormsColumns.DESCRIPTION, FormsColumns.DESCRIPTION);
        sFormsProjectionMap.put(FormsColumns.TABLE_ID, FormsColumns.TABLE_ID);
        sFormsProjectionMap.put(FormsColumns.FORM_ID, FormsColumns.FORM_ID);
        sFormsProjectionMap.put(FormsColumns.FORM_VERSION, FormsColumns.FORM_VERSION);
        sFormsProjectionMap.put(FormsColumns.XML_SUBMISSION_URL, FormsColumns.XML_SUBMISSION_URL);
        sFormsProjectionMap.put(FormsColumns.XML_BASE64_RSA_PUBLIC_KEY, FormsColumns.XML_BASE64_RSA_PUBLIC_KEY);
        sFormsProjectionMap.put(FormsColumns.XML_ROOT_ELEMENT_NAME, FormsColumns.XML_ROOT_ELEMENT_NAME);
        sFormsProjectionMap.put(FormsColumns.MD5_HASH, FormsColumns.MD5_HASH);
        sFormsProjectionMap.put(FormsColumns.DATE, FormsColumns.DATE);
        sFormsProjectionMap.put(FormsColumns.FORM_MEDIA_PATH, FormsColumns.FORM_MEDIA_PATH);
        sFormsProjectionMap.put(FormsColumns.FORM_PATH, FormsColumns.FORM_PATH);
        sFormsProjectionMap.put(FormsColumns.FORM_FILE_PATH, FormsColumns.FORM_FILE_PATH);
        sFormsProjectionMap.put(FormsColumns.DEFAULT_FORM_LOCALE, FormsColumns.DEFAULT_FORM_LOCALE);
    }

}
