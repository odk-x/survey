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

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.lang3.StringEscapeUtils;
import org.opendatakit.common.android.provider.InstanceColumns;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.activities.ODKActivity;
import org.opendatakit.survey.android.fragments.AlertDialogFragment.ConfirmAlertDialog;
import org.opendatakit.survey.android.fragments.ProgressDialogFragment.CancelProgressDialog;
import org.opendatakit.survey.android.listeners.InstanceUploaderListener;
import org.opendatakit.survey.android.logic.FormIdStruct;
import org.opendatakit.survey.android.logic.InstanceUploadOutcome;
import org.opendatakit.survey.android.provider.InstanceProviderAPI;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

/**
 * List view of the instances of the current form that are finalized and can be
 * uploaded.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class InstanceUploaderListFragment extends ListFragment implements OnLongClickListener,
    LoaderManager.LoaderCallbacks<Cursor>, InstanceUploaderListener, ConfirmAlertDialog,
    CancelProgressDialog {
  private static final String PROGRESS_DIALOG_TAG = "progressDialog";

  private static final String t = "InstanceUploaderListFragment";

  private static final int INSTANCE_UPLOADER_LIST_LOADER = 0x03;

  public static final int ID = R.layout.instance_uploader_list;

  private static enum DialogState {
    Progress, Alert, None
  };

  // keys for the data being retained

  private static final String BUNDLE_SELECTED_ITEMS_KEY = "selected_items";
  private static final String BUNDLE_TOGGLED_KEY = "toggled";
  private static final String DIALOG_TITLE = "dialogtitle";
  private static final String DIALOG_MSG = "dialogmsg";
  private static final String DIALOG_STATE = "dialogState";
  private static final String SHOW_UNSENT = "showUnsent";
  private static final String URL = "url";
  private static final String FORM_URI = "formUri";

  // data to retain across orientation changes

  private ArrayList<String> mSelected = new ArrayList<String>();
  private boolean mToggled = false;
  private String mAlertTitle;
  private String mAlertMsg;
  private DialogState mDialogState = DialogState.None;
  private boolean mShowUnsent = true;
  private URI mUrl;
  private FormIdStruct currentForm = null; // via uri

  // data that is not retained

  private Button mUploadButton;
  private Button mToggleButton;

  private SimpleCursorAdapter mInstances;

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

    String[] data = new String[] { InstanceColumns.DISPLAY_NAME, InstanceColumns.DISPLAY_SUBTEXT,
        InstanceColumns._ID };
    int[] view = new int[] { R.id.text1, R.id.text2 };

    // render total instance view
    mInstances = new SimpleCursorAdapter(getActivity(), R.layout.upload_multiple_choice, null,
        data, view, 0);
    setListAdapter(mInstances);

    getLoaderManager().initLoader(INSTANCE_UPLOADER_LIST_LOADER, null, this);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    view = inflater.inflate(ID, container, false);

    // set up long click listener

    mUploadButton = (Button) view.findViewById(R.id.upload_button);
    mUploadButton.setOnClickListener(new OnClickListener() {

      @Override
      public void onClick(View arg0) {
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity()
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();

        if (ni == null || !ni.isConnected()) {
          Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        } else {
          // items selected
          uploadSelectedFiles();
          mToggled = false;
          mSelected.clear();
          clearChoices();
        }
      }
    });

    mToggleButton = (Button) view.findViewById(R.id.toggle_button);
    mToggleButton.setLongClickable(true);
    mToggleButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        // toggle selections of items to all or none
        ListView ls = getListView();
        mToggled = !mToggled;
        // remove all items from selected list
        mSelected.clear();
        for (int pos = 0; pos < ls.getCount(); pos++) {
          ls.setItemChecked(pos, mToggled);
          // add all items if mToggled sets to select all
          if (mToggled) {
            Cursor c = (Cursor) ls.getItemAtPosition(pos);
            String uuid = c.getString(c.getColumnIndex(InstanceColumns._ID));
            mSelected.add(uuid);
          }
        }
        mUploadButton.setEnabled(!(mSelected.size() == 0));

      }
    });
    mToggleButton.setOnLongClickListener(this);

    if (savedInstanceState != null) {
      String[] selectedArray = savedInstanceState.getStringArray(BUNDLE_SELECTED_ITEMS_KEY);
      for (int i = 0; i < selectedArray.length; i++)
        mSelected.add(selectedArray[i]);

      // indicating whether or not select-all is on or off.
      if (savedInstanceState.containsKey(BUNDLE_TOGGLED_KEY)) {
        mToggled = savedInstanceState.getBoolean(BUNDLE_TOGGLED_KEY);
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

      if (savedInstanceState.containsKey(SHOW_UNSENT)) {
        mShowUnsent = savedInstanceState.getBoolean(SHOW_UNSENT);
      }

      if (savedInstanceState.containsKey(URL)) {
        mUrl = URI.create(savedInstanceState.getString(URL));
      }

      if (savedInstanceState.containsKey(FORM_URI)) {
        currentForm = FormIdStruct.retrieveFormIdStruct(getActivity().getContentResolver(),
            Uri.parse(savedInstanceState.getString(FORM_URI)));
      }
    }
    mUploadButton.setEnabled(!(mSelected.size() == 0));

    return view;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    String[] selectedArray = new String[mSelected.size()];
    for (int i = 0; i < mSelected.size(); i++)
      selectedArray[i] = mSelected.get(i);
    outState.putStringArray(BUNDLE_SELECTED_ITEMS_KEY, selectedArray);
    outState.putBoolean(BUNDLE_TOGGLED_KEY, mToggled);
    outState.putString(DIALOG_TITLE, mAlertTitle);
    outState.putString(DIALOG_MSG, mAlertMsg);
    outState.putString(DIALOG_STATE, mDialogState.name());
    outState.putBoolean(SHOW_UNSENT, mShowUnsent);
    if (mUrl != null) {
      outState.putString(URL, mUrl.toString());
    }
    if (currentForm != null) {
      outState.putString(FORM_URI, currentForm.formUri.toString());
    }
  }

  private void clearChoices() {
    getListView().clearChoices();
    mUploadButton.setEnabled(false);
  }

  public void changeForm(FormIdStruct form) {
    currentForm = form;
    if (getActivity() != null) {
      // if we are already attached to an activity, restart the loader.
      // otherwise, we will eventually be attached.
      getLoaderManager().restartLoader(INSTANCE_UPLOADER_LIST_LOADER, null, this);
    }
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    super.onListItemClick(l, v, position, id);

    // get row id from db
    Cursor c = (Cursor) getListAdapter().getItem(position);
    String k = c.getString(c.getColumnIndex(InstanceColumns._ID));

    // add/remove from selected list
    if (mSelected.contains(k))
      mSelected.remove(k);
    else
      mSelected.add(k);

    mUploadButton.setEnabled(!(mSelected.size() == 0));

  }

  private void uploadSelectedFiles() {
    if (mSelected.size() > 0 && currentForm != null) {
      // show dialog box
      showProgressDialog();

      BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag(
          "background");
      String[] str = new String[mSelected.size()];
      f.uploadInstances(this, currentForm, mSelected.toArray(str));
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    getListView().setItemsCanFocus(false);

    // if current activity is being reinitialized due to changing
    // orientation restore all checkmarks for ones selected
    ListView ls = getListView();
    for (String id : mSelected) {
      for (int pos = 0; pos < ls.getCount(); pos++) {
        Cursor c = (Cursor) ls.getItemAtPosition(pos);
        String uuid = c.getString(c.getColumnIndex(InstanceColumns._ID));
        if (id.equals(uuid)) {
          ls.setItemChecked(pos, true);
          break;
        }
      }

    }

    FragmentManager mgr = getFragmentManager();
    BackgroundTaskFragment f = (BackgroundTaskFragment) mgr.findFragmentByTag("background");

    f.establishInstanceUploaderListener(this);

    if (mDialogState == DialogState.Progress) {
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

  private void showAuthDialog() {
    AuthDialogFragment f = AuthDialogFragment.newInstance(getId(),
        getString(R.string.server_requires_auth),
        getString(R.string.server_auth_credentials, mUrl.toString()), mUrl.toString());

    f.show(getFragmentManager(), "authDialog");
  }

  private void restoreProgressDialog() {
    Fragment alert = getFragmentManager().findFragmentByTag("alertDialog");
    if (alert != null) {
      ((AlertDialogFragment) alert).dismiss();
    }

    if ( mDialogState == DialogState.Progress ) {
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
    }
  }

  private void showProgressDialog() {
    mDialogState = DialogState.Progress;
    mAlertTitle = getString(R.string.uploading_data);
    mAlertMsg = getString(R.string.please_wait);
    restoreProgressDialog();
  }

  private void updateProgressDialogMessage(String message) {
    if (mDialogState == DialogState.Progress) {
      mAlertTitle = getString(R.string.uploading_data);
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
          try {
            scopedReference.dismiss();
          } catch ( Exception e ) {
            // ignore... we tried!
          }
        }
      });
    }
  }

  private void restoreAlertDialog() {
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
  public void progressUpdate(int progress, int total) {
    String msg = getString(R.string.sending_items, progress, total);
    updateProgressDialogMessage(msg);
  }

  @Override
  public void uploadingComplete(InstanceUploadOutcome outcome) {
    try {
      mDialogState = DialogState.None;
      dismissProgressDialog();
    } catch (Exception e) {
      e.printStackTrace();
      Log.i(t, "Attempting to close a dialog that was not previously opened");
    }

    BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag(
        "background");
    f.clearUploadInstancesTask();

    if (outcome.mAuthRequestingServer == null && currentForm != null) {
      StringBuilder message = new StringBuilder();

      Set<String> keys = outcome.mResults.keySet();
      for (String id : keys) {

        Cursor results = null;
        try {
          Uri uri = Uri.withAppendedPath(InstanceProviderAPI.CONTENT_URI, currentForm.appName + "/"
              + currentForm.formId + "/" + StringEscapeUtils.escapeHtml4(id));
          results = getActivity().getContentResolver().query(uri, null, null, null, null);
          if (results.getCount() == 1 && results.moveToFirst()) {
            String name = results.getString(results.getColumnIndex(InstanceColumns.DISPLAY_NAME));
            message.append(name + " - " + outcome.mResults.get(id) + "\n\n");
          }
        } finally {
          if (results != null) {
            results.close();
          }
        }
      }

      if (message.length() == 0) {
        message.append(getString(R.string.no_forms_uploaded));
      }

      createAlertDialog(getString(R.string.uploading_data), message.toString().trim());
    } else {
      // add our list of completed uploads to "completed"
      // and remove them from our toSend list.
      if (outcome.mResults != null) {
        Set<String> uploadedInstances = outcome.mResults.keySet();
        Iterator<String> itr = uploadedInstances.iterator();

        while (itr.hasNext()) {
          String removeMe = itr.next();
          boolean removed = mSelected.remove(removeMe);
          if (removed) {
            Log.i(t, removeMe + " was already sent, removing from queue before restarting task");
          }
        }
      }

      // Bundle b = new Bundle();
      // b.putString(AUTH_URI, url.toString());
      // showDialog(AUTH_DIALOG, b);
      mUrl = outcome.mAuthRequestingServer;
      showAuthDialog();
    }
  }

  private void showUnsent() {
    mShowUnsent = true;
    getLoaderManager().restartLoader(INSTANCE_UPLOADER_LIST_LOADER, null, this);
  }

  private void showAll() {
    mShowUnsent = false;
    getLoaderManager().restartLoader(INSTANCE_UPLOADER_LIST_LOADER, null, this);
  }

  @Override
  public boolean onLongClick(View v) {
    /**
     * Create a dialog with options to save and exit, save, or quit without
     * saving
     */
    String[] items = { getString(R.string.show_unsent_forms),
        getString(R.string.show_sent_and_unsent_forms) };

    AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
        .setIcon(android.R.drawable.ic_dialog_info).setTitle(getString(R.string.change_view))
        .setNeutralButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int id) {
            dialog.cancel();
          }
        }).setItems(items, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            switch (which) {

            case 0: // show unsent
              showUnsent();
              break;

            case 1: // show all
              showAll();
              break;

            case 2:// do nothing
              break;
            }
          }
        }).create();
    alertDialog.show();
    return true;
  }

  CursorLoader loader = null;

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    // This is called when a new Loader needs to be created. This
    // sample only has one Loader, so we don't care about the ID.
    // First, pick the base URI to use depending on whether we are
    // currently filtering.
    Uri baseUri;
    if (currentForm != null) {
      baseUri = Uri.withAppendedPath(InstanceProviderAPI.CONTENT_URI, currentForm.appName + "/"
          + currentForm.formId);
    } else {
      baseUri = Uri.withAppendedPath(InstanceProviderAPI.CONTENT_URI,
          ((ODKActivity) getActivity()).getAppName());
    }

    String selection;
    String selectionArgs[];
    String sortOrder;

    if (mShowUnsent) {
      // show all unsent or failed-sending records
      selection = InstanceColumns.XML_PUBLISH_STATUS + " IS NULL or "
          + InstanceColumns.XML_PUBLISH_STATUS + "=?";
      selectionArgs = new String[] { InstanceColumns.STATUS_SUBMISSION_FAILED };
      sortOrder = InstanceColumns.DISPLAY_NAME + " ASC";
    } else {
      // show all completed instances (ones ready to be submitted)
      selection = null;
      selectionArgs = null;
      sortOrder = InstanceColumns.DISPLAY_NAME + " ASC";
    }

    // Now create and return a CursorLoader that will take care of
    // creating a Cursor for the data being displayed.
    loader = new CursorLoader(getActivity(), baseUri, null, selection, selectionArgs, sortOrder);
    return loader;
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    // Swap the new cursor in. (The framework will take care of closing the
    // old cursor once we return.)
    mInstances.swapCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    // This is called when the last Cursor provided to onLoadFinished()
    // above is about to be closed. We need to make sure we are no
    // longer using it.
    mInstances.swapCursor(null);
  }

  @Override
  public void cancelProgressDialog() {
    // Notify the task that we want it to stop.
    // The task will call back through its completion
    // callbacks once it has halted, thereby updating the UI.
    BackgroundTaskFragment f = (BackgroundTaskFragment) getFragmentManager().findFragmentByTag(
        "background");
    f.cancelUploadInstancesTask();
  }

}
