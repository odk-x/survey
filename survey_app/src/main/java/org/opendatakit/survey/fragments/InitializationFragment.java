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

import java.util.ArrayList;

import org.opendatakit.activities.IAppAwareActivity;
import org.opendatakit.activities.IInitResumeActivity;
import org.opendatakit.fragment.AlertDialogFragment.ConfirmAlertDialog;
import org.opendatakit.fragment.AlertNProgessMsgFragmentMger;
import org.opendatakit.listener.DatabaseConnectionListener;
import org.opendatakit.listener.InitializationListener;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.survey.R;
import org.opendatakit.survey.application.Survey;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Attempt to initialize data directories using the APK Expansion files.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class InitializationFragment extends Fragment implements InitializationListener,
    ConfirmAlertDialog, DatabaseConnectionListener {

  private static final String t = InitializationFragment.class.getSimpleName();

  private static final int ID = R.layout.copy_expansion_files_layout;
  private static final String ALERT_SURVEY_DIALOG_TAG = "alertDialogSurvey";
  private static final String INIT_SURVEY_PROGRESS_DIALOG_TAG = "progressDialogSurvey";

  private static final String INIT_STATE_KEY = "IF_initStateKeySurvey";

  // The types of dialogs we handle
  public enum InitializationState {
    START, IN_PROGRESS, FINISH
  }

  private static String appName;

  private InitializationState initState = InitializationState.START;
  private AlertNProgessMsgFragmentMger msgManager;

  private String mainDialogTitle;


  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    appName = ((IAppAwareActivity) getActivity()).getAppName();

    View view = inflater.inflate(ID, container, false);

    mainDialogTitle = getString(R.string.configuring_app,
        getString(Survey.getInstance().getApkDisplayNameResourceId()));

    // restore any state
    if (savedInstanceState != null) {
      if (savedInstanceState.containsKey(INIT_STATE_KEY)) {
        initState = InitializationState.valueOf(savedInstanceState.getString(INIT_STATE_KEY));
      }
      msgManager = AlertNProgessMsgFragmentMger
          .restoreInitMessaging(appName, ALERT_SURVEY_DIALOG_TAG, INIT_SURVEY_PROGRESS_DIALOG_TAG,
              savedInstanceState);
    }

    // if message manager was not created from saved state, create fresh
    if(msgManager == null) {
      msgManager = new AlertNProgessMsgFragmentMger(appName, ALERT_SURVEY_DIALOG_TAG,
          INIT_SURVEY_PROGRESS_DIALOG_TAG);
    }

    return view;
  }

  @Override
  public void onStart() {
    super.onStart();
    Survey.getInstance().possiblyFireDatabaseCallback(getActivity(), this);
  }

  @Override
  public void onResume() {
    super.onResume();

    if (initState == InitializationState.START) {
      WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName()).i(t,
          "onResume -- calling initializeAppName");
      msgManager.createProgressDialog(mainDialogTitle, getString(R.string.please_wait),
          getFragmentManager());
      Survey.getInstance().initializeAppName(((IAppAwareActivity) getActivity()).getAppName(), this);
      initState = InitializationState.IN_PROGRESS;
    } else {

      msgManager.restoreDialog(getFragmentManager(), getId());

      // re-attach to the task for task notifications...
      Survey.getInstance().establishInitializationListener(this);
    }
  }



  @Override
  public void onPause() {
    msgManager.clearDialogsAndRetainCurrentState(getFragmentManager());
    super.onPause();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    msgManager.addStateToSaveStateBundle(outState);
    outState.putString(INIT_STATE_KEY, initState.name());
  }


  @Override
  public void initializationComplete(boolean overallSuccess, ArrayList<String> result) {
    initState = InitializationState.FINISH;
    Survey.getInstance().clearInitializationTask();

    if (overallSuccess && result.isEmpty()) {
      // do not require an OK if everything went well
      if(msgManager != null) {
        msgManager.dismissProgressDialog(getFragmentManager());
      }

      ((IInitResumeActivity) getActivity()).initializationCompleted();
      return;
    }

    StringBuilder b = new StringBuilder();
    for (String k : result) {
      b.append(k);
      b.append("\n\n");
    }

    if(msgManager != null) {
      String revisedTitle = overallSuccess ?
          getString(R.string.initialization_complete) :
          getString(R.string.initialization_failed);
      msgManager.createAlertDialog(revisedTitle, b.toString().trim(), getFragmentManager(),
          getId());
    }
  }

  @Override
  public void initializationProgressUpdate(String displayString) {
    if(msgManager != null && msgManager.displayingProgressDialog()) {
      msgManager.updateProgressDialogMessage(displayString, getFragmentManager());
    }
  }
  /**
   * Called when the user clicks the ok button on an alert dialog
   */
  @Override
  public void okAlertDialog() {
    ((IInitResumeActivity) getActivity()).initializationCompleted();
  }

  @Override
  public void databaseAvailable() {
    if (initState == InitializationState.IN_PROGRESS) {
      Survey.getInstance().initializeAppName(((IAppAwareActivity) getActivity()).getAppName(), this);
    }
  }

  @Override
  public void databaseUnavailable() {
    if(msgManager != null && msgManager.displayingProgressDialog()) {
      msgManager.updateProgressDialogMessage(getString(R.string.database_unavailable), getFragmentManager());
    }
  }

}