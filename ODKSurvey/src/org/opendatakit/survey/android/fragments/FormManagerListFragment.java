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
import org.opendatakit.survey.android.listeners.DeleteFormsListener;
import org.opendatakit.survey.android.listeners.DiskSyncListener;
import org.opendatakit.survey.android.provider.FormsProviderAPI.FormsColumns;
import org.opendatakit.survey.android.utilities.VersionHidingCursorAdapter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * List of form definitions on the device that can be deleted.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class FormManagerListFragment extends ListFragment implements
		DiskSyncListener, DeleteFormsListener,
		LoaderManager.LoaderCallbacks<Cursor> {

	private static String t = "FormManagerListFragment";
	private static int FORM_MANAGER_LIST_LOADER = 0x01;

	public static int ID = R.layout.form_manage_list;

	private AlertDialog mAlertDialog;
	private Button mDeleteButton;

	private CursorAdapter mInstances;
	private ArrayList<Long> mSelected = new ArrayList<Long>();
	private final String SELECTED = "selected";

	private final String syncMsgKey = "syncmsgkey";

	private View view;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		String[] data = new String[] { FormsColumns.DISPLAY_NAME,
				FormsColumns.DISPLAY_SUBTEXT, FormsColumns.FORM_VERSION };
		int[] viewParams = new int[] { R.id.text1, R.id.text2, R.id.text3 };

		// render total instance view
		mInstances = new VersionHidingCursorAdapter(FormsColumns.FORM_VERSION,
				this.getActivity(), R.layout.two_item_multiple_choice, data,
				viewParams);
		setListAdapter(mInstances);

		getLoaderManager().initLoader(FORM_MANAGER_LIST_LOADER, null, this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		view = inflater.inflate(R.layout.form_manage_list, container, false);

		mDeleteButton = (Button) view.findViewById(R.id.delete_button);
		mDeleteButton.setText(getString(R.string.delete_file));
		mDeleteButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				if (mSelected.size() > 0) {
					createDeleteFormsDialog();
				} else {
					Toast.makeText(getActivity().getApplicationContext(),
							R.string.noselect_error, Toast.LENGTH_SHORT).show();
				}
			}
		});

		if (savedInstanceState != null
				&& savedInstanceState.containsKey(syncMsgKey)) {
			TextView tv = (TextView) view.findViewById(R.id.status_text);
			tv.setText(savedInstanceState.getString(syncMsgKey));
		}

		if (savedInstanceState != null
				&& savedInstanceState.containsKey(SELECTED)) {
			long[] selectedArray = savedInstanceState.getLongArray(SELECTED);
			for (int i = 0; i < selectedArray.length; i++) {
				mSelected.add(selectedArray[i]);
			}
		}
		mDeleteButton.setEnabled(!(mSelected.size() == 0));

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		long[] selectedArray = new long[mSelected.size()];
		for (int i = 0; i < mSelected.size(); i++) {
			selectedArray[i] = mSelected.get(i);
		}
		outState.putLongArray(SELECTED, selectedArray);
		TextView tv = (TextView) view.findViewById(R.id.status_text);
		outState.putString(syncMsgKey, tv.getText().toString());
	}

	@Override
	public void onResume() {
		super.onResume();

		getListView().setBackgroundColor(Color.WHITE);
		getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		getListView().setItemsCanFocus(false);

		// if current activity is being reinitialized due to changing
		// orientation restore all checkmarks for ones selected
		ListView ls = getListView();
		for (long id : mSelected) {
			for (int pos = 0; pos < ls.getCount(); pos++) {
				if (id == ls.getItemIdAtPosition(pos)) {
					ls.setItemChecked(pos, true);
					break;
				}
			}

		}

		FragmentManager mgr = getFragmentManager();
		BackgroundTaskFragment f = (BackgroundTaskFragment) mgr
				.findFragmentByTag("background");

		f.establishDiskSyncListener(this);
		f.establishDeleteFormsListener(this);
	}

	@Override
	public void onPause() {
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}

		super.onPause();
	}

	/**
	 * Create the form delete dialog
	 */
	private void createDeleteFormsDialog() {
		mAlertDialog = new AlertDialog.Builder(getActivity()).create();
		mAlertDialog.setTitle(getString(R.string.delete_file));
		mAlertDialog.setMessage(getString(R.string.delete_confirm,
				mSelected.size()));
		DialogInterface.OnClickListener dialogYesNoListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int i) {
				switch (i) {
				case DialogInterface.BUTTON_POSITIVE: // delete
					deleteSelectedForms();
					break;
				case DialogInterface.BUTTON_NEGATIVE: // do nothing
					break;
				}
			}
		};
		mAlertDialog.setCancelable(false);
		mAlertDialog.setButton(getString(R.string.delete_yes),
				dialogYesNoListener);
		mAlertDialog.setButton2(getString(R.string.delete_no),
				dialogYesNoListener);
		mAlertDialog.show();
	}

	/**
	 * Deletes the selected files.First from the database then from the file
	 * system
	 */
	private void deleteSelectedForms() {
		FragmentManager mgr = getFragmentManager();
		BackgroundTaskFragment f = (BackgroundTaskFragment) mgr
				.findFragmentByTag("background");

		f.deleteSelectedForms(this,
				mSelected.toArray(new Long[mSelected.size()]));
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		// get row id from db
		Cursor c = (Cursor) getListAdapter().getItem(position);
		long k = c.getLong(c.getColumnIndex(FormsColumns._ID));

		// add/remove from selected list
		if (mSelected.contains(k))
			mSelected.remove(k);
		else
			mSelected.add(k);

		mDeleteButton.setEnabled(!(mSelected.size() == 0));

	}

	@Override
	public void SyncComplete(String result) {
		Log.i(t, "Disk scan complete");
		TextView tv = (TextView) view.findViewById(R.id.status_text);
		tv.setText(result);
	}

	@Override
	public void deleteFormsComplete(int deletedForms) {
		Log.i(t, "Delete forms complete");
		if (deletedForms == mSelected.size()) {
			// all deletes were successful
			Toast.makeText(getActivity(),
					getString(R.string.file_deleted_ok, deletedForms),
					Toast.LENGTH_SHORT).show();
		} else {
			// had some failures
			Log.e(t, "Failed to delete " + (mSelected.size() - deletedForms)
					+ " forms");
			Toast.makeText(
					getActivity(),
					getString(R.string.file_deleted_error, mSelected.size()
							- deletedForms, mSelected.size()),
					Toast.LENGTH_LONG).show();
		}
		mSelected.clear();
		getListView().clearChoices(); // doesn't unset the checkboxes
		for (int i = 0; i < getListView().getCount(); ++i) {
			getListView().setItemChecked(i, false);
		}
		mDeleteButton.setEnabled(false);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		// This is called when a new Loader needs to be created. This
		// sample only has one Loader, so we don't care about the ID.
		// First, pick the base URI to use depending on whether we are
		// currently filtering.
		Uri baseUri = FormsColumns.CONTENT_URI;

		// Now create and return a CursorLoader that will take care of
		// creating a Cursor for the data being displayed.
		String sortOrder = FormsColumns.DISPLAY_NAME + " ASC, "
				+ FormsColumns.FORM_VERSION + " DESC";
		return new CursorLoader(getActivity(), baseUri, null, null, null,
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
