/*
 * Copyright (C) 2009 University of Washington
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

package org.opendatakit.survey.android.tasks;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.apache.commons.io.FileUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.listeners.DiskSyncListener;
import org.opendatakit.survey.android.logic.FormInfo;
import org.opendatakit.survey.android.provider.FormsProviderAPI.FormsColumns;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Background task for adding to the forms content provider, any forms that have been added to the
 * sdcard manually. Returns immediately if it detects an error.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class DiskSyncTask extends AsyncTask<Void, String, String> {
    private final static String t = "DiskSyncTask";
    private static int counter = 0;

    int instance;

    DiskSyncListener mListener;

    String statusMessage;

	public static final String updateFormInfo(File mediaPath) {
    	StringBuilder errors = new StringBuilder();
    	int value = ++counter; // roughly track the scan # we're on... logging use only
    	Log.i(t, "["+value+"] doInBackground begins!");
        updateFormInfo(errors, mediaPath, value);
        return errors.toString();
    }

    private static final void updateFormInfo(StringBuilder errors, File mediaPath, int instance) {
    	FormInfo fi = null;

		Uri uri = null;
    	Cursor c = null;
    	try {
    		File formDef = new File(mediaPath, "formDef.json");

        	String selection = FormsColumns.FORM_MEDIA_PATH + "=?";
    		String[] selectionArgs = { mediaPath.getAbsolutePath() };
    		c = Survey.getInstance().getContentResolver().query(FormsColumns.CONTENT_URI, null, selection, selectionArgs, null);

    		if ( c.getCount() > 1 ) {
    			c.close();
    			// we have multiple records for this one directory.
    			// Rename the directory. Delete the records, and move the directory back.
    			int i = 0;
    			File formPath = new File(mediaPath.getParentFile(), mediaPath.getName() + ".xml");
    			File tempFormPath = new File(mediaPath.getParentFile(), mediaPath.getName() + "_" + Integer.toString(i) + ".xml");
    			File tempMediaPath = new File(mediaPath.getParentFile(), mediaPath.getName() + "_" + Integer.toString(i));
    			while ( tempFormPath.exists() && tempMediaPath.exists()) {
    				++i;
        			tempFormPath = new File(mediaPath.getParentFile(), mediaPath.getName() + "_" + Integer.toString(i) + ".xml");
        			tempMediaPath = new File(mediaPath.getParentFile(), mediaPath.getName() + "_" + Integer.toString(i));
    			}
    			if ( formPath.exists() ) {
    				FileUtils.moveFile(formPath, tempFormPath);
    			}
    			FileUtils.moveDirectory(mediaPath, tempMediaPath);
    			Survey.getInstance().getContentResolver().delete(FormsColumns.CONTENT_URI, selection, selectionArgs);
    			FileUtils.moveDirectory(tempMediaPath, mediaPath);
    			if ( tempFormPath.exists() ) {
    				FileUtils.moveFile(tempFormPath, formPath);
    			}

    			// we don't know which of the above records was correct, so reparse this to get ground truth...
        		fi = new FormInfo(formDef);
    		} else if ( c.getCount() == 1) {
    			c.moveToFirst();
    			String id = Integer.toString(c.getInt(c.getColumnIndex(FormsColumns._ID)));
                uri = Uri.withAppendedPath(FormsColumns.CONTENT_URI, id);
                Long lastModificationDate = c.getLong(c.getColumnIndex(FormsColumns.DATE));
                if ( lastModificationDate.compareTo(formDef.lastModified()) == 0 ) {
    	            Log.i(t, "["+instance+"] formDef unchanged: " + mediaPath.getName());
    	            fi = new FormInfo(c, false);
                } else {
                	fi = new FormInfo(formDef);
                }
    		} else if ( c.getCount() == 0) {
        		// it should be new, try to parse it...
        		fi = new FormInfo(formDef);
    		}

        } catch ( SQLiteException e ) {
        	e.printStackTrace();
        	errors.append(e.toString()).append("\r\n");
        	return;
        } catch ( IOException e ) {
        	e.printStackTrace();
        	errors.append(e.toString()).append("\r\n");
        	return;
        } catch ( IllegalArgumentException e ) {
        	e.printStackTrace();
        	errors.append(e.toString()).append("\r\n");
        	File formPath = new File(mediaPath.getParentFile(), mediaPath.getName() + ".xml");
			try {
				if ( formPath.exists() ) {
					formPath.delete();
				}
				FileUtils.deleteDirectory(mediaPath);
	        	Log.i(t, "["+instance+"] Removing -- unable to parse formDef file: " + e.toString());
			} catch (IOException e1) {
				e1.printStackTrace();
	        	Log.i(t, "["+instance+"] Removing -- unable to delete form directory: " +
	        			mediaPath.getName() + " error: " + e.toString());
			}
			return;
		} finally {
        	if ( c != null && !c.isClosed() ) {
        		c.close();
        	}
        }

    	ContentValues v = new ContentValues();
        String[] values = fi.asRowValues(FormsColumns.formsDataColumnNames);
        for ( int i = 0 ; i < values.length ; ++i ) {
        	v.put(FormsColumns.formsDataColumnNames[i], values[i]);
        }

        // OK we have the set of values to update or insert. Now check if there are
        // any records that match this entry but that do not match the media path.
        boolean deleteNewDirectories = false;
        ArrayList<Uri> badEntries = new ArrayList<Uri>();
        long referenceModificationDate = -1;
        Uri referenceUri = null;
        try {
        	String selection = FormsColumns.FORM_MEDIA_PATH + "!=? AND " + FormsColumns.FORM_ID + "=? AND " +
        						FormsColumns.FORM_VERSION + ((fi.formVersion == null) ? " IS NULL" : "=?");
    		String[] selectionArgs;
    		if ( fi.formVersion == null) {
    			String[] tempArgs = { mediaPath.getAbsolutePath(), fi.formId };
    			selectionArgs = tempArgs;
    		} else {
    			String[] tempArgs = { mediaPath.getAbsolutePath(), fi.formId, fi.formVersion };
    			selectionArgs = tempArgs;
    		}
    		c = Survey.getInstance().getContentResolver().query(FormsColumns.CONTENT_URI, null, selection, selectionArgs, null);

    		if ( c.moveToFirst() ) {
    			do {
        			String id = Integer.toString(c.getInt(c.getColumnIndex(FormsColumns._ID)));
                    Uri otherUri = Uri.withAppendedPath(FormsColumns.CONTENT_URI, id);
                    if ( uri == null || otherUri.compareTo(uri) != 0 ) {
                        Long lastModificationDate = c.getLong(c.getColumnIndex(FormsColumns.DATE));
                        if ( referenceModificationDate == -1 && lastModificationDate.compareTo(fi.lastModificationDate) > 0 ) {
                        	// the datastore has an entry with a newer modification time -- delete the directory we are processing
                        	// and track this entry as the current-best entry...
                        	deleteNewDirectories = true;
                        	if ( uri != null ) {
                        		badEntries.add(uri);
                        		uri = null;
                        	}
                        	referenceModificationDate = lastModificationDate;
                        	referenceUri = otherUri;
                        } else if ( referenceModificationDate != -1 && lastModificationDate.compareTo(referenceModificationDate) > 0 ) {
                        	// there is a 2nd or follow-on entry with a newer modification time
                        	// mark the earlier current-best for removal and update the current-best entries
                        	badEntries.add(referenceUri);
                        	referenceModificationDate = lastModificationDate;
                        	referenceUri = otherUri;
                        } else {
                        	// otherwise, this entry is not as current as either
                        	// the directory we are processing or the current-best entry we have.
                        	// mark it for removal
                        	badEntries.add(otherUri);
                        }
                    }
    			} while ( c.moveToNext() );
    		}
        } catch ( SQLiteException e ) {
        	e.printStackTrace();
        	errors.append(e.toString()).append("\r\n");
        	return;
        } finally {
        	if ( c != null && !c.isClosed() ) {
        		c.close();
        	}
        }

        // delete the other entries (and directories)
        for ( Uri badUri : badEntries ) {
			Survey.getInstance().getContentResolver().delete(badUri, null, null);
        }

        if ( deleteNewDirectories ) {
			try {
				File formPath = new File(mediaPath.getParentFile(), mediaPath.getName() + ".xml");
				if ( formPath.exists() ) {
					formPath.delete();
				}
				FileUtils.deleteDirectory(mediaPath);
	        	Log.i(t, "["+instance+"] Removing -- another directory is newer");
			} catch (IOException e1) {
				e1.printStackTrace();
	        	Log.i(t, "["+instance+"] Removing -- unable to delete form directory: " +
	        			mediaPath.getName() + " error: " + e1.toString());
			}
        } else if ( uri != null ) {
	        int count = Survey.getInstance().getContentResolver()
	                        .update(uri, v, null, null);
	            Log.i(t, "["+instance+"] " + count + " records successfully updated");
        } else {
    		Survey.getInstance().getContentResolver()
							.insert(FormsColumns.CONTENT_URI, v);
            Log.i(t, "["+instance+"] one record successfully inserted");
        }
    }

    public final void updateFormInfo(StringBuilder errors, int instance) {

		File formsDir = new File(Survey.FORMS_PATH);

		File[] candidates = formsDir.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				if ( !pathname.isDirectory() ) return false;
				File f = new File(pathname, "formDef.json");
				return (f.exists() && f.isFile());
			}});

		ArrayList<File> formDirs = new ArrayList<File>();
		for ( File mediaDir : candidates ) {
			formDirs.add(mediaDir);
		}

		// sort the directories so we process the ones that are newest first.
		// this will ensure that if there are two directories with the same
		// form definitions, we keep the one with the most recent timestamp.
		Collections.sort(formDirs, new Comparator<File>(){

			@Override
			public int compare(File lhs, File rhs) {
				return (lhs.lastModified() > rhs.lastModified()) ? -1 :
						((lhs.lastModified() == rhs.lastModified()) ? 0 : 1);
			}});

		for ( File f : formDirs ) {
			Log.i(t, "Directory: " + f.getName() + " lastModified: " + f.lastModified());
			updateFormInfo(errors, f, instance);
		}
    }

    @Override
    protected String doInBackground(Void... params) {

    	instance = ++counter; // roughly track the scan # we're on... logging use only
    	Log.i(t, "["+instance+"] doInBackground begins!");

    	try {
	    	// Process everything then report what didn't work.
    		StringBuilder errors = new StringBuilder();

	    	updateFormInfo(errors, instance);

	        if ( errors.length() != 0 ) {
	        	statusMessage = errors.toString();
	        } else {
	        	statusMessage = Survey.getInstance().getString(R.string.finished_disk_scan);
	        }
	        return statusMessage;
    	} finally {
    		Log.i(t, "["+instance+"] doInBackground ends!");
    	}
    }

    public String getStatusMessage() {
    	return statusMessage;
    }

    public void setDiskSyncListener(DiskSyncListener l) {
        mListener = l;
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if (mListener != null) {
            mListener.SyncComplete(result);
        }
    }

}
