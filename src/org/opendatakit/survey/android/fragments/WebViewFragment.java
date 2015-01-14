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

import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.views.ODKWebView;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment that doesn't actually render -- the activity will make the WebKit
 * view visible or gone based upon which of these fragments is 'chosen'.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class WebViewFragment extends Fragment {
  private static final String LOGTAG = "WebViewFragment";
  public static final int ID = R.layout.blank_layout;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  public void onAttach(Activity activity) {
    super.onAttach(activity);
  }

  @Override
  public void onResume() {
    super.onResume();
    
    if ( getActivity() != null ) {
      FragmentManager mgr = getFragmentManager();
      BackgroundTaskFragment f = (BackgroundTaskFragment) mgr.findFragmentByTag("background");
  
      f.configureView();
    }
  }

  @Override
  public void onDestroyView() {

    if ( getActivity() != null ) {
      ODKWebView wv = (ODKWebView) getActivity().findViewById(R.id.webkit_view);
      if (wv != null) {
        Log.i(LOGTAG, "onDestroyView - Rolling back all active transactions held by DbShim service");
        wv.beforeDbShimServiceDisconnected();
      }
    }

    super.onDestroyView();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    return inflater.inflate(ID, container, false);
  }
}
