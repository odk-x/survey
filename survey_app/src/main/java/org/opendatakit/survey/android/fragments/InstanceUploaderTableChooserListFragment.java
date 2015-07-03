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

import java.util.List;

import org.opendatakit.common.android.activities.IAppAwareActivity;
import org.opendatakit.common.android.activities.ODKActivity;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.tasks.TableSetLoader;
import org.opendatakit.survey.android.tasks.TableSetLoader.TableSetEntry;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Fragment displaying the list of available tables to upload.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class InstanceUploaderTableChooserListFragment extends ListFragment implements
    LoaderManager.LoaderCallbacks<List<TableSetEntry>> {

  @SuppressWarnings("unused")
  private static final String t = "InstanceUploaderTableChooserListFragment";
  private static final int INSTANCE_UPLOADER_TABLE_CHOOSER_LIST_LOADER = 0x04;

  public static final int ID = R.layout.table_chooser_list;

  // keys for the data being retained

  // data to retain across orientation changes

  // data that is not retained

  private static class TableSetEntryAdapter extends ArrayAdapter<TableSetEntry> {
    private final LayoutInflater mInflater;

    public TableSetEntryAdapter(Context context) {
      super(context, R.layout.upload_choose_table);
      mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    /**
     * Populate items in the list.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView == null) {
            view = mInflater.inflate(R.layout.upload_choose_table, parent, false);
        } else {
            view = convertView;
        }

        TableSetEntry item = getItem(position);
        ((TextView)view.findViewById(R.id.text2)).setText(item.tableId);
        ((TextView)view.findViewById(R.id.text1)).setText(item.displayName);

        return view;
    }

  }

  private TableSetEntryAdapter mInstances;
  private View view;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    // render total instance view
    mInstances = new TableSetEntryAdapter(getActivity());
    setListAdapter(mInstances);
    // getListView().setBackgroundColor(Color.WHITE);

    getLoaderManager().initLoader(INSTANCE_UPLOADER_TABLE_CHOOSER_LIST_LOADER, null, this);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    view = inflater.inflate(ID, container, false);

    TextView tv = (TextView) view.findViewById(R.id.status_text);
    tv.setText(R.string.choose_table_to_upload);

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();

    TextView tv = (TextView) view.findViewById(R.id.status_text);
    tv.setText(R.string.choose_table_to_upload);
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    super.onListItemClick(l, v, position, id);

    // get uri to form
    TableSetEntry entry = (((TableSetEntryAdapter) getListAdapter()).getItem(position));
    String tableId = entry.tableId;

    ((ODKActivity) getActivity()).chooseInstanceUploaderTable(tableId);
  }

  @Override
  public Loader<List<TableSetEntry>> onCreateLoader(int id, Bundle args) {
    // This is called when a new Loader needs to be created. This
    // sample only has one Loader, so we don't care about the ID.
    return new TableSetLoader(getActivity(), ((IAppAwareActivity) getActivity()).getAppName());
  }

  @Override
  public void onLoadFinished(Loader<List<TableSetEntry>> loader, List<TableSetEntry> cursor) {
    // Swap the new cursor in. (The framework will take care of closing the
    // old cursor once we return.)
    mInstances.clear();
    mInstances.addAll(cursor);
  }

  @Override
  public void onLoaderReset(Loader<List<TableSetEntry>> loader) {
    // This is called when the last Cursor provided to onLoadFinished()
    // above is about to be closed. We need to make sure we are no
    // longer using it.
    mInstances.clear();
  }
}
