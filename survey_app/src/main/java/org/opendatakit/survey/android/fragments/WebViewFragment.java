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
import org.opendatakit.common.android.application.CommonApplication;
import org.opendatakit.common.android.listener.DatabaseConnectionListener;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.common.android.views.*;
import org.opendatakit.database.service.OdkDbInterface;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.opendatakit.survey.android.logic.SurveyDataExecutorProcessor;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * Fragment that doesn't actually render -- the activity will make the WebKit
 * view visible or gone based upon which of these fragments is 'chosen'.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class WebViewFragment extends Fragment implements ICallbackFragment, DatabaseConnectionListener {
  private static final String LOGTAG = "WebViewFragment";
  public static final int ID = R.layout.blank_layout;
  private static final String RESPONSE_JSON = "responseJSON";

  OdkData mDataReference;
  LinkedList<String> queueResponseJSON = new LinkedList<String>();

  DatabaseConnectionListener listener = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if ( savedInstanceState != null && savedInstanceState.containsKey(RESPONSE_JSON)) {
      String[] pendingResponseJSON = savedInstanceState.getStringArray(RESPONSE_JSON);
      queueResponseJSON.addAll(Arrays.asList(pendingResponseJSON));
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if ( !queueResponseJSON.isEmpty() ) {
      String[] qra = queueResponseJSON.toArray(new String[queueResponseJSON.size()]);
      outState.putStringArray(RESPONSE_JSON, qra);
    }
  }

  public void onAttach(Activity activity) {
    super.onAttach(activity);
  }

  @Override
  public void onResume() {
    super.onResume();
    
    Survey.getInstance().configureView();
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

  @Override
  public void signalResponseAvailable(String responseJSON) {
    if ( responseJSON == null ) {
      WebLogger.getLogger(getAppName()).e(LOGTAG, "signalResponseAvailable -- got null responseJSON!");
    } else {
      WebLogger.getLogger(getAppName()).e(LOGTAG, "signalResponseAvailable -- got " + responseJSON.length() + " long responseJSON!");
    }
    if ( getActivity() != null && responseJSON != null) {
      this.queueResponseJSON.push(responseJSON);
      final ODKWebView webView = (ODKWebView) getActivity().findViewById(R.id.webkit_view);
      if (webView != null) {
        this.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            webView.loadUrl("javascript:datarsp.responseAvailable();");
          }
        });
      }
    }
  }

  @Override
  public String getResponseJSON() {
    if ( queueResponseJSON.isEmpty() ) {
      return null;
    }
    String responseJSON = queueResponseJSON.removeFirst();
    return responseJSON;
  }

  @Override
  public ExecutorProcessor newExecutorProcessor(ExecutorContext context) {
    return new SurveyDataExecutorProcessor(context);
  }

  @Override
  public void registerDatabaseConnectionBackgroundListener(DatabaseConnectionListener listener) {
    this.listener = listener;
  }

  @Override
  public OdkDbInterface getDatabase() {
    return ((CommonApplication) this.getActivity().getApplication()).getDatabase();
  }

  @Override
  public void databaseAvailable() {
    if ( listener != null ) {
      listener.databaseAvailable();
    }
  }

  @Override
  public void databaseUnavailable() {
    if ( listener != null ) {
      listener.databaseUnavailable();
    }
  }

  @Override
  public String getAppName() {
    return ((IAppAwareActivity) this.getActivity()).getAppName();
  }
}
