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

package org.opendatakit.survey.android.tasks;

import java.util.HashMap;

import org.kxml2.kdom.Element;
import org.opendatakit.common.android.utilities.DocumentFetchResult;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebUtils;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.listeners.FormListDownloaderListener;
import org.opendatakit.survey.android.logic.FormDetails;
import org.opendatakit.survey.android.logic.PropertiesSingleton;
import org.opendatakit.survey.android.preferences.PreferencesActivity;

import android.app.Application;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Background task for downloading a formlist from a url. 
 * In ODK 2.0, the formlist is actually a list of tableIds.
 * The actual forms are buried within the zips for those
 * tableIds.
 *
 * @author mitchellsundt@gmail.com
 */
public class DownloadFormListTask extends AsyncTask<Void, String, HashMap<String, FormDetails>> {

  private static final String t = "DownloadFormListTask";

  // used to store error message if one occurs
  public static final String DL_ERROR_MSG = "dlerrormessage";
  public static final String DL_AUTH_REQUIRED = "dlauthrequired";

  private Application appContext;
  private String appName;
  private FormListDownloaderListener mStateListener;
  private HashMap<String, FormDetails> mFormList;

  private static final String NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_LIST = "http://openrosa.org/xforms/xformsList";

  private boolean isXformsListNamespacedElement(Element e) {
    return e.getNamespace().equalsIgnoreCase(NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_LIST);
  }

  public DownloadFormListTask(String appName) {
    super();
    this.appName = appName;
  }

  @Override
  protected HashMap<String, FormDetails> doInBackground(Void... values) {
    // Getting from a file now
    String downloadListUrl = PropertiesSingleton.getProperty(appName,
        PreferencesActivity.KEY_SERVER_URL);

    // NOTE: /formlist must not be translated! It is the well-known path on
    // the server.
    String downloadPath = PropertiesSingleton.getProperty(appName,
        PreferencesActivity.KEY_FORMLIST_URL);
    downloadListUrl += downloadPath;
    String auth = PropertiesSingleton.getProperty(appName, PreferencesActivity.KEY_AUTH);

    // We populate this with available forms from the specified server.
    // <formname, details>
    HashMap<String, FormDetails> formList = new HashMap<String, FormDetails>();

    // get shared HttpContext so that authentication and cookies are
    // retained.
    HttpContext localContext = WebUtils.get().getHttpContext();
    HttpClient httpclient = WebUtils.get().createHttpClient(WebUtils.CONNECTION_TIMEOUT);

    DocumentFetchResult result = WebUtils.get().getXmlDocument(downloadListUrl, localContext, httpclient,
        auth);

    // If we can't get the document, return the error, cancel the task
    if (result.errorMessage != null) {
      if (result.responseCode == 401) {
        formList.put(DL_AUTH_REQUIRED, new FormDetails(result.errorMessage));
      } else {
        formList.put(DL_ERROR_MSG, new FormDetails(result.errorMessage));
      }
      return formList;
    }

    if (result.isOpenRosaResponse) {
      // Attempt OpenRosa 1.0 parsing
      Element xformsElement = result.doc.getRootElement();
      if (!xformsElement.getName().equals("xforms")) {
        String error = "root element is not <xforms> : " + xformsElement.getName();
        Log.e(t, "Parsing OpenRosa reply -- " + error);
        formList.put(DL_ERROR_MSG,
            new FormDetails(appContext.getString(R.string.parse_openrosa_formlist_failed, error)));
        return formList;
      }
      String namespace = xformsElement.getNamespace();
      if (!isXformsListNamespacedElement(xformsElement)) {
        String error = "root element namespace is incorrect:" + namespace;
        Log.e(t, "Parsing OpenRosa reply -- " + error);
        formList.put(DL_ERROR_MSG,
            new FormDetails(appContext.getString(R.string.parse_openrosa_formlist_failed, error)));
        return formList;
      }
      int nElements = xformsElement.getChildCount();
      for (int i = 0; i < nElements; ++i) {
        if (xformsElement.getType(i) != Element.ELEMENT) {
          // e.g., whitespace (text)
          continue;
        }
        Element xformElement = (Element) xformsElement.getElement(i);
        if (!isXformsListNamespacedElement(xformElement)) {
          // someone else's extension?
          continue;
        }
        String name = xformElement.getName();
        if (!name.equalsIgnoreCase("xform")) {
          // someone else's extension?
          continue;
        }

        // this is something we know how to interpret
        String formId = null;
        String formName = null;
        String version = null;
        String description = null;
        String downloadUrl = null;
        String manifestUrl = null;
        String hash = null;
        // don't process descriptionUrl
        int fieldCount = xformElement.getChildCount();
        for (int j = 0; j < fieldCount; ++j) {
          if (xformElement.getType(j) != Element.ELEMENT) {
            // whitespace
            continue;
          }
          Element child = xformElement.getElement(j);
          if (!isXformsListNamespacedElement(child)) {
            // someone else's extension?
            continue;
          }
          String tag = child.getName();
          if (tag.equals("formID")) {
            formId = ODKFileUtils.getXMLText(child, true);
            if (formId != null && formId.length() == 0) {
              formId = null;
            }
          } else if (tag.equals("name")) {
            formName = ODKFileUtils.getXMLText(child, true);
            if (formName != null && formName.length() == 0) {
              formName = null;
            }
          } else if (tag.equals("version")) {
            version = ODKFileUtils.getXMLText(child, true);
            if (version != null && version.length() == 0) {
              version = null;
            }
          } else if (tag.equals("hash")) {
            hash = ODKFileUtils.getXMLText(child, true);
            if (hash != null && hash.length() == 0) {
              hash = null;
            }
          } else if (tag.equals("descriptionText")) {
            description = ODKFileUtils.getXMLText(child, true);
            if (description != null && description.length() == 0) {
              description = null;
            }
          } else if (tag.equals("downloadUrl")) {
            downloadUrl = ODKFileUtils.getXMLText(child, true);
            if (downloadUrl != null && downloadUrl.length() == 0) {
              downloadUrl = null;
            }
          } else if (tag.equals("manifestUrl")) {
            manifestUrl = ODKFileUtils.getXMLText(child, true);
            if (manifestUrl != null && manifestUrl.length() == 0) {
              manifestUrl = null;
            }
          }
        }
        if (formId == null || downloadUrl == null || formName == null || hash == null) {
          String error = "Forms list entry " + Integer.toString(i)
              + " is missing one or more tags: formId, hash, name, or downloadUrl";
          Log.e(t, "Parsing OpenRosa reply -- " + error);
          formList.clear();
          formList
              .put(
                  DL_ERROR_MSG,
                  new FormDetails(appContext.getString(R.string.parse_openrosa_formlist_failed,
                      error)));
          return formList;
        }
        formList.put(formId, new FormDetails(formName, downloadUrl, manifestUrl, formId, version,
            hash));
      }
    } else {
      String error = "Server is not OpenRosa compliant";
      Log.e(t, error);
      formList.clear();
      formList.put(DL_ERROR_MSG,
          new FormDetails(appContext.getString(R.string.parse_openrosa_formlist_failed, error)));
      return formList;
    }
    return formList;
  }

  @Override
  protected void onPostExecute(HashMap<String, FormDetails> result) {
    synchronized (this) {
      mFormList = result;
      if (mStateListener != null) {
        mStateListener.formListDownloadingComplete(mFormList);
      }
    }
  }

  @Override
  protected void onCancelled(HashMap<String, FormDetails> result) {
    synchronized (this) {
      // can be null if cancelled before task executes
      if (result == null) {
        mFormList = new HashMap<String, FormDetails>();
      } else {
        mFormList = result;
      }
      if (mStateListener != null) {
        mStateListener.formListDownloadingComplete(mFormList);
      }
    }
  }

  public HashMap<String, FormDetails> getFormList() {
    return mFormList;
  }

  public void setDownloaderListener(FormListDownloaderListener sl) {
    synchronized (this) {
      mStateListener = sl;
    }
  }

  public void setApplication(Application appContext) {
    synchronized (this) {
      this.appContext = appContext;
    }
  }

  public Application getApplication() {
    return appContext;
  }
}
