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
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.utilities.WebUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

/**
 * Presentation of the dialog to get the authentication credentials for a
 * particular google account user.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class AuthDialogFragment extends DialogFragment {

    public static AuthDialogFragment newInstance(int fragmentId, String title, String message, String url) {
    	AuthDialogFragment frag = new AuthDialogFragment();
        Bundle args = new Bundle();
        args.putInt("fragmentId", fragmentId);
        args.putString("title", title);
        args.putString("message", message);
        args.putString("url", url);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String title = getArguments().getString("title");
        String message = getArguments().getString("message");
        final String url = getArguments().getString("url");

        Integer fragmentId = getArguments().getInt("fragmentId");
        FragmentManager mgr = getFragmentManager();
    	Fragment f = mgr.findFragmentById(fragmentId);

    	setTargetFragment(f, RequestCodes.AUTH_DIALOG.ordinal());

		LayoutInflater factory = LayoutInflater.from(getActivity());
		final View dialogView = factory.inflate(
				R.layout.server_auth_dialog, null);

		// Get the server, username, and password from the settings
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(getActivity().getBaseContext());

		EditText username = (EditText) dialogView
				.findViewById(R.id.username_edit);
		String storedUsername = settings.getString(
				PreferencesActivity.KEY_USERNAME, null);
		username.setText(storedUsername);

		EditText password = (EditText) dialogView
				.findViewById(R.id.password_edit);
		String storedPassword = settings.getString(
				PreferencesActivity.KEY_PASSWORD, null);
		password.setText(storedPassword);

		return new AlertDialog.Builder(getActivity())
			.setTitle(title)
			.setMessage(message)
			.setView(dialogView)
			.setCancelable(false)
			.setPositiveButton(getString(R.string.ok),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						EditText username = (EditText) dialogView
								.findViewById(R.id.username_edit);
						EditText password = (EditText) dialogView
								.findViewById(R.id.password_edit);

						Uri u = Uri.parse(url);

						WebUtils.addCredentials(username.getText()
								.toString(), password.getText().toString(),
								u.getHost());
						// return and trigger resumption of actions...
						getActivity().finish();
					}
				})
			.setNegativeButton(getString(R.string.cancel),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						getActivity().setResult(Activity.RESULT_CANCELED);
						getActivity().finish();
					}
				})
			.create();
    }
}
