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

import org.opendatakit.survey.android.R;
import org.opendatakit.httpclientandroidlib.client.CookieStore;
import org.opendatakit.httpclientandroidlib.client.CredentialsProvider;
import org.opendatakit.httpclientandroidlib.client.protocol.ClientContext;
import org.opendatakit.httpclientandroidlib.impl.client.BasicCookieStore;
import org.opendatakit.httpclientandroidlib.protocol.BasicHttpContext;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;
import org.opendatakit.survey.android.logic.FormIdStruct;
import org.opendatakit.survey.android.logic.PropertyManager;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.utilities.AgingCredentialsProvider;
import org.opendatakit.survey.android.utilities.WebLogger;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Extends the Application class to implement
 * @author carlhartung
 *
 */
public class Survey extends Application {

	public static final String t = "Survey";

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
    public static final String DEFAULT_FONTSIZE = "21";

    // shared state
    private FormIdStruct currentForm = null;
    private String instanceId = null;
    private String pageRef = null;
    private String auxilllaryHash = null;

    // share all session cookies across all sessions...
    private CookieStore cookieStore = new BasicCookieStore();
    // retain credentials for 7 minutes...
    private CredentialsProvider credsProvider = new AgingCredentialsProvider(7 * 60 * 1000);
    private PropertyManager mPropertyManager;

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

    public void setCurrentForm(FormIdStruct currentForm) {
    	this.currentForm = currentForm;
    }

    public FormIdStruct getCurrentForm() {
    	return this.currentForm;
    }

    public void setInstanceId(String instanceId) {
    	this.instanceId = instanceId;
    }

    public String getInstanceId() {
    	return this.instanceId;
    }

    public void setPageRef(String pageRef) {
    	this.pageRef = pageRef;
    }

    public String getPageRef() {
    	return this.pageRef;
    }

    public void setAuxillaryHash(String auxillaryHash) {
    	this.auxilllaryHash = auxillaryHash;
    }

    public String getAuxillaryHash() {
    	return this.auxilllaryHash;
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
     * @throws RuntimeException if there is no SDCard or the directory exists as a non directory
     */
    public static void createODKDirs() throws RuntimeException {
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
    }

    /**
     * Shared HttpContext so a user doesn't have to re-enter login information
     * @return
     */
    public synchronized HttpContext getHttpContext() {

        // context holds authentication state machine, so it cannot be
        // shared across independent activities.
        HttpContext localContext = new BasicHttpContext();

        localContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
        localContext.setAttribute(ClientContext.CREDS_PROVIDER, credsProvider);

        return localContext;
    }

    public CredentialsProvider getCredentialsProvider() {
    	return credsProvider;
    }

    public CookieStore getCookieStore() {
    	return cookieStore;
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
    }

    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.i(t, "onConfigurationChanged");
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		Log.i(t, "onTerminate");
	}

}
