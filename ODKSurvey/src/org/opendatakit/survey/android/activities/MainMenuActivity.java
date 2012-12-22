/*
 * Copyright (C) 2009 University of Washington
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
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.fragments.BackgroundTaskFragment;
import org.opendatakit.survey.android.fragments.FormChooserListFragment;
import org.opendatakit.survey.android.fragments.FormDownloadListFragment;
import org.opendatakit.survey.android.fragments.FormManagerListFragment;
import org.opendatakit.survey.android.fragments.InstanceUploaderListFragment;
import org.opendatakit.survey.android.fragments.WebViewFragment;
import org.opendatakit.survey.android.fragments.FormChooserListFragment.FormChooserListListener;
import org.opendatakit.survey.android.logic.FormIdStruct;
import org.opendatakit.survey.android.preferences.AdminPreferencesActivity;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.provider.FormsProviderAPI.FormsColumns;
import org.opendatakit.survey.android.views.JQueryJavascriptCallback;
import org.opendatakit.survey.android.views.JQueryODKView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
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
 * Responsible for displaying buttons to launch the major activities. Launches some activities based
 * on returns of others.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class MainMenuActivity extends SherlockFragmentActivity implements ODKActivity, FormChooserListListener, OnBackStackChangedListener {

	private static final String t = "MainMenuActivity";

    public static enum ScreenList { MAIN_SCREEN, FORM_CHOOSER, FORM_DOWNLOADER, FORM_DELETER, WEBKIT, INSTANCE_UPLOADER, CUSTOM_VIEW };

    private ScreenList currentScreen = ScreenList.MAIN_SCREEN;
    private ScreenList topScreen = ScreenList.MAIN_SCREEN;
    private ScreenList nestScreen = ScreenList.WEBKIT;

    private int fragmentId;

    // TODO: can this go away?
    // Extra returned from gp activity
    public static final String LOCATION_LATITUDE_RESULT = "latitude";
    public static final String LOCATION_LONGITUDE_RESULT = "longitude";
    public static final String LOCATION_ALTITUDE_RESULT = "altitude";
    public static final String LOCATION_ACCURACY_RESULT = "accuracy";

    // TODO: move elsewhere?
    // keys in instance uploader...
    public static final String KEY_INSTANCES = "instances";
    public static final String KEY_SUCCESS = "success";

    // menu options

	private static final int MENU_FILL_FORM = Menu.FIRST;
	private static final int MENU_PULL_FORMS = Menu.FIRST+1;
	private static final int MENU_MANAGE_FORMS = Menu.FIRST+2;
    private static final int MENU_PREFERENCES = Menu.FIRST+3;
    private static final int MENU_ADMIN_PREFERENCES = Menu.FIRST+4;
    private static final int MENU_EDIT_INSTANCE = Menu.FIRST+5;
    private static final int MENU_PUSH_INSTANCES = Menu.FIRST+6;

    // activity callback codes
    public static final int HANDLER_ACTIVITY_CODE = 20;

    /*package*/ static final FrameLayout.LayoutParams COVER_SCREEN_PARAMS =
            new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);

    /*package*/ static final FrameLayout.LayoutParams COVER_SCREEN_GRAVITY_CENTER =
            new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER);

    private JQueryJavascriptCallback mJSCallback = null;
    private SharedPreferences mAdminPreferences;

    private AlertDialog mAlertDialog;

    private static boolean EXIT = true;

    public static FormIdStruct currentForm = null;
    public static String instanceId = null;

    public static String getInstanceFolder(String instanceId) {
    	if ( instanceId == null || currentForm == null ) {
    		return Survey.INSTANCES_PATH + File.separator + "bogus";
    	} else {
    		String instanceFolder = instanceId.replaceAll("[\\p{Punct}\\p{Space}]", "_");
    		String tableFolder = currentForm.tableId.replaceAll("[\\p{Punct}\\p{Space}]", "_");

    		File f = new File(new File(new File(Survey.INSTANCES_PATH), tableFolder), instanceFolder);
    		f.mkdirs();
        	return f.getAbsolutePath();
    	}
    }

    public static String getInstanceFolder() {
    	if ( instanceId == null && currentForm == null ) {
    		return Survey.INSTANCES_PATH + File.separator + "bogus";
    	} else {
    		String instanceFolder = instanceId.replaceAll("[\\p{Punct}\\p{Space}]", "_");
    		String tableFolder = currentForm.tableId.replaceAll("[\\p{Punct}\\p{Space}]", "_");

    		File f = new File(new File(new File(Survey.INSTANCES_PATH), tableFolder), instanceFolder);
    		f.mkdirs();
        	return f.getAbsolutePath();
    	}
    }

    public static String getInstanceFilePath(String extension) {
    	String mediaPath = MainMenuActivity.getInstanceFolder() + File.separator
				+ System.currentTimeMillis() + extension;
    	return mediaPath;
    }
    // private static boolean DO_NOT_EXIT = false;


    public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private Fragment mFragment;
        private final Activity mActivity;
        private final String mTag;
        private final Class<T> mClass;

        /** Constructor used each time a new tab is created.
          * @param activity  The host Activity, used to instantiate the fragment
          * @param tag  The identifier tag for the fragment
          * @param clz  The fragment's Class, used to instantiate the fragment
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // must be at the beginning of any activity that can be called from an external intent
        Log.i(t, "Starting up, creating directories");
        try {
            Survey.createODKDirs();
        } catch (RuntimeException e) {
            createErrorDialog(e.getMessage(), EXIT);
            return;
        }

        mAdminPreferences = this.getSharedPreferences(
				AdminPreferencesActivity.ADMIN_PREFERENCES, 0);

//        String instanceId = "uuid:" + UUID.randomUUID().toString();
//
//        // set paths to /sdcard/odk/forms/formfilename/
//        String formFileName = "json";
//
//        FormController fc = new FormController(new File(Survey.FORMS_PATH, formFileName), instanceId);
//        Survey.getInstance().setFormController(fc);

        mJSCallback = new JQueryJavascriptCallback(this);

        setContentView(R.layout.main_screen);

        {
        	JQueryODKView view = (JQueryODKView) findViewById(R.id.webkit_view);
        	view.setJavascriptCallback(mJSCallback);
        	view.loadPage(null);
        }

        // ensure we have the background task fragment available before any actions are taken...
		FragmentManager mgr = getSupportFragmentManager();
		mgr.addOnBackStackChangedListener(this);

		{
			BackgroundTaskFragment f = (BackgroundTaskFragment) mgr
					.findFragmentByTag("background");

			if ( f == null ) {
				f = (BackgroundTaskFragment) Fragment.instantiate(this, BackgroundTaskFragment.class.getName());
				mgr.beginTransaction().add(f, "background").commit();
			}
		}

		ActionBar actionBar = getSupportActionBar();
		actionBar.setIcon(R.drawable.odk_logo);
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setDisplayShowHomeEnabled(false);
		actionBar.setDisplayHomeAsUpEnabled(false);


		Fragment f = mgr.findFragmentById(FormChooserListFragment.ID);
		if ( f == null ) {
			f = new FormChooserListFragment();
			((FormChooserListFragment) f).setFormChooserListListener(this);
			mgr.beginTransaction().add(R.id.main_content, f).commit();
		} else {
			((FormChooserListFragment) f).setFormChooserListListener(this);
			mgr.beginTransaction().show(f).commit();
		}
		topScreen = ScreenList.FORM_CHOOSER;
		fragmentId = FormChooserListFragment.ID;

//		{
//			MainMenuFragment f = (MainMenuFragment) mgr.findFragmentById(MainMenuFragment.ID);
//			if ( f == null ) {
//				f = new MainMenuFragment();
//				mgr.beginTransaction().add(R.id.main_content, f).commit();
//			}
//		}

        /////////////////////////
        // TODO: DataManagerListFragment -- for deleting individual instances -- not used (enter form, delete from instances page).
        //
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem item;
        if ( currentScreen == ScreenList.MAIN_SCREEN ) {
	        item = menu.add(Menu.NONE, MENU_FILL_FORM, Menu.NONE,
					 getString(R.string.enter_data_button));
	        item.setIcon(R.drawable.forms)
	        	.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
	        boolean get = mAdminPreferences.getBoolean(AdminPreferencesActivity.KEY_GET_BLANK,
	                true);
	        if ( get ) {
		        item = menu.add(Menu.NONE, MENU_PULL_FORMS, Menu.NONE,
						 getString(R.string.get_forms));
		        item.setIcon(R.drawable.down_arrow)
		        	.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
	        }
	        boolean manage = mAdminPreferences.getBoolean(AdminPreferencesActivity.KEY_MANAGE_FORMS,
	                true);
	        if ( manage ) {
		        item = menu.add(Menu.NONE, MENU_MANAGE_FORMS, Menu.NONE,
						 getString(R.string.manage_files));
		        item.setIcon(R.drawable.trash)
		        	.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
	        }
	        boolean settings = mAdminPreferences.getBoolean(AdminPreferencesActivity.KEY_ACCESS_SETTINGS, true);
	        if ( settings ) {
		        item = menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE,
		        						 getString(R.string.general_preferences));
		        item.setIcon(android.R.drawable.ic_menu_preferences)
		        	.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
	        }
	        item = menu.add(Menu.NONE, MENU_ADMIN_PREFERENCES, Menu.NONE,
					 getString(R.string.admin_preferences));
	        item.setIcon(R.drawable.ic_menu_login)
	        	.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        } else {
	        item = menu.add(Menu.NONE, MENU_EDIT_INSTANCE, Menu.NONE,
					 getString(R.string.review_data));
	        item.setIcon(R.drawable.form)
	        	.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
	        boolean send = mAdminPreferences.getBoolean(AdminPreferencesActivity.KEY_SEND_FINALIZED,
	                true);
	        if ( send ) {
		        item = menu.add(Menu.NONE, MENU_PUSH_INSTANCES, Menu.NONE,
						 getString(R.string.send_data));
		        item.setIcon(R.drawable.up_arrow)
		        	.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
	        }
	        boolean settings = mAdminPreferences.getBoolean(AdminPreferencesActivity.KEY_ACCESS_SETTINGS, true);
	        if ( settings ) {
		        item = menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE,
		        						 getString(R.string.general_preferences));
		        item.setIcon(android.R.drawable.ic_menu_preferences)
		        	.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
	        }
	        item = menu.add(Menu.NONE, MENU_ADMIN_PREFERENCES, Menu.NONE,
					 getString(R.string.admin_preferences));
	        item.setIcon(R.drawable.ic_menu_login)
	        	.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
	    }


        return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		FragmentManager mgr = getSupportFragmentManager();
		Fragment currentFragment = mgr.findFragmentById(fragmentId);

		Fragment f;
		if ( item.getItemId() == MENU_FILL_FORM ) {
			f = mgr.findFragmentById(FormChooserListFragment.ID);
			if ( f == null ) {
				f = new FormChooserListFragment();
				((FormChooserListFragment) f).setFormChooserListListener(this);
				mgr.beginTransaction().replace(R.id.main_content, f).commit();
				currentScreen = ScreenList.MAIN_SCREEN;
				topScreen = ScreenList.FORM_CHOOSER;
				fragmentId = FormChooserListFragment.ID;
				swapToFragmentView();
			} else {
				mgr.beginTransaction().replace(R.id.main_content, f).commit();
				currentScreen = ScreenList.MAIN_SCREEN;
				topScreen = ScreenList.FORM_CHOOSER;
				fragmentId = FormChooserListFragment.ID;
				swapToFragmentView();
			}
			return true;
		} else if ( item.getItemId() == MENU_PULL_FORMS ) {
			f = this.getSupportFragmentManager().findFragmentById(FormDownloadListFragment.ID);
			if ( f == null ) {
				f = new FormDownloadListFragment();
				mgr.beginTransaction().replace(R.id.main_content, f).commit();
				currentScreen = ScreenList.MAIN_SCREEN;
				topScreen = ScreenList.FORM_DOWNLOADER;
				fragmentId = FormDownloadListFragment.ID;
				swapToFragmentView();
			} else {
				mgr.beginTransaction().replace(R.id.main_content, f).commit();
				currentScreen = ScreenList.MAIN_SCREEN;
				topScreen = ScreenList.FORM_DOWNLOADER;
				fragmentId = FormDownloadListFragment.ID;
				swapToFragmentView();
			}
			return true;
		} else if ( item.getItemId() == MENU_MANAGE_FORMS ) {
			f = this.getSupportFragmentManager().findFragmentById(FormManagerListFragment.ID);
			if ( f == null ) {
				f = new FormManagerListFragment();
				mgr.beginTransaction().replace(R.id.main_content, f).commit();
				currentScreen = ScreenList.MAIN_SCREEN;
				topScreen = ScreenList.FORM_DELETER;
				fragmentId = FormManagerListFragment.ID;
				swapToFragmentView();
			} else {
				mgr.beginTransaction().replace(R.id.main_content, f).commit();
				currentScreen = ScreenList.MAIN_SCREEN;
				topScreen = ScreenList.FORM_DELETER;
				fragmentId = FormManagerListFragment.ID;
				swapToFragmentView();
			}
			return true;
		} else if ( item.getItemId() == MENU_EDIT_INSTANCE ) {
			f = mgr.findFragmentById(WebViewFragment.ID);
			if ( f == null ) {
				f = new WebViewFragment();
				mgr.beginTransaction().replace(R.id.main_content, f).commit();
				currentScreen = ScreenList.WEBKIT;
				nestScreen = ScreenList.WEBKIT;
				fragmentId = WebViewFragment.ID;
				swapToWebKitView();
			} else {
				mgr.beginTransaction().replace(R.id.main_content, f).commit();
				currentScreen = ScreenList.WEBKIT;
				nestScreen = ScreenList.WEBKIT;
				fragmentId = WebViewFragment.ID;
				swapToWebKitView();
			}
			return true;
		} else if ( item.getItemId() == MENU_PUSH_INSTANCES ) {
			f = this.getSupportFragmentManager().findFragmentById(InstanceUploaderListFragment.ID);
			if ( f == null ) {
				f = new InstanceUploaderListFragment();
				mgr.beginTransaction().replace(R.id.main_content, f).commit();
				currentScreen = ScreenList.WEBKIT;
				nestScreen = ScreenList.INSTANCE_UPLOADER;
				fragmentId = InstanceUploaderListFragment.ID;
				swapToFragmentView();
			} else {
				mgr.beginTransaction().replace(R.id.main_content, f).commit();
				currentScreen = ScreenList.WEBKIT;
				nestScreen = ScreenList.INSTANCE_UPLOADER;
				fragmentId = InstanceUploaderListFragment.ID;
				swapToFragmentView();
			}
			return true;
		} else if ( item.getItemId() == MENU_PREFERENCES ) {
			// PreferenceFragment missing from support library...
       	    Intent ig = new Intent(this, PreferencesActivity.class);
            startActivity(ig);
            return true;
		} else if ( item.getItemId() == MENU_ADMIN_PREFERENCES ) {
	        SharedPreferences admin = this.getSharedPreferences(AdminPreferencesActivity.ADMIN_PREFERENCES, 0);
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
		/* WAS:
		String action = getActivity().getIntent().getAction();
		if (Intent.ACTION_PICK.equals(action)) {
			// caller is waiting on a picked form
			getActivity().setResult(Activity.RESULT_OK,
					new Intent().setData(formUri));
		} else {
			startActivity(new Intent(Intent.ACTION_EDIT, formUri));
		}
		*/

		nestScreen = ScreenList.WEBKIT;
		currentScreen = ScreenList.WEBKIT;

    	FragmentManager mgr = getSupportFragmentManager();
    	// TODO: get the webkit fragment
    	WebViewFragment f = (WebViewFragment) mgr.findFragmentById(WebViewFragment.ID);
		if ( f == null ) {
			f = new WebViewFragment();
		}
		FragmentTransaction tr = mgr.beginTransaction();
		tr.replace(R.id.main_content, f);
		tr.addToBackStack(null);
		tr.commit();

		JQueryODKView webkitView = (JQueryODKView) findViewById(R.id.webkit_view);
		//webkitView.setActivity(this);
		Cursor c = null;
		try {
			c = getContentResolver().query(formUri, null, null, null, null);
			if ( c.getCount() == 1 ) {
				int formMedia = c.getColumnIndex(FormsColumns.FORM_MEDIA_PATH);
				int formPath = c.getColumnIndex(FormsColumns.FORM_PATH);
				int formId = c.getColumnIndex(FormsColumns.FORM_ID);
				int formVersion = c.getColumnIndex(FormsColumns.FORM_VERSION);
				int tableId = c.getColumnIndex(FormsColumns.TABLE_ID);
				int date = c.getColumnIndex(FormsColumns.DATE);

				c.moveToFirst();
				FormIdStruct newForm = new FormIdStruct(
						new File(c.getString(formMedia), "formDef.json"),
						c.getString(formPath), c.getString(formId), c.getString(formVersion),
						c.getString(tableId), new Date(c.getLong(date)));
				if ( currentForm != null && newForm.formPath.equals(currentForm.formPath) ) {
					// keep the same instance...
				} else {
					currentForm = newForm;
					instanceId = null;
					webkitView.loadPage(null);
				}
			}
		} finally {
			if ( c != null ) {
				c.close();
			}
		}

		swapToWebKitView();
		invalidateOptionsMenu();

	}


	@Override
	public void onBackPressed() {
		if ( currentScreen == ScreenList.WEBKIT && nestScreen == ScreenList.WEBKIT && instanceId != null ) {
        	JQueryODKView view = (JQueryODKView) findViewById(R.id.webkit_view);
        	view.goBack();
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void onBackStackChanged() {
		//View frags = findViewById(R.id.main_content);
		//View wkt = findViewById(R.id.webkit_view);

		FragmentManager mgr = getSupportFragmentManager();
		if (  mgr.getBackStackEntryCount() == 0 ) {
			currentScreen = ScreenList.MAIN_SCREEN;
			swapToFragmentView();
		} else {
			currentScreen = ScreenList.WEBKIT;
			if ( nestScreen == ScreenList.WEBKIT ) {
				swapToWebKitView();
			} else {
				swapToFragmentView();
			}
		}
		invalidateOptionsMenu();
	}

    @Override
    protected void onPause() {
        super.onPause();
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
    }

    private void createErrorDialog(String errorMsg, final boolean shouldExit) {
    	if ( mAlertDialog != null ) {
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
    	if ( mAlertDialog != null ) {
    		mAlertDialog.dismiss();
    		mAlertDialog = null;
    	}
        final AlertDialog passwordDialog = new AlertDialog.Builder(this).create();

        passwordDialog.setTitle(getString(R.string.enter_admin_password));
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordDialog.setView(input, 20, 10, 20, 10);

        passwordDialog.setButton(AlertDialog.BUTTON_POSITIVE,
                getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String value = input.getText().toString();
                        String pw = mAdminPreferences.getString(
                                AdminPreferencesActivity.KEY_ADMIN_PW, "");
                        if (pw.compareTo(value) == 0) {
                            Intent i = new Intent(getApplicationContext(),
                                    AdminPreferencesActivity.class);
                            startActivity(i);
                            input.setText("");
                            passwordDialog.dismiss();
                        } else {
                            Toast.makeText(
                                    MainMenuActivity.this,
                                    getString(R.string.admin_password_incorrect),
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

    Bitmap mDefaultVideoPoster = null;

    public Bitmap getDefaultVideoPoster() {
        if (mDefaultVideoPoster == null) {
            mDefaultVideoPoster = BitmapFactory.decodeResource(
                    getResources(), R.drawable.expander_ic_right);
        }
        return mDefaultVideoPoster;
    }

    View mVideoProgressView = null;

    public View getVideoLoadingProgressView() {
        if (mVideoProgressView == null) {
            LayoutInflater inflater = LayoutInflater.from(this);
            mVideoProgressView = inflater.inflate(
                    R.layout.video_loading_progress, null);
        }
        return mVideoProgressView;
    }

    public void hideWebkitView() {

    	// In the fragment UI, we want to return to not having any instanceId defined.
    	JQueryODKView webkitView = (JQueryODKView) findViewById(R.id.webkit_view);
    	instanceId = null;
    	webkitView.loadPage(null);
    }

    public void swapToWebKitView() {
    	FrameLayout shadow = (FrameLayout) findViewById(R.id.shadow_content);
		View frags = findViewById(R.id.main_content);
		View wkt = findViewById(R.id.webkit_view);
		shadow.setVisibility(View.GONE);
    	shadow.removeAllViews();
		frags.setVisibility(View.GONE);
		wkt.setVisibility(View.VISIBLE);
    }

    public void swapToCustomView(View customView) {
    	FrameLayout shadow = (FrameLayout) findViewById(R.id.shadow_content);
		View frags = findViewById(R.id.main_content);
		View wkt = findViewById(R.id.webkit_view);
    	shadow.removeAllViews();
    	shadow.addView(customView, COVER_SCREEN_GRAVITY_CENTER);
		frags.setVisibility(View.GONE);
		wkt.setVisibility(View.GONE);
		shadow.setVisibility(View.VISIBLE);
    }

    public void swapOffCustomView() {
    	FrameLayout shadow = (FrameLayout) findViewById(R.id.shadow_content);
		View frags = findViewById(R.id.main_content);
		View wkt = findViewById(R.id.webkit_view);
		shadow.setVisibility(View.GONE);
    	shadow.removeAllViews();
    	if (currentScreen == ScreenList.WEBKIT ) {
    		frags.setVisibility(View.GONE);
    		wkt.setVisibility(View.VISIBLE);
    	} else {
    		frags.setVisibility(View.VISIBLE);
    		wkt.setVisibility(View.GONE);
    	}
    }

    public void swapToFragmentView() {
    	FrameLayout shadow = (FrameLayout) findViewById(R.id.shadow_content);
		View frags = findViewById(R.id.main_content);
		View wkt = findViewById(R.id.webkit_view);
		shadow.setVisibility(View.GONE);
    	shadow.removeAllViews();
		wkt.setVisibility(View.GONE);
		frags.setVisibility(View.VISIBLE);
    }

	@Override
	public void saveAllChangesCompleted(String tableId, String instanceId, final boolean asComplete) {
		hideWebkitView();
	}

	@Override
	public void saveAllChangesFailed(String tableId, String instanceId) {
		// probably keep the webkit view?
		// hideWebkitView();
	}

	@Override
	public void ignoreAllChangesCompleted(String tableId, String instanceId) {
		hideWebkitView();
	}

	@Override
	public void ignoreAllChangesFailed(String tableId, String instanceId) {
		// probably keep the webkit view?
		// hideWebkitView();
	}

	public static JSONObject convertFromBundle(Bundle b) throws JSONException {
		JSONObject jo = new JSONObject();
		Set<String> keys = b.keySet();
		for ( String key : keys ) {
			Object o = b.get(key);
			if ( o == null ) {
				jo.put(key, JSONObject.NULL);
			} else if ( o.getClass().isArray() ) {
				JSONArray ja = new JSONArray();
				Class<?> t = o.getClass().getComponentType();
				if ( t.equals(long.class) ) {
					long[] a = (long[]) o;
					for ( int i = 0 ; i < a.length ; ++i ) {
						ja.put(a[i]);
					}
					jo.put(key, ja);
				} else if ( t.equals(int.class) ) {
					int[] a = (int[]) o;
					for ( int i = 0 ; i < a.length ; ++i ) {
						ja.put(a[i]);
					}
					jo.put(key, ja);
				} else if ( t.equals(double.class) ) {
					double[] a = (double[]) o;
					for ( int i = 0 ; i < a.length ; ++i ) {
						ja.put(a[i]);
					}
					jo.put(key, ja);
				} else if ( t.equals(boolean.class) ) {
					boolean[] a = (boolean[]) o;
					for ( int i = 0 ; i < a.length ; ++i ) {
						ja.put(a[i]);
					}
					jo.put(key, ja);
				} else if ( t.equals(Long.class) ) {
					Long[] a = (Long[]) o;
					for ( int i = 0 ; i < a.length ; ++i ) {
						ja.put((a[i] == null) ? JSONObject.NULL : a[i]);
					}
				} else if ( t.equals(Integer.class) ) {
					Integer[] a = (Integer[]) o;
					for ( int i = 0 ; i < a.length ; ++i ) {
						ja.put((a[i] == null) ? JSONObject.NULL : a[i]);
					}
					jo.put(key, ja);
				} else if ( t.equals(Double.class) ) {
					Double[] a = (Double[]) o;
					for ( int i = 0 ; i < a.length ; ++i ) {
						ja.put((a[i] == null) ? JSONObject.NULL : a[i]);
					}
					jo.put(key, ja);
				} else if ( t.equals(Boolean.class) ) {
					Boolean[] a = (Boolean[]) o;
					for ( int i = 0 ; i < a.length ; ++i ) {
						ja.put((a[i] == null) ? JSONObject.NULL : a[i]);
					}
					jo.put(key, ja);
				} else if ( t.equals(Bundle.class) || Bundle.class.isAssignableFrom(t) ) {
					Bundle[] a = (Bundle[]) o;
					for ( int i = 0 ; i < a.length ; ++i ) {
						ja.put((a[i] == null) ? JSONObject.NULL : convertFromBundle(a[i]));
					}
					jo.put(key, ja);
				} else {
					throw new JSONException("unrecognized class");
				}
			} else if ( o instanceof Bundle ) {
				jo.put(key, convertFromBundle((Bundle) o));
			} else if ( o instanceof String ) {
				jo.put(key, b.getString(key));
			} else if ( o instanceof Boolean ) {
				jo.put(key, b.getBoolean(key));
			} else if ( o instanceof Integer ) {
				jo.put(key, b.getInt(key));
			} else if ( o instanceof Long ) {
				jo.put(key, b.getLong(key));
			} else if ( o instanceof Double ) {
				jo.put(key, b.getDouble(key));
			}
		}
		return jo;
	}

	public static Bundle convertToBundle(JSONObject valueMap) throws JSONException {
		Bundle b = new Bundle();
		Iterator<String> cur = valueMap.keys();
		while ( cur.hasNext() ) {
			String key = cur.next();
			if ( !valueMap.isNull(key) ) {
				Object o = valueMap.get(key);
				if ( o instanceof JSONObject ) {
					Bundle be = convertToBundle( (JSONObject) o);
					b.putBundle(key, be);
				} else if ( o instanceof JSONArray ) {
					JSONArray a = (JSONArray) o;
					// only non-empty arrays are written into the Bundle
					// first non-null element defines data type
					// for the array
					Object oe = null;
					for ( int j = 0 ; j < a.length(); ++j ) {
						if (!a.isNull(j) ) {
							oe = a.get(j);
							break;
						}
					}
					if ( oe != null ) {
						if ( oe instanceof JSONObject ) {
							Bundle[] va = new Bundle[a.length()];
							for ( int j = 0 ; j < a.length() ; ++j ) {
								if ( a.isNull(j) ) {
									va[j] = null;
								} else {
									va[j] = convertToBundle( (JSONObject) a.getJSONObject(j));
								}
							}
							b.putParcelableArray(key, va);
						} else if ( oe instanceof JSONArray ) {
							throw new JSONException("Unable to convert nested arrays");
						} else if ( oe instanceof String ) {
							String[] va = new String[a.length()];
							for ( int j = 0 ; j < a.length() ; ++j ) {
								if ( a.isNull(j) ) {
									va[j] = null;
								} else {
									va[j] = a.getString(j);
								}
							}
							b.putStringArray(key, va);
						} else if ( oe instanceof Boolean ) {
							boolean[] va = new boolean[a.length()];
							for ( int j = 0 ; j < a.length() ; ++j ) {
								if ( a.isNull(j) ) {
									va[j] = false;
								} else {
									va[j] = a.getBoolean(j);
								}
							}
							b.putBooleanArray(key, va);
						} else if ( oe instanceof Integer ) {
							int[] va = new int[a.length()];
							for ( int j = 0 ; j < a.length() ; ++j ) {
								if ( a.isNull(j) ) {
									va[j] = 0;
								} else {
									va[j] = a.getInt(j);
								}
							}
							b.putIntArray(key, va);
						} else if ( oe instanceof Long ) {
							long[] va = new long[a.length()];
							for ( int j = 0 ; j < a.length() ; ++j ) {
								if ( a.isNull(j) ) {
									va[j] = 0;
								} else {
									va[j] = a.getLong(j);
								}
							}
							b.putLongArray(key, va);
						} else if ( oe instanceof Double ) {
							double[] va = new double[a.length()];
							for ( int j = 0 ; j < a.length() ; ++j ) {
								if ( a.isNull(j) ) {
									va[j] = Double.NaN;
								} else {
									va[j] = a.getDouble(j);
								}
							}
							b.putDoubleArray(key, va);
						}
					}
				} else if ( o instanceof String ) {
					b.putString(key, valueMap.getString(key));
				} else if ( o instanceof Boolean ) {
					b.putBoolean(key, valueMap.getBoolean(key));
				} else if ( o instanceof Integer ) {
					b.putInt(key, valueMap.getInt(key));
				} else if ( o instanceof Long ) {
					b.putLong(key, valueMap.getLong(key));
				} else if ( o instanceof Double ) {
					b.putDouble(key, valueMap.getDouble(key));
				}
			}
		}
		return b;
	}

    public static String getPathFromUri(Uri uri, String pathKey) {
        if (uri.toString().startsWith("file")) {
            return uri.toString().substring(7);
        } else {
            String[] projection = {
            		pathKey
                };
            Cursor c = null;
            try {
            	c = Survey.getInstance().getContentResolver().query(uri, projection, null, null, null);
            	int column_index = c.getColumnIndexOrThrow(pathKey);
            	String path = null;
	            if (c.getCount() > 0) {
	                c.moveToFirst();
	                path = c.getString(column_index);
	            }
	            return path;
            } finally {
            	if ( c != null ) {
            		c.close();
            	}
            }
        }
    }

	private String pageWaitingForData = null;
	private String pathWaitingForData = null;
	private String actionWaitingForData = null;

	public boolean isWaitingForBinaryData() {
		return actionWaitingForData != null;
	}

	/**
	 * Invoked from within Javascript to launch an activity.
	 *
	 *  @param page -- page containing prompt requesting the action
	 *  @param path -- prompt requesting the action
	 *  @param action -- the intent to be launched
	 *  @param valueMap -- parameters to pass to the intent
	 */
	public String doAction(String page, String path, String action,
			JSONObject valueMap) {

		if ( isWaitingForBinaryData() ) {
			Log.w(t, "Already waiting for data -- ignoring");
			return "IGNORE";
		}

		Intent i;
		if ( action.startsWith("org.opendatakit.survey") ) {
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
			if ( valueMap != null ) {
				Bundle b;
				b = convertToBundle(valueMap);
				i.putExtras(b);
			}
		} catch (JSONException ex) {
			ex.printStackTrace();
			return "JSONException: " + ex.toString();
		}

		pageWaitingForData = page;
		pathWaitingForData = path;
		actionWaitingForData = action;

        startActivityForResult(i, HANDLER_ACTIVITY_CODE );
		return "OK";
	}
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	Log.i(t, "onActivityResult");
    	JQueryODKView view = (JQueryODKView) findViewById(R.id.webkit_view);

        if ( requestCode == HANDLER_ACTIVITY_CODE ) {
	    	try {
				String jsonObject = null;
        		Bundle b = (intent == null) ? null : intent.getExtras();
        		JSONObject val = (b == null) ? null : convertFromBundle(b);
        		jsonObject = "{\"status\":" + Integer.toString(resultCode) +
        				((val == null) ? "" : ", \"result\":" + val.toString()) + "}";
        		Log.i(t, "HANDLER_ACTIVITY_CODE: " + jsonObject);

        		view.loadJavascriptUrl("javascript:controller.opendatakitCallback('"+pageWaitingForData+"','"+pathWaitingForData+"','"+actionWaitingForData+"', '"+jsonObject+"' )");
			} catch ( Exception e ) {
				view.loadJavascriptUrl("javascript:controller.opendatakitCallback('"+pageWaitingForData+"','"+pathWaitingForData+"','"+actionWaitingForData+"', '{ \"status\":0, \"result\":\"" + e.toString() + "\"}' )");
	    	} finally {
	            pathWaitingForData = null;
	            pageWaitingForData = null;
	            actionWaitingForData = null;
	    	}
        }
    }
}
