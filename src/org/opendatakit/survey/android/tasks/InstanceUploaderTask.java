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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringEscapeUtils;
import org.opendatakit.aggregate.odktables.rest.KeyValueStoreConstants;
import org.opendatakit.common.android.database.DataModelDatabaseHelper;
import org.opendatakit.common.android.database.DataModelDatabaseHelperFactory;
import org.opendatakit.common.android.provider.InstanceColumns;
import org.opendatakit.common.android.provider.KeyValueStoreColumns;
import org.opendatakit.common.android.utilities.ODKDataUtils;
import org.opendatakit.common.android.utilities.ODKDatabaseUtils;
import org.opendatakit.common.android.utilities.WebUtils;
import org.opendatakit.httpclientandroidlib.Header;
import org.opendatakit.httpclientandroidlib.HttpResponse;
import org.opendatakit.httpclientandroidlib.client.ClientProtocolException;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.client.methods.HttpHead;
import org.opendatakit.httpclientandroidlib.client.methods.HttpPost;
import org.opendatakit.httpclientandroidlib.conn.ConnectTimeoutException;
import org.opendatakit.httpclientandroidlib.conn.HttpHostConnectException;
import org.opendatakit.httpclientandroidlib.entity.mime.MultipartEntity;
import org.opendatakit.httpclientandroidlib.entity.mime.content.FileBody;
import org.opendatakit.httpclientandroidlib.entity.mime.content.StringBody;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.listeners.InstanceUploaderListener;
import org.opendatakit.survey.android.logic.InstanceUploadOutcome;
import org.opendatakit.survey.android.logic.PropertiesSingleton;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.provider.FileSet;
import org.opendatakit.survey.android.provider.FileSet.MimeFile;
import org.opendatakit.survey.android.provider.InstanceProviderAPI;
import org.opendatakit.survey.android.provider.SubmissionProvider;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * Background task for uploading completed forms.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class InstanceUploaderTask extends AsyncTask<String, Integer, InstanceUploadOutcome> {

  private static final String t = "InstanceUploaderTask";
  private static final String fail = "Error: ";

  private Application appContext;
  private InstanceUploaderListener mStateListener;

  private String mAuth = "";

  private InstanceUploadOutcome mOutcome = new InstanceUploadOutcome();

  private InstanceUploadOutcome mResultOutcome = new InstanceUploadOutcome();

  private final String appName;
  private final String uploadTableId;

  public InstanceUploaderTask(String appName, String uploadTableId) {
    super();
    this.appName = appName;
    this.uploadTableId = uploadTableId;
  }

  private void setAuth(String auth) {
    this.mAuth = auth;
  }

  /**
   * Uploads to urlString the submission identified by id with filepath of
   * instance
   *
   * @param urlString
   *          destination URL
   * @param id -- _ID in the InstanceColumns table.
   * @param instanceFilePath
   * @param httpclient
   *          - client connection
   * @param localContext
   *          - context (e.g., credentials, cookies) for client connection
   * @param uriRemap
   *          - mapping of Uris to avoid redirects on subsequent invocations
   * @return false if credentials are required and we should terminate
   *         immediately.
   */
  private boolean uploadOneSubmission(String urlString, Uri toUpdate, String id, String submissionInstanceId,
      FileSet instanceFiles, HttpClient httpclient, HttpContext localContext, Map<URI, URI> uriRemap) {

    ContentValues cv = new ContentValues();
    cv.put(InstanceColumns.SUBMISSION_INSTANCE_ID, submissionInstanceId);
    URI u = null;
    try {
      URL url = new URL(URLDecoder.decode(urlString, CharEncoding.UTF_8));
      u = url.toURI();
    } catch (MalformedURLException e) {
      e.printStackTrace();
      mOutcome.mResults.put(id,
          fail + "invalid url: " + urlString + " :: details: " + e.getMessage());
      cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
      appContext.getContentResolver().update(toUpdate, cv, null, null);
      return true;
    } catch (URISyntaxException e) {
      e.printStackTrace();
      mOutcome.mResults.put(id,
          fail + "invalid uri: " + urlString + " :: details: " + e.getMessage());
      cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
      appContext.getContentResolver().update(toUpdate, cv, null, null);
      return true;
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      mOutcome.mResults.put(id,
          fail + "invalid url: " + urlString + " :: details: " + e.getMessage());
      cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
      appContext.getContentResolver().update(toUpdate, cv, null, null);
      return true;
    }

    // NOTE: ODK Survey assumes you are interfacing with an
    // OpenRosa-compliant server

    if (uriRemap.containsKey(u)) {
      // we already issued a head request and got a response,
      // so we know the proper URL to send the submission to
      // and the proper scheme. We also know that it was an
      // OpenRosa compliant server.
      u = uriRemap.get(u);
    } else {
      // we need to issue a head request
      HttpHead httpHead = WebUtils.createOpenRosaHttpHead(u);

      // prepare response
      HttpResponse response = null;
      try {
        response = httpclient.execute(httpHead, localContext);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 401) {
          WebUtils.discardEntityBytes(response);
          // we need authentication, so stop and return what we've
          // done so far.
          mOutcome.mAuthRequestingServer = u;
          return false;
        } else if (statusCode == 204) {
          Header[] locations = response.getHeaders("Location");
          WebUtils.discardEntityBytes(response);
          if (locations != null && locations.length == 1) {
            try {
              URL url = new URL(URLDecoder.decode(locations[0].getValue(), CharEncoding.UTF_8));
              URI uNew = url.toURI();
              if (u.getHost().equalsIgnoreCase(uNew.getHost())) {
                // trust the server to tell us a new location
                // ... and possibly to use https instead.
                uriRemap.put(u, uNew);
                u = uNew;
              } else {
                // Don't follow a redirection attempt to a
                // different host.
                // We can't tell if this is a spoof or not.
                mOutcome.mResults.put(id, fail
                    + "Unexpected redirection attempt to a different host: " + uNew.toString());
                cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
                appContext.getContentResolver().update(toUpdate, cv, null, null);
                return true;
              }
            } catch (Exception e) {
              e.printStackTrace();
              mOutcome.mResults.put(id, fail + urlString + " " + e.getMessage());
              cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
              appContext.getContentResolver().update(toUpdate, cv, null, null);
              return true;
            }
          }
        } else {
          // may be a server that does not handle
          WebUtils.discardEntityBytes(response);

          Log.w(t, "Status code on Head request: " + statusCode);
          if (statusCode >= 200 && statusCode <= 299) {
            mOutcome.mResults
                .put(
                    id,
                    fail
                        + "Invalid status code on Head request.  If you have a web proxy, you may need to login to your network. ");
            cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
            appContext.getContentResolver().update(toUpdate, cv, null, null);
            return true;
          }
        }
      } catch (ClientProtocolException e) {
        e.printStackTrace();
        Log.e(t, e.getMessage());
        WebUtils.clearHttpConnectionManager();
        mOutcome.mResults.put(id, fail + "Client Protocol Exception");
        cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
        appContext.getContentResolver().update(toUpdate, cv, null, null);
        return true;
      } catch (ConnectTimeoutException e) {
        e.printStackTrace();
        Log.e(t, e.getMessage());
        WebUtils.clearHttpConnectionManager();
        mOutcome.mResults.put(id, fail + "Connection Timeout");
        cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
        appContext.getContentResolver().update(toUpdate, cv, null, null);
        return true;
      } catch (UnknownHostException e) {
        e.printStackTrace();
        WebUtils.clearHttpConnectionManager();
        mOutcome.mResults.put(id, fail + e.getMessage() + " :: Network Connection Failed");
        Log.e(t, e.getMessage());
        cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
        appContext.getContentResolver().update(toUpdate, cv, null, null);
        return true;
      } catch (SocketTimeoutException e) {
        e.printStackTrace();
        Log.e(t, e.getMessage());
        WebUtils.clearHttpConnectionManager();
        mOutcome.mResults.put(id, fail + "Connection Timeout");
        cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
        appContext.getContentResolver().update(toUpdate, cv, null, null);
        return true;
      } catch (HttpHostConnectException e) {
        e.printStackTrace();
        Log.e(t, e.toString());
        WebUtils.clearHttpConnectionManager();
        mOutcome.mResults.put(id, fail + "Network Connection Refused");
        cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
        appContext.getContentResolver().update(toUpdate, cv, null, null);
        return true;
      } catch (Exception e) {
        e.printStackTrace();
        WebUtils.clearHttpConnectionManager();
        mOutcome.mResults.put(id, fail + "Generic Exception");
        Log.e(t, e.getMessage());
        cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
        appContext.getContentResolver().update(toUpdate, cv, null, null);
        return true;
      }
    }

    // At this point, we may have updated the uri to use https.
    // This occurs only if the Location header keeps the host name
    // the same. If it specifies a different host name, we error
    // out.
    //
    // And we may have set authentication cookies in our
    // cookiestore (referenced by localContext) that will enable
    // authenticated publication to the server.
    //
    // get instance file
    File instanceFile = instanceFiles.instanceFile;

    if (!instanceFile.exists()) {
      mOutcome.mResults.put(id, fail + "instance XML file does not exist!");
      cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
      appContext.getContentResolver().update(toUpdate, cv, null, null);
      return true;
    }

    List<MimeFile> files = instanceFiles.attachmentFiles;
    boolean first = true;
    int j = 0;
    int lastJ;
    while (j < files.size() || first) {
      lastJ = j;
      first = false;

      HttpPost httppost = WebUtils.createOpenRosaHttpPost(u, mAuth);

      long byteCount = 0L;

      // mime post
      MultipartEntity entity = new MultipartEntity();

      // add the submission file first...
      FileBody fb = new FileBody(instanceFile, "text/xml");
      entity.addPart("xml_submission_file", fb);
      Log.i(t, "added xml_submission_file: " + instanceFile.getName());
      byteCount += instanceFile.length();

      for (; j < files.size(); j++) {
        MimeFile mf = files.get(j);
        File f = mf.file;
        String contentType = mf.contentType;

        fb = new FileBody(f, contentType);
        entity.addPart(f.getName(), fb);
        byteCount += f.length();
        Log.i(t, "added " + contentType + " file " + f.getName());

        // we've added at least one attachment to the request...
        if (j + 1 < files.size()) {
          long nextFileLength = (files.get(j + 1).file.length());
          if ((j - lastJ + 1 > 100) || (byteCount + nextFileLength > 10000000L)) {
            // the next file would exceed the 10MB threshold...
            Log.i(t, "Extremely long post is being split into multiple posts");
            try {
              StringBody sb = new StringBody("yes", Charset.forName(CharEncoding.UTF_8));
              entity.addPart("*isIncomplete*", sb);
            } catch (Exception e) {
              e.printStackTrace(); // never happens...
            }
            ++j; // advance over the last attachment added...
            break;
          }
        }
      }

      httppost.setEntity(entity);

      // prepare response and return uploaded
      HttpResponse response = null;
      try {
        response = httpclient.execute(httppost, localContext);
        int responseCode = response.getStatusLine().getStatusCode();
        WebUtils.discardEntityBytes(response);

        Log.i(t, "Response code:" + responseCode);
        // verify that the response was a 201 or 202.
        // If it wasn't, the submission has failed.
        if (responseCode != 201 && responseCode != 202) {
          if (responseCode == 200) {
            mOutcome.mResults.put(id, fail + "Network login failure? Again?");
          } else {
            mOutcome.mResults.put(id, fail + response.getStatusLine().getReasonPhrase() + " ("
                + responseCode + ") at " + urlString);
          }
          cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
          appContext.getContentResolver().update(toUpdate, cv, null, null);
          return true;
        }
      } catch (Exception e) {
        e.printStackTrace();
        mOutcome.mResults.put(id, fail + "Generic Exception. " + e.getMessage());
        cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMISSION_FAILED);
        appContext.getContentResolver().update(toUpdate, cv, null, null);
        return true;
      }
    }

    // if it got here, it must have worked
    mOutcome.mResults.put(id, appContext.getString(R.string.success));
    cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceColumns.STATUS_SUBMITTED);
    appContext.getContentResolver().update(toUpdate, cv, null, null);
    return true;
  }

  /**
   * Write's the data to the sdcard, and updates the instances content provider.
   * In theory we don't have to write to disk, and this is where you'd add other
   * methods.
   *
   * @param markCompleted
   * @return
   * @throws IOException
   * @throws JsonMappingException
   * @throws JsonParseException
   */
  private FileSet constructSubmissionFiles(String instanceId, String submissionInstanceId)
      throws JsonParseException, JsonMappingException, IOException {

    Uri manifest = Uri.parse(SubmissionProvider.XML_SUBMISSION_URL_PREFIX
        + "/"
        + URLEncoder.encode(appName, CharEncoding.UTF_8)
        + "/"
        + URLEncoder.encode(uploadTableId, CharEncoding.UTF_8)
        + "/"
        + URLEncoder.encode(instanceId, CharEncoding.UTF_8)
        + "/"
        + URLEncoder.encode(submissionInstanceId, CharEncoding.UTF_8));

    InputStream is = appContext.getContentResolver().openInputStream(manifest);

    FileSet f = FileSet.parse(appContext, appName, is);
    return f;
  }

  // TODO: This method is like 350 lines long, down from 400.
  // still. ridiculous. make it smaller.
  @Override
  protected InstanceUploadOutcome doInBackground(String... toUpload) {
    mOutcome = new InstanceUploadOutcome();
    mOutcome.mResults = new HashMap<String, String>();
    mOutcome.mAuthRequestingServer = null;

    String auth = PropertiesSingleton.getProperty(appName, PreferencesActivity.KEY_AUTH);
    setAuth(auth);

    String urlString = null;
    
    /** 
     * retrieve the URL string for the table, if defined...
     * otherwise, use the app property values to construct it.
     */
    {
      DataModelDatabaseHelper dbHelper = DataModelDatabaseHelperFactory.getDbHelper(appContext, appName);
      SQLiteDatabase db = dbHelper.getReadableDatabase();
      
      Cursor c = null;
      try {
        c = db.query( DataModelDatabaseHelper.KEY_VALUE_STORE_ACTIVE_TABLE_NAME, null,
            KeyValueStoreColumns.TABLE_ID + "=? AND " +
            KeyValueStoreColumns.PARTITION + "=? AND " +
            KeyValueStoreColumns.ASPECT + "=? AND " + 
            KeyValueStoreColumns.KEY + "=?",
            new String[] {
              uploadTableId, 
              KeyValueStoreConstants.PARTITION_TABLE,
              KeyValueStoreConstants.ASPECT_DEFAULT,
              KeyValueStoreConstants.XML_SUBMISSION_URL },
            null, null, null);
        if ( c.getCount() == 1 ) {
          c.moveToFirst();
          int idxValue = c.getColumnIndex(KeyValueStoreColumns.VALUE);
          urlString = c.getString(idxValue);
        } else if ( c.getCount() != 0 ) {
          throw new IllegalStateException("two or more entries for " + KeyValueStoreConstants.XML_SUBMISSION_URL);
        }
      } finally {
        c.close();
        db.releaseReference();
      }
      
      if ( urlString == null ) {
        urlString = PropertiesSingleton.getProperty(appName, PreferencesActivity.KEY_SERVER_URL);
        String submissionUrl = PropertiesSingleton.getProperty(appName, PreferencesActivity.KEY_SUBMISSION_URL);
        urlString = urlString + submissionUrl;
      }
    }
    
    //FormInfo fi = new FormInfo(appContext, appName, ODKFileUtils.getuploadingForm.formDefFile);
    // get shared HttpContext so that authentication and cookies are
    // retained.
    HttpContext localContext = WebUtils.getHttpContext();
    HttpClient httpclient = WebUtils.createHttpClient(WebUtils.CONNECTION_TIMEOUT);

    Map<URI, URI> uriRemap = new HashMap<URI, URI>();

    for (int i = 0; i < toUpload.length; ++i) {
      if (isCancelled()) {
        return mOutcome;
      }
      publishProgress(i + 1, toUpload.length);

      Uri toUpdate = Uri.withAppendedPath(InstanceProviderAPI.CONTENT_URI, appName
          + "/" + uploadTableId + "/" + StringEscapeUtils.escapeHtml4(toUpload[i]));
      Cursor c = null;
      try {
        c = appContext.getContentResolver().query(toUpdate, null, null, null, null);
        if (c.getCount() == 1 && c.moveToFirst()) {

          String id = ODKDatabaseUtils.getIndexAsString(c, c.getColumnIndex(InstanceColumns._ID));
          String dataTableInstanceId = ODKDatabaseUtils.getIndexAsString(c, c.getColumnIndex(InstanceColumns.DATA_INSTANCE_ID));
          String lastOutcome = ODKDatabaseUtils.getIndexAsString(c, c.getColumnIndex(InstanceColumns.XML_PUBLISH_STATUS));
          String submissionInstanceId = ODKDataUtils.genUUID();
          // submissions always get a new legacy instance id UNLESS the last submission failed, 
          // in which case we retry the submission using the legacy instance id associated with 
          // that failure. This supports resumption of sends of forms with many attachments.
          if ( lastOutcome != null && lastOutcome.equals(InstanceColumns.STATUS_SUBMISSION_FAILED) ) {
            String lastId = ODKDatabaseUtils.getIndexAsString(c, c.getColumnIndex(InstanceColumns.SUBMISSION_INSTANCE_ID));
            if ( lastId != null ) {
              submissionInstanceId = lastId;
            }
          }
          c.close();

          FileSet instanceFiles;
          try {
            instanceFiles = constructSubmissionFiles(dataTableInstanceId, submissionInstanceId);
            // NOTE: /submission must not be translated! It is
            // the well-known path on the server.

            if (!uploadOneSubmission(urlString, toUpdate, id, submissionInstanceId, instanceFiles, httpclient,
                localContext, uriRemap)) {
              return mOutcome; // get credentials...
            }
          } catch (JsonParseException e) {
            e.printStackTrace();
            mOutcome.mResults.put(id, fail + "unable to obtain manifest: " + dataTableInstanceId + " :: details: "
                + e.toString());
          } catch (JsonMappingException e) {
            e.printStackTrace();
            mOutcome.mResults.put(id, fail + "unable to obtain manifest: " + dataTableInstanceId + " :: details: "
                + e.toString());
          } catch (IOException e) {
            e.printStackTrace();
            mOutcome.mResults.put(id, fail + "unable to obtain manifest: " + dataTableInstanceId + " :: details: "
                + e.toString());
          }
        } else {
          mOutcome.mResults.put("unknown", fail + "unable to retrieve instance information via: "
              + toUpdate.toString());
        }
      } finally {
        if (c != null && !c.isClosed()) {
          c.close();
        }
      }
    }

    return mOutcome;
  }

  @Override
  protected void onPostExecute(InstanceUploadOutcome result) {
    synchronized (this) {
      mResultOutcome = result;
      if (mStateListener != null) {
        mStateListener.uploadingComplete(mResultOutcome);
      }
    }
  }

  @Override
  protected void onCancelled(InstanceUploadOutcome result) {
    synchronized (this) {
      if (result == null) {
        mResultOutcome = new InstanceUploadOutcome();
        mResultOutcome.mAuthRequestingServer = null;
        mResultOutcome.mResults = new HashMap<String, String>();
        mResultOutcome.mResults.put("unknown", "cancelled");
      } else {
        mResultOutcome = result;
      }
      if (mStateListener != null) {
        mStateListener.uploadingComplete(mResultOutcome);
      }
    }
  }

  @Override
  protected void onProgressUpdate(Integer... values) {
    synchronized (this) {
      if (mStateListener != null) {
        // update progress and total
        mStateListener.progressUpdate(values[0].intValue(), values[1].intValue());
      }
    }
  }

  public InstanceUploadOutcome getResult() {
    return mResultOutcome;
  }

  public void setUploaderListener(InstanceUploaderListener sl) {
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
