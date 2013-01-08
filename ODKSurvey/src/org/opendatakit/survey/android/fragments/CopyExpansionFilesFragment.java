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

import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.activities.ODKActivity;
import org.opendatakit.survey.android.fragments.ProgressDialogFragment.CancelProgressDialog;
import org.opendatakit.survey.android.listeners.CopyExpansionFilesListener;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Attempt to initialize data directories using the APK Expansion files.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class CopyExpansionFilesFragment extends Fragment implements
		CopyExpansionFilesListener, CancelProgressDialog {

	private static final String t = "CopyExpansionFilesFragment";

	public static int ID = R.layout.copy_expansion_files_layout;

	private static final String DIALOG_TITLE = "dialogtitle";
	private static final String DIALOG_MSG = "dialogmsg";
	private static final String DIALOG_SHOWING = "dialogshowing";

	private String mAlertMsg;
	private boolean mAlertShowing = false;
	private String mAlertTitle;

	private AlertDialog mAlertDialog;

	private View view;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}


	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		if ( savedInstanceState == null ) {
			// attempt to copy the expansion files
			copyExpansionFiles();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		view = inflater.inflate(ID, container, false);
		mAlertMsg = getString(R.string.please_wait);

		if (savedInstanceState != null) {

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

		return view;
	}

	/**
	 * Starts the download task and shows the progress dialog.
	 */
	private void copyExpansionFiles() {
		mAlertMsg = getString(R.string.please_wait);
		showProgressDialog();

		BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag("background");
		f.copyExpansionFiles(this);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(DIALOG_TITLE, mAlertTitle);
		outState.putString(DIALOG_MSG, mAlertMsg);
		outState.putBoolean(DIALOG_SHOWING, mAlertShowing);
	}

	@Override
	public void onResume() {

		FragmentManager mgr = getFragmentManager();
		BackgroundTaskFragment f = (BackgroundTaskFragment) mgr
				.findFragmentByTag("background");

		f.establishCopyExpansionFilesListener(this);

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


	@Override
	public void copyExpansionFilesComplete(ArrayList<String> result) {
		try {
			dismissProgressDialog();
		} catch (IllegalArgumentException e) {
			Log.i(t,
					"Attempting to close a dialog that was not previously opened");
		}

		BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag("background");
		f.clearDownloadFormsTask();

		StringBuilder b = new StringBuilder();
		for (String k : result) {
			b.append(k);
			b.append("\n\n");
		}

		createAlertDialog(getString(R.string.download_forms_result), b.toString().trim());
	}


	private void showProgressDialog() {
		ProgressDialogFragment f = ProgressDialogFragment.newInstance(getId(),
				getString(R.string.searching_for_expansion_files),
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


	/**
	 * Creates an alert dialog with the given tite and message. If shouldExit is
	 * set to true, the activity will exit when the user clicks "ok".
	 *
	 * @param title
	 * @param shouldExit
	 */
	private void createAlertDialog(String title, String message) {
		if ( mAlertDialog != null && mAlertDialog.isShowing() ) {
			mAlertDialog.dismiss();
		}

		DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int i) {
				switch (i) {
				case DialogInterface.BUTTON_POSITIVE: // ok
					// just close the dialog
					mAlertShowing = false;
					((ODKActivity) getActivity()).expansionFilesCopied();
					break;
				}
			}
		};

		mAlertDialog = new AlertDialog.Builder(getActivity())
			.setTitle(title)
			.setMessage(message)
			.setCancelable(false)
			.setPositiveButton(getString(R.string.ok), quitListener)
			.setIcon(android.R.drawable.ic_dialog_info).create();
		mAlertMsg = message;
		mAlertTitle = title;
		mAlertShowing = true;
		mAlertDialog.show();
	}

	@Override
	public void copyProgressUpdate(String currentFile, int progress, int total) {
		mAlertMsg = getString(R.string.expanding_file, currentFile, progress,
				total);
		updateProgressDialogMessage(mAlertMsg);
	}


	@Override
	public void cancelProgressDialog() {

		BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag("background");
		f.clearDownloadFormListTask();
		f.clearDownloadFormsTask();
	}

}