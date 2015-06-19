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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;


public class SelectConfirmationDialogFragment extends DialogFragment {

  public static interface SelectConfirmationDialog {
    public void okConfirmationDialog();

    public void cancelConfirmationDialog();

    public void okWithOptionsConfirmationDialog();
  };

  public static SelectConfirmationDialogFragment newInstance(int fragmentId, String title,
    String message, String okButton, String cancelButton, String okWithOptionsButton) {
    SelectConfirmationDialogFragment frag = new SelectConfirmationDialogFragment();
    Bundle args = new Bundle();
    args.putInt("fragmentId", fragmentId);
    args.putString("title", title);
    args.putString("message", message);
    args.putString("okButton", okButton);
    args.putString("cancelButton", cancelButton);
    args.putString("okWithOptionsButton", okWithOptionsButton);
    frag.setArguments(args);
    return frag;
  }

  public void setMessage(String message) {
    ((AlertDialog) this.getDialog()).setMessage(message);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    String title = getArguments().getString("title");
    String message = getArguments().getString("message");
    String okButton = getArguments().getString("okButton");
    String cancelButton = getArguments().getString("cancelButton");
    String okWithOptionsButton = getArguments().getString("okWithOptionsButton");

    final Integer fragmentId = getArguments().getInt("fragmentId");

    DialogInterface.OnClickListener dialogYesNoListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int i) {
        FragmentManager mgr = getFragmentManager();
        Fragment f = mgr.findFragmentById(fragmentId);
        switch (i) {
        case DialogInterface.BUTTON_POSITIVE: // delete metadata
          ((SelectConfirmationDialog) f).okConfirmationDialog();
          dialog.dismiss();
          break;
        case DialogInterface.BUTTON_NEGATIVE: // do nothing
          ((SelectConfirmationDialog) f).cancelConfirmationDialog();
          dialog.dismiss();
          break;
        case DialogInterface.BUTTON_NEUTRAL: // delete data and metadata
          ((SelectConfirmationDialog) f).okWithOptionsConfirmationDialog();
          dialog.dismiss();
          break;
        }
      }
    };

    AlertDialog dlg = new AlertDialog.Builder(getActivity())
        .setIcon(android.R.drawable.ic_dialog_info).setTitle(title).setMessage(message)
        .setCancelable(false).setPositiveButton(okButton, dialogYesNoListener)
        .setNeutralButton(okWithOptionsButton, dialogYesNoListener)
        .setNegativeButton(cancelButton, dialogYesNoListener).create();
    dlg.setCanceledOnTouchOutside(false);
    return dlg;
  }
}
