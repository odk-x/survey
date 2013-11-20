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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONObject;
import org.opendatakit.common.android.logic.PropertyManager;
import org.opendatakit.common.android.provider.FileProvider;
import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.utilities.AndroidUtils;
import org.opendatakit.common.android.utilities.AndroidUtils.MacroStringExpander;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.fragments.CopyExpansionFilesFragment;
import org.opendatakit.survey.android.fragments.FormChooserListFragment;
import org.opendatakit.survey.android.fragments.FormDeleteListFragment;
import org.opendatakit.survey.android.fragments.FormDownloadListFragment;
import org.opendatakit.survey.android.fragments.InstanceUploaderFormChooserListFragment;
import org.opendatakit.survey.android.fragments.InstanceUploaderListFragment;
import org.opendatakit.survey.android.fragments.WebViewFragment;
import org.opendatakit.survey.android.logic.DynamicPropertiesCallback;
import org.opendatakit.survey.android.logic.FormIdStruct;
import org.opendatakit.survey.android.preferences.AdminPreferencesActivity;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.provider.FormsProviderAPI;
import org.opendatakit.survey.android.views.JQueryODKView;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.BackStackEntry;
import android.support.v4.app.FragmentTransaction;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

/**
 * Responsible for displaying buttons to launch the major activities. Launches
 * some activities based on returns of others.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class MainMenuActivity extends SherlockFragmentActivity implements ODKActivity {

  private static final String t = "MainMenuActivity";

  public static enum ScreenList {
    MAIN_SCREEN, FORM_CHOOSER, FORM_DOWNLOADER, FORM_DELETER, WEBKIT, INSTANCE_UPLOADER_FORM_CHOOSER, INSTANCE_UPLOADER, CUSTOM_VIEW, COPY_EXPANSION_FILES
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

  private static final String APP_NAME = "appName";
  private static final String FORM_URI = "formUri";
  private static final String INSTANCE_ID = "instanceId";
  private static final String SCREEN_PATH = "screenPath";
  private static final String CONTROLLER_STATE = "controllerState";
  private static final String AUXILLARY_HASH = "auxillaryHash";
  private static final String SESSION_VARIABLES = "sessionVariables";
  private static final String SECTION_STATE_SCREEN_HISTORY = "sectionStateScreenHistory";

  private static final String CURRENT_FRAGMENT = "currentFragment";
  private static final String PROCESS_APK_EXPANSION_FILES = "processAPKExpansionFiles";

  // menu options

  private static final int MENU_FILL_FORM = Menu.FIRST;
  private static final int MENU_PULL_FORMS = Menu.FIRST + 1;
  private static final int MENU_MANAGE_FORMS = Menu.FIRST + 2;
  private static final int MENU_PREFERENCES = Menu.FIRST + 3;
  private static final int MENU_ADMIN_PREFERENCES = Menu.FIRST + 4;
  private static final int MENU_EDIT_INSTANCE = Menu.FIRST + 5;
  private static final int MENU_PUSH_FORMS = Menu.FIRST + 6;

  // activity callback codes
  private static final int HANDLER_ACTIVITY_CODE = 20;
  private static final int INTERNAL_ACTIVITY_CODE = 21;

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

  private boolean mProcessAPKExpansionFiles = false;

  private String pageWaitingForData = null;
  private String pathWaitingForData = null;
  private String actionWaitingForData = null;

  private String appName = null;
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

  /**
   * Member variables that do not need to be preserved across orientation
   * changes, etc.
   */

  // no need to preserve
  private PropertyManager mPropertyManager;

  // no need to preserve
  private AlertDialog mAlertDialog;

  // cached for efficiency only -- no need to preserve
  private SharedPreferences mAdminPreferences;

  // cached for efficiency only -- no need to preserve
  private Bitmap mDefaultVideoPoster = null;

  // cached for efficiency only -- no need to preserve
  private View mVideoProgressView = null;

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
    outState.putBoolean(PROCESS_APK_EXPANSION_FILES, mProcessAPKExpansionFiles);

    if (getCurrentForm() != null) {
      outState.putString(FORM_URI, getCurrentForm().formUri.toString());
    }
    if (getInstanceId() != null) {
      outState.putString(INSTANCE_ID, getInstanceId());
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
  }

  @Override
  public void expansionFilesCopied(String fragmentToShowNext) {
    // whether we have can cancelled or completed update,
    // remember to not do the expansion files check next time through
    mProcessAPKExpansionFiles = false;
    currentFragment = ScreenList.valueOf(fragmentToShowNext);
    swapToFragmentView(currentFragment);
  }

  @Override
  protected void onStart() {
    super.onStart();

    FrameLayout shadow = (FrameLayout) findViewById(R.id.shadow_content);
    View frags = findViewById(R.id.main_content);
    JQueryODKView wkt = (JQueryODKView) findViewById(R.id.webkit_view);

    if (currentFragment == ScreenList.FORM_CHOOSER || currentFragment == ScreenList.FORM_DOWNLOADER
        || currentFragment == ScreenList.FORM_DELETER || currentFragment == ScreenList.INSTANCE_UPLOADER_FORM_CHOOSER
        || currentFragment == ScreenList.INSTANCE_UPLOADER || currentFragment == ScreenList.COPY_EXPANSION_FILES) {
      shadow.setVisibility(View.GONE);
      shadow.removeAllViews();
      wkt.setVisibility(View.GONE);
      frags.setVisibility(View.VISIBLE);
    } else if (currentFragment == ScreenList.WEBKIT) {
      shadow.setVisibility(View.GONE);
      shadow.removeAllViews();
      wkt.setVisibility(View.VISIBLE);
      wkt.requestPageFocus();
      frags.setVisibility(View.GONE);
    } else if (currentFragment == ScreenList.CUSTOM_VIEW) {
      shadow.setVisibility(View.VISIBLE);
      // shadow.removeAllViews();
      wkt.setVisibility(View.GONE);
      frags.setVisibility(View.GONE);
    }

    FragmentManager mgr = this.getSupportFragmentManager();
    if (mgr.getBackStackEntryCount() == 0) {
      swapToFragmentView(currentFragment);
      // we are not recovering...
      if ((currentFragment != ScreenList.COPY_EXPANSION_FILES) && mProcessAPKExpansionFiles) {
        // no form files -- see if we can explode an APK Expansion file
        ArrayList<Map<String, Object>> files = Survey.getInstance().expansionFiles();
        if (files != null || Survey.debugAPKExpansionFile() != null) {
          // double-check that we have no forms...
          try {
            mProcessAPKExpansionFiles = !Survey.createODKDirs(getAppName());
          } catch (RuntimeException e) {
            createErrorDialog(e.getMessage(), EXIT);
            return;
          }
          if (mProcessAPKExpansionFiles) {
            // OK we should swap to the CopyExpansionFiles view
            swapToFragmentView(ScreenList.COPY_EXPANSION_FILES);
          }
        }
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
  public String getContentProviderUri() {
    Uri u = FileProvider.getContentUri(this);
    return u.toString() + "/";
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
        formPath = c.getString(c.getColumnIndex(FormsColumns.FORM_PATH));
        lastModified = c.getLong(c.getColumnIndex(FormsColumns.DATE));
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

    String fullPath = FileProvider.getAsUri(this, appName, ODKFileUtils.asUriFragment(appName, htmlFile));

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

    mPropertyManager = new PropertyManager(this);

    // must be at the beginning of any activity that can be called from an
    // external intent
    setAppName("survey");
    Uri uri = getIntent().getData();
    if (uri != null) {
      // initialize to the URI, then we will customize further based upon the
      // savedInstanceState...
      String authority = uri.getAuthority();
      if (authority.equalsIgnoreCase(FileProvider.getFileAuthority(this))) {
        List<String> segments = uri.getPathSegments();
        if (segments != null && segments.size() == 1) {
          String appName = segments.get(0);
          setAppName(appName);
        } else {
          String err = "Invalid " + FileProvider.getFileAuthority(this) + " uri (" + uri.toString()
              + "). Expected one segment (the application name).";
          Log.e(t, err);
          Intent i = new Intent();
          setResult(RESULT_CANCELED, i);
          finish();
          return;
        }
      } else if (authority.equalsIgnoreCase(FormsProviderAPI.AUTHORITY)) {
        List<String> segments = uri.getPathSegments();
        if (segments != null && segments.size() >= 2) {
          String appName = segments.get(0);
          setAppName(appName);
          Uri simpleUri = Uri.withAppendedPath(
              Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName), segments.get(1));
          FormIdStruct newForm = FormIdStruct.retrieveFormIdStruct(getContentResolver(), simpleUri);
          if (newForm != null) {
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
          } else {
            // cancel action if the form is not found...
            String err = "Invalid " + FormsProviderAPI.AUTHORITY + " uri (" + uri.toString()
                + "). Form not found.";
            Log.e(t, err);
            Intent i = new Intent();
            setResult(RESULT_CANCELED, i);
            finish();
            return;
          }
        } else {
          String err = "Invalid " + FormsProviderAPI.AUTHORITY + " uri (" + uri.toString()
              + "). Expected two segments.";
          Log.e(t, err);
          Intent i = new Intent();
          setResult(RESULT_CANCELED, i);
          finish();
          return;
        }
      } else {
        String err = "Unexpected " + authority + " uri. Only one of " + FileProvider.getFileAuthority(this)
            + " or " + FormsProviderAPI.AUTHORITY + " allowed.";
        Log.e(t, err);
        Intent i = new Intent();
        setResult(RESULT_CANCELED, i);
        finish();
        return;
      }
    }

    if (savedInstanceState != null) {

      pageWaitingForData = savedInstanceState.containsKey(PAGE_WAITING_FOR_DATA) ? savedInstanceState
          .getString(PAGE_WAITING_FOR_DATA) : null;
      pathWaitingForData = savedInstanceState.containsKey(PATH_WAITING_FOR_DATA) ? savedInstanceState
          .getString(PATH_WAITING_FOR_DATA) : null;
      actionWaitingForData = savedInstanceState.containsKey(ACTION_WAITING_FOR_DATA) ? savedInstanceState
          .getString(ACTION_WAITING_FOR_DATA) : null;

      currentFragment = ScreenList
          .valueOf(savedInstanceState.containsKey(CURRENT_FRAGMENT) ? savedInstanceState
              .getString(CURRENT_FRAGMENT) : currentFragment.name());
      if (savedInstanceState.containsKey(PROCESS_APK_EXPANSION_FILES)) {
        mProcessAPKExpansionFiles = savedInstanceState.getBoolean(PROCESS_APK_EXPANSION_FILES);
      }

      // if appName is explicitly set, use it...
      setAppName(savedInstanceState.containsKey(APP_NAME) ? savedInstanceState.getString(APP_NAME)
          : getAppName());
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
    }

    Log.i(t, "Starting up, creating directories");
    try {
      mProcessAPKExpansionFiles = !Survey.createODKDirs(getAppName());
    } catch (RuntimeException e) {
      createErrorDialog(e.getMessage(), EXIT);
      return;
    }

    mAdminPreferences = this.getSharedPreferences(AdminPreferencesActivity.ADMIN_PREFERENCES, 0);

    // This creates the WebKit. We need all our values initialized by this point
    setContentView(R.layout.main_screen);

    ActionBar actionBar = getSupportActionBar();
    actionBar.setDisplayOptions(ActionBar.DISPLAY_USE_LOGO);
    actionBar.show();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    int showOption = MenuItem.SHOW_AS_ACTION_IF_ROOM;
    MenuItem item;
    if (currentFragment != ScreenList.WEBKIT) {
      ActionBar actionBar = getSupportActionBar();
      actionBar.setDisplayOptions(ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_TITLE);
      actionBar.show();

      item = menu.add(Menu.NONE, MENU_FILL_FORM, Menu.NONE, getString(R.string.enter_data_button));
      item.setIcon(R.drawable.ic_action_collections_collection).setShowAsAction(showOption);

      boolean get = mAdminPreferences.getBoolean(AdminPreferencesActivity.KEY_GET_BLANK, true);
      if (get) {
        item = menu.add(Menu.NONE, MENU_PULL_FORMS, Menu.NONE, getString(R.string.get_forms));
        item.setIcon(R.drawable.ic_action_av_download).setShowAsAction(showOption);
      }

      boolean send = mAdminPreferences
          .getBoolean(AdminPreferencesActivity.KEY_SEND_FINALIZED, true);
      if (send) {
        item = menu.add(Menu.NONE, MENU_PUSH_FORMS, Menu.NONE, getString(R.string.send_data));
        item.setIcon(R.drawable.ic_action_av_upload).setShowAsAction(showOption);
      }

      boolean manage = mAdminPreferences
          .getBoolean(AdminPreferencesActivity.KEY_MANAGE_FORMS, true);
      if (manage) {
        item = menu.add(Menu.NONE, MENU_MANAGE_FORMS, Menu.NONE, getString(R.string.manage_files));
        item.setIcon(R.drawable.trash).setShowAsAction(showOption);
      }

      boolean settings = mAdminPreferences.getBoolean(AdminPreferencesActivity.KEY_ACCESS_SETTINGS,
          true);
      if (settings) {
        item = menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE,
            getString(R.string.general_preferences));
        item.setIcon(R.drawable.ic_menu_preferences).setShowAsAction(showOption);
      }
      item = menu.add(Menu.NONE, MENU_ADMIN_PREFERENCES, Menu.NONE,
          getString(R.string.admin_preferences));
      item.setIcon(R.drawable.ic_action_device_access_accounts).setShowAsAction(showOption);
    } else {
      ActionBar actionBar = getSupportActionBar();
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
    } else if (item.getItemId() == MENU_MANAGE_FORMS) {
      swapToFragmentView(ScreenList.FORM_DELETER);
      return true;
    } else if (item.getItemId() == MENU_EDIT_INSTANCE) {
      swapToFragmentView(ScreenList.WEBKIT);
      return true;
    } else if (item.getItemId() == MENU_PUSH_FORMS) {
      swapToFragmentView(ScreenList.INSTANCE_UPLOADER_FORM_CHOOSER);
      return true;
    } else if (item.getItemId() == MENU_PREFERENCES) {
      // PreferenceFragment missing from support library...
      Intent ig = new Intent(this, PreferencesActivity.class);
      startActivity(ig);
      return true;
    } else if (item.getItemId() == MENU_ADMIN_PREFERENCES) {
      SharedPreferences admin = this.getSharedPreferences(
          AdminPreferencesActivity.ADMIN_PREFERENCES, 0);
      String pw = admin.getString(AdminPreferencesActivity.KEY_ADMIN_PW, "");
      if ("".equalsIgnoreCase(pw)) {
        Intent i = new Intent(getApplicationContext(), AdminPreferencesActivity.class);
        startActivity(i);
      } else {
        createPasswordDialog();
      }
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
  public void chooseInstanceUploaderForm(Uri formUri) {
    boolean success = true;

    JQueryODKView webkitView = (JQueryODKView) findViewById(R.id.webkit_view);

    FormIdStruct newForm = FormIdStruct.retrieveFormIdStruct(getContentResolver(), formUri);

    // create this from the datastore
    if (newForm == null) {
      success = false;
    } else {
      // always loose the current instance...
      setCurrentForm(newForm);
      setInstanceId(null);
      clearSectionScreenState();
      setAuxillaryHash(null);
      webkitView.clearPage();
    }

    if (success) {
      swapToFragmentView(ScreenList.INSTANCE_UPLOADER);
    } else {
      Toast.makeText(this, getString(R.string.form_load_error), Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onBackPressed() {
    FragmentManager mgr = getSupportFragmentManager();
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
    if (mAlertDialog != null) {
      mAlertDialog.dismiss();
      mAlertDialog = null;
    }
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
            String pw = mAdminPreferences.getString(AdminPreferencesActivity.KEY_ADMIN_PW, "");
            if (pw.compareTo(value) == 0) {
              Intent i = new Intent(getApplicationContext(), AdminPreferencesActivity.class);
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
    // (this may be an issue with the Sherlock compatibility library)
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
    currentFragment = ScreenList.WEBKIT;
    levelSafeInvalidateOptionsMenu();
  }

  public void swapToFragmentView(ScreenList newFragment) {
    Log.i(t, "swapToFragmentView: " + newFragment.toString());
    FragmentManager mgr = getSupportFragmentManager();
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
    } else if (newFragment == ScreenList.COPY_EXPANSION_FILES) {
      f = mgr.findFragmentById(CopyExpansionFilesFragment.ID);
      if (f == null) {
        f = new CopyExpansionFilesFragment();
      }
      ((CopyExpansionFilesFragment) f).setFragmentToShowNext(currentFragment.name());
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
    } else if (newFragment == ScreenList.INSTANCE_UPLOADER_FORM_CHOOSER) {
      f = mgr.findFragmentById(InstanceUploaderFormChooserListFragment.ID);
      if (f == null) {
        f = new InstanceUploaderFormChooserListFragment();
      }
    } else if (newFragment == ScreenList.INSTANCE_UPLOADER) {
      f = mgr.findFragmentById(InstanceUploaderListFragment.ID);
      if (f == null) {
        f = new InstanceUploaderListFragment();
      }
      ((InstanceUploaderListFragment) f).changeForm(getCurrentForm());
    } else if (newFragment == ScreenList.WEBKIT) {
      f = mgr.findFragmentById(WebViewFragment.ID);
      if (f == null) {
        f = new WebViewFragment();
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
    // never put the copy-expansion-files fragment on the
    // back stack it is always a transient screen.
    if (currentFragment != ScreenList.COPY_EXPANSION_FILES) {
      trans.addToBackStack(currentFragment.name());
    } else {
      trans.disallowAddToBackStack();
    }
    trans.commit();
    levelSafeInvalidateOptionsMenu();
  }

  /**
   * Android Lint complains, but we are using Sherlock,
   * so this does exist for down-level devices.
   */
  @SuppressLint("NewApi")
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

    if (isWaitingForBinaryData()) {
      Log.w(t, "Already waiting for data -- ignoring");
      return "IGNORE";
    }

    Intent i;

    if (action.startsWith("org.opendatakit.survey")) {
      Class<?> clazz;
      try {
        clazz = Class.forName(action);
        i = new Intent(this, clazz);
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        i = new Intent(action);
      }
    } else {
      i = new Intent(action);
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
    JQueryODKView view = (JQueryODKView) findViewById(R.id.webkit_view);

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
    }
  }
}
