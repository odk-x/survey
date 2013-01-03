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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.fragments.ProgressDialogFragment.CancelProgressDialog;
import org.opendatakit.survey.android.listeners.FormDownloaderListener;
import org.opendatakit.survey.android.listeners.FormListDownloaderListener;
import org.opendatakit.survey.android.logic.FormDetails;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.tasks.DownloadFormListTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/**
 * Interface to the legacy OpenRosa compliant Form Discovery API
 * (e.g., ODK Aggregate forms listing).
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class FormDownloadListFragment extends ListFragment implements
		FormListDownloaderListener, FormDownloaderListener, CancelProgressDialog {

	private static final String t = "FormDownloadListFragment";

	public static int ID = R.layout.remote_file_manage_list;

	private static final String BUNDLE_TOGGLED_KEY = "toggled";
	private static final String BUNDLE_SELECTED_COUNT = "selectedcount";
	private static final String BUNDLE_FORM_MAP = "formmap";
	private static final String DIALOG_TITLE = "dialogtitle";
	private static final String DIALOG_MSG = "dialogmsg";
	private static final String DIALOG_SHOWING = "dialogshowing";
	private static final String FORMLIST = "formlist";

	public static final String LIST_URL = "listurl";

	private static final String FORMNAME = "formname";
	private static final String FORMDETAIL_KEY = "formdetailkey";
	private static final String FORMID_DISPLAY = "formiddisplay";

	private String mAlertMsg;
	private boolean mAlertShowing = false;
	private String mAlertTitle;

	private AlertDialog mAlertDialog;
	private Button mDownloadButton;

	private Button mToggleButton;
	private Button mRefreshButton;

	private HashMap<String, FormDetails> mFormNamesAndURLs;
	private SimpleAdapter mFormListAdapter;
	private ArrayList<HashMap<String, String>> mFormList = new ArrayList<HashMap<String, String>>();

	private boolean mToggled = false;
	private int mSelectedCount = 0;

	private View view;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}


	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);


		String[] data = new String[] { FORMNAME, FORMID_DISPLAY, FORMDETAIL_KEY };
		int[] view = new int[] { R.id.text1, R.id.text2 };

		mFormListAdapter = new SimpleAdapter(getActivity(), mFormList, R.layout.two_item_multiple_choice, data, view);
//		// need white background before load
//		getListView().setBackgroundColor(Color.WHITE);
//		getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
//		getListView().setItemsCanFocus(false);

		setListAdapter(mFormListAdapter);

		if ( savedInstanceState == null ) {
			// first time, so get the formlist
			downloadFormList();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		view = inflater.inflate(R.layout.remote_file_manage_list, container, false);
		mAlertMsg = getString(R.string.please_wait);

		mDownloadButton = (Button) view.findViewById(R.id.add_button);
		mDownloadButton.setEnabled(false);
		mDownloadButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				downloadSelectedFiles();
				mToggled = false;
				clearChoices();
			}
		});

		mToggleButton = (Button) view.findViewById(R.id.toggle_button);
		mToggleButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// toggle selections of items to all or none
				ListView ls = getListView();
				mToggled = !mToggled;

				for (int pos = 0; pos < ls.getCount(); pos++) {
					ls.setItemChecked(pos, mToggled);
				}

				mDownloadButton.setEnabled(!(selectedItemCount() == 0));
			}
		});

		mRefreshButton = (Button) view.findViewById(R.id.refresh_button);
		mRefreshButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mToggled = false;
				downloadFormList();
				getListView().clearChoices();
				clearChoices();
			}
		});

		if (savedInstanceState != null) {
			// If the screen has rotated, the hashmap with the form ids and urls
			// is passed here.
			if (savedInstanceState.containsKey(BUNDLE_FORM_MAP)) {
				mFormNamesAndURLs = (HashMap<String, FormDetails>) savedInstanceState
						.getSerializable(BUNDLE_FORM_MAP);
			}

			// indicating whether or not select-all is on or off.
			if (savedInstanceState.containsKey(BUNDLE_TOGGLED_KEY)) {
				mToggled = savedInstanceState.getBoolean(BUNDLE_TOGGLED_KEY);
			}

			// how many items we've selected
			// Android should keep track of this, but broken on rotate...
			if (savedInstanceState.containsKey(BUNDLE_SELECTED_COUNT)) {
				mSelectedCount = savedInstanceState
						.getInt(BUNDLE_SELECTED_COUNT);
				mDownloadButton.setEnabled(!(mSelectedCount == 0));
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

			if (savedInstanceState.containsKey(FORMLIST)) {
			mFormList = (ArrayList<HashMap<String, String>>) savedInstanceState
					.getSerializable(FORMLIST);
			}
		} else {
			mFormList.clear();
		}

		return view;
	}

	private void clearChoices() {
		getListView().clearChoices();
		mDownloadButton.setEnabled(false);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		mDownloadButton.setEnabled(!(selectedItemCount() == 0));
	}

	/**
	 * Starts the download task and shows the progress dialog.
	 */
	private void downloadFormList() {
		ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = connectivityManager.getActiveNetworkInfo();

		if (ni == null || !ni.isConnected()) {
			Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT)
					.show();
		} else {

			mFormNamesAndURLs = new HashMap<String, FormDetails>();
			mAlertMsg = getString(R.string.please_wait);
			showProgressDialog();

			BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag("background");
			f.downloadFormList(this);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(BUNDLE_TOGGLED_KEY, mToggled);
		outState.putInt(BUNDLE_SELECTED_COUNT, selectedItemCount());
		outState.putSerializable(BUNDLE_FORM_MAP, mFormNamesAndURLs);
		outState.putString(DIALOG_TITLE, mAlertTitle);
		outState.putString(DIALOG_MSG, mAlertMsg);
		outState.putBoolean(DIALOG_SHOWING, mAlertShowing);
		outState.putSerializable(FORMLIST, mFormList);
	}

	/**
	 * returns the number of items currently selected in the list.
	 *
	 * @return
	 */
	private int selectedItemCount() {
		int count = 0;
		SparseBooleanArray sba = getListView().getCheckedItemPositions();
		for (int i = 0; i < getListView().getCount(); i++) {
			if (sba.get(i, false)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * starts the task to download the selected forms, also shows progress
	 * dialog
	 */
	@SuppressWarnings("unchecked")
	private void downloadSelectedFiles() {
		int totalCount = 0;
		ArrayList<FormDetails> filesToDownload = new ArrayList<FormDetails>();

		SparseBooleanArray sba = getListView().getCheckedItemPositions();
		for (int i = 0; i < getListView().getCount(); i++) {
			if (sba.get(i, false)) {
				HashMap<String, String> item = (HashMap<String, String>) getListAdapter()
						.getItem(i);
				filesToDownload.add(mFormNamesAndURLs.get(item
						.get(FORMDETAIL_KEY)));
			}
		}
		totalCount = filesToDownload.size();

		if (totalCount > 0) {
			// show dialog box
			showProgressDialog();
			BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag("background");
			f.downloadForms(this, filesToDownload.toArray(new FormDetails[filesToDownload.size()]));
		} else {
			Toast.makeText(getActivity(), R.string.noselect_error,
					Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onResume() {

		getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		getListView().setItemsCanFocus(false);

		FragmentManager mgr = getFragmentManager();
		BackgroundTaskFragment f = (BackgroundTaskFragment) mgr
				.findFragmentByTag("background");

		f.establishFormListDownloaderListener(this);
		f.establishFormDownloaderListener(this);

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

	/**
	 * Called when the form list has finished downloading. results will either
	 * contain a set of <formname, formdetails> tuples, or one tuple of
	 * DL.ERROR.MSG and the associated message.
	 *
	 * @param result
	 */
	public void formListDownloadingComplete(HashMap<String, FormDetails> result) {
		try {
			dismissProgressDialog();
		} catch (IllegalArgumentException e) {
			Log.i(t,
					"Attempting to close a dialog that was not previously opened");
		}

		BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag("background");
		f.clearDownloadFormListTask();

		if (result == null) {
			Log.e(t,
					"Formlist Downloading returned null.  That shouldn't happen");
			// Just displayes "error occured" to the user, but this should never
			// happen.
			createAlertDialog(getString(R.string.load_remote_form_error),
					getString(R.string.error_occured));
			return;
		}

		if (result.containsKey(DownloadFormListTask.DL_AUTH_REQUIRED)) {
			// need authorization
			showAuthDialog();
		} else if (result.containsKey(DownloadFormListTask.DL_ERROR_MSG)) {
			// Download failed
			String dialogMessage = getString(R.string.list_failed_with_error,
					result.get(DownloadFormListTask.DL_ERROR_MSG).errorStr);
			String dialogTitle = getString(R.string.load_remote_form_error);
			createAlertDialog(dialogTitle, dialogMessage);
		} else {
			// Everything worked. Clear the list and add the results.
			mFormNamesAndURLs = result;

			mFormList.clear();

			ArrayList<String> ids = new ArrayList<String>(
					mFormNamesAndURLs.keySet());
			for (int i = 0; i < result.size(); i++) {
				String formDetailsKey = ids.get(i);
				FormDetails details = mFormNamesAndURLs.get(formDetailsKey);
				HashMap<String, String> item = new HashMap<String, String>();
				item.put(FORMNAME, details.formName);
				item.put(FORMID_DISPLAY, ((details.formVersion == null) ? ""
						: (getString(R.string.version) + " "
								+ details.formVersion + " "))
						+ "ID: " + details.formID);
				item.put(FORMDETAIL_KEY, formDetailsKey);

				// Insert the new form in alphabetical order.
				if (mFormList.size() == 0) {
					mFormList.add(item);
				} else {
					int j;
					for (j = 0; j < mFormList.size(); j++) {
						HashMap<String, String> compareMe = mFormList.get(j);
						String name = compareMe.get(FORMNAME);
						if (name.compareTo(mFormNamesAndURLs.get(ids.get(i)).formName) > 0) {
							break;
						}
					}
					mFormList.add(j, item);
				}
			}
			mFormListAdapter.notifyDataSetChanged();
		}
	}

	private void showAuthDialog() {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(getActivity().getBaseContext());
		String server = settings.getString(
				PreferencesActivity.KEY_SERVER_URL,
				getString(R.string.default_server_url));

		final String url = server
				+ settings.getString(PreferencesActivity.KEY_FORMLIST_URL,
						"/formList");

		AuthDialogFragment f = AuthDialogFragment.newInstance(getId(),
				getString(R.string.server_requires_auth),
				getString(R.string.server_auth_credentials, url), url);

		f.show(getFragmentManager(), "authDialog");
	}

	private void showProgressDialog() {
		ProgressDialogFragment f = ProgressDialogFragment.newInstance(getId(),
				getString(R.string.downloading_data),
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
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if ( requestCode == RequestCodes.AUTH_DIALOG.ordinal()) {
			if ( resultCode == Activity.RESULT_OK ) {
				downloadFormList();
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
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
	public void progressUpdate(String currentFile, int progress, int total) {
		mAlertMsg = getString(R.string.fetching_file, currentFile, progress,
				total);
		updateProgressDialogMessage(mAlertMsg);
	}

	@Override
	public void formsDownloadingComplete(HashMap<String, String> result) {
		try {
			dismissProgressDialog();
		} catch (IllegalArgumentException e) {
			Log.i(t,
					"Attempting to close a dialog that was not previously opened");
		}

		BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag("background");
		f.clearDownloadFormsTask();

		StringBuilder b = new StringBuilder();
		Set<String> keys = result.keySet();
		for (String k : keys) {
			b.append(k + " - " + result.get(k));
			b.append("\n\n");
		}

		createAlertDialog(getString(R.string.download_forms_result), b.toString().trim());
	}


	@Override
	public void cancelProgressDialog() {

		BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag("background");
		f.clearDownloadFormListTask();
		f.clearDownloadFormsTask();
	}

}