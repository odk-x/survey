/*
 * Copyright (C) 2016 University of Washington
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import org.opendatakit.survey.R;
import org.opendatakit.survey.activities.IOdkSurveyActivity;

/**
 * Fragment-version of Alert dialog that is displayed when
 * the user hits the device back button and they are in a
 * form editing a row.
 *
 * @author mitchellsundt@gmail.com
 */
public class BackPressWebkitConfirmationDialogFragment extends DialogFragment {

  private Handler handler = new Handler();

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {

    DialogInterface.OnClickListener saveAsIncompleteButtonListener = new DialogInterface
        .OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {


        // user code should dismiss the dialog since this is a cancellation action... dialog
        // .dismiss();
        BackPressWebkitConfirmationDialogFragment.this.saveAllAsIncompleteOutcomeDialog();
      }
    };

    DialogInterface.OnClickListener discardChangesButtonListener = new DialogInterface
        .OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {

        // user code should dismiss the dialog since this is a cancellation action... dialog
        // .dismiss();
        BackPressWebkitConfirmationDialogFragment.this.resolveAllCheckpointsOutcomeDialog();
      }
    };

    DialogInterface.OnClickListener cancelButtonListener = new DialogInterface
        .OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {

        // user code should dismiss the dialog
        // since this is a cancellation action...
        // dialog.dismiss();
        BackPressWebkitConfirmationDialogFragment.this.cancelOutcomeDialog();
      }
    };

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    AlertDialog alertDialog = builder.setTitle(R.string.backpress_title)
        .setMessage(R.string.backpress_warning)
        .setIcon(android.R.drawable.ic_dialog_info)
        .setCancelable(false)
        .setPositiveButton(R.string.backpress_save_all_incomplete_exit, saveAsIncompleteButtonListener)
        .setNeutralButton(R.string.backpress_discard_all_checkpoints_exit, discardChangesButtonListener)
        .setNegativeButton(R.string.backpress_ignore, cancelButtonListener).create();

    alertDialog.setCanceledOnTouchOutside(false);

    return alertDialog;
  }

  /**
   * Called when the user clicks the save button
   */
  public void saveAllAsIncompleteOutcomeDialog() {
    getDialog().dismiss();
    // trigger save AFTER dismiss is complete...
    handler.post(new Runnable() {
      @Override public void run() {
        ((IOdkSurveyActivity) BackPressWebkitConfirmationDialogFragment.this.getActivity())
            .saveAllAsIncompleteThenPopBackStack();
      }
    });
  }

  /**
   * Called when the user clicks the ignore changes button
   */
  public void resolveAllCheckpointsOutcomeDialog() {
    getDialog().dismiss();
    // trigger ignore AFTER dismiss is complete...
    handler.post(new Runnable() {
      @Override public void run() {
        ((IOdkSurveyActivity) BackPressWebkitConfirmationDialogFragment.this.getActivity())
            .resolveAllCheckpointsThenPopBackStack();
      }
    });
  }

  /**
   * Called when the user clicks the cancel button
   */
  public void cancelOutcomeDialog() {
    getDialog().dismiss();
  }
}
