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

import org.opendatakit.common.android.activities.IAppAwareActivity;
import org.opendatakit.common.android.provider.FormsProviderAPI;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.fragments.SelectConfirmationDialogFragment.SelectConfirmationDialog;
import org.opendatakit.survey.android.listeners.DeleteFormsListener;
import org.opendatakit.survey.android.utilities.FormInfo;
import org.opendatakit.survey.android.utilities.FormListLoader;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.opendatakit.survey.android.utilities.TableIdFormIdVersionListAdapter;

/**
 * List of form definitions on the device that can be deleted.
 *
 * @author mitchellsundt@gmail.com
 */
public class FormDeleteListFragment extends ListFragment
    implements DeleteFormsListener, SelectConfirmationDialog,
    LoaderManager.LoaderCallbacks<ArrayList<FormInfo>> {

  private static final String t = "FormDeleteListFragment";
  private static final int FORM_DELETE_LIST_LOADER = 0x01;

  public static final int ID = R.layout.form_delete_list;

  private static enum DialogState {
    Confirmation, None
  }

  ;

  // keys for the data being retained

  private static final String DIALOG_STATE = "dialogState";
  private static final String SELECTED = "selected";

  // data to retain across orientation changes

  private DialogState mDialogState = DialogState.None;

  private ArrayList<FormDeleteListFragmentSelection> mSelected = new ArrayList<FormDeleteListFragmentSelection>();

  // data that is not retained

  private Button mDeleteButton;

  private TableIdFormIdVersionListAdapter mAdapter;

  private View view;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    // render total instance view
    mAdapter = new TableIdFormIdVersionListAdapter(getActivity(), R.layout.delete_multiple_choice,
        R.id.text1, R.id.text2, R.id.text3);
    setListAdapter(mAdapter);

    getLoaderManager().initLoader(FORM_DELETE_LIST_LOADER, null, this);
  }

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    view = inflater.inflate(ID, container, false);

    mDeleteButton = (Button) view.findViewById(R.id.delete_button);
    mDeleteButton.setText(getString(R.string.delete_file));
    mDeleteButton.setOnClickListener(new OnClickListener() {
      @Override public void onClick(View v) {
        if (mSelected.size() > 0) {
          createDeleteFormsDialog();
        } else {
          Toast.makeText(getActivity().getApplicationContext(), R.string.noselect_error,
              Toast.LENGTH_SHORT).show();
        }
      }
    });

    if (savedInstanceState != null) {
      // to restore alert dialog.
      if (savedInstanceState.containsKey(DIALOG_STATE)) {
        mDialogState = DialogState.valueOf(savedInstanceState.getString(DIALOG_STATE));
      }

      if (savedInstanceState.containsKey(SELECTED)) {
        FormDeleteListFragmentSelection[] selectedArray = (FormDeleteListFragmentSelection[]) savedInstanceState
            .getParcelableArray(SELECTED);
        for (int i = 0; i < selectedArray.length; i++) {
          mSelected.add(selectedArray[i]);
        }
      }
    }

    TextView tv = (TextView) view.findViewById(R.id.status_text);
    tv.setText(R.string.select_form_to_delete);

    mDeleteButton.setEnabled(!(mSelected.size() == 0));

    return view;
  }

  @Override public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    FormDeleteListFragmentSelection[] selectedArray = new FormDeleteListFragmentSelection[mSelected
        .size()];
    for (int i = 0; i < mSelected.size(); i++) {
      selectedArray[i] = mSelected.get(i);
    }
    outState.putParcelableArray(SELECTED, selectedArray);
    outState.putString(DIALOG_STATE, mDialogState.name());
  }

  @Override public void onResume() {
    super.onResume();

    getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    getListView().setItemsCanFocus(false);

    // if current activity is being reinitialized due to changing
    // orientation restore all checkmarks for ones selected
    ListView ls = getListView();
    for (FormDeleteListFragmentSelection id : mSelected) {
      for (int pos = 0; pos < ls.getCount(); pos++) {
        if (id.formId.equals(ls.getItemIdAtPosition(pos))) {
          ls.setItemChecked(pos, true);
          break;
        }
      }

    }

    FragmentManager mgr = getFragmentManager();
    BackgroundTaskFragment f = (BackgroundTaskFragment) mgr.findFragmentByTag("background");

    f.establishDeleteFormsListener(this);

    TextView tv = (TextView) view.findViewById(R.id.status_text);
    tv.setText(R.string.select_form_to_delete);

    if (mDialogState == DialogState.Confirmation) {
      restoreConfirmationDialog();
    }
  }

  @Override public void onPause() {
    FragmentManager mgr = getFragmentManager();

    // dismiss dialogs...
    SelectConfirmationDialogFragment dialog = (SelectConfirmationDialogFragment) mgr
        .findFragmentByTag("selectConfirmationDialog");
    if (dialog != null) {
      dialog.dismiss();
    }
    super.onPause();
  }

  private void restoreConfirmationDialog() {
    Fragment dialog = getFragmentManager().findFragmentByTag("selectConfirmationDialog");
    StringBuilder b = new StringBuilder();
    b.append(
        getString(R.string.delete_confirm, mSelected.size()));

    FormDeleteListFragmentSelection sel;
    for (int i = 0; i < mSelected.size(); i++) {
      sel = mSelected.get(i);
      b.append(
          getString(R.string.delete_confirm_entry,
              sel.formName, sel.tableId,
              sel.formId, sel.formVersion));
    }

    String alertMsg = b.toString();

    if (dialog != null && ((SelectConfirmationDialogFragment) dialog).getDialog() != null) {
      mDialogState = DialogState.Confirmation;
      ((SelectConfirmationDialogFragment) dialog).getDialog()
          .setTitle(getString(R.string.delete_file));
      ((SelectConfirmationDialogFragment) dialog).setMessage(alertMsg);
      // TODO: may need to set the ok/cancel button text if this is ever
      // reused?
    } else {

      SelectConfirmationDialogFragment f = SelectConfirmationDialogFragment
          .newInstance(getId(), getString(R.string.delete_file), alertMsg,
              getString(R.string.delete_yes), getString(R.string.delete_no),
              getString(R.string.delete_yes_with_options));

      mDialogState = DialogState.Confirmation;
      f.show(getFragmentManager(), "selectConfirmationDialog");
    }
  }

  @Override public void okConfirmationDialog() {
    WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName())
        .i(t, "ok (delete) selected files");
    mDialogState = DialogState.None;
    deleteSelectedForms(false);
  }

  @Override public void okWithOptionsConfirmationDialog() {
    WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName())
        .i(t, "ok (delete) selected files and data");
    mDialogState = DialogState.None;
    deleteSelectedForms(true);
  }

  @Override public void cancelConfirmationDialog() {
    // no-op
    mDialogState = DialogState.None;
    WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName())
        .i(t, "cancel (do not delete) selected files");
  }

  /**
   * Create the form delete dialog
   */
  private void createDeleteFormsDialog() {
    restoreConfirmationDialog();
  }

  /**
   * Deletes the selected files.First from the database then from the file
   * system
   */
  private void deleteSelectedForms(boolean deleteFormAndData) {
    FragmentManager mgr = getFragmentManager();
    BackgroundTaskFragment f = (BackgroundTaskFragment) mgr.findFragmentByTag("background");

    Uri[] selectedFormUris = new Uri[mSelected.size()];
    for (int i = 0; i < mSelected.size(); i++) {
      FormDeleteListFragmentSelection selected = mSelected.get(i);
      selectedFormUris[i] = Uri.withAppendedPath(Uri.withAppendedPath(
              Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI,
                  ((IAppAwareActivity) getActivity()).getAppName()), selected.tableId),
          selected.formId);
    }

    f.deleteSelectedForms(((IAppAwareActivity) getActivity()).getAppName(), this, selectedFormUris,
        deleteFormAndData);
  }

  @Override public void onListItemClick(ListView l, View v, int position, long id) {
    super.onListItemClick(l, v, position, id);

    // get row id from db
    FormInfo info = (FormInfo) mAdapter.getItem(position);

    FormDeleteListFragmentSelection clickedItem = new FormDeleteListFragmentSelection(
        info.tableId, info.formId, info.formDisplayName, info.formVersion);

    if (mSelected.contains(clickedItem))
      mSelected.remove(clickedItem);
    else
      mSelected.add(clickedItem);

    mDeleteButton.setEnabled(!(mSelected.size() == 0));

  }

  @Override public void deleteFormsComplete(int deletedForms, boolean deleteFormData) {
    WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName())
        .i(t, "Delete forms complete");
    if (deletedForms == mSelected.size()) {
      if (deleteFormData) {
        // all form deletes were successful
        Toast.makeText(getActivity(), getString(R.string.file_and_data_deleted_ok, deletedForms),
            Toast.LENGTH_SHORT).show();
      } else {
        // all form and data deletes were successful
        Toast.makeText(getActivity(), getString(R.string.file_deleted_ok, deletedForms),
            Toast.LENGTH_SHORT).show();
      }

    } else {
      // had some failures
      WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName())
          .e(t, "Failed to delete " + (mSelected.size() - deletedForms) + " forms");
      Toast.makeText(getActivity(),
          getString(R.string.file_deleted_error, mSelected.size() - deletedForms, mSelected.size()),
          Toast.LENGTH_LONG).show();
    }
    mSelected.clear();
    getListView().clearChoices(); // doesn't unset the checkboxes
    for (int i = 0; i < getListView().getCount(); ++i) {
      getListView().setItemChecked(i, false);
    }
    mDeleteButton.setEnabled(false);
    // force a re-fetch of the list
    getLoaderManager().restartLoader(FORM_DELETE_LIST_LOADER, null, this);
  }

  @Override public Loader<ArrayList<FormInfo>> onCreateLoader(int id, Bundle args) {
    // This is called when a new Loader needs to be created. This
    // sample only has one Loader, so we don't care about the ID.
    return new FormListLoader(getActivity(), ((IAppAwareActivity) getActivity()).getAppName());
  }

  @Override public void onLoadFinished(Loader<ArrayList<FormInfo>> loader,
      ArrayList<FormInfo> dataset) {
    // Swap the new cursor in. (The framework will take care of closing the
    // old cursor once we return.)
    mAdapter.clear();
    mAdapter.addAll(dataset);
    mAdapter.notifyDataSetChanged();
  }

  @Override public void onLoaderReset(Loader<ArrayList<FormInfo>> loader) {
    // This is called when the last Cursor provided to onLoadFinished()
    // above is about to be closed. We need to make sure we are no
    // longer using it.
    mAdapter.clear();
    mAdapter.notifyDataSetChanged();
  }
}
