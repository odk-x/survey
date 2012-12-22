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

import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.listeners.DiskSyncListener;
import org.opendatakit.survey.android.provider.FormsProviderAPI.FormsColumns;
import org.opendatakit.survey.android.utilities.VersionHidingCursorAdapter;

import android.content.ContentUris;
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
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Fragment displaying the list of available forms to fill out.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class FormChooserListFragment extends ListFragment implements
		DiskSyncListener,
		LoaderManager.LoaderCallbacks<Cursor> {

	public interface FormChooserListListener {
		void chooseForm(Uri formUri);
	};

	private static String t = "FormChooserListFragment";
	private static int FORM_CHOOSER_LIST_LOADER = 0x02;

	public static int ID = R.layout.chooser_list_layout;

	private CursorAdapter mInstances;

	private final String syncMsgKey = "syncmsgkey";

	private View view;
	private FormChooserListListener listener;

	public void setFormChooserListListener(FormChooserListListener listener) {
		this.listener = listener;
	}

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
				this.getActivity(), R.layout.two_item, data, viewParams);
		setListAdapter(mInstances);

    	getListView().setBackgroundColor(Color.WHITE);
		getLoaderManager().initLoader(FORM_CHOOSER_LIST_LOADER, null, this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		view = inflater.inflate(ID, container, false);

		if (savedInstanceState != null
				&& savedInstanceState.containsKey(syncMsgKey)) {
			TextView tv = (TextView) view.findViewById(R.id.status_text);
			tv.setText(savedInstanceState.getString(syncMsgKey));
		}

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if ( view != null ) {
			TextView tv = (TextView) view.findViewById(R.id.status_text);
			outState.putString(syncMsgKey, tv.getText().toString());
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		FragmentManager mgr = getFragmentManager();
		BackgroundTaskFragment f = (BackgroundTaskFragment) mgr
				.findFragmentByTag("background");

		f.establishDiskSyncListener(this);
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		// get uri to form
		long idFormsTable = ((SimpleCursorAdapter) getListAdapter())
				.getItemId(position);
		Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI,
				idFormsTable);

		if ( listener != null ) {
			listener.chooseForm(formUri);
		}
	}

	@Override
	public void SyncComplete(String result) {
		Log.i(t, "Disk scan complete");
		TextView tv = (TextView) view.findViewById(R.id.status_text);
		tv.setText(result);
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
