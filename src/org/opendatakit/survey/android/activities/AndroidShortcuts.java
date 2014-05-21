/*
 * Copyright (C) 2011 University of Washington
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

import java.io.File;
import java.util.ArrayList;

import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.provider.FormsProviderAPI;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Allows the user to create desktop shortcuts to any form currently avaiable to
 * Survey
 *
 * @author ctsims
 * @author carlhartung (modified for ODK)
 */
public class AndroidShortcuts extends Activity {

  private AlertDialog mAlertDialog;
  private static final boolean EXIT = true;

  private class Choice {
    public final int iconResourceId;
    public final Bitmap icon;
    public final Uri command;
    public final String name;
    public final String appName;

    public Choice(int iconResourceId, Bitmap icon, Uri command, String name, String appName) {
      this.iconResourceId = iconResourceId;
      this.icon = icon;
      this.command = command;
      this.name = name;
      this.appName = appName;
    }
  }

  private ArrayList<Choice> choices = new ArrayList<Choice>();

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    // verify that the external SD Card is available.
    try {
      ODKFileUtils.verifyExternalStorageAvailability();
    } catch (RuntimeException e) {
      createErrorDialog(e.getMessage(), EXIT);
      return;
    }

    final Intent intent = getIntent();
    final String action = intent.getAction();

    // The Android needs to know what shortcuts are available, generate the
    // list
    if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
      buildMenuList();
    }
  }

  /**
   * Builds a list of shortcuts
   */
  private void buildMenuList() {
    Bitmap appIcon = BitmapFactory.decodeResource(getResources(), R.drawable.snotes_app);
    Bitmap formIcon = BitmapFactory.decodeResource(getResources(), R.drawable.snotes_form);

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Select ODK Shortcut");

    choices.clear();

    File[] directories = ODKFileUtils.getAppFolders();
    for (File app : directories) {
      String appName = app.getName();
      Uri uri = Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, app.getName());
      choices.add(new Choice(R.drawable.snotes_app, appIcon, uri, appName, appName));

      Cursor c = null;
      try {
        c = getContentResolver().query(
            Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName), null, null, null,
            null);

        if (c != null && c.getCount() > 0) {
          c.moveToPosition(-1);
          while (c.moveToNext()) {
            String formName = app.getName() + " > "
                + c.getString(c.getColumnIndex(FormsColumns.DISPLAY_NAME));
            uri = Uri.withAppendedPath(
                Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName),
                c.getString(c.getColumnIndex(FormsColumns.FORM_ID)));
            choices.add(new Choice(R.drawable.snotes_form, formIcon, uri, formName, appName));
          }
        }
      } finally {
        if (c != null) {
          c.close();
        }
      }
    }

    builder.setAdapter(new ArrayAdapter<Choice>(this, R.layout.shortcut_item, choices) {

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        View row;

        if (convertView == null) {
          row = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(
              R.layout.shortcut_item, null);
        } else {
          row = convertView;
        }
        TextView vw = (TextView) row.findViewById(R.id.shortcut_title);
        vw.setTextSize(Survey.getQuestionFontsize(getItem(position).appName));
        vw.setText(getItem(position).name);

        ImageView iv = (ImageView) row.findViewById(R.id.shortcut_icon);
        iv.setImageBitmap(getItem(position).icon);

        return row;
      }
    }, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Choice choice = choices.get(which);
        returnShortcut(choice);
      }
    });

    builder.setOnCancelListener(new OnCancelListener() {
      public void onCancel(DialogInterface dialog) {
        AndroidShortcuts sc = AndroidShortcuts.this;
        sc.setResult(RESULT_CANCELED);
        sc.finish();
        return;
      }
    });

    AlertDialog alert = builder.create();
    alert.show();
  }

  /**
   * Returns the results to the calling intent.
   */
  private void returnShortcut(Choice choice) {
    Intent shortcutIntent = new Intent(Intent.ACTION_VIEW);
    shortcutIntent.putExtra(MainMenuActivity.APP_NAME, choice.appName);
    shortcutIntent.setData(choice.command);

    Intent intent = new Intent();
    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, choice.name);
    Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this, choice.iconResourceId);
    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

    // Now, return the result to the launcher

    setResult(RESULT_OK, intent);
    finish();
    return;
  }

  private void createErrorDialog(String errorMsg, final boolean shouldExit) {
    mAlertDialog = new AlertDialog.Builder(this).create();
    mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
    mAlertDialog.setMessage(errorMsg);
    DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int i) {
        switch (i) {
        case DialogInterface.BUTTON_POSITIVE:
          if (shouldExit) {
            finish();
          }
          break;
        }
      }
    };
    mAlertDialog.setCancelable(false);
    mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), errorListener);
    mAlertDialog.show();
  }

}
