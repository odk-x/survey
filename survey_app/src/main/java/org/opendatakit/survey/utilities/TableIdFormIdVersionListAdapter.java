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

package org.opendatakit.survey.utilities;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import org.opendatakit.survey.R;

import java.util.ArrayList;

/**
 * Implementation of cursor adapter that displays the version of a form if a
 * form has a version.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class TableIdFormIdVersionListAdapter extends BaseAdapter {

  private final Context mContext;
  private final int mLayout;
  private final int mFormDisplayNameId;
  private final int mFormLastUpdateDateId;
  private final int mTableIdFormVersionId;
  private final ArrayList<FormInfo> mItems = new ArrayList<FormInfo>();

  private static final String TAG = TableIdFormIdVersionListAdapter.class.getSimpleName();

  public TableIdFormIdVersionListAdapter(Context context, int layout, int form_display_name_id,
      int form_last_update_date_id, int form_version_id) {
    this.mContext = context;
    this.mLayout = layout;
    this.mFormDisplayNameId = form_display_name_id;
    this.mFormLastUpdateDateId = form_last_update_date_id;
    this.mTableIdFormVersionId = form_version_id;
  }

  public void clear() {
    mItems.clear();
  }

  public void swapData(ArrayList<FormInfo> items) {
    mItems.clear();
    mItems.addAll(items);
    notifyDataSetChanged();
  }

  @Override
  public int getCount() {
    return mItems.size();
  }

  @Override
  public Object getItem(int position) {
    return mItems.get(position);
  }

  @Override
  public long getItemId(int position) {
    return Integer.MAX_VALUE - position;
  }

  @Override
  public int getItemViewType(int position) {
    return 1;
  }

  @Override
  public boolean isEnabled(int position) {
    return true;
  }

  @Override
  public int getViewTypeCount() {
    return 1;
  }

  @Override
  public View getView(int position, View view, ViewGroup parent) {
    FormInfo info = mItems.get(position);
    if (view == null ) {
      LayoutInflater layoutInflater =
        (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      view = layoutInflater.inflate(mLayout, parent, false);
    }
    TextView formTitleView = (TextView) view.findViewById(mFormDisplayNameId);
    formTitleView.setText(info.formDisplayName);

    if ( mFormLastUpdateDateId != -1) {
      TextView formDateView = (TextView) view.findViewById(mFormLastUpdateDateId);
      formDateView.setText(info.formDisplaySubtext);
    }

    if ( mTableIdFormVersionId != -1 ) {
      TextView v = (TextView) view.findViewById(mTableIdFormVersionId);
      v.setVisibility(View.VISIBLE);
      if ( info.formVersion != null) {
        v.setText(mContext.getString(R.string.table_id_form_id_version, info.tableId, info.formId,
            info.formVersion));
      } else {
        v.setText(mContext.getString(R.string.table_id_form_id, info.tableId, info.formId));
      }
    }
    return view;
  }

}