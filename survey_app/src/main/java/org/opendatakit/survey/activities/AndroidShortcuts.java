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

package org.opendatakit.survey.activities;

import android.annotation.SuppressLint;
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
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import org.opendatakit.activities.BaseActivity;
import org.opendatakit.application.ToolAwareApplication;
import org.opendatakit.consts.IntentConsts;
import org.opendatakit.database.utilities.CursorUtils;
import org.opendatakit.properties.CommonToolProperties;
import org.opendatakit.properties.PropertiesSingleton;
import org.opendatakit.provider.FormsColumns;
import org.opendatakit.provider.FormsProviderAPI;
import org.opendatakit.survey.R;
import org.opendatakit.utilities.LocalizationUtils;
import org.opendatakit.utilities.ODKFileUtils;

import java.io.File;
import java.util.ArrayList;

/**
 * Allows the user to create desktop shortcuts to any form currently avaiable to
 * Survey
 *
 * @author ctsims
 * @author carlhartung (modified for ODK)
 */
public class AndroidShortcuts extends BaseActivity {

  private static final boolean EXIT = true;
  private ArrayList<Choice> choices = new ArrayList<>();

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
      PropertiesSingleton props = CommonToolProperties.get(this, appName);
      Uri uri = Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, app.getName());
      choices.add(new Choice(R.drawable.snotes_app, appIcon, uri, appName, appName));

      Cursor c = null;
      try {
        c = getContentResolver()
            .query(Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName), null, null, null,
                null);

        if (c != null && c.getCount() > 0) {
          c.moveToPosition(-1);
          while (c.moveToNext()) {
            String tableId = CursorUtils
                .getIndexAsString(c, c.getColumnIndex(FormsColumns.TABLE_ID));
            String localizableDisplayName = CursorUtils
                .getIndexAsString(c, c.getColumnIndex(FormsColumns.DISPLAY_NAME));
            String formName = app.getName() + " > " + LocalizationUtils
                .getLocalizedDisplayName(appName, tableId, props.getUserSelectedDefaultLocale(),
                    localizableDisplayName);
            uri = Uri.withAppendedPath(
                Uri.withAppendedPath(Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName),
                    CursorUtils.getIndexAsString(c, c.getColumnIndex(FormsColumns.TABLE_ID))),
                CursorUtils.getIndexAsString(c, c.getColumnIndex(FormsColumns.FORM_ID)));
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

      @NonNull
      @Override
      public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View row;

        if (convertView == null) {
          row = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
              .inflate(R.layout.shortcut_item, null);
        } else {
          row = convertView;
        }
        TextView vw = (TextView) row.findViewById(R.id.shortcut_title);
        ImageView iv = (ImageView) row.findViewById(R.id.shortcut_icon);
        Choice item;
        if ((item = getItem(position)) != null) {
          vw.setText(item.name);
          iv.setImageBitmap(item.icon);
        }

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
    shortcutIntent.putExtra(IntentConsts.INTENT_KEY_APP_NAME, choice.appName);
    shortcutIntent.setData(choice.command);

    Intent intent = new Intent();
    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, choice.name);
    Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this, choice.iconResourceId);
    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

    // Now, return the result to the launcher

    setResult(RESULT_OK, intent);
    finish();
  }

  private void createErrorDialog(String errorMsg, final boolean shouldExit) {
    AlertDialog mAlertDialog = new AlertDialog.Builder(this).create();
    mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
    mAlertDialog.setMessage(errorMsg);
    DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int i) {
        if (i == DialogInterface.BUTTON_POSITIVE && shouldExit) {
          finish();
        }
      }
    };
    mAlertDialog.setCancelable(false);
    mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), errorListener);
    mAlertDialog.show();
  }

  @Override
  public void databaseAvailable() {
  }

  @Override
  public void databaseUnavailable() {
  }

  @Override
  public String getAppName() {
    return getCommonApplication().getToolName();
  }

  private static class Choice {
    /**
     * The display name of the choice in the list
     */
    public final String name;
    /**
     * the app name
     */
    public final String appName;
    /**
     * The resource id for the icon of the choice
     */
    final int iconResourceId;
    /**
     * the actual icon for the choice
     */
    final Bitmap icon;
    /**
     * The uri needed to start survey to a particular form
     */
    final Uri command;

    /**
     * Constructor that just stores its arguments
     *
     * @param iconResourceId The resource id for the icon of the choice
     * @param icon           the actual icon for the choice
     * @param command        The uri needed to start survey to a particular form
     * @param name           The display name of the choice in the list
     * @param appName        the app name
     */
    Choice(int iconResourceId, Bitmap icon, Uri command, String name, String appName) {
      this.iconResourceId = iconResourceId;
      this.icon = icon;
      this.command = command;
      this.name = name;
      this.appName = appName;
    }
  }

}
