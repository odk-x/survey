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

package org.opendatakit.survey.fragments;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.apache.commons.lang3.ArrayUtils;
import org.opendatakit.activities.IAppAwareActivity;
import org.opendatakit.provider.FormsProviderAPI;
import org.opendatakit.survey.R;
import org.opendatakit.survey.activities.IOdkSurveyActivity;
import org.opendatakit.survey.utilities.FormInfo;
import org.opendatakit.survey.utilities.FormListLoader;
import org.opendatakit.survey.utilities.TableIdFormIdVersionListAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Fragment displaying the list of available forms to fill out.
 *
 * @author mitchellsundt@gmail.com
 */
public class FormChooserListFragment extends ListFragment
    implements LoaderManager.LoaderCallbacks<ArrayList<FormInfo>> {

  private static ArrayList<FormInfo> mItems = new ArrayList<FormInfo>();
  private TableIdFormIdVersionListAdapter mAdapter;
  private SharedPreferences mPreferences;
  private static final String SORT_BY_TABLEID = "sortByTableID";
  private static final String SORT_BY_NAME = "sortByName";

  // used as key while saving user selected sorting order
  private static final String SORTING_KEY = "sortKey";


  @SuppressWarnings("unused") private static final String t = "FormChooserListFragment";
  private static final int FORM_CHOOSER_LIST_LOADER = 0x02;

  public static final int ID = R.layout.form_chooser_list;

  // data to retain across orientation changes

  // data that is not retained


  private View view;

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

    inflater.inflate(R.menu.sort,menu);

    if(mPreferences.getString(SORTING_KEY,SORT_BY_NAME).equals(SORT_BY_NAME)){
      menu.findItem(R.id.nameSort).setChecked(true);
    }else {
      menu.findItem(R.id.tableIdSort).setChecked(true);
    }

    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch(item.getItemId()){

      case R.id.nameSort:
        if (!item.isChecked()) {
          item.setChecked(true);
        }
        changeSortingOrder(mPreferences,SORT_BY_NAME);
        sortFormList(mItems,SORT_BY_NAME);
        break;

      case R.id.tableIdSort:
        changeSortingOrder(mPreferences,SORT_BY_TABLEID);
        sortFormList(mItems,SORT_BY_TABLEID);
        if (!item.isChecked()) {
          item.setChecked(true);
        }
        break;

      default:
        return super.onOptionsItemSelected(item);
    }

    mAdapter.swapData(mItems);
    return true;

  }

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }


  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    mPreferences = getActivity().getPreferences(Context.MODE_PRIVATE);

    // render total instance view
    mAdapter = new TableIdFormIdVersionListAdapter(getActivity(), R.layout.two_item, R.id.text1,
        R.id.text2, R.id.text3);
    setListAdapter(mAdapter);

    getLoaderManager().initLoader(FORM_CHOOSER_LIST_LOADER, null, this);
  }

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    view = inflater.inflate(ID, container, false);
    return view;
  }

  @Override public void onResume() {
    super.onResume();
  }

  @Override public void onPause() {
    super.onPause();
  }

  @Override public void onListItemClick(ListView l, View v, int position, long id) {
    super.onListItemClick(l, v, position, id);

    // get uri to form
    FormInfo info = (FormInfo) mAdapter.getItem(position);
    Uri formUri = Uri.withAppendedPath(Uri.withAppendedPath(
        Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI,
            ((IAppAwareActivity) getActivity()).getAppName()), info.tableId), info.formId);

    ((IOdkSurveyActivity) getActivity()).chooseForm(formUri);
  }

  @Override public Loader<ArrayList<FormInfo>> onCreateLoader(int id, Bundle args) {
    // This is called when a new Loader needs to be created. This
    // sample only has one Loader, so we don't care about the ID.
    return new FormListLoader(getActivity(), ((IAppAwareActivity) getActivity()).getAppName());
  }

  @Override public void onLoadFinished(Loader<ArrayList<FormInfo>> loader,
      ArrayList<FormInfo> dataset) {

    String sortingOrder = getActivity().getPreferences(Context.MODE_PRIVATE)
                          .getString(SORTING_KEY,SORT_BY_NAME);
    mItems = dataset;
    sortFormList(dataset,sortingOrder);

    // Swap the new cursor in. (The framework will take care of closing the
    // old cursor once we return.)
    mAdapter.swapData(dataset);
  }


  @Override public void onLoaderReset(Loader<ArrayList<FormInfo>> loader) {
    // This is called when the last Cursor provided to onLoadFinished()
    // above is about to be closed. We need to make sure we are no
    // longer using it.
    mAdapter.clear();
  }

  //  Sorts the forms list according to sorting order
  private void sortFormList(ArrayList<FormInfo> forms, String sortingOrder) {

    if (sortingOrder.equals(SORT_BY_NAME)) {
      Collections.sort(forms, new Comparator<FormInfo>() {
        @Override
        public int compare(FormInfo lhs, FormInfo rhs) {
          int cmp = lhs.formDisplayName.compareTo(rhs.formDisplayName);
          if (cmp != 0) {
            return cmp;
          }
          cmp = lhs.tableId.compareTo(rhs.tableId);
          if (cmp != 0) {
            return cmp;
          }
          cmp = lhs.formId.compareTo(rhs.formId);
          if (cmp != 0) {
            return cmp;
          }
          cmp = lhs.formVersion.compareTo(rhs.formVersion);
          if (cmp != 0) {
            return cmp;
          }
          cmp = lhs.formDisplaySubtext.compareTo(rhs.formDisplaySubtext);
          return cmp;
        }
      });
    } else if (sortingOrder.equals(SORT_BY_TABLEID)) {

      Collections.sort(mItems, new Comparator<FormInfo>() {
        @Override
        public int compare(FormInfo left, FormInfo right) {
          int cmp = left.tableId.compareTo(right.tableId);
          if (cmp != 0) {
            return cmp;
          }
          cmp = left.formDisplayName.compareTo(right.formDisplayName);
          if (cmp != 0) {
            return cmp;
          }
          cmp = left.formId.compareTo(right.formId);
          if (cmp != 0) {
            return cmp;
          }
          cmp = left.formVersion.compareTo(right.formVersion);
          if (cmp != 0) {
            return cmp;
          }
          cmp = left.formDisplaySubtext.compareTo(right.formDisplaySubtext);
          return cmp;
        }
      });
      mAdapter.swapData(mItems);

    }
  }

  // Saves/changes user selected sorting order in preference file

  private  void changeSortingOrder(SharedPreferences preferences,String order){

    SharedPreferences.Editor editor = preferences.edit();
    editor.putString(SORTING_KEY,order);
    editor.commit();

  }
}
