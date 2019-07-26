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

import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.opendatakit.activities.IAppAwareActivity;
import org.opendatakit.properties.CommonToolProperties;
import org.opendatakit.properties.PropertiesSingleton;
import org.opendatakit.provider.FormsProviderAPI;
import org.opendatakit.survey.R;
import org.opendatakit.survey.activities.IOdkSurveyActivity;
import org.opendatakit.survey.utilities.FormInfo;
import org.opendatakit.survey.utilities.FormListLoader;
import org.opendatakit.survey.utilities.TableIdFormIdVersionListAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static org.opendatakit.properties.CommonToolProperties.*;

/**
 * Fragment displaying the list of available forms to fill out.
 *
 * @author mitchellsundt@gmail.com
 */
public class FormChooserListFragment extends ListFragment
    implements LoaderManager.LoaderCallbacks<ArrayList<FormInfo>> {

  private static ArrayList<FormInfo> mItems = new ArrayList<FormInfo>();
  private TableIdFormIdVersionListAdapter mAdapter;
  private  PropertiesSingleton mPropSingleton;
  private static String mAppName;
  private static final String SORT_BY_TABLEID = "sortByTableID";

  /*this is also used in CommonToolProperties.java to set default value for sorting order ,if updated
    change there too..*/
  private static final String SORT_BY_NAME = "sortByName";


  @SuppressWarnings("unused") private static final String t = "FormChooserListFragment";
  private static final int FORM_CHOOSER_LIST_LOADER = 0x02;

  public static final int ID = R.layout.form_chooser_list;

  // data to retain across orientation changes

  // data that is not retained


  private View view;


  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

    inflater.inflate(R.menu.sort,menu);

    if(mPropSingleton.getProperty(KEY_SURVEY_SORT_ORDER).equals(SORT_BY_NAME)){
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
        mPropSingleton.setProperties(Collections.singletonMap(KEY_SURVEY_SORT_ORDER,SORT_BY_NAME));
        sortFormList(mItems,SORT_BY_NAME);
        break;

      case R.id.tableIdSort:
        mPropSingleton.setProperties(Collections.singletonMap(KEY_SURVEY_SORT_ORDER,SORT_BY_TABLEID));
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

    mAppName = ((IAppAwareActivity) getActivity()).getAppName();
    mPropSingleton = CommonToolProperties.get(getActivity(),mAppName);

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
            mAppName), info.tableId), info.formId);

    ((IOdkSurveyActivity) getActivity()).chooseForm(formUri);
  }

  @Override public Loader<ArrayList<FormInfo>> onCreateLoader(int id, Bundle args) {
    // This is called when a new Loader needs to be created. This
    // sample only has one Loader, so we don't care about the ID.
    return new FormListLoader(getActivity(), ((IAppAwareActivity) getActivity()).getAppName());
  }

  @Override public void onLoadFinished(Loader<ArrayList<FormInfo>> loader,
      ArrayList<FormInfo> dataset) {

    String sortingOrder = mPropSingleton.getProperty(KEY_SURVEY_SORT_ORDER);
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
          int cmp = lhs.formDisplayName.compareToIgnoreCase(rhs.formDisplayName);
          if (cmp != 0) {
            return cmp;
          }
          cmp = lhs.tableId.compareToIgnoreCase(rhs.tableId);
          if (cmp != 0) {
            return cmp;
          }
          cmp = lhs.formId.compareToIgnoreCase(rhs.formId);
          if (cmp != 0) {
            return cmp;
          }
          cmp = lhs.formVersion.compareToIgnoreCase(rhs.formVersion);
          if (cmp != 0) {
            return cmp;
          }
          cmp = lhs.formDisplaySubtext.compareToIgnoreCase(rhs.formDisplaySubtext);
          return cmp;
        }
      });
    } else if (sortingOrder.equals(SORT_BY_TABLEID)) {

      Collections.sort(forms, new Comparator<FormInfo>() {
        @Override
        public int compare(FormInfo left, FormInfo right) {
          int cmp = left.tableId.compareToIgnoreCase(right.tableId);
          if (cmp != 0) {
            return cmp;
          }
          cmp = left.formDisplayName.compareToIgnoreCase(right.formDisplayName);
          if (cmp != 0) {
            return cmp;
          }
          cmp = left.formId.compareToIgnoreCase(right.formId);
          if (cmp != 0) {
            return cmp;
          }
          cmp = left.formVersion.compareToIgnoreCase(right.formVersion);
          if (cmp != 0) {
            return cmp;
          }
          cmp = left.formDisplaySubtext.compareToIgnoreCase(right.formDisplaySubtext);
          return cmp;
        }
      });
      mAdapter.swapData(mItems);

    }
  }

}
