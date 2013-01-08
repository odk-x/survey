/*
 * Copyright (C) 2012 University of Washington
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

/**
 * Alert dialog implemented as a fragment for notifying user of a problem.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class AlertDialogFragment extends DialogFragment {

	public interface ConfirmAlertDialog {
		public void okAlertDialog();
	};

    public static AlertDialogFragment newInstance(int fragmentId, String title, String message) {
    	AlertDialogFragment frag = new AlertDialogFragment();
        Bundle args = new Bundle();
        args.putInt("fragmentId", fragmentId);
        args.putString("title", title);
        args.putString("message", message);
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

        final Integer fragmentId = getArguments().getInt("fragmentId");

    	DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE: // ok
                        FragmentManager mgr = getFragmentManager();
                    	Fragment f = mgr.findFragmentById(fragmentId);

        				((ConfirmAlertDialog) f).okAlertDialog();
        				dialog.dismiss();
                        break;
                }
            }
        };

        AlertDialog dlg = new AlertDialog.Builder(getActivity())
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok), quitListener)
                .create();
        dlg.setCanceledOnTouchOutside(false);
        return dlg;
    }
}
