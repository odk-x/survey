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

import android.app.DialogFragment;
import org.opendatakit.activities.IAppAwareActivity;
import org.opendatakit.activities.IInitResumeActivity;
import org.opendatakit.application.ToolAwareApplication;
import org.opendatakit.fragment.AlertDialogFragment;
import org.opendatakit.fragment.AlertDialogFragment.ConfirmAlertDialog;
import org.opendatakit.fragment.ProgressDialogFragment;
import org.opendatakit.fragment.ProgressDialogFragment.CancelProgressDialog;
import org.opendatakit.listener.DatabaseConnectionListener;
import org.opendatakit.listener.InitializationListener;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.survey.R;
import org.opendatakit.survey.application.Survey;

import android.app.Fragment;
import android.app.FragmentManager;
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
    ConfirmAlertDialog, CancelProgressDialog, DatabaseConnectionListener {

  private static final String TAG = "InitializationFragment";

  private static final int ID = R.layout.copy_expansion_files_layout;

  private enum DialogState {
    Init, Progress, Alert, None
  }

  // keys for the data being retained

  private static final String DIALOG_TITLE = "dialogTitle";
  private static final String DIALOG_MSG = "dialogMsg";
  private static final String DIALOG_STATE = "dialogState";

  // data to retain across orientation changes

  private String mAlertTitle;
  private String mAlertMsg;
  private DialogState mDialogState = DialogState.Init;
  private DialogState mPendingDialogState = DialogState.Init;

  // data that is not retained

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(ID, container, false);

    if (savedInstanceState != null) {

      // to restore alert dialog.
      if (savedInstanceState.containsKey(DIALOG_TITLE)) {
        mAlertTitle = savedInstanceState.getString(DIALOG_TITLE);
      }
      if (savedInstanceState.containsKey(DIALOG_MSG)) {
        mAlertMsg = savedInstanceState.getString(DIALOG_MSG);
      }
      if (savedInstanceState.containsKey(DIALOG_STATE)) {
        mDialogState = DialogState.valueOf(savedInstanceState.getString(DIALOG_STATE));
      }
    }

    return view;
  }

  /**
   * Starts the download task and shows the progress dialog.
   */
  private void intializeAppName() {
    // set up the first dialog, but don't show it...
    mAlertTitle = getString(R.string.configuring_app, 
          getString(((ToolAwareApplication) getActivity().getApplication()).getApkDisplayNameResourceId()));
    mAlertMsg = getString(R.string.please_wait);
    mDialogState = DialogState.Progress;
    
    restoreProgressDialog();

    // launch the copy operation
    WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName()).i(TAG,
        "initializeAppName called ");
    Survey.getInstance(getActivity()).initializeAppName(((IAppAwareActivity) getActivity()).getAppName(),
        this);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (mAlertTitle != null) {
      outState.putString(DIALOG_TITLE, mAlertTitle);
    }
    if (mAlertMsg != null) {
      outState.putString(DIALOG_MSG, mAlertMsg);
    }
    outState.putString(DIALOG_STATE, mDialogState.name());
  }

  @Override
  public void onResume() {
    super.onResume();

    if (mDialogState == DialogState.Init) {
      WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName()).i(TAG,
          "onResume -- calling initializeAppName");
      intializeAppName();
    } else {

      if (mDialogState == DialogState.Progress) {
        restoreProgressDialog();
      } else if (mDialogState == DialogState.Alert) {
        restoreAlertDialog();
      }

      // re-attach to the task for task notifications...
      Survey.getInstance(getActivity()).establishInitializationListener(this);
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    Survey.getInstance(getActivity()).possiblyFireDatabaseCallback(getActivity(), this);
  }

  @Override
  public void onPause() {
    FragmentManager mgr = getFragmentManager();

    // dismiss dialogs...
    DialogFragment alertDialog = (DialogFragment) mgr.findFragmentByTag("alertDialog");
    if (alertDialog != null) {
      alertDialog.dismiss();
    }
    DialogFragment progressDialog = (DialogFragment) mgr.findFragmentByTag("progressDialog");
    if (progressDialog != null) {
      progressDialog.dismiss();
    }
    mPendingDialogState = DialogState.None;
    super.onPause();
  }

  @Override
  public void initializationComplete(boolean overallSuccess, ArrayList<String> result) {
    try {
      dismissProgressDialog();
    } catch (IllegalArgumentException e) {
      WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName()).i(TAG,
          "Attempting to close a dialog that was not previously opened");
      WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName()).printStackTrace(e);
    }

    Survey.getInstance(getActivity()).clearInitializationTask();

    if (overallSuccess && result.isEmpty()) {
      // do not require an OK if everything went well
      Fragment progress = getFragmentManager().findFragmentByTag("progressDialog");
      if (progress != null) {
        ((DialogFragment) progress).dismiss();
        mDialogState = DialogState.None;
      }

      ((IInitResumeActivity) getActivity()).initializationCompleted();
      return;
    }

    StringBuilder b = new StringBuilder();
    for (String k : result) {
      b.append(k);
      b.append("\n\n");
    }

    createAlertDialog(overallSuccess ? getString(R.string.initialization_complete)
        : getString(R.string.initialization_failed), b.toString().trim());
  }

  private void restoreProgressDialog() {
    Fragment alert = getFragmentManager().findFragmentByTag("alertDialog");
    if (alert != null) {
      ((DialogFragment) alert).dismiss();
    }

    Fragment dialog = getFragmentManager().findFragmentByTag("progressDialog");

    if (dialog != null && ((DialogFragment) dialog).getDialog() != null) {
      mDialogState = DialogState.Progress;
      ((DialogFragment) dialog).getDialog().setTitle(mAlertTitle);
      ((ProgressDialogFragment) dialog).setMessage(mAlertMsg);

    } else {

      ProgressDialogFragment f = ProgressDialogFragment
          .newInstance(mAlertTitle, mAlertMsg);

      mDialogState = DialogState.Progress;
      if ( mPendingDialogState != mDialogState ) {
        mPendingDialogState = mDialogState;
        f.show(getFragmentManager(), "progressDialog");
      }
    }
  }

  private void updateProgressDialogMessage(String message) {
    if (mDialogState == DialogState.Progress) {
      mAlertTitle = getString(R.string.configuring_app, 
          getString(Survey.getInstance(getActivity()).getApkDisplayNameResourceId()));
      mAlertMsg = message;
      restoreProgressDialog();
    }
  }

  private void dismissProgressDialog() {
    if (mDialogState == DialogState.Progress) {
      mDialogState = DialogState.None;
    }
    FragmentManager mgr = getFragmentManager();
    if ( mgr != null ) {
      Fragment dialog = mgr.findFragmentByTag("progressDialog");
      if (dialog != null) {
        ((DialogFragment) dialog).dismiss();
        mPendingDialogState = DialogState.None;
      }
    } else {
      mPendingDialogState = DialogState.None;
    }
  }

  private void restoreAlertDialog() {
    Fragment progress = getFragmentManager().findFragmentByTag("progressDialog");
    if (progress != null) {
      ((DialogFragment) progress).dismiss();
    }

    Fragment dialog = getFragmentManager().findFragmentByTag("alertDialog");

    if (dialog != null && ((DialogFragment) dialog).getDialog() != null) {
      mDialogState = DialogState.Alert;
      ((DialogFragment) dialog).getDialog().setTitle(mAlertTitle);
      ((AlertDialogFragment) dialog).setMessage(mAlertMsg);

    } else {

      AlertDialogFragment f = AlertDialogFragment.newInstance(getId(), mAlertTitle, mAlertMsg);

      mDialogState = DialogState.Alert;
      if ( mPendingDialogState != mDialogState ) {
        mPendingDialogState = mDialogState;
        f.show(getFragmentManager(), "alertDialog");
      }
    }
  }

  @Override
  public void okAlertDialog() {
    mDialogState = DialogState.None;
    ((IInitResumeActivity) getActivity()).initializationCompleted();
  }

  /**
   * Creates an alert dialog with the given title and message. If shouldExit is
   * set to true, the activity will exit when the user clicks "ok".
   *
   * @param title the title for the dialog
   * @param message the message for the dialog
   */
  private void createAlertDialog(String title, String message) {
    mAlertMsg = message;
    mAlertTitle = title;
    restoreAlertDialog();
  }

  @Override
  public void initializationProgressUpdate(String displayString) {
    updateProgressDialogMessage(displayString);
  }

  @Override
  public void cancelProgressDialog() {
    WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName()).i(TAG,
        "cancelProgressDialog -- calling cancelInitializationTask");
    // signal the task that we want it to be cancelled.
    // but keep the notification path...
    // the task will call back with a copyExpansionFilesComplete()
    // to report status (cancelled).
    Survey.getInstance(getActivity()).cancelInitializationTask();
  }

  @Override
  public void databaseAvailable() {
    if ( mDialogState == DialogState.Progress ) {
      Survey.getInstance(getActivity()).initializeAppName(((IAppAwareActivity) getActivity()).getAppName(),
          this);
    }
  }

  @Override
  public void databaseUnavailable() {
    if ( mDialogState == DialogState.Progress ) {
      updateProgressDialogMessage(getString(R.string.database_unavailable));
    }
  }

}