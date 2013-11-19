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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.activities.ODKActivity;
import org.opendatakit.survey.android.fragments.AlertDialogFragment.ConfirmAlertDialog;
import org.opendatakit.survey.android.fragments.ProgressDialogFragment.CancelProgressDialog;
import org.opendatakit.survey.android.listeners.FormDownloaderListener;
import org.opendatakit.survey.android.listeners.FormListDownloaderListener;
import org.opendatakit.survey.android.logic.FormDetails;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.tasks.DownloadFormListTask;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/**
 * Interface to the legacy OpenRosa compliant Form Discovery API (e.g., ODK
 * Aggregate forms listing).
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class FormDownloadListFragment extends ListFragment implements FormListDownloaderListener,
    FormDownloaderListener, ConfirmAlertDialog, CancelProgressDialog {

  private static final String PROGRESS_DIALOG_TAG = "progressDialog";

  private static final String t = "FormDownloadListFragment";

  public static final int ID = R.layout.form_download_list;

  private static enum DialogState {
    ProgressFormList, ProgressForms, Alert, None
  };

  // value fields in available-forms list

  private static final String FORMNAME = "formname";
  private static final String FORMDETAIL_KEY = "formdetailkey";
  private static final String FORMID_DISPLAY = "formiddisplay";

  // keys for the data being retained

  private static final String TOGGLED_KEY = "toggled";
  private static final String DOWNLOAD_ENABLED = "downloadEnabled";
  private static final String BUNDLE_FORM_MAP = "formmap";
  private static final String DIALOG_TITLE = "dialogtitle";
  private static final String DIALOG_MSG = "dialogmsg";
  private static final String DIALOG_STATE = "dialogState";
  private static final String FORMLIST = "formlist";

  // data to retain across orientation changes

  private boolean mToggled = false;
  private boolean mDownloadEnabled = false;
  private HashMap<String, FormDetails> mFormIdsAndDetails;
  private String mAlertTitle;
  private String mAlertMsg;
  private DialogState mDialogState = DialogState.None;
  private ArrayList<HashMap<String, String>> mFormList = new ArrayList<HashMap<String, String>>();

  // data that is not retained

  private Button mDownloadButton;
  private Button mToggleButton;
  private Button mRefreshButton;

  private SimpleAdapter mFormListAdapter;

  private View view;

  private Handler handler = new Handler();
  private ProgressDialogFragment progressDialog = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    String[] data = new String[] { FORMNAME, FORMID_DISPLAY, FORMDETAIL_KEY };
    int[] view = new int[] { R.id.text1, R.id.text2 };

    mFormListAdapter = new SimpleAdapter(getActivity(), mFormList,
        R.layout.two_item_multiple_choice, data, view);
    // // need white background before load
    // getListView().setBackgroundColor(Color.WHITE);
    // getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    // getListView().setItemsCanFocus(false);

    setListAdapter(mFormListAdapter);

    if (mFormList.size() == 0 && mDialogState == DialogState.None) {
      // nothing showing -- so try to get something...
      downloadFormList();
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    view = inflater.inflate(ID, container, false);
    mAlertMsg = getString(R.string.please_wait);

    mDownloadButton = (Button) view.findViewById(R.id.add_button);
    mDownloadButton.setEnabled(mDownloadEnabled);
    mDownloadButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        downloadSelectedFiles();
        mToggled = false;
        clearChoices();
      }
    });

    mToggleButton = (Button) view.findViewById(R.id.toggle_button);
    mToggleButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        // toggle selections of items to all or none
        ListView ls = getListView();
        mToggled = !mToggled;

        for (int pos = 0; pos < ls.getCount(); pos++) {
          ls.setItemChecked(pos, mToggled);
        }
        mDownloadEnabled = !(selectedItemCount() == 0);
        mDownloadButton.setEnabled(mDownloadEnabled);
      }
    });

    mRefreshButton = (Button) view.findViewById(R.id.refresh_button);
    mRefreshButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        mToggled = false;
        downloadFormList();
        getListView().clearChoices();
        clearChoices();
      }
    });

    if (savedInstanceState != null) {
      // If the screen has rotated, the hashmap with the form ids and urls
      // is passed here.
      if (savedInstanceState.containsKey(BUNDLE_FORM_MAP)) {
        mFormIdsAndDetails = (HashMap<String, FormDetails>) savedInstanceState
            .getSerializable(BUNDLE_FORM_MAP);
      }

      // indicating whether or not select-all is on or off.
      if (savedInstanceState.containsKey(TOGGLED_KEY)) {
        mToggled = savedInstanceState.getBoolean(TOGGLED_KEY);
      }

      // how many items we've selected
      // Android should keep track of this, but broken on rotate...
      if (savedInstanceState.containsKey(DOWNLOAD_ENABLED)) {
        mDownloadEnabled = savedInstanceState.getBoolean(DOWNLOAD_ENABLED);
        mDownloadButton.setEnabled(mDownloadEnabled);
      }

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

      if (savedInstanceState.containsKey(FORMLIST)) {
        mFormList = (ArrayList<HashMap<String, String>>) savedInstanceState
            .getSerializable(FORMLIST);
      }
    } else {
      mFormList.clear();
    }

    return view;
  }

  private void clearChoices() {
    getListView().clearChoices();
    mDownloadEnabled = false;
    mDownloadButton.setEnabled(mDownloadEnabled);
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    super.onListItemClick(l, v, position, id);
    mDownloadEnabled = !(selectedItemCount() == 0);
    mDownloadButton.setEnabled(mDownloadEnabled);
  }

  /**
   * Starts the download task and shows the progress dialog.
   */
  private void downloadFormList() {
    ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(
        Context.CONNECTIVITY_SERVICE);
    NetworkInfo ni = connectivityManager.getActiveNetworkInfo();

    if (ni == null || !ni.isConnected()) {
      Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
    } else {

      mFormIdsAndDetails = new HashMap<String, FormDetails>();
      showProgressDialog(DialogState.ProgressFormList);

      BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag(
          "background");
      f.downloadFormList(this);
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(TOGGLED_KEY, mToggled);
    outState.putBoolean(DOWNLOAD_ENABLED, mDownloadEnabled);
    outState.putSerializable(BUNDLE_FORM_MAP, mFormIdsAndDetails);
    outState.putString(DIALOG_TITLE, mAlertTitle);
    outState.putString(DIALOG_MSG, mAlertMsg);
    outState.putString(DIALOG_STATE, mDialogState.name());
    outState.putSerializable(FORMLIST, mFormList);
  }

  /**
   * returns the number of items currently selected in the list.
   *
   * @return
   */
  private int selectedItemCount() {
    int count = 0;
    SparseBooleanArray sba = getListView().getCheckedItemPositions();
    for (int i = 0; i < getListView().getCount(); i++) {
      if (sba.get(i, false)) {
        count++;
      }
    }
    return count;
  }

  /**
   * starts the task to download the selected forms, also shows progress dialog
   */
  @SuppressWarnings("unchecked")
  private void downloadSelectedFiles() {
    int totalCount = 0;
    ArrayList<FormDetails> filesToDownload = new ArrayList<FormDetails>();

    SparseBooleanArray sba = getListView().getCheckedItemPositions();
    for (int i = 0; i < getListView().getCount(); i++) {
      if (sba.get(i, false)) {
        HashMap<String, String> item = (HashMap<String, String>) getListAdapter().getItem(i);
        filesToDownload.add(mFormIdsAndDetails.get(item.get(FORMDETAIL_KEY)));
      }
    }
    totalCount = filesToDownload.size();

    if (totalCount > 0) {
      // show dialog box
      showProgressDialog(DialogState.ProgressForms);
      BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag(
          "background");
      f.downloadForms(((ODKActivity) getActivity()).getAppName(), this,
          filesToDownload.toArray(new FormDetails[filesToDownload.size()]));
    } else {
      Toast.makeText(getActivity(), R.string.noselect_error, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onResume() {

    getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    getListView().setItemsCanFocus(false);

    FragmentManager mgr = getFragmentManager();
    BackgroundTaskFragment f = (BackgroundTaskFragment) mgr.findFragmentByTag("background");

    f.establishFormListDownloaderListener(this);
    f.establishFormDownloaderListener(this);

    super.onResume();

    if (mDialogState == DialogState.ProgressFormList || mDialogState == DialogState.ProgressForms) {
      restoreProgressDialog();
    } else if (mDialogState == DialogState.Alert) {
      restoreAlertDialog();
    }
  }

  @Override
  public void onPause() {
    FragmentManager mgr = getFragmentManager();

    // dismiss dialogs...
    AlertDialogFragment alertDialog = (AlertDialogFragment) mgr.findFragmentByTag("alertDialog");
    if (alertDialog != null) {
      alertDialog.dismiss();
    }
    dismissProgressDialog();
    super.onPause();
  }

  /**
   * Called when the form list has finished downloading. results will either
   * contain a set of <formname, formdetails> tuples, or one tuple of
   * DL.ERROR.MSG and the associated message.
   *
   * @param result
   */
  public void formListDownloadingComplete(HashMap<String, FormDetails> result) {
    try {
      mDialogState = DialogState.None;
      dismissProgressDialog();
    } catch (IllegalArgumentException e) {
      Log.i(t, "Attempting to close a dialog that was not previously opened");
    }

    BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag(
        "background");
    f.clearDownloadFormListTask();

    if (result == null) {
      Log.e(t, "Formlist Downloading returned null.  That shouldn't happen");
      // Just displayes "error occured" to the user, but this should never
      // happen.
      createAlertDialog(getString(R.string.load_remote_form_error),
          getString(R.string.error_occured));
      return;
    }

    if (result.containsKey(DownloadFormListTask.DL_AUTH_REQUIRED)) {
      // need authorization
      showAuthDialog();
    } else if (result.containsKey(DownloadFormListTask.DL_ERROR_MSG)) {
      // Download failed
      String dialogMessage = getString(R.string.list_failed_with_error,
          result.get(DownloadFormListTask.DL_ERROR_MSG).errorStr);
      String dialogTitle = getString(R.string.load_remote_form_error);
      createAlertDialog(dialogTitle, dialogMessage);
    } else {
      // Everything worked. Clear the list and add the results.
      mFormIdsAndDetails = result;

      mFormList.clear();

      ArrayList<String> ids = new ArrayList<String>(mFormIdsAndDetails.keySet());
      for (int i = 0; i < result.size(); i++) {
        String formDetailsKey = ids.get(i);
        FormDetails details = mFormIdsAndDetails.get(formDetailsKey);
        HashMap<String, String> item = new HashMap<String, String>();
        item.put(FORMNAME, details.formName);
        item.put(FORMID_DISPLAY, ((details.formVersion == null) ? "" : (getString(R.string.version)
            + " " + details.formVersion + " "))
            + "ID: " + details.formID);
        item.put(FORMDETAIL_KEY, formDetailsKey);

        // Insert the new form in alphabetical order.
        if (mFormList.size() == 0) {
          mFormList.add(item);
        } else {
          int j;
          for (j = 0; j < mFormList.size(); j++) {
            HashMap<String, String> compareMe = mFormList.get(j);
            String name = compareMe.get(FORMNAME);
            if (name.compareTo(mFormIdsAndDetails.get(ids.get(i)).formName) > 0) {
              break;
            }
          }
          mFormList.add(j, item);
        }
      }
      mFormListAdapter.notifyDataSetChanged();
    }
  }

  private void showAuthDialog() {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
    String server = settings.getString(PreferencesActivity.KEY_SERVER_URL,
        getString(R.string.default_server_url));

    final String url = server
        + settings.getString(PreferencesActivity.KEY_FORMLIST_URL, "/formList");

    AuthDialogFragment f = AuthDialogFragment.newInstance(getId(),
        getString(R.string.server_requires_auth), getString(R.string.server_auth_credentials, url),
        url);

    f.show(getFragmentManager(), "authDialog");
  }

  private void restoreProgressDialog() {
    Fragment alert = getFragmentManager().findFragmentByTag("alertDialog");
    if (alert != null) {
      ((AlertDialogFragment) alert).dismiss();
    }

    if (mDialogState == DialogState.ProgressFormList || mDialogState == DialogState.ProgressForms) {
      Fragment dialog = getFragmentManager().findFragmentByTag(PROGRESS_DIALOG_TAG);

      if (dialog != null && ((ProgressDialogFragment) dialog).getDialog() != null) {
        ((ProgressDialogFragment) dialog).getDialog().setTitle(mAlertTitle);
        ((ProgressDialogFragment) dialog).setMessage(mAlertMsg);
      } else if (progressDialog != null && progressDialog.getDialog() != null) {
        progressDialog.getDialog().setTitle(mAlertTitle);
        progressDialog.setMessage(mAlertMsg);
      } else {
        if ( progressDialog != null ) {
          dismissProgressDialog();
        }
        progressDialog = ProgressDialogFragment.newInstance(getId(), mAlertTitle, mAlertMsg);
        progressDialog.show(getFragmentManager(), PROGRESS_DIALOG_TAG);
      }
    } else {
      mDialogState = DialogState.None;
      dismissProgressDialog();
    }
  }

  private void showProgressDialog(DialogState state) {
    mDialogState = state;
    mAlertTitle = getString(mDialogState == DialogState.ProgressFormList ? R.string.downloading_form_list
        : R.string.downloading_data);
    mAlertMsg = getString(R.string.please_wait);
    restoreProgressDialog();
  }

  private void updateProgressDialogMessage(String message) {
    if (mDialogState == DialogState.ProgressFormList || mDialogState == DialogState.ProgressForms) {
      mAlertTitle = getString(R.string.downloading_data);
      mAlertMsg = message;
      restoreProgressDialog();
    } else {
      mDialogState = DialogState.None;
      dismissProgressDialog();
    }
  }

  private void dismissProgressDialog() {
    final Fragment dialog = getFragmentManager().findFragmentByTag(PROGRESS_DIALOG_TAG);
    if (dialog != null && dialog != progressDialog) {
      // the UI may not yet have resolved the showing of the dialog.
      // use a handler to add the dismiss to the end of the queue.
      handler.post(new Runnable() {
        @Override
        public void run() {
          ((ProgressDialogFragment) dialog).dismiss();
        }
      });
    }
    if (progressDialog != null) {
      final ProgressDialogFragment scopedReference = progressDialog;
      progressDialog = null;
      // the UI may not yet have resolved the showing of the dialog.
      // use a handler to add the dismiss to the end of the queue.
      handler.post(new Runnable() {
        @Override
        public void run() {
          scopedReference.dismiss();
        }
      });
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == RequestCodes.AUTH_DIALOG.ordinal()) {
      if (resultCode == Activity.RESULT_OK) {
        downloadFormList();
      }
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void restoreAlertDialog() {
    mDialogState = DialogState.None;
    dismissProgressDialog();

    Fragment dialog = getFragmentManager().findFragmentByTag("alertDialog");

    if (dialog != null && ((AlertDialogFragment) dialog).getDialog() != null) {
      mDialogState = DialogState.Alert;
      ((AlertDialogFragment) dialog).getDialog().setTitle(mAlertTitle);
      ((AlertDialogFragment) dialog).setMessage(mAlertMsg);

    } else {

      AlertDialogFragment f = AlertDialogFragment.newInstance(getId(), mAlertTitle, mAlertMsg);

      mDialogState = DialogState.Alert;
      f.show(getFragmentManager(), "alertDialog");
    }
  }

  @Override
  public void okAlertDialog() {
    mDialogState = DialogState.None;
  }

  /**
   * Creates an alert dialog with the given tite and message. If shouldExit is
   * set to true, the activity will exit when the user clicks "ok".
   *
   * @param title
   * @param shouldExit
   */
  private void createAlertDialog(String title, String message) {
    mAlertTitle = title;
    mAlertMsg = message;
    restoreAlertDialog();
  }

  @Override
  public void formDownloadProgressUpdate(String currentFile, int progress, int total) {
    mAlertMsg = getString(R.string.fetching_file, currentFile, progress, total);
    updateProgressDialogMessage(mAlertMsg);
  }

  @Override
  public void formsDownloadingComplete(HashMap<String, String> result) {
    try {
       mDialogState = DialogState.None;
       dismissProgressDialog();
    } catch (IllegalArgumentException e) {
      Log.i(t, "Attempting to close a dialog that was not previously opened");
    }

    BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag(
        "background");
    f.clearDownloadFormsTask();

    StringBuilder b = new StringBuilder();
    Set<String> keys = result.keySet();
    for (String k : keys) {
      b.append(k + " - " + result.get(k));
      b.append("\n\n");
    }

    createAlertDialog(getString(R.string.download_forms_result), b.toString().trim());
  }

  @Override
  public void cancelProgressDialog() {

    // notify the task(s) that we want them to be cancelled.
    // they will then report back through the completion
    // callbacks once they are stopped (and the UI will be updated).

    BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag(
        "background");
    if (mDialogState == DialogState.ProgressFormList) {
      f.cancelDownloadFormListTask();
    } else if (mDialogState == DialogState.ProgressForms) {
      f.cancelDownloadFormsTask();
    }
  }

}