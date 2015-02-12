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

import org.opendatakit.common.android.listener.LicenseReaderListener;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.activities.ODKActivity;
import org.opendatakit.survey.android.application.Survey;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.text.Html;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class AboutMenuFragment extends Fragment implements LicenseReaderListener {
  public static final String t = "AboutMenuFragment";

  public static final int ID = R.layout.about_menu_layout;
  /**
   * Key for savedInstanceState
   */
  private static final String LICENSE_TEXT = "LICENSE_TEXT";

  private TextView mTextView;
  
  /**
   * retained value...
   */
  private String mLicenseText = null;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    View aboutMenuView = inflater.inflate(ID, container, false);

    TextView versionBox = (TextView) aboutMenuView.findViewById(R.id.versionText);
    versionBox.setText(Survey.getInstance().getVersionedAppName());

    mTextView = (TextView) aboutMenuView.findViewById(R.id.text1);
    mTextView.setAutoLinkMask(Linkify.WEB_URLS);
    mTextView.setClickable(true);

    if (savedInstanceState != null && savedInstanceState.containsKey(LICENSE_TEXT)) {
      mLicenseText = savedInstanceState.getString(LICENSE_TEXT);
      mTextView.setText(Html.fromHtml(mLicenseText));
    } else {
      readLicenseFile();
    }

    return aboutMenuView;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(LICENSE_TEXT, mLicenseText);
  }

  @Override
  public void readLicenseComplete(String result) {
    ODKActivity activity = (ODKActivity) getActivity();
    WebLogger.getLogger(activity.getAppName()).i(t, "Read license complete");
    if (result != null) {
      // Read license file successfully
      Toast.makeText(getActivity(), R.string.read_license_success, Toast.LENGTH_SHORT).show();
      mLicenseText = result;
      mTextView.setText(Html.fromHtml(result));
    } else {
      // had some failures
      WebLogger.getLogger(activity.getAppName()).e(t, "Failed to read license file");
      Toast.makeText(getActivity(), R.string.read_license_fail, Toast.LENGTH_LONG).show();
    }
  }

  private void readLicenseFile() {
    FragmentManager mgr = getFragmentManager();
    BackgroundTaskFragment f = (BackgroundTaskFragment) mgr.findFragmentByTag("background");

    ODKActivity activity = (ODKActivity) getActivity();
    String appName = activity.getAppName();

    f.readLicenseFile(appName, this);
  }

}
