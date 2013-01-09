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
package org.opendatakit.survey.android.application;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.logic.PropertyManager;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.utilities.WebLogger;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.APKExpansionPolicy;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.util.Base64;
import com.google.android.vending.licensing.util.Base64DecoderException;

/**
 * Extends the Application class to implement
 * @author carlhartung
 *
 */
public class Survey extends Application implements LicenseCheckerCallback {

	private static final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzvIdMy97S9QLTp/gBxp7WUPM" +
			"QWdXu2IDQJbZ3is95cx+WYuLXIdjNyok+VCZo8CxIHb2kuIeDmFYCwagqw9Uclnh2WDWagT+BB4dfyBD3V4IetGNdSZFKh2Y+9KHnysmsY7w" +
			"E4z6NcrGlYbPWyboORjODBRNZ4rWPnLNix8WaNlHDW05uahGnSmto0lNCkdSC2PvmTUE3BimUScoiS+6LxQR/THqnR1RqA1IimjG2JsP58Oo" +
			"n5NjWN4maX08IroXWDBGjhPtdngWOnoR8GoJ96M8k0eAM1LJ84eB/v9LnQZlrZjBdtUEhXlGKVudo41vmp1sC1OpRLYMbshhst7dzQIDAQAB";
	public static final String t = "Survey";

	// keys for expansion files
	public static final String EXPANSION_FILE_PATH = "path";
	public static final String EXPANSION_FILE_LENGTH = "length";
	public static final String EXPANSION_FILE_URL = "url";

	// special filename
    public static final String FORMDEF_JSON_FILENAME = "formDef.json";

	// Storage paths
    public static final String ODK_ROOT = Environment.getExternalStorageDirectory() + File.separator + "odk" + File.separator + "js";
    public static final String FORMS_PATH = ODK_ROOT + File.separator + "forms";
    public static final String STALE_FORMS_PATH = ODK_ROOT + File.separator + "forms.old";
    public static final String INSTANCES_PATH = ODK_ROOT + File.separator + "instances";
    public static final String METADATA_PATH = ODK_ROOT + File.separator + "metadata";
    public static final String LOGGING_PATH = ODK_ROOT + File.separator + "logging";
    // for WebKit:
    public static final String APPCACHE_PATH = METADATA_PATH + File.separator + "appCache";
    public static final String GEOCACHE_PATH = METADATA_PATH + File.separator + "geoCache";
    public static final String WEBDB_PATH = METADATA_PATH + File.separator + "webDb";

    // private values
    private static final String DEFAULT_FONTSIZE = "21";
	private static final ObjectMapper mapper = new ObjectMapper();

    private PropertyManager mPropertyManager;

    private int versionCode;
    private byte[] mSalt;
    private LicenseChecker mLicenseChecker;
    private APKExpansionPolicy mAPKExpansionPolicy;

    private WebLogger logger = null;

	private static Survey singleton = null;


    public static Survey getInstance() {
        return singleton;
    }

    public static int getQuestionFontsize() {
        SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(Survey.getInstance());
        String question_font =
                settings.getString(PreferencesActivity.KEY_FONT_SIZE, Survey.DEFAULT_FONTSIZE);
        int questionFontsize = Integer.valueOf(question_font);
        return questionFontsize;
    }

    public String getVersionedAppName() {
        String versionDetail = "";
		try {
	        PackageInfo pinfo;
			pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
	        int versionNumber = pinfo.versionCode;
	        String versionName = pinfo.versionName;
	        versionDetail = " " + versionName + "(" + versionNumber + ")";
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
        return getString(R.string.app_name) + versionDetail;
    }
    /**
     * Creates required directories on the SDCard (or other external storage)
     * @return true if there are forms present
     * @throws RuntimeException if there is no SDCard or the directory exists as a non directory
     */
    public static boolean createODKDirs() throws RuntimeException {
        String cardstatus = Environment.getExternalStorageState();
        if (cardstatus.equals(Environment.MEDIA_REMOVED)
                || cardstatus.equals(Environment.MEDIA_UNMOUNTABLE)
                || cardstatus.equals(Environment.MEDIA_UNMOUNTED)
                || cardstatus.equals(Environment.MEDIA_MOUNTED_READ_ONLY)
                || cardstatus.equals(Environment.MEDIA_SHARED)) {
            RuntimeException e =
                new RuntimeException("ODK reports :: SDCard error: "
                        + Environment.getExternalStorageState());
            throw e;
        }

        String[] dirs = {
                ODK_ROOT, FORMS_PATH, STALE_FORMS_PATH, INSTANCES_PATH, METADATA_PATH,
                LOGGING_PATH, APPCACHE_PATH, GEOCACHE_PATH, WEBDB_PATH
        };

        for (String dirName : dirs) {
            File dir = new File(dirName);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    RuntimeException e =
                        new RuntimeException("ODK reports :: Cannot create directory: " + dirName);
                    throw e;
                }
            } else {
                if (!dir.isDirectory()) {
                    RuntimeException e =
                        new RuntimeException("ODK reports :: " + dirName
                                + " exists, but is not a directory");
                    throw e;
                }
            }
        }

        File[] files = new File(FORMS_PATH).listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory() && new File(pathname, FORMDEF_JSON_FILENAME).exists();
			}});

        return (files.length != 0);
    }

    public PropertyManager getPropertyManager() {
    	return mPropertyManager;
    }

    public synchronized WebLogger getLogger() {
        if ( logger == null ) {
        	createODKDirs();
            logger = new WebLogger();
        }
    	return logger;
    }

    @Override
    public void onCreate() {
        singleton = this;
        mPropertyManager = new PropertyManager(getApplicationContext());
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        PreferenceManager.setDefaultValues(this, R.xml.admin_preferences, false);
        super.onCreate();

        SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(Survey.getInstance());
        String saltString = settings.getString(PreferencesActivity.KEY_SALT, null);
        do {
	        if ( saltString == null ) {
	            SecureRandom random = new SecureRandom();
	            mSalt = new byte[20];
	            random.nextBytes(mSalt);
	            saltString = Base64.encode(mSalt);
	            settings.edit().putString(PreferencesActivity.KEY_SALT, saltString).commit();
	        } else {
	        	try {
					mSalt = Base64.decode(saltString);
				} catch (Base64DecoderException e) {
					Log.e(t, "Unable to decode saved salt string -- regenerating");
					saltString = null;
					e.printStackTrace();
				}
	        }
        } while ( saltString == null );

 		try {
	        PackageInfo pinfo;
			pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			versionCode = pinfo.versionCode;
	        mSalt[0] = (byte) (versionCode % 251);
		} catch (NameNotFoundException e) {
			versionCode = 0;
			e.printStackTrace();
		}

        String deviceId = mPropertyManager.getSingularProperty(PropertyManager.OR_DEVICE_ID_PROPERTY);

        // Construct the LicenseChecker with a Policy.
        mAPKExpansionPolicy = new APKExpansionPolicy(this,
                new AESObfuscator(mSalt, getPackageName(), deviceId));
        mLicenseChecker = new LicenseChecker(
            this, mAPKExpansionPolicy,
            BASE64_PUBLIC_KEY  // Your public licensing key.
            );

        mLicenseChecker.checkAccess(this);
    }

    public byte[] getSalt() {
    	return mSalt;
    }

    public String getBase64PublicKey() {
    	return BASE64_PUBLIC_KEY;
    }

    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.i(t, "onConfigurationChanged");
	}

	@Override
	public void onTerminate() {
		mLicenseChecker.onDestroy();
		super.onTerminate();
		Log.i(t, "onTerminate");
	}

	@Override
	public void allow(int reason) {
		Log.i(t, "allow: license check succeeded: " + Integer.toString(reason));

		if ( mAPKExpansionPolicy.isUpdatedFromServer() ) {
			// we got a response from the server, as opposed to a cached entry
			// (a cached entry does not have the expansion info).
			// Gather and persist the expansion file info into the user preferences.
			File f = new File( new File(new
					File(Environment.getExternalStorageDirectory(), "Android"), "obb"), getPackageName());
			f.mkdirs();

			ArrayList<Map<String,Object>> expansions = new ArrayList<Map<String,Object>>();
			int exps = mAPKExpansionPolicy.getExpansionURLCount();
			for ( int i = 0 ; i < exps ; ++i ) {
				String name = mAPKExpansionPolicy.getExpansionFileName(i);
				String url = mAPKExpansionPolicy.getExpansionURL(i);
				long len = mAPKExpansionPolicy.getExpansionFileSize(i);
				File ext = new File(f, name);

				Map<String,Object> ex = new HashMap<String,Object>();
				ex.put(EXPANSION_FILE_PATH, ext.getAbsoluteFile());
				ex.put(EXPANSION_FILE_URL, url);
				ex.put(EXPANSION_FILE_LENGTH, Long.valueOf(len));
				expansions.add(ex);
			}

			String expansionDefs;
			try {
				expansionDefs = mapper.writeValueAsString(expansions);

				SharedPreferences settings =
		                PreferenceManager.getDefaultSharedPreferences(Survey.getInstance());

				settings.edit().putString(PreferencesActivity.KEY_APK_EXPANSIONS, expansionDefs).commit();
				Log.i(t, "persisted the expansion file list (" + expansions.size() + " expansion files)");
			} catch (JsonGenerationException e) {
				e.printStackTrace();
				Log.e(t,"unable to persist expected APK Expansion information");
			} catch (JsonMappingException e) {
				e.printStackTrace();
				Log.e(t,"unable to persist expected APK Expansion information");
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(t,"unable to persist expected APK Expansion information");
			}
		}
	}

	@SuppressWarnings("unchecked")
	public ArrayList<Map<String,Object>> expansionFiles() {
		File f = new File( new File(new
				File(Environment.getExternalStorageDirectory(), "Android"), "obb"), getPackageName());
		f.mkdirs();

		SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(Survey.getInstance());
		String expansionDefs = settings.getString(PreferencesActivity.KEY_APK_EXPANSIONS, null);
		if ( expansionDefs != null ) {
			try {
				return mapper.readValue(expansionDefs, ArrayList.class);
			} catch (JsonParseException e) {
				e.printStackTrace();
				Log.e(t,"unable to retrieve expected APK Expansion information");
			} catch (JsonMappingException e) {
				e.printStackTrace();
				Log.e(t,"unable to retrieve expected APK Expansion information");
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(t,"unable to retrieve expected APK Expansion information");
			}
		}
		return null;
	}

	public File localExpansionFile() {
		File f = new File( new File(new
				File(Environment.getExternalStorageDirectory(), "Android"), "obb"), getPackageName());
		f.mkdirs();

		String name = Helpers.getExpansionAPKFileName(this, true, versionCode);

		return new File(f, name);
	}

	@Override
	public void dontAllow(int reason) {
		Log.e(t, "dontAllow: license check FAILED: " + Integer.toString(reason));
	}

	@Override
	public void applicationError(int errorCode) {
		Log.e(t, "applicationError: license check ERROR: " + Integer.toString(errorCode));
	}

}
