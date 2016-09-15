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

import android.widget.TextView;
import org.opendatakit.common.android.activities.IAppAwareActivity;
import org.opendatakit.common.android.activities.IOdkDataActivity;
import org.opendatakit.common.android.application.CommonApplication;
import org.opendatakit.common.android.listener.DatabaseConnectionListener;
import org.opendatakit.common.android.logging.WebLogger;
import org.opendatakit.common.android.views.*;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.opendatakit.survey.android.logic.SurveyDataExecutorProcessor;
import org.opendatakit.survey.android.views.OdkSurveyWebView;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * Fragment that doesn't actually render -- the activity will make the WebKit
 * view visible or gone based upon which of these fragments is 'chosen'.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class WebViewFragment extends Fragment implements DatabaseConnectionListener {

  private static final int ID = R.layout.web_view_container;

  @Override
  public View onCreateView(
      LayoutInflater inflater,
      ViewGroup container,
      Bundle savedInstanceState) {

    View v = inflater.inflate(
        R.layout.web_view_container,
        container,
        false);

    return v;
  }

  @Override
  public void onResume() {
    super.onResume();
    Survey.getInstance().possiblyFireDatabaseCallback(getActivity(), this);
  }

  public OdkSurveyWebView getWebKit() {
    if ( getView() == null ) {
      return null;
    }

    return (OdkSurveyWebView) getView().findViewById(R.id.webkit);
  }

  public void setWebKitVisibility() {
    if ( getView() == null ) {
      return;
    }

    OdkSurveyWebView webView = (OdkSurveyWebView) getView().findViewById(R.id.webkit);
    TextView noDatabase = (TextView) getView().findViewById(android.R.id.empty);

    if ( Survey.getInstance().getDatabase() != null ) {
      webView.setVisibility(View.VISIBLE);
      noDatabase.setVisibility(View.GONE);
    } else {
      webView.setVisibility(View.GONE);
      noDatabase.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void databaseAvailable() {

    if ( getView() != null ) {
      setWebKitVisibility();
      getWebKit().reloadPage();
    }
  }

  @Override
  public void databaseUnavailable() {
    if ( getView() != null ) {
      setWebKitVisibility();
      getWebKit().setForceLoadDuringReload();
    }
  }
}
