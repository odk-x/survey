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

package org.opendatakit.survey.android.fragments;

import org.opendatakit.common.android.activities.IAppAwareActivity;
import org.opendatakit.common.android.activities.ODKActivity;
import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.provider.FormsProviderAPI;
import org.opendatakit.common.android.utilities.ODKCursorUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.utilities.VersionHidingCursorAdapter;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

/**
 * Fragment displaying the list of available forms to fill out.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class FormChooserListFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>
    {

  @SuppressWarnings("unused")
  private static final String t = "FormChooserListFragment";
  private static final int FORM_CHOOSER_LIST_LOADER = 0x02;

  public static final int ID = R.layout.form_chooser_list;

  // data to retain across orientation changes

  // data that is not retained

  private CursorAdapter mInstances;
  private View view;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    String[] data = new String[] { FormsColumns.DISPLAY_NAME, FormsColumns.DISPLAY_SUBTEXT,
        FormsColumns.FORM_VERSION };
    int[] viewParams = new int[] { R.id.text1, R.id.text2, R.id.text3 };

    // render total instance view
    mInstances = new VersionHidingCursorAdapter(FormsColumns.FORM_VERSION, this.getActivity(),
        R.layout.two_item, data, viewParams);
    setListAdapter(mInstances);

    getLoaderManager().initLoader(FORM_CHOOSER_LIST_LOADER, null, this);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    view = inflater.inflate(ID, container, false);
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    super.onListItemClick(l, v, position, id);

    // get uri to form
    Cursor c = (Cursor) (((SimpleCursorAdapter) getListAdapter()).getItem(position));
    String tableId = ODKCursorUtils.getIndexAsString(c, c.getColumnIndex(FormsColumns.TABLE_ID));
    String formId = ODKCursorUtils.getIndexAsString(c, c.getColumnIndex(FormsColumns.FORM_ID));
    Uri formUri = Uri.withAppendedPath(
        Uri.withAppendedPath(
          Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI,
            ((IAppAwareActivity) getActivity()).getAppName()), tableId),
            formId);

    ((ODKActivity) getActivity()).chooseForm(formUri);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    // This is called when a new Loader needs to be created. This
    // sample only has one Loader, so we don't care about the ID.
    // First, pick the base URI to use depending on whether we are
    // currently filtering.
    Uri baseUri = Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI,
        ((IAppAwareActivity) getActivity()).getAppName());

    // Now create and return a CursorLoader that will take care of
    // creating a Cursor for the data being displayed.
    String sortOrder = FormsColumns.DISPLAY_NAME + " ASC, " + FormsColumns.FORM_VERSION + " DESC";
    return new CursorLoader(getActivity(), baseUri, null, null, null, sortOrder);
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
