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

package org.opendatakit.survey.android.fragments;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.lang3.StringEscapeUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.activities.MainMenuActivity;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.listeners.InstanceUploaderListener;
import org.opendatakit.survey.android.logic.FormIdStruct;
import org.opendatakit.survey.android.logic.InstanceUploadOutcome;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.provider.InstanceProviderAPI;
import org.opendatakit.survey.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

/**
 * List view of the instances of the current form that are finalized and
 * can be uploaded.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class InstanceUploaderListFragment extends ListFragment implements
		OnLongClickListener, LoaderManager.LoaderCallbacks<Cursor>,
		InstanceUploaderListener {
	private static final String t = "InstanceUploaderListFragment";

	private static int INSTANCE_UPLOADER_LIST_LOADER = 0x03;

	public static int ID = R.layout.instance_uploader_list;

	private static final String BUNDLE_SELECTED_ITEMS_KEY = "selected_items";
	private static final String BUNDLE_TOGGLED_KEY = "toggled";
	private static final String DIALOG_TITLE = "dialogtitle";
	private static final String DIALOG_MSG = "dialogmsg";
	private static final String DIALOG_SHOWING = "dialogshowing";

	private static final int INSTANCE_UPLOADER = 0;

	private String mAlertMsg;
	private boolean mAlertShowing = false;
	private String mAlertTitle;

	private AlertDialog mAlertDialog;
	private URI mUrl;

	private Button mUploadButton;
	private Button mToggleButton;

	private boolean mShowUnsent = true;
	private SimpleCursorAdapter mInstances;
	private ArrayList<String> mSelected = new ArrayList<String>();
	private boolean mToggled = false;

	private View view;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}


	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		String[] data = new String[] { InstanceColumns.DISPLAY_NAME,
				InstanceColumns.DISPLAY_SUBTEXT, InstanceColumns._ID };
		int[] view = new int[] { R.id.text1, R.id.text2 };

		// render total instance view
		mInstances =  new SimpleCursorAdapter(getActivity(), R.layout.two_item_multiple_choice, null, data, view, 0);
		setListAdapter(mInstances);
		getLoaderManager().initLoader(INSTANCE_UPLOADER_LIST_LOADER, null, this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		view = inflater.inflate(R.layout.instance_uploader_list, container, false);

		// set up long click listener

		mUploadButton = (Button) view.findViewById(R.id.upload_button);
		mUploadButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo ni = connectivityManager.getActiveNetworkInfo();

				if (ni == null || !ni.isConnected()) {
					Toast.makeText(getActivity(),
							R.string.no_connection, Toast.LENGTH_SHORT).show();
				} else {
					// items selected
					uploadSelectedFiles();
					mToggled = false;
					mSelected.clear();
					clearChoices();
				}
			}
		});

		mToggleButton = (Button) view.findViewById(R.id.toggle_button);
		mToggleButton.setLongClickable(true);
		mToggleButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// toggle selections of items to all or none
				ListView ls = getListView();
				mToggled = !mToggled;
				// remove all items from selected list
				mSelected.clear();
				for (int pos = 0; pos < ls.getCount(); pos++) {
					ls.setItemChecked(pos, mToggled);
					// add all items if mToggled sets to select all
					if (mToggled) {
						Cursor c = (Cursor) ls.getItemAtPosition(pos);
						String uuid = c.getString(c.getColumnIndex(InstanceColumns._ID));
						mSelected.add(uuid);
					}
				}
				mUploadButton.setEnabled(!(mSelected.size() == 0));

			}
		});
		mToggleButton.setOnLongClickListener(this);

		if ( savedInstanceState != null ) {
			String[] selectedArray = savedInstanceState
					.getStringArray(BUNDLE_SELECTED_ITEMS_KEY);
			for (int i = 0; i < selectedArray.length; i++)
				mSelected.add(selectedArray[i]);
			mToggled = savedInstanceState.getBoolean(BUNDLE_TOGGLED_KEY);

			// indicating whether or not select-all is on or off.
			if (savedInstanceState.containsKey(BUNDLE_TOGGLED_KEY)) {
				mToggled = savedInstanceState.getBoolean(BUNDLE_TOGGLED_KEY);
			}

			// to restore alert dialog.
			if (savedInstanceState.containsKey(DIALOG_TITLE)) {
				mAlertTitle = savedInstanceState.getString(DIALOG_TITLE);
			}
			if (savedInstanceState.containsKey(DIALOG_MSG)) {
				mAlertMsg = savedInstanceState.getString(DIALOG_MSG);
			}
			if (savedInstanceState.containsKey(DIALOG_SHOWING)) {
				mAlertShowing = savedInstanceState.getBoolean(DIALOG_SHOWING);
			}
		}
		mUploadButton.setEnabled(!(mSelected.size() == 0));

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		String[] selectedArray = new String[mSelected.size()];
		for (int i = 0; i < mSelected.size(); i++)
			selectedArray[i] = mSelected.get(i);
		outState.putStringArray(BUNDLE_SELECTED_ITEMS_KEY, selectedArray);
		outState.putBoolean(BUNDLE_TOGGLED_KEY, mToggled);
		outState.putString(DIALOG_TITLE, mAlertTitle);
		outState.putString(DIALOG_MSG, mAlertMsg);
		outState.putBoolean(DIALOG_SHOWING, mAlertShowing);
	}

	private void clearChoices() {
		getListView().clearChoices();
		mUploadButton.setEnabled(false);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		// get row id from db
		Cursor c = (Cursor) getListAdapter().getItem(position);
		String k = c.getString(c.getColumnIndex(InstanceColumns._ID));

		// add/remove from selected list
		if (mSelected.contains(k))
			mSelected.remove(k);
		else
			mSelected.add(k);

		mUploadButton.setEnabled(!(mSelected.size() == 0));

	}

	private void uploadSelectedFiles() {
		if ( mSelected.size() > 0 ) {
			// show dialog box
			mAlertMsg = getString(R.string.please_wait);
			showProgressDialog();

			BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag("background");
			f.uploadInstances(this, mSelected);
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		getListView().setItemsCanFocus(false);

		// if current activity is being reinitialized due to changing
		// orientation restore all checkmarks for ones selected
		ListView ls = getListView();
		for (String id : mSelected) {
			for (int pos = 0; pos < ls.getCount(); pos++) {
				Cursor c = (Cursor) ls.getItemAtPosition(pos);
				String uuid = c.getString(c.getColumnIndex(InstanceColumns._ID));
				if (id.equals(uuid)) {
					ls.setItemChecked(pos, true);
					break;
				}
			}

		}

		FragmentManager mgr = getFragmentManager();
		BackgroundTaskFragment f = (BackgroundTaskFragment) mgr
				.findFragmentByTag("background");

		f.establishInstanceUploaderListener(this);

		if (mAlertShowing) {
			createAlertDialog(mAlertTitle, mAlertMsg);
		}
		super.onResume();
	}

	@Override
	public void onPause() {
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}
		super.onPause();
	}

	private void showAuthDialog() {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(getActivity().getBaseContext());
		String server = settings.getString(
				PreferencesActivity.KEY_SERVER_URL,
				getString(R.string.default_server_url));

		AuthDialogFragment f = AuthDialogFragment.newInstance(getId(),
				getString(R.string.server_requires_auth),
				getString(R.string.server_auth_credentials, mUrl.toString()), mUrl.toString());

		f.show(getFragmentManager(), "authDialog");
	}

	private void showProgressDialog() {
		ProgressDialogFragment f = ProgressDialogFragment.newInstance(getId(),
				getString(R.string.uploading_data),
				mAlertMsg);

		f.show(getFragmentManager(), "progressDialog");
	}

	private void updateProgressDialogMessage(String message) {
		Fragment dialog = getFragmentManager().findFragmentByTag("progressDialog");

		if ( dialog != null ) {
			((ProgressDialogFragment) dialog).setMessage(message);
		}
	}

	private void dismissProgressDialog() {
		Fragment dialog = getFragmentManager().findFragmentByTag("progressDialog");
		if ( dialog != null ) {
			((ProgressDialogFragment) dialog).dismiss();
		}
	}


	@Override
	public void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (resultCode == Activity.RESULT_CANCELED) {
			return;
		}
		switch (requestCode) {
		// returns with a form path, start entry
		case INSTANCE_UPLOADER:
			if (intent.getBooleanExtra(MainMenuActivity.KEY_SUCCESS, false)) {
				mSelected.clear();
				getListView().clearChoices();
				if (mInstances.isEmpty()) {
					getActivity().finish();
				}
			}
			break;
		default:
			break;
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	/**
	 * Creates an alert dialog with the given tite and message. If shouldExit is
	 * set to true, the activity will exit when the user clicks "ok".
	 *
	 * @param title
	 * @param shouldExit
	 */
	private void createAlertDialog(String title, String message) {
		mAlertDialog = new AlertDialog.Builder(getActivity()).create();
		mAlertDialog.setTitle(title);
		mAlertDialog.setMessage(message);
		DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int i) {
				switch (i) {
				case DialogInterface.BUTTON_POSITIVE: // ok
					// just close the dialog
					mAlertShowing = false;
					break;
				}
			}
		};
		mAlertDialog.setCancelable(false);
		mAlertDialog.setButton(Dialog.BUTTON_POSITIVE, getString(R.string.ok), quitListener);
		mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
		mAlertMsg = message;
		mAlertTitle = title;
		mAlertShowing = true;
		mAlertDialog.show();
	}

    @Override
    public void progressUpdate(int progress, int total) {
        mAlertMsg = getString(R.string.sending_items, progress, total);
        updateProgressDialogMessage(mAlertMsg);
    }

	@Override
	public void uploadingComplete(InstanceUploadOutcome outcome) {
        try {
        	dismissProgressDialog();
        } catch (Exception e) {
        	e.printStackTrace();
        	Log.i(t,"Attempting to close a dialog that was not previously opened");
        }

		if ( outcome.mAuthRequestingServer == null ) {
	        StringBuilder message = new StringBuilder();

	        Set<String> keys = outcome.mResults.keySet();
	        for ( String id : keys ) {

	        	Cursor results = null;
	        	try {
	        		Uri uri = Uri.withAppendedPath(InstanceColumns.CONTENT_URI,
							MainMenuActivity.currentForm.tableId + "/" + StringEscapeUtils.escapeHtml4(id));
	                results = Survey.getInstance().getContentResolver().query( uri, null, null, null, null);
	                if (results.getCount() == 1 && results.moveToFirst() ) {
	                    String name =
	                        results.getString(results.getColumnIndex(InstanceColumns.DISPLAY_NAME));
	                    message.append(name + " - " + outcome.mResults.get(id) + "\n\n");
	                }
	        	} finally {
	        		if ( results != null ) {
	        			results.close();
	        		}
	        	}
	        }

	        if ( message.length() == 0 ) {
	            message.append(getString(R.string.no_forms_uploaded));
	        }

	        createAlertDialog(getString(R.string.uploading_data), message.toString().trim());
	    } else {
	        // add our list of completed uploads to "completed"
	        // and remove them from our toSend list.
	        if (outcome.mResults != null) {
	            Set<String> uploadedInstances = outcome.mResults.keySet();
	            Iterator<String> itr = uploadedInstances.iterator();

	            while (itr.hasNext()) {
	            	String removeMe = itr.next();
	                boolean removed = mSelected.remove(removeMe);
	                if (removed) {
	                    Log.i(t, removeMe
	                            + " was already sent, removing from queue before restarting task");
	                }
	            }
	        }

	        // Bundle b = new Bundle();
	        // b.putString(AUTH_URI, url.toString());
	        // showDialog(AUTH_DIALOG, b);
	        mUrl = outcome.mAuthRequestingServer;
	        showAuthDialog();
	    }
	}



	private void showUnsent() {
		mShowUnsent = true;
		getLoaderManager().restartLoader(INSTANCE_UPLOADER_LIST_LOADER, null, this);
	}

	private void showAll() {
		mShowUnsent = false;
		getLoaderManager().restartLoader(INSTANCE_UPLOADER_LIST_LOADER, null, this);
	}

	@Override
	public boolean onLongClick(View v) {
		/**
		 * Create a dialog with options to save and exit, save, or quit without
		 * saving
		 */
		String[] items = { getString(R.string.show_unsent_forms),
				getString(R.string.show_sent_and_unsent_forms) };

		AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(getString(R.string.change_view))
				.setNeutralButton(getString(R.string.cancel),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						})
				.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch (which) {

						case 0: // show unsent
							showUnsent();
							break;

						case 1: // show all
							showAll();
							break;

						case 2:// do nothing
							break;
						}
					}
				}).create();
		alertDialog.show();
		return true;
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		// This is called when a new Loader needs to be created. This
		// sample only has one Loader, so we don't care about the ID.
		// First, pick the base URI to use depending on whether we are
		// currently filtering.
		MainMenuActivity a = (MainMenuActivity) getActivity();
		FormIdStruct fs = MainMenuActivity.currentForm;
		Uri baseUri =  Uri.withAppendedPath(InstanceColumns.CONTENT_URI, fs.tableId);

		String selection;
		String selectionArgs[];
		String sortOrder;

		if ( mShowUnsent ) {
			// show all unsent or failed-sending records
			selection = InstanceColumns.XML_PUBLISH_STATUS + " IS NULL or " +
						InstanceColumns.XML_PUBLISH_STATUS + "=?";
			selectionArgs = new String[]{ InstanceProviderAPI.STATUS_SUBMISSION_FAILED };
			sortOrder = InstanceColumns.DISPLAY_NAME + " ASC";
		} else {
			// show all completed instances (ones ready to be submitted)
			selection = null;
			selectionArgs = null;
			sortOrder = InstanceColumns.DISPLAY_NAME + " ASC";
		}

		// Now create and return a CursorLoader that will take care of
		// creating a Cursor for the data being displayed.
		return new CursorLoader(getActivity(), baseUri, null, selection, selectionArgs,
				sortOrder);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		// Swap the new cursor in. (The framework will take care of closing the
		// old cursor once we return.)
		mInstances.swapCursor(cursor);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		// This is called when the last Cursor provided to onLoadFinished()
		// above is about to be closed. We need to make sure we are no
		// longer using it.
		mInstances.swapCursor(null);
	}

}
