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

package org.opendatakit.common.android.fragment;

import org.opendatakit.common.android.logic.CommonToolProperties;
import org.opendatakit.common.android.logic.PropertiesSingleton;
import org.opendatakit.common.android.logic.RequestCodes;
import org.opendatakit.common.android.utilities.ClientConnectionManagerFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import org.opendatakit.survey.android.R;

/**
 * Presentation of the dialog to get the authentication credentials for a
 * particular google account user.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class AuthDialogFragment extends DialogFragment {

  public static AuthDialogFragment newInstance(String appName, int fragmentId, String title, String message,
      String url) {
    AuthDialogFragment frag = new AuthDialogFragment();
    Bundle args = new Bundle();
    args.putString("appName", appName);
    args.putInt("fragmentId", fragmentId);
    args.putString("title", title);
    args.putString("message", message);
    args.putString("url", url);
    frag.setArguments(args);
    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    final String appName = getArguments().getString("appName");
    String title = getArguments().getString("title");
    String message = getArguments().getString("message");
    final String url = getArguments().getString("url");

    Integer fragmentId = getArguments().getInt("fragmentId");
    FragmentManager mgr = getFragmentManager();
    Fragment f = mgr.findFragmentById(fragmentId);

    PropertiesSingleton props = CommonToolProperties.get(getActivity(),appName);
    
    setTargetFragment(f, RequestCodes.AUTH_DIALOG.ordinal());

    LayoutInflater factory = LayoutInflater.from(getActivity());
    final View dialogView = factory.inflate(R.layout.server_auth_dialog, null);

    EditText username = (EditText) dialogView.findViewById(R.id.username_edit);
    String storedUsername = props.getProperty(CommonToolProperties.KEY_USERNAME);
    username.setText(storedUsername);

    EditText password = (EditText) dialogView.findViewById(R.id.password_edit);
    String storedPassword = props.getProperty(CommonToolProperties.KEY_PASSWORD);
    password.setText(storedPassword);

    AlertDialog dlg = new AlertDialog.Builder(getActivity()).setTitle(title).setMessage(message)
        .setView(dialogView).setCancelable(false)
        .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            EditText usernameText = (EditText) dialogView.findViewById(R.id.username_edit);
            EditText passwordText = (EditText) dialogView.findViewById(R.id.password_edit);

            Uri u = Uri.parse(url);

            String username = usernameText.getText().toString();
            String password = passwordText.getText().toString();
            
            PropertiesSingleton props = CommonToolProperties.get(getActivity(),appName);

            if ( username != null && username.length() > 0 ) {
              props.setProperty(CommonToolProperties.KEY_USERNAME, username);
              props.setProperty(CommonToolProperties.KEY_PASSWORD, password);
            } else {
              props.removeProperty(CommonToolProperties.KEY_USERNAME);
              props.removeProperty(CommonToolProperties.KEY_PASSWORD);
            }
            
            ClientConnectionManagerFactory.get(appName).addCredentials(username, password,
                u.getHost());
            // return and trigger resumption of actions...
            getActivity().finish();
          }
        }).setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            getActivity().setResult(Activity.RESULT_CANCELED);
            getActivity().finish();
          }
        }).create();
    dlg.setCanceledOnTouchOutside(false);
    return dlg;
  }
}
