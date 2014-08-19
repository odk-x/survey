/*
 * Copyright (C) 2009-2013 University of Washington
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONObject;
import org.opendatakit.common.android.database.DataModelDatabaseHelper;
import org.opendatakit.common.android.database.DataModelDatabaseHelperFactory;
import org.opendatakit.common.android.logic.PropertyManager;
import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.provider.TableDefinitionsColumns;
import org.opendatakit.common.android.utilities.AndroidUtils;
import org.opendatakit.common.android.utilities.AndroidUtils.MacroStringExpander;
import org.opendatakit.common.android.utilities.ODKDatabaseUtils;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.UrlUtils;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.fragments.AboutMenuFragment;
import org.opendatakit.survey.android.fragments.FormChooserListFragment;
import org.opendatakit.survey.android.fragments.FormDeleteListFragment;
import org.opendatakit.survey.android.fragments.FormDownloadListFragment;
import org.opendatakit.survey.android.fragments.InitializationFragment;
import org.opendatakit.survey.android.fragments.InstanceUploaderListFragment;
import org.opendatakit.survey.android.fragments.InstanceUploaderTableChooserListFragment;
import org.opendatakit.survey.android.fragments.WebViewFragment;
import org.opendatakit.survey.android.logic.DynamicPropertiesCallback;
import org.opendatakit.survey.android.logic.FormIdStruct;
import org.opendatakit.survey.android.logic.PropertiesSingleton;
import org.opendatakit.survey.android.preferences.AdminPreferencesActivity;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.provider.DbShimService;
import org.opendatakit.survey.android.provider.FormsProviderAPI;
import org.opendatakit.survey.android.views.ODKWebView;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentManager.BackStackEntry;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Responsible for displaying buttons to launch the major activities. Launches
 * some activities based on returns of others.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class MainMenuActivity extends Activity implements ODKActivity {

  private static final String t = "MainMenuActivity";

  public static enum ScreenList {
    MAIN_SCREEN, FORM_CHOOSER, FORM_DOWNLOADER, FORM_DELETER, WEBKIT, 
    INSTANCE_UPLOADER_TABLE_CHOOSER, INSTANCE_UPLOADER, CUSTOM_VIEW, INITIALIZATION_DIALOG, ABOUT_MENU
  };

  // Extra returned from gp activity
  // TODO: move to Survey???
  public static final String LOCATION_LATITUDE_RESULT = "latitude";
  public static final String LOCATION_LONGITUDE_RESULT = "longitude";
  public static final String LOCATION_ALTITUDE_RESULT = "altitude";
  public static final String LOCATION_ACCURACY_RESULT = "accuracy";

  // tags for retained context
  private static final String PAGE_WAITING_FOR_DATA = "pageWaitingForData";
  private static final String PATH_WAITING_FOR_DATA = "pathWaitingForData";
  private static final String ACTION_WAITING_FOR_DATA = "actionWaitingForData";

  public static final String APP_NAME = "appName";
  private static final String FORM_URI = "formUri";
  private static final String UPLOAD_TABLE_ID = "uploadTableId";
  private static final String INSTANCE_ID = "instanceId";
  private static final String SCREEN_PATH = "screenPath";
  private static final String CONTROLLER_STATE = "controllerState";
  private static final String AUXILLARY_HASH = "auxillaryHash";
  private static final String SESSION_VARIABLES = "sessionVariables";
  private static final String SECTION_STATE_SCREEN_HISTORY = "sectionStateScreenHistory";

  private static final String CURRENT_FRAGMENT = "currentFragment";
  
  /** tables that have conflict rows */
  public static final String CONFLICT_TABLES = "conflictTables";

  // menu options

  private static final int MENU_FILL_FORM = Menu.FIRST;
  private static final int MENU_PULL_FORMS = Menu.FIRST + 1;
  private static final int MENU_CLOUD_FORMS = Menu.FIRST + 2;
  private static final int MENU_MANAGE_FORMS = Menu.FIRST + 3;
  private static final int MENU_PREFERENCES = Menu.FIRST + 4;
  private static final int MENU_ADMIN_PREFERENCES = Menu.FIRST + 5;
  private static final int MENU_EDIT_INSTANCE = Menu.FIRST + 6;
  private static final int MENU_PUSH_FORMS = Menu.FIRST + 7;
  private static final int MENU_ABOUT = Menu.FIRST + 8;

  // activity callback codes
  private static final int HANDLER_ACTIVITY_CODE = 20;
  private static final int INTERNAL_ACTIVITY_CODE = 21;
  private static final int SYNC_ACTIVITY_CODE = 22;
  private static final int CONFLICT_ACTIVITY_CODE = 23;

  // values for external intents to resolve conflicts
  /** Survey's package name as declared in the manifest. */
  public static final String SYNC_PACKAGE_NAME = "org.opendatakit.sync";
  /** The full path to Sync's checkpoint list activity. */
  public static final String SYNC_CHECKPOINT_ACTIVITY_COMPONENT_NAME = "org.opendatakit.conflict.activities.CheckpointResolutionListActivity";
  /** The full path to Sync's conflict list activity. */
  public static final String SYNC_CONFLICT_ACTIVITY_COMPONENT_NAME = "org.opendatakit.conflict.activities.ConflictResolutionListActivity";
  /** The field name for the tableId to resolve conflicts on */
  public static final String SYNC_TABLE_ID_PARAMETER = "tableId";
  
  private static final boolean EXIT = true;

  private static final FrameLayout.LayoutParams COVER_SCREEN_GRAVITY_CENTER = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);

  public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
    private Fragment mFragment;
    private final Activity mActivity;
    private final String mTag;
    private final Class<T> mClass;

    /**
     * Constructor used each time a new tab is created.
     *
     * @param activity
     *          The host Activity, used to instantiate the fragment
     * @param tag
     *          The identifier tag for the fragment
     * @param clz
     *          The fragment's Class, used to instantiate the fragment
     */
    public TabListener(Activity activity, String tag, Class<T> clz) {
      mActivity = activity;
      mTag = tag;
      mClass = clz;
    }

    /* The following are each of the ActionBar.TabListener callbacks */

    public void onTabSelected(Tab tab, FragmentTransaction ft) {
      // Check if the fragment is already initialized
      if (mFragment == null) {
        // If not, instantiate and add it to the activity
        mFragment = Fragment.instantiate(mActivity, mClass.getName());
        ft.add(android.R.id.content, mFragment, mTag);
      } else {
        // If it exists, simply attach it in order to show it
        ft.attach(mFragment);
      }
    }

    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
      if (mFragment != null) {
        // Detach the fragment, because another one is being attached
        ft.detach(mFragment);
      }
    }

    public void onTabReselected(Tab tab, FragmentTransaction ft) {
      // User selected the already selected tab. Usually do nothing.
    }
  }

  private static class ScreenState {
    String screenPath;
    String state;

    ScreenState(String screenPath, String state) {
      this.screenPath = screenPath;
      this.state = state;
    }
  }

  private static class SectionScreenStateHistory implements Parcelable {
    ScreenState currentScreen = new ScreenState(null,null);
    ArrayList<ScreenState> history = new ArrayList<ScreenState>();

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(currentScreen.screenPath);
      dest.writeString(currentScreen.state);

      dest.writeInt(history.size());
      for ( int i = 0 ; i < history.size() ; ++i ) {
        ScreenState screen = history.get(i);
        dest.writeString(screen.screenPath);
        dest.writeString(screen.state);
      }
    }

    public static final Parcelable.Creator<SectionScreenStateHistory> CREATOR
            = new Parcelable.Creator<SectionScreenStateHistory>() {
        public SectionScreenStateHistory createFromParcel(Parcel in) {
          SectionScreenStateHistory cur = new SectionScreenStateHistory();
          String screenPath = in.readString();
          String state = in.readString();
          cur.currentScreen = new ScreenState(screenPath, state);
          int count = in.readInt();
          for ( int i = 0 ; i < count ; ++i ) {
            screenPath = in.readString();
            state = in.readString();
            cur.history.add(new ScreenState(screenPath, state));
          }
          return cur;
        }

        @Override
        public SectionScreenStateHistory[] newArray(int size) {
          SectionScreenStateHistory[] array = new SectionScreenStateHistory[size];
          for ( int i = 0 ; i < size ; ++i ) {
            array[i] = null;
          }
          return array;
        }
    };
  }

  /**
   * Member variables that are saved and restored across orientation changes.
   */

  private ScreenList currentFragment = ScreenList.FORM_CHOOSER;

  private String pageWaitingForData = null;
  private String pathWaitingForData = null;
  private String actionWaitingForData = null;

  private String appName = null;
  private String uploadTableId = null;
  private FormIdStruct currentForm = null; // via FORM_URI (formUri)
  private String instanceId = null;

  private Bundle sessionVariables = new Bundle();
  private ArrayList<SectionScreenStateHistory> sectionStateScreenHistory =
      new ArrayList<SectionScreenStateHistory>();

  private String refId = UUID.randomUUID().toString();
  private String auxillaryHash = null;

  private String frameworkBaseUrl = null;
  private Long frameworkLastModifiedDate = 0L;
  // DO NOT USE THESE -- only used to determine if the current form has changed.
  private String trackingFormPath = null;
  private Long trackingFormLastModifiedDate = 0L;
  
  /** track which tables have conflicts (these need to be resolved before Survey can operate) */
  Bundle mConflictTables = new Bundle();

  /**
   * Member variables that do not need to be preserved across orientation
   * changes, etc.
   */

  // no need to preserve
  private PropertyManager mPropertyManager;

  // no need to preserve
  private AlertDialog mAlertDialog;

  // cached for efficiency only -- no need to preserve
  private Bitmap mDefaultVideoPoster = null;

  // cached for efficiency only -- no need to preserve
  private View mVideoProgressView = null;

  private ServiceConnection mConnection = new ServiceConnection() {

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      ODKWebView wkt = (ODKWebView) findViewById(R.id.webkit_view);
      if ( wkt != null ) {
        wkt.onServiceConnected(name, service);
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      ODKWebView wkt = (ODKWebView) findViewById(R.id.webkit_view);
      if ( wkt != null ) {
        wkt.onServiceDisconnected(name);
      }
    }

  };

  @Override
  protected void onPause() {
//    if (mAlertDialog != null && mAlertDialog.isShowing()) {
//      mAlertDialog.dismiss();
//    }
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    // TODO Auto-generated method stub
    super.onSaveInstanceState(outState);

    if (pageWaitingForData != null) {
      outState.putString(PAGE_WAITING_FOR_DATA, pageWaitingForData);
    }
    if (pathWaitingForData != null) {
      outState.putString(PATH_WAITING_FOR_DATA, pathWaitingForData);
    }
    if (actionWaitingForData != null) {
      outState.putString(ACTION_WAITING_FOR_DATA, actionWaitingForData);
    }
    outState.putString(CURRENT_FRAGMENT, currentFragment.name());

    if (getCurrentForm() != null) {
      outState.putString(FORM_URI, getCurrentForm().formUri.toString());
    }
    if (getInstanceId() != null) {
      outState.putString(INSTANCE_ID, getInstanceId());
    }
    if (getUploadTableId() != null) {
      outState.putString(UPLOAD_TABLE_ID, getUploadTableId());
    }
    if (getScreenPath() != null) {
      outState.putString(SCREEN_PATH, getScreenPath());
    }
    if (getControllerState() != null) {
      outState.putString(CONTROLLER_STATE, getControllerState());
    }
    if (getAuxillaryHash() != null) {
      outState.putString(AUXILLARY_HASH, getAuxillaryHash());
    }
    if (getAppName() != null) {
      outState.putString(APP_NAME, getAppName());
    }
    outState.putBundle(SESSION_VARIABLES, sessionVariables);

    outState.putParcelableArrayList(SECTION_STATE_SCREEN_HISTORY, sectionStateScreenHistory);

    if ( mConflictTables != null && !mConflictTables.isEmpty() ) {
      outState.putBundle(CONFLICT_TABLES, mConflictTables);
    }

  }

  @Override
  public void initializationCompleted(String fragmentToShowNext) {
    // whether we have can cancelled or completed update,
    // remember to not do the expansion files check next time through
    ScreenList newFragment = ScreenList.valueOf(fragmentToShowNext);
    if ( newFragment == ScreenList.WEBKIT && getCurrentForm() == null ) {
      // we were sent off to the initialization dialog to try to
      // discover the form. We need to inquire about the form again
      // and, if we cannot find it, report an error to the user.
      final Uri uriFormsProvider = FormsProviderAPI.CONTENT_URI;
      Uri uri = getIntent().getData();
      Uri formUri = null;

      if (uri.getScheme().equalsIgnoreCase(uriFormsProvider.getScheme()) &&
          uri.getAuthority().equalsIgnoreCase(uriFormsProvider.getAuthority())) {
        List<String> segments = uri.getPathSegments();
        if (segments != null && segments.size() >= 2) {
          String appName = segments.get(0);
          setAppName(appName);
          formUri = Uri.withAppendedPath(
              Uri.withAppendedPath(uriFormsProvider, appName), segments.get(1));
        } else {
          swapToFragmentView(ScreenList.FORM_CHOOSER);
          createErrorDialog(getString(R.string.invalid_uri_expecting_n_segments, uri.toString(), 2), EXIT);
          return;
        }
        // request specifies a specific formUri -- try to open that
        FormIdStruct newForm = FormIdStruct.retrieveFormIdStruct(getContentResolver(), formUri);
        if (newForm == null) {
          // error
          swapToFragmentView(ScreenList.FORM_CHOOSER);
          createErrorDialog(getString(R.string.form_not_found, segments.get(1)), EXIT);
          return;
        } else {
          transitionToFormHelper(uri, newForm);
          swapToFragmentView(newFragment);
        }
      }
    } else {
      Log.i(t, "initializationCompleted: swapping to " + newFragment.name());
      swapToFragmentView(newFragment);
    }
  }

  private void transitionToFormHelper(Uri uri, FormIdStruct newForm) {
    // work through switching to that form
    setAppName(newForm.appName);
    setCurrentForm(newForm);
    clearSectionScreenState();
    String fragment = uri.getFragment();
    if ( fragment != null && fragment.length() != 0 ) {
      // and process the fragment to find the instanceId, screenPath and other kv pairs
      String[] pargs = fragment.split("&");
      boolean first = true;
      StringBuilder b = new StringBuilder();
      int i;
      for (i = 0; i < pargs.length; ++i) {
        String[] keyValue = pargs[i].split("=");
        if ("instanceId".equals(keyValue[0])) {
          if (keyValue.length == 2) {
             setInstanceId(StringEscapeUtils.unescapeHtml4(keyValue[1]));
          }
        } else if ("screenPath".equals(keyValue[0])) {
          if (keyValue.length == 2) {
            setSectionScreenState(StringEscapeUtils.unescapeHtml4(keyValue[1]),null);
          }
        } else if ("refId".equals(keyValue[0]) || "formPath".equals(keyValue[0])) {
          // ignore
        } else {
          if (!first) {
            b.append("&");
          }
          first = false;
          b.append(pargs[i]);
        }
      }
      String aux = b.toString();
      if (aux.length() != 0) {
        setAuxillaryHash(aux);
      }
    } else {
      setInstanceId(null);
      setAuxillaryHash(null);
    }
    currentFragment = ScreenList.WEBKIT;
  }

  @Override
  protected void onStop() {
    super.onStop();
    ODKWebView wkt = (ODKWebView) findViewById(R.id.webkit_view);
    if ( wkt != null ) {
      wkt.beforeDbShimServiceDisconnected();
    }
    unbindService(mConnection);
  }

  @SuppressLint("InlinedApi")
  @Override
  protected void onStart() {
    super.onStart();

    // ensure the DbShimService is started
    Intent intent = new Intent(this, DbShimService.class);
    this.startService(intent);

    this.bindService(intent,  mConnection,
        Context.BIND_AUTO_CREATE | ((Build.VERSION.SDK_INT >= 14) ? Context.BIND_ADJUST_WITH_ACTIVITY : 0));

    FrameLayout shadow = (FrameLayout) findViewById(R.id.shadow_content);
    View frags = findViewById(R.id.main_content);
    ODKWebView wkt = (ODKWebView) findViewById(R.id.webkit_view);

    if (currentFragment == ScreenList.FORM_CHOOSER || currentFragment == ScreenList.FORM_DOWNLOADER
        || currentFragment == ScreenList.FORM_DELETER || currentFragment == ScreenList.INSTANCE_UPLOADER_TABLE_CHOOSER
        || currentFragment == ScreenList.INSTANCE_UPLOADER || currentFragment == ScreenList.INITIALIZATION_DIALOG) {
      shadow.setVisibility(View.GONE);
      shadow.removeAllViews();
      wkt.setVisibility(View.GONE);
      frags.setVisibility(View.VISIBLE);
    } else if (currentFragment == ScreenList.WEBKIT) {
      shadow.setVisibility(View.GONE);
      shadow.removeAllViews();
      wkt.setVisibility(View.VISIBLE);
      wkt.invalidate();
      frags.setVisibility(View.GONE);
    } else if (currentFragment == ScreenList.CUSTOM_VIEW) {
      shadow.setVisibility(View.VISIBLE);
      // shadow.removeAllViews();
      wkt.setVisibility(View.GONE);
      frags.setVisibility(View.GONE);
    }

    FragmentManager mgr = getFragmentManager();
    if (mgr.getBackStackEntryCount() == 0) {
      swapToFragmentView(currentFragment);
    }
  }
  
  public void scanForConflictAllTables() {
    long now = System.currentTimeMillis();
    Log.i(this.getClass().getSimpleName(), "scanForConflictAllTables -- searching for conflicts and checkpoints ");
    
    DataModelDatabaseHelper dbh = DataModelDatabaseHelperFactory.getDbHelper(this, getAppName());
    
    SQLiteDatabase db = dbh.getReadableDatabase();
    Cursor c = null;

    StringBuilder b = new StringBuilder();
    b.append("SELECT ").append(TableDefinitionsColumns.DB_TABLE_NAME).append(", ")
     .append(TableDefinitionsColumns.TABLE_ID).append(" FROM \"")
     .append(DataModelDatabaseHelper.TABLE_DEFS_TABLE_NAME).append("\"");

    Map<String,String> tableMap = new TreeMap<String,String>();
    try {
      c = db.rawQuery(b.toString(), null);
      int idxId = c.getColumnIndex(TableDefinitionsColumns.TABLE_ID);
      int idxName = c.getColumnIndex(TableDefinitionsColumns.DB_TABLE_NAME);
      if ( c.moveToFirst() ) {
        do {
          tableMap.put(ODKDatabaseUtils.getIndexAsString(c, idxId), 
              ODKDatabaseUtils.getIndexAsString(c, idxName));
        } while ( c.moveToNext() );
      }
      c.close();
    } finally {
      if ( c != null && !c.isClosed() ) {
        c.close();
      }
    }
    
    Bundle conflictTables = new Bundle();
    
    for ( Map.Entry<String,String> table : tableMap.entrySet() ) {
      String tableId = table.getKey();
      String dbTableName = table.getValue();
      b.setLength(0);
      b.append("SELECT SUM(case when _conflict_type is not null then 1 else 0 end) as conflicts from \"")
       .append(dbTableName).append("\"");
      
      try {
        c = db.rawQuery(b.toString(), null);
        int idxConflicts = c.getColumnIndex("conflicts");
        c.moveToFirst();
        int conflicts = ODKDatabaseUtils.getIndexAsType(c, Integer.class, idxConflicts);
        c.close();
        
        if ( conflicts != 0 ) {
          conflictTables.putString(tableId, dbTableName);
        }
      } finally {
        if ( c != null && !c.isClosed() ) {
          c.close();
        }
      }
    }
    mConflictTables = conflictTables;
    
    
    long elapsed = System.currentTimeMillis() - now;
    Log.i(this.getClass().getSimpleName(), "scanForConflictAllTables -- full table scan completed: " + Long.toString(elapsed) + " ms");
  }
  
  @Override
  protected void onPostResume() {
    super.onPostResume();
    // Hijack the app here, after all screens have been resumed,
    // to ensure that all checkpoints and conflicts have been
    // resolved. If they haven't, we branch to the resolution
    // activity.
    
    if ( mConflictTables == null || mConflictTables.isEmpty() ) {
      scanForConflictAllTables();
    }
    if ( (mConflictTables != null) && !mConflictTables.isEmpty() ) {
      Iterator<String> iterator = mConflictTables.keySet().iterator();
      String tableId = iterator.next();
      mConflictTables.remove(tableId);

      Intent i;
      i = new Intent();
      i.setComponent(new ComponentName(SYNC_PACKAGE_NAME,
          SYNC_CONFLICT_ACTIVITY_COMPONENT_NAME));
      i.setAction(Intent.ACTION_EDIT);
      i.putExtra(APP_NAME, getAppName());
      i.putExtra(SYNC_TABLE_ID_PARAMETER, tableId);
      try {
        this.startActivityForResult(i, CONFLICT_ACTIVITY_CODE);
      } catch ( ActivityNotFoundException e ) {
        Toast.makeText(this, getString(R.string.activity_not_found, 
            SYNC_CONFLICT_ACTIVITY_COMPONENT_NAME), Toast.LENGTH_LONG).show();
      }
    }
  }

  public void setCurrentForm(FormIdStruct currentForm) {
    WebLogger.getLogger(getAppName()).i(t,
        "setCurrentForm: " + ((currentForm == null) ? "null" : currentForm.formPath));
    this.currentForm = currentForm;
  }

  public FormIdStruct getCurrentForm() {
    return this.currentForm;
  }

  private void setUploadTableId(String uploadTableId) {
    WebLogger.getLogger(getAppName()).i(t,  "setUploadTableId: " + uploadTableId);
    this.uploadTableId = uploadTableId;
  }

  @Override
  public String getUploadTableId() {
    return this.uploadTableId;
  }

  @Override
  public void setInstanceId(String instanceId) {
    WebLogger.getLogger(getAppName()).i(t, "setInstanceId: " + instanceId);
    this.instanceId = instanceId;
  }

  @Override
  public String getInstanceId() {
    return this.instanceId;
  }

  public void setAuxillaryHash(String auxillaryHash) {
    WebLogger.getLogger(getAppName()).i(t, "setAuxillaryHash: " + auxillaryHash);
    this.auxillaryHash = auxillaryHash;
  }

  @Override
  public String getAppName() {
    return this.appName;
  }

  @Override
  public String getActiveUser() {
    FormIdStruct form = getCurrentForm();
    final DynamicPropertiesCallback cb = new DynamicPropertiesCallback(this, getAppName(),
        form == null ? null : getCurrentForm().tableId, getInstanceId());

    String name = mPropertyManager.getSingularProperty(PropertyManager.EMAIL, cb);
    if ( name == null || name.length() == 0) {
      name = mPropertyManager.getSingularProperty(PropertyManager.USERNAME, cb);
      if ( name != null && name.length() != 0 ) {
        name = "username:" + name;
      } else {
        name = null;
      }
    } else {
      name = "mailto:" + name;
    }
    return name;
  }

  @Override
  public String getWebViewContentUri() {
    Uri u = UrlUtils.getWebViewContentUri(this);

    String uriString = u.toString();

    // Ensures that the string always ends with '/'
    if (uriString.charAt(uriString.length() - 1) != '/') {
      return uriString + "/";
    } else {
        return uriString;
    }
  }

  @Override
  public FrameworkFormPathInfo getFrameworkFormPathInfo() {

    // Find the formPath for the default form with the most recent
    // version...
    Cursor c = null;
    String formPath = null;
    Long lastModified = null;

    try {
      //
      // the default form is named 'default' ...
      String selection = FormsColumns.FORM_ID + "=?";
      String[] selectionArgs = { FormsColumns.COMMON_BASE_FORM_ID };
      // use the most recently created of the matches
      // (in case DB corrupted)
      String orderBy = FormsColumns.FORM_VERSION + " DESC";
      c = getContentResolver().query(
          Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName), null, selection,
          selectionArgs, orderBy);

      if (c != null && c.getCount() > 0) {
        // we found a match...
        c.moveToFirst();
        formPath = ODKDatabaseUtils.getIndexAsString(c, c.getColumnIndex(FormsColumns.FORM_PATH));
        lastModified = ODKDatabaseUtils.getIndexAsType(c, Long.class, c.getColumnIndex(FormsColumns.DATE));
      }
    } finally {
       if (c != null && !c.isClosed()) {
          c.close();
       }
    }

    if ( formPath == null ) {
      return null;
    } else {
      return new FrameworkFormPathInfo(formPath, lastModified);
    }
  }

  @Override
  public String getUrlBaseLocation(boolean ifChanged) {
    // Find the formPath for the default form with the most recent
    // version...
    // we need this so that we can load the index.html and main javascript
    // code
    FrameworkFormPathInfo info = getFrameworkFormPathInfo();
    if ( info == null ) {
      return null;
    }
    String formPath = info.relativePath;

    // formPath always begins ../ -- strip that off to get explicit path
    // suffix...
    File mediaFolder = new File(new File(ODKFileUtils.getAppFolder(appName)), formPath.substring(3));

    // File htmlFile = new File(mediaFolder, mPrompt.getAppearanceHint());
    File htmlFile = new File(mediaFolder, "index.html");

    if (!htmlFile.exists()) {
      return null;
    }

    String fullPath = UrlUtils.getAsWebViewUri(this, appName, ODKFileUtils.asUriFragment(appName, htmlFile));

    if (fullPath == null) {
      return null;
    }

    // for some reason, the jqMobile framework wants an empty search string...
    // add this here now...
    fullPath += "?";
    Long frameworkLastModified = info.lastModified;

    boolean changed = false;

    if (ifChanged &&
        frameworkBaseUrl != null &&
        frameworkBaseUrl.equals(fullPath)) {
      // determine if there are any changes in the framework
      // or in the form. If there are, reload. Otherwise,
      // return null.

      changed = (!frameworkLastModified.equals(frameworkLastModifiedDate));
    }

    if ( currentForm == null ) {
      trackingFormPath = null;
      trackingFormLastModifiedDate = 0L;
      changed = true;
    } else if ( trackingFormPath == null ||
      !trackingFormPath.equals(currentForm.formPath)) {
      trackingFormPath = currentForm.formPath;
      trackingFormLastModifiedDate = currentForm.lastDownloadDate.getTime();
    } else {
      changed = changed || (Long.valueOf(trackingFormLastModifiedDate)
          .compareTo(currentForm.lastDownloadDate.getTime()) < 0);
      trackingFormLastModifiedDate = currentForm.lastDownloadDate.getTime();
    }

    frameworkBaseUrl = fullPath;
    frameworkLastModifiedDate = frameworkLastModified;
    return (ifChanged && !changed) ? null : frameworkBaseUrl;
  }

  @Override
  public String getUrlLocationHash() {
    if ( currentForm == null ) {
      // we want framework...
      FrameworkFormPathInfo info = getFrameworkFormPathInfo();
      if ( info == null ) {
        return "";
      }
      String hashUrl = "#formPath=" + StringEscapeUtils.escapeHtml4(info.relativePath)
          + ((instanceId == null) ? "" : "&instanceId=" + StringEscapeUtils.escapeHtml4(instanceId))
          + ((getScreenPath() == null) ? "" : "&screenPath=" + StringEscapeUtils.escapeHtml4(getScreenPath()))
          + ((refId == null) ? "" : "&refId=" + StringEscapeUtils.escapeHtml4(refId))
          + ((auxillaryHash == null) ? "" : "&" + auxillaryHash);
      return hashUrl;
    } else {
      String hashUrl = "#formPath=" + StringEscapeUtils.escapeHtml4((currentForm == null) ? "" : currentForm.formPath)
          + ((instanceId == null) ? "" : "&instanceId=" + StringEscapeUtils.escapeHtml4(instanceId))
          + ((getScreenPath() == null) ? "" : "&screenPath=" + StringEscapeUtils.escapeHtml4(getScreenPath()))
          + ((refId == null) ? "" : "&refId=" + StringEscapeUtils.escapeHtml4(refId))
          + ((auxillaryHash == null) ? "" : "&" + auxillaryHash);

      return hashUrl;
    }
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getRefId() {
    return this.refId;
  }

  public String getAuxillaryHash() {
    return this.auxillaryHash;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // android.os.Debug.waitForDebugger();

    mPropertyManager = new PropertyManager(this);

    // must be at the beginning of any activity that can be called from an
    // external intent
    setAppName("survey");
    Uri uri = getIntent().getData();
    Uri formUri = null;

    if (uri != null) {
      // initialize to the URI, then we will customize further based upon the
      // savedInstanceState...
      final Uri uriFormsProvider = FormsProviderAPI.CONTENT_URI;
      final Uri uriWebView = UrlUtils.getWebViewContentUri(this);
      if (uri.getScheme().equalsIgnoreCase(uriFormsProvider.getScheme()) &&
          uri.getAuthority().equalsIgnoreCase(uriFormsProvider.getAuthority())) {
        List<String> segments = uri.getPathSegments();
        if (segments != null && segments.size() == 1) {
          String appName = segments.get(0);
          setAppName(appName);
        } else if (segments != null && segments.size() >= 2) {
          String appName = segments.get(0);
          setAppName(appName);
          formUri = Uri.withAppendedPath(
              Uri.withAppendedPath(uriFormsProvider, appName), segments.get(1));
        } else {
          assignContentView();
          createErrorDialog(getString(R.string.invalid_uri_expecting_n_segments, uri.toString(), 2), EXIT);
          return;
        }
      } else if ( uri.getScheme().equals(uriWebView.getScheme()) &&
          uri.getAuthority().equals(uriWebView.getAuthority()) &&
          uri.getPort() == uriWebView.getPort()) {
        List<String> segments = uri.getPathSegments();
        if (segments != null && segments.size() == 1) {
          String appName = segments.get(0);
          setAppName(appName);
        } else {
          assignContentView();
          createErrorDialog(getString(R.string.invalid_uri_expecting_one_segment, uri.toString()), EXIT);
          return;
        }

      } else {
        assignContentView();
        createErrorDialog(getString(R.string.unrecognized_uri,
            uri.toString(),
            uriWebView.toString(),
            uriFormsProvider.toString()), EXIT);
        return;
      }
    }

    if (savedInstanceState != null) {
      // if appName is explicitly set, use it...
      setAppName(savedInstanceState.containsKey(APP_NAME) ? savedInstanceState.getString(APP_NAME)
          : getAppName());

      if ( savedInstanceState.containsKey(CONFLICT_TABLES) ) {
        mConflictTables = savedInstanceState.getBundle(CONFLICT_TABLES);
      }
    }


    Log.i(t, "Starting up, creating directories");
    try {
      String appName = getAppName();
      if ( appName != null && appName.length() != 0 ) {
        ODKFileUtils.verifyExternalStorageAvailability();
        ODKFileUtils.assertDirectoryStructure(appName);
      }
    } catch (RuntimeException e) {
      assignContentView();
      createErrorDialog(e.getMessage(), EXIT);
      return;
    }

    if (savedInstanceState != null) {
      // if we are restoring, assume that initialization has already occurred.

      pageWaitingForData = savedInstanceState.containsKey(PAGE_WAITING_FOR_DATA) ? savedInstanceState
          .getString(PAGE_WAITING_FOR_DATA) : null;
      pathWaitingForData = savedInstanceState.containsKey(PATH_WAITING_FOR_DATA) ? savedInstanceState
          .getString(PATH_WAITING_FOR_DATA) : null;
      actionWaitingForData = savedInstanceState.containsKey(ACTION_WAITING_FOR_DATA) ? savedInstanceState
          .getString(ACTION_WAITING_FOR_DATA) : null;

      currentFragment = ScreenList
          .valueOf(savedInstanceState.containsKey(CURRENT_FRAGMENT) ? savedInstanceState
              .getString(CURRENT_FRAGMENT) : currentFragment.name());

      if (savedInstanceState.containsKey(FORM_URI)) {
        FormIdStruct newForm = FormIdStruct.retrieveFormIdStruct(getContentResolver(),
            Uri.parse(savedInstanceState.getString(FORM_URI)));
        if (newForm != null) {
          setAppName(newForm.appName);
          setCurrentForm(newForm);
        }
      }
      setInstanceId(savedInstanceState.containsKey(INSTANCE_ID) ? savedInstanceState
          .getString(INSTANCE_ID) : getInstanceId());
      setUploadTableId(savedInstanceState.containsKey(UPLOAD_TABLE_ID) ? savedInstanceState
          .getString(UPLOAD_TABLE_ID) : getUploadTableId());

      String tmpScreenPath = savedInstanceState.containsKey(SCREEN_PATH) ?
          savedInstanceState.getString(SCREEN_PATH) : getScreenPath();
      String tmpControllerState = savedInstanceState.containsKey(CONTROLLER_STATE) ?
          savedInstanceState.getString(CONTROLLER_STATE) : getControllerState();
      setSectionScreenState(tmpScreenPath, tmpControllerState);

      setAuxillaryHash(savedInstanceState.containsKey(AUXILLARY_HASH) ? savedInstanceState
          .getString(AUXILLARY_HASH) : getAuxillaryHash());

      if (savedInstanceState.containsKey(SESSION_VARIABLES)) {
        sessionVariables = savedInstanceState.getBundle(SESSION_VARIABLES);
      }

      if (savedInstanceState.containsKey(SECTION_STATE_SCREEN_HISTORY)) {
        sectionStateScreenHistory = savedInstanceState.getParcelableArrayList(SECTION_STATE_SCREEN_HISTORY);
      }
    } else if ( formUri != null ) {
      // request specifies a specific formUri -- try to open that
      FormIdStruct newForm = FormIdStruct.retrieveFormIdStruct(getContentResolver(), formUri);
      if (newForm == null) {
        // can't find it -- launch the initialization dialog to hopefully discover it.
        Log.i(t, "onCreate -- calling setRunInitializationTask");
        Survey.getInstance().setRunInitializationTask(getAppName());
        currentFragment = ScreenList.WEBKIT;
      } else {
        transitionToFormHelper(uri, newForm);
      }
    }

    assignContentView();
  }

  /**
   * This creates the WebKit.
   * We need all our values initialized by this point.
   */
  private void assignContentView() {
    setContentView(R.layout.main_screen);

    ActionBar actionBar = getActionBar();
    actionBar.setDisplayOptions(ActionBar.DISPLAY_USE_LOGO);
    actionBar.show();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    int showOption = MenuItem.SHOW_AS_ACTION_IF_ROOM;
    MenuItem item;
    if (currentFragment != ScreenList.WEBKIT) {
      ActionBar actionBar = getActionBar();
      actionBar.setDisplayOptions(ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_TITLE);
      actionBar.show();

      item = menu.add(Menu.NONE, MENU_FILL_FORM, Menu.NONE, getString(R.string.enter_data_button));
      item.setIcon(R.drawable.ic_action_collections_collection).setShowAsAction(showOption);

      // Using a file for this work now
      String get = PropertiesSingleton.getProperty(appName, AdminPreferencesActivity.KEY_GET_BLANK);
      if (get.equalsIgnoreCase("true")) {
        item = menu.add(Menu.NONE, MENU_PULL_FORMS, Menu.NONE, getString(R.string.get_forms));
        item.setIcon(R.drawable.ic_action_av_download).setShowAsAction(showOption);

        item = menu.add(Menu.NONE, MENU_CLOUD_FORMS, Menu.NONE, getString(R.string.get_forms));
        item.setIcon(R.drawable.ic_action_cloud).setShowAsAction(showOption);
      }

      String send = PropertiesSingleton.getProperty(appName, AdminPreferencesActivity.KEY_SEND_FINALIZED);
      if (send.equalsIgnoreCase("true")) {
        item = menu.add(Menu.NONE, MENU_PUSH_FORMS, Menu.NONE, getString(R.string.send_data));
        item.setIcon(R.drawable.ic_action_av_upload).setShowAsAction(showOption);
      }

      String manage = PropertiesSingleton.getProperty(appName, AdminPreferencesActivity.KEY_MANAGE_FORMS);
      if (manage.equalsIgnoreCase("true")) {
        item = menu.add(Menu.NONE, MENU_MANAGE_FORMS, Menu.NONE, getString(R.string.manage_files));
        item.setIcon(R.drawable.trash).setShowAsAction(showOption);
      }

      String settings = PropertiesSingleton.getProperty(appName, AdminPreferencesActivity.KEY_ACCESS_SETTINGS);
      if (settings.equalsIgnoreCase("true")) {
        item = menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE,
            getString(R.string.general_preferences));
        item.setIcon(R.drawable.ic_menu_preferences).setShowAsAction(showOption);
      }
      item = menu.add(Menu.NONE, MENU_ADMIN_PREFERENCES, Menu.NONE,
          getString(R.string.admin_preferences));
      item.setIcon(R.drawable.ic_action_device_access_accounts).setShowAsAction(showOption);

      item = menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, getString(R.string.about));
      item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    } else {
      ActionBar actionBar = getActionBar();
      actionBar.hide();
    }

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == MENU_FILL_FORM) {
      swapToFragmentView(ScreenList.FORM_CHOOSER);
      return true;
    } else if (item.getItemId() == MENU_PULL_FORMS) {
      swapToFragmentView(ScreenList.FORM_DOWNLOADER);
      return true;
    } else if (item.getItemId() == MENU_CLOUD_FORMS) {
      try {
        Intent syncIntent = new Intent();
        syncIntent.setComponent(new ComponentName(
            "org.opendatakit.sync",
            "org.opendatakit.sync.activities.SyncActivity"));
        syncIntent.setAction(Intent.ACTION_DEFAULT);
        Bundle bundle = new Bundle();
        bundle.putString(APP_NAME, appName);
        syncIntent.putExtras(bundle);
        this.startActivityForResult(syncIntent, SYNC_ACTIVITY_CODE);
      } catch (ActivityNotFoundException e) {
        e.printStackTrace();
        Toast.makeText(this, R.string.sync_not_found, Toast.LENGTH_LONG).show();
      }
      return true;
    } else if (item.getItemId() == MENU_MANAGE_FORMS) {
      swapToFragmentView(ScreenList.FORM_DELETER);
      return true;
    } else if (item.getItemId() == MENU_EDIT_INSTANCE) {
      swapToFragmentView(ScreenList.WEBKIT);
      return true;
    } else if (item.getItemId() == MENU_PUSH_FORMS) {
      swapToFragmentView(ScreenList.INSTANCE_UPLOADER_TABLE_CHOOSER);
      return true;
    } else if (item.getItemId() == MENU_PREFERENCES) {
      // PreferenceFragment missing from support library...
      Intent ig = new Intent(this, PreferencesActivity.class);
      // TODO: convert this activity into a preferences fragment
      ig.putExtra(APP_NAME, getAppName());
      startActivity(ig);
      return true;
    } else if (item.getItemId() == MENU_ADMIN_PREFERENCES) {
    	String pw = PropertiesSingleton.getProperty(appName, AdminPreferencesActivity.KEY_ADMIN_PW);
      if (pw == null || "".equalsIgnoreCase(pw)) {
        Intent i = new Intent(getApplicationContext(), AdminPreferencesActivity.class);
        // TODO: convert this activity into a preferences fragment
        i.putExtra(APP_NAME, getAppName());
        startActivity(i);
      } else {
        createPasswordDialog();
      }
      return true;
    } else if (item.getItemId() == MENU_ABOUT) {
      swapToFragmentView(ScreenList.ABOUT_MENU);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void chooseForm(Uri formUri) {

    Intent i = new Intent(Intent.ACTION_EDIT, formUri, this, MainMenuActivity.class);
    startActivityForResult(i, INTERNAL_ACTIVITY_CODE);
  }

  @Override
  public void chooseInstanceUploaderTable(String tableId) {
    boolean success = true;

    // TODO: Verify there are no checkpoint saves on this tableId
    // TODO: Verify there are no checkpoint saves on this tableId
    // TODO: Verify there are no checkpoint saves on this tableId
    // TODO: Verify there are no checkpoint saves on this tableId
    // TODO: Verify there are no checkpoint saves on this tableId
    setUploadTableId(tableId);

    swapToFragmentView(ScreenList.INSTANCE_UPLOADER);
  }

  @Override
  public void onBackPressed() {
    FragmentManager mgr = getFragmentManager();
    int idxLast = mgr.getBackStackEntryCount()-2;
    if (idxLast < 0) {
      Intent result = new Intent();
      // If we are in a WEBKIT, return the instanceId and the savepoint_type...
      if ( this.getInstanceId() != null && currentFragment == ScreenList.WEBKIT ) {
        result.putExtra("instanceId", getInstanceId());
        // in this case, the savepoint_type is null (a checkpoint).
      }
      this.setResult(RESULT_OK, result);
      finish();
    } else {
      BackStackEntry entry = mgr.getBackStackEntryAt(idxLast);
      swapToFragmentView(ScreenList.valueOf(entry.getName()));
    }
  }

  private void createErrorDialog(String errorMsg, final boolean shouldExit) {
    Log.e(t, errorMsg);
    if (mAlertDialog != null) {
      mAlertDialog.dismiss();
      mAlertDialog = null;
    }
    mAlertDialog = new AlertDialog.Builder(this).create();
    mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
    mAlertDialog.setMessage(errorMsg);
    DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int button) {
        switch (button) {
        case DialogInterface.BUTTON_POSITIVE:
          if (shouldExit) {
            Intent i = new Intent();
            setResult(RESULT_CANCELED, i);
            finish();
          }
          break;
        }
      }
    };
    mAlertDialog.setCancelable(false);
    mAlertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.ok), errorListener);
    mAlertDialog.show();
  }

  protected void createPasswordDialog() {
    if (mAlertDialog != null) {
      mAlertDialog.dismiss();
      mAlertDialog = null;
    }
    final AlertDialog passwordDialog = new AlertDialog.Builder(this).create();

    passwordDialog.setTitle(getString(R.string.enter_admin_password));
    final EditText input = new EditText(this);
    input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
    input.setTransformationMethod(PasswordTransformationMethod.getInstance());
    passwordDialog.setView(input, 20, 10, 20, 10);

    passwordDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok),
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
            String value = input.getText().toString();
            String pw = PropertiesSingleton.getProperty(appName, AdminPreferencesActivity.KEY_ADMIN_PW);
            if (pw != null && pw.compareTo(value) == 0) {
              Intent i = new Intent(getApplicationContext(), AdminPreferencesActivity.class);
              // TODO: convert this activity into a preferences fragment
              i.putExtra(APP_NAME, getAppName());
              startActivity(i);
              input.setText("");
              passwordDialog.dismiss();
            } else {
              Toast.makeText(MainMenuActivity.this, getString(R.string.admin_password_incorrect),
                  Toast.LENGTH_SHORT).show();
            }
          }
        });

    passwordDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
        new DialogInterface.OnClickListener() {

          public void onClick(DialogInterface dialog, int which) {
            input.setText("");
            return;
          }
        });

    passwordDialog.getWindow().setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    mAlertDialog = passwordDialog;
    mAlertDialog.show();
  }

  @Override
  public synchronized Bitmap getDefaultVideoPoster() {
    if (mDefaultVideoPoster == null) {
      mDefaultVideoPoster = BitmapFactory.decodeResource(getResources(),
          R.drawable.default_video_poster);
    }
    return mDefaultVideoPoster;
  }

  @Override
  public synchronized View getVideoLoadingProgressView() {
    if (mVideoProgressView == null) {
      LayoutInflater inflater = LayoutInflater.from(this);
      mVideoProgressView = inflater.inflate(R.layout.video_loading_progress, null);
    }
    return mVideoProgressView;
  }

  public void hideWebkitView() {
    // This is a callback thread.
    // We must invalidate the options menu on the UI thread
    this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        WebLogger.getLogger(getAppName()).i(t, "hideWebkitView");
        // In the fragment UI, we want to return to not having any
        // instanceId defined.
//        JQueryODKView webkitView = (JQueryODKView) findViewById(R.id.webkit_view);
//        setInstanceId(null);
//        setSectionScreenState(null,null);
//        setAuxillaryHash(null);
//        webkitView.loadPage();
        levelSafeInvalidateOptionsMenu();
      }
    });
  }

  @Override
  public void swapToCustomView(View customView) {
    FrameLayout shadow = (FrameLayout) findViewById(R.id.shadow_content);
    View frags = findViewById(R.id.main_content);
    View wkt = findViewById(R.id.webkit_view);
    shadow.removeAllViews();
    shadow.addView(customView, COVER_SCREEN_GRAVITY_CENTER);
    frags.setVisibility(View.GONE);
    wkt.setVisibility(View.GONE);
    shadow.setVisibility(View.VISIBLE);
    currentFragment = ScreenList.CUSTOM_VIEW;
  }

  @Override
  public void swapOffCustomView() {
    FrameLayout shadow = (FrameLayout) findViewById(R.id.shadow_content);
    View frags = findViewById(R.id.main_content);
    View wkt = findViewById(R.id.webkit_view);
    shadow.setVisibility(View.GONE);
    shadow.removeAllViews();
    frags.setVisibility(View.GONE);
    wkt.setVisibility(View.VISIBLE);
    wkt.invalidate();
    currentFragment = ScreenList.WEBKIT;
    levelSafeInvalidateOptionsMenu();
  }

  public void swapToFragmentView(ScreenList newFragment) {
    Log.i(t, "swapToFragmentView: " + newFragment.toString());
    FragmentManager mgr = getFragmentManager();
    Fragment f;
    if (newFragment == ScreenList.MAIN_SCREEN) {
      throw new IllegalStateException("unexpected reference to generic main screen");
    } else if (newFragment == ScreenList.CUSTOM_VIEW) {
      Log.w(t, "swapToFragmentView: changing navigation to move to WebKit (was custom view)");
      f = mgr.findFragmentById(WebViewFragment.ID);
      if (f == null) {
        f = new WebViewFragment();
      }
      newFragment = ScreenList.WEBKIT;
    } else if (newFragment == ScreenList.FORM_CHOOSER) {
      f = mgr.findFragmentById(FormChooserListFragment.ID);
      if (f == null) {
        f = new FormChooserListFragment();
      }
    } else if (newFragment == ScreenList.INITIALIZATION_DIALOG) {
      if ( currentFragment == ScreenList.INITIALIZATION_DIALOG ) {
        Log.e(t,"Unexpected: currentFragment == INITIALIZATION_DIALOG");
        return;
      } else {
        f = mgr.findFragmentById(InitializationFragment.ID);
        if (f == null) {
          f = new InitializationFragment();
        }
        ((InitializationFragment) f).setFragmentToShowNext((currentFragment == null) ? ScreenList.FORM_CHOOSER.name() : currentFragment.name());
      }
    } else if (newFragment == ScreenList.FORM_DELETER) {
      f = mgr.findFragmentById(FormDeleteListFragment.ID);
      if (f == null) {
        f = new FormDeleteListFragment();
      }
    } else if (newFragment == ScreenList.FORM_DOWNLOADER) {
      f = mgr.findFragmentById(FormDownloadListFragment.ID);
      if (f == null) {
        f = new FormDownloadListFragment();
      }
    } else if (newFragment == ScreenList.INSTANCE_UPLOADER_TABLE_CHOOSER) {
      f = mgr.findFragmentById(InstanceUploaderTableChooserListFragment.ID);
      if (f == null) {
        f = new InstanceUploaderTableChooserListFragment();
      }
    } else if (newFragment == ScreenList.INSTANCE_UPLOADER) {
      f = mgr.findFragmentById(InstanceUploaderListFragment.ID);
      if (f == null) {
        f = new InstanceUploaderListFragment();
      }
      ((InstanceUploaderListFragment) f).changeUploadTableId();
    } else if (newFragment == ScreenList.WEBKIT) {
      f = mgr.findFragmentById(WebViewFragment.ID);
      if (f == null) {
        f = new WebViewFragment();
      }
    } else if (newFragment == ScreenList.ABOUT_MENU) {
      f = mgr.findFragmentById(AboutMenuFragment.ID);
      if (f == null) {
        f = new AboutMenuFragment();
      }

    } else {
      throw new IllegalStateException("Unrecognized ScreenList type");
    }

    FrameLayout shadow = (FrameLayout) findViewById(R.id.shadow_content);
    View frags = findViewById(R.id.main_content);
    View wkt = findViewById(R.id.webkit_view);
    shadow.setVisibility(View.GONE);
    shadow.removeAllViews();
    if (newFragment == ScreenList.WEBKIT) {
      frags.setVisibility(View.GONE);
      wkt.setVisibility(View.VISIBLE);
      wkt.invalidate();
    } else {
      wkt.setVisibility(View.GONE);
      frags.setVisibility(View.VISIBLE);
    }

    currentFragment = newFragment;
    BackStackEntry entry = null;
    for (int i = 0; i < mgr.getBackStackEntryCount(); ++i) {
      BackStackEntry e = mgr.getBackStackEntryAt(i);
      if (e.getName().equals(currentFragment.name())) {
        entry = e;
        break;
      }
    }
    if (entry != null) {
      // flush backward, including the screen want to go back to
      mgr.popBackStackImmediate(currentFragment.name(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    // add transaction to show the screen we want
    FragmentTransaction trans = mgr.beginTransaction();
    trans.replace(R.id.main_content, f);
    trans.addToBackStack(currentFragment.name());
    trans.commit();
    
    // and see if we should re-initialize...
    if ((currentFragment != ScreenList.INITIALIZATION_DIALOG) &&
        Survey.getInstance().shouldRunInitializationTask(getAppName())) {
      Log.i(t, "swapToFragmentView -- calling clearRunInitializationTask");
      // and immediately clear the should-run flag...
      Survey.getInstance().clearRunInitializationTask(getAppName());
      // OK we should swap to the InitializationFragment view
      swapToFragmentView(ScreenList.INITIALIZATION_DIALOG);
    } else {
      levelSafeInvalidateOptionsMenu();
    }
  }

  private void levelSafeInvalidateOptionsMenu() {
    invalidateOptionsMenu();
  }

  /***********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * Interfaces to Javascript layer (also used in Java).
   */

  private void dumpScreenStateHistory() {
    WebLogger l = WebLogger.getLogger(getAppName());

    l.d(t, "-------------*start* dumpScreenStateHistory--------------------");
    if ( sectionStateScreenHistory.isEmpty() ) {
      l.d(t, "sectionScreenStateHistory EMPTY");
    } else {
      for ( int i = sectionStateScreenHistory.size()-1 ; i >= 0 ; --i ) {
        SectionScreenStateHistory thisSection = sectionStateScreenHistory.get(i);
        l.d(t, "[" + i + "] screenPath: " + thisSection.currentScreen.screenPath );
        l.d(t, "[" + i + "] state:      " + thisSection.currentScreen.state );
        if ( thisSection.history.isEmpty() ) {
          l.d(t, "[" + i + "] history[] EMPTY" );
        } else {
          for ( int j = thisSection.history.size()-1 ; j >= 0 ; --j ) {
            ScreenState ss = thisSection.history.get(j);
            l.d(t, "[" + i + "] history[" + j + "] screenPath: " + ss.screenPath );
            l.d(t, "[" + i + "] history[" + j + "] state:      " + ss.state );
          }
        }
      }
    }
    l.d(t, "------------- *end*  dumpScreenStateHistory--------------------");
  }

  @Override
  public void pushSectionScreenState() {
    if (sectionStateScreenHistory.size() == 0) {
      WebLogger.getLogger(getAppName()).i(t, "pushSectionScreenState: NULL!");
      return;
    }
    SectionScreenStateHistory lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size()-1);
    lastSection.history.add(new ScreenState(lastSection.currentScreen.screenPath, lastSection.currentScreen.state));
  }

  @Override
  public void setSectionScreenState(String screenPath, String state) {
    if ( screenPath == null ) {
      WebLogger.getLogger(getAppName()).e(t, "pushSectionScreenState: NULL currentScreen.screenPath!");
      return;
    } else {
      String[] splits = screenPath.split("/");
      String sectionName = splits[0] + "/";

      SectionScreenStateHistory lastSection;
      if (sectionStateScreenHistory.size() == 0) {
        sectionStateScreenHistory.add(new SectionScreenStateHistory());
        lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size()-1);
        lastSection.currentScreen.screenPath = screenPath;
        lastSection.currentScreen.state = state;
        lastSection.history.clear();
      } else {
        lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size()-1);
        if ( lastSection.currentScreen.screenPath.startsWith(sectionName) ) {
          lastSection.currentScreen.screenPath = screenPath;
          lastSection.currentScreen.state = state;
        } else {
          sectionStateScreenHistory.add(new SectionScreenStateHistory());
          lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size()-1);
          lastSection.currentScreen.screenPath = screenPath;
          lastSection.currentScreen.state = state;
          lastSection.history.clear();
        }
      }
    }
  }

  @Override
  public void clearSectionScreenState() {
    sectionStateScreenHistory.clear();
    sectionStateScreenHistory.add(new SectionScreenStateHistory());
    SectionScreenStateHistory lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size()-1);
    lastSection.currentScreen.screenPath = "initial/0";
    lastSection.currentScreen.state = null;
    lastSection.history.clear();
  }

  @Override
  public String getControllerState() {
    if (sectionStateScreenHistory.size() == 0) {
      WebLogger.getLogger(getAppName()).i(t, "getControllerState: NULL!");
      return null;
    }
    SectionScreenStateHistory lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size()-1);
    return lastSection.currentScreen.state;
  }

  public String getScreenPath() {
    dumpScreenStateHistory();
    if (sectionStateScreenHistory.size() == 0) {
      WebLogger.getLogger(getAppName()).i(t, "getScreenPath: NULL!");
      return null;
    }
    SectionScreenStateHistory lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size()-1);
    return lastSection.currentScreen.screenPath;
  }

  @Override
  public boolean hasScreenHistory() {
    // two or more sections -- there must be history
    if ( sectionStateScreenHistory.size() > 1 ) {
      return true;
    }
    // no sections -- no history
    if ( sectionStateScreenHistory.size() == 0 ) {
      return false;
    }

    SectionScreenStateHistory thisSection = sectionStateScreenHistory.get(0);
    return thisSection.history.size() != 0;
  }

  @Override
  public String popScreenHistory() {
    if ( sectionStateScreenHistory.size() == 0 ) {
      return null;
    }

    SectionScreenStateHistory lastSection;
    lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size()-1);
    if ( lastSection.history.size() != 0 ) {
      ScreenState lastHistory = lastSection.history.remove(lastSection.history.size()-1);
      lastSection.currentScreen.screenPath = lastHistory.screenPath;
      lastSection.currentScreen.state = lastHistory.state;
      return lastSection.currentScreen.screenPath;
    }

    // pop to an enclosing screen
    sectionStateScreenHistory.remove(sectionStateScreenHistory.size()-1);

    if ( sectionStateScreenHistory.size() == 0 ) {
      return null;
    }

    lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size()-1);
    return lastSection.currentScreen.screenPath;
  }

  @Override
  public boolean hasSectionStack() {
    return sectionStateScreenHistory.size() != 0;
  }

  @Override
  public String popSectionStack() {
    if (sectionStateScreenHistory.size() != 0) {
      sectionStateScreenHistory.remove(sectionStateScreenHistory.size()-1);
    }

    if (sectionStateScreenHistory.size() != 0) {
      SectionScreenStateHistory lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size()-1);
      return lastSection.currentScreen.screenPath;
    }

    return null;
  }

  @Override
  public void setSessionVariable( String elementPath, String jsonValue ) {
    sessionVariables.putString(elementPath, jsonValue);
  }

  @Override
  public String getSessionVariable( String elementPath ) {
    return sessionVariables.getString(elementPath);
  }

  @Override
  public void saveAllChangesCompleted(String instanceId, final boolean asComplete) {
    Intent result = new Intent();
    result.putExtra("instanceId", instanceId);
    result.putExtra("savepoint_type", "COMPLETE");
    // TODO: unclear what to put in the result intent...
    this.setResult(RESULT_OK, result);
    finish();
  }

  @Override
  public void saveAllChangesFailed(String instanceId) {
    // should we message anything?
  }

  @Override
  public void ignoreAllChangesCompleted(String instanceId) {
    Intent result = new Intent();
    result.putExtra("instanceId", instanceId);
    result.putExtra("savepoint_type", "INCOMPLETE");
    // TODO: unclear what to put in the result intent...
    this.setResult(RESULT_OK, result);
    finish();
  }

  @Override
  public void ignoreAllChangesFailed(String instanceId) {
    // should we message anything?
  }

  /**
   * Invoked from within Javascript to launch an activity.
   *
   * @param page
   *          -- page containing prompt requesting the action
   * @param path
   *          -- prompt requesting the action
   * @param action
   *          -- the intent to be launched
   * @param valueContentMap
   *          -- parameters to pass to the intent { uri: uriValue, extras: extrasMap }
   */
  @Override
  public String doAction(String page, String path, String action, JSONObject valueContentMap) {

    // android.os.Debug.waitForDebugger();

    if (isWaitingForBinaryData()) {
      Log.w(t, "Already waiting for data -- ignoring");
      return "IGNORE";
    }

    Intent i;
    boolean isSurveyApp = false;
    boolean isTablesApp = false;
    if (action.startsWith("org.opendatakit.survey")) {
      Class<?> clazz;
      try {
        clazz = Class.forName(action);
        i = new Intent(this, clazz);
        isSurveyApp = true;
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        i = new Intent(action);
      }
    } else {
      i = new Intent(action);
    }

    if (action.startsWith("org.opendatakit.tables")) {
      isTablesApp = true;
    }

    try {
      JSONObject valueMap = null;
      if (valueContentMap != null) {
        if ( valueContentMap.has("uri") ) {
          String v = valueContentMap.getString("uri");
          if ( v != null ) {
            Uri uri = Uri.parse(v);
            i.setData(uri);
          }
        }
        if ( valueContentMap.has("extras") ) {
          valueMap = valueContentMap.getJSONObject("extras");
        }
      }

      if (valueMap != null) {
        Bundle b;
        final DynamicPropertiesCallback cb = new DynamicPropertiesCallback(this, getAppName(),
            getCurrentForm().tableId, getInstanceId());

        b = AndroidUtils.convertToBundle(valueMap, new MacroStringExpander() {

          @Override
          public String expandString(String value) {
            if (value != null && value.startsWith("opendatakit-macro(") && value.endsWith(")")) {
              String term = value.substring("opendatakit-macro(".length(), value.length() - 1)
                  .trim();
              String v = mPropertyManager.getSingularProperty(term, cb);
              if (v != null) {
                return v;
              } else {
                Log.e(t, "Unable to process opendatakit-macro: " + value);
                throw new IllegalArgumentException(
                    "Unable to process opendatakit-macro expression: " + value);
              }
            } else {
              return value;
            }
          }
        });

        i.putExtras(b);
      }

      if ( isSurveyApp || isTablesApp ) {
        // ensure that we supply our appName...
        if ( !i.hasExtra(APP_NAME) ) {
          i.putExtra(APP_NAME, getAppName());
          Log.w(t, "doAction into Survey or Tables does not supply an appName. Adding: " + getAppName());
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      return "JSONException: " + ex.toString();
    }

    pageWaitingForData = page;
    pathWaitingForData = path;
    actionWaitingForData = action;

    try {
      startActivityForResult(i, HANDLER_ACTIVITY_CODE);
      return "OK";
    } catch (ActivityNotFoundException ex) {
      ex.printStackTrace();
      Log.e(t, "Unable to launch activity: " + ex.toString());
      return "Application not found";
    }
  }

  /*
   * END - Interfaces to Javascript layer (also used in Java).
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   ***********************************************************************/

  public boolean isWaitingForBinaryData() {
    return actionWaitingForData != null;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    Log.i(t, "onActivityResult");
    ODKWebView view = (ODKWebView) findViewById(R.id.webkit_view);

    if (requestCode == HANDLER_ACTIVITY_CODE) {
      try {
        String jsonObject = null;
        Bundle b = (intent == null) ? null : intent.getExtras();
        JSONObject val = (b == null) ? null : AndroidUtils.convertFromBundle(b);
        jsonObject = "{\"status\":" + Integer.toString(resultCode)
            + ((val == null) ? "" : ", \"result\":" + val.toString()) + "}";
        Log.i(t, "HANDLER_ACTIVITY_CODE: " + jsonObject);

        view.doActionResult(pageWaitingForData, pathWaitingForData, actionWaitingForData, jsonObject );
      } catch (Exception e) {
        view.doActionResult(pageWaitingForData, pathWaitingForData, actionWaitingForData,
            "{ \"status\":0, \"result\":\"" + e.toString() + "\"}");
      } finally {
        pathWaitingForData = null;
        pageWaitingForData = null;
        actionWaitingForData = null;
      }
    } else if ( requestCode == SYNC_ACTIVITY_CODE ) {
      Survey.getInstance().setRunInitializationTask(getAppName());
      this.swapToFragmentView((currentFragment == null) ? ScreenList.FORM_CHOOSER : currentFragment);
    }
  }
}
