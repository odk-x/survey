/*
 * Copyright 2011 Google Inc. All rights reserved.
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

package org.opendatakit.survey.android.activities;

import java.io.IOException;

import org.opendatakit.IntentConsts;
import org.opendatakit.common.android.activities.BaseListActivity;
import org.opendatakit.common.android.fragment.AlertDialogFragment;
import org.opendatakit.common.android.logic.CommonToolProperties;
import org.opendatakit.common.android.logic.PropertiesSingleton;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Provides a popup listing of the accounts on the phone that we can
 * authenticate against.
 *
 * @author cswenson@google.com (Christopher Swenson)
 */
public class AccountList extends BaseListActivity {
  private static final String t = "AccountList";
  
  private static final String KEY_AUTHENTICATING = "authenticating";

  protected AccountManager accountManager;

  private String mAppName;
  
  private boolean mAuthenticating = false;
  final static String authString = "gather";
  boolean shownDialog = false;

  /**
   * Called to initialize the activity the first time it is run.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mAppName = this.getIntent().getStringExtra(IntentConsts.INTENT_KEY_APP_NAME);
    if ( mAppName == null || mAppName.length() == 0 ) {
    	mAppName = "survey";
    }
    
    if ( savedInstanceState != null && savedInstanceState.containsKey(KEY_AUTHENTICATING)) {
      mAuthenticating = savedInstanceState.getBoolean(KEY_AUTHENTICATING);
    }
    
    WebLogger.getLogger(mAppName).i(t, t + ".onCreate appName=" + mAppName);

    setTitle(mAppName + " > " + getString(R.string.google_account));
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(KEY_AUTHENTICATING, mAuthenticating);
  }
  
  @Override
  public String getAppName() {
    return mAppName;
  }
  
  /**
   * Called when the activity is resumed.
   */
  @Override
  public void onResume() {
    super.onResume();
    accountManager = AccountManager.get(getApplicationContext());
    final Account[] accounts = accountManager.getAccountsByType("com.google");
    this.setListAdapter(new ArrayAdapter<Account>(this, R.layout.account_chooser, accounts) {
      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        View row;
        
        PropertiesSingleton props = CommonToolProperties.get(AccountList.this, mAppName);

        if (convertView == null) {
          row = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(
              R.layout.account_chooser, null);
        } else {
          row = convertView;
        }
        TextView vw = (TextView) row.findViewById(android.R.id.text1);
        vw.setTextSize(((Survey) getApplication()).getQuestionFontsize(mAppName));
        String selected = props.getProperty(CommonToolProperties.KEY_ACCOUNT);
        if (accounts[position].name.equals(selected)) {
          vw.setBackgroundColor(Color.LTGRAY);
        } else {
          vw.setBackgroundColor(Color.WHITE);
        }
        vw.setText(getItem(position).name);

        return row;
      }
    });

    PropertiesSingleton props = CommonToolProperties.get(AccountList.this, mAppName);
    String selected = props.getProperty(CommonToolProperties.KEY_ACCOUNT);

    Account selectedAccount = null;
    if ( selected != null && accounts != null ) {
      for ( Account c : accounts ) {
        if ( c.name.equals(selected) ) {
          selectedAccount = c;
          break;
        }
      }
    }

    if ( selectedAccount != null && mAuthenticating ) {
      beginAuthenticating(selectedAccount);
    } else {
      dismissAlertDialog();
    }
  }

  /**
   * Do not know what to return...
   * @return
   */
  private int getId() {
    return 0;
  }
  
  private void beginAuthenticating(Account selectedAccount) {
    
    accountManager.getAuthToken(selectedAccount, authString, null, false, new AuthTokenCallback(), null);

    AlertDialogFragment alertDialog =
        (AlertDialogFragment) getFragmentManager().findFragmentByTag("alertDialog");

    if (alertDialog != null && alertDialog.getDialog() != null) {
      alertDialog.getDialog().setTitle(getString(R.string.waiting_auth_title));
      alertDialog.setMessage(getString(R.string.waiting_auth));
    } else {
      AlertDialogFragment f = AlertDialogFragment.newInstance(getId(), 
          getString(R.string.waiting_auth_title), getString(R.string.waiting_auth));

      f.show(getFragmentManager(), "alertDialog");
    }
    
  }
  
  private void dismissAlertDialog() {
    mAuthenticating = false;

    AlertDialogFragment alertDialog =
        (AlertDialogFragment) getFragmentManager().findFragmentByTag("alertDialog");
    
    if (alertDialog != null) {
      alertDialog.dismiss();
    }
  }
  
  /**
   * When the user clicks an item, authenticate against that account.
   */
  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    Account account = (Account) getListView().getItemAtPosition(position);
    PropertiesSingleton props = CommonToolProperties.get(AccountList.this, mAppName);

    props.removeProperty(CommonToolProperties.KEY_AUTH);
    props.setProperty(CommonToolProperties.KEY_ACCOUNT, account.name);
    props.writeProperties();

    beginAuthenticating(account);
  }
  

  /**
   * Helper class to handle getting the auth token.
   *
   * @author cswenson@google.com (Christopher Swenson)
   */
  private class AuthTokenCallback implements AccountManagerCallback<Bundle> {
    @Override
    public void run(AccountManagerFuture<Bundle> result) {
      Bundle bundle;
      try {
        bundle = result.getResult();
        Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);

        // Check to see if the last intent failed.
        if ((intent != null) && shownDialog) {
          failedAuthToken();
        }
        // We need to call the intent to get the token.
        else if (intent != null) {
          // Use the bundle dialog.
          startActivity(intent);
          shownDialog = true;
        } else {
          gotAuthToken(bundle);
        }
      } catch (OperationCanceledException e) {
        failedAuthToken();
      } catch (AuthenticatorException e) {
        failedAuthToken();
      } catch (IOException e) {
        failedAuthToken();
      }
    }
  }

  /**
   * If we failed to get an auth token.
   */
  protected void failedAuthToken() {
    PropertiesSingleton props = CommonToolProperties.get(this, mAppName);

    props.removeProperty(CommonToolProperties.KEY_ACCOUNT);
    props.removeProperty(CommonToolProperties.KEY_AUTH);
    props.writeProperties();

    dismissAlertDialog();

    finish();
  }

  /**
   * If we got one, store it in shared preferences.
   *
   * @param bundle
   */
  protected void gotAuthToken(Bundle bundle) {
    // Set the authentication token and dismiss the dialog.
    String auth_token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
    String account = bundle.getString(AccountManager.KEY_ACCOUNT_NAME);

    PropertiesSingleton props = CommonToolProperties.get(this, mAppName);

    props.setProperty(CommonToolProperties.KEY_ACCOUNT, account);
    props.setProperty(CommonToolProperties.KEY_AUTH, auth_token);
    props.writeProperties();

    dismissAlertDialog();

    finish();
  }

}
