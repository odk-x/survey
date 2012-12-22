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

package org.opendatakit.survey.android.tasks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringEscapeUtils;
import org.opendatakit.httpclientandroidlib.Header;
import org.opendatakit.httpclientandroidlib.HttpResponse;
import org.opendatakit.httpclientandroidlib.client.ClientProtocolException;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.client.methods.HttpHead;
import org.opendatakit.httpclientandroidlib.client.methods.HttpPost;
import org.opendatakit.httpclientandroidlib.conn.ConnectTimeoutException;
import org.opendatakit.httpclientandroidlib.entity.mime.MultipartEntity;
import org.opendatakit.httpclientandroidlib.entity.mime.content.FileBody;
import org.opendatakit.httpclientandroidlib.entity.mime.content.StringBody;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.activities.MainMenuActivity;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.listeners.InstanceUploaderListener;
import org.opendatakit.survey.android.logic.FormInfo;
import org.opendatakit.survey.android.logic.InstanceUploadOutcome;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.provider.InstanceProviderAPI;
import org.opendatakit.survey.android.provider.InstanceProviderAPI.InstanceColumns;
import org.opendatakit.survey.android.utilities.EncryptionUtils;
import org.opendatakit.survey.android.utilities.EncryptionUtils.EncryptedFormInformation;
import org.opendatakit.survey.android.utilities.WebUtils;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.MimeTypeMap;

/**
 * Background task for uploading completed forms.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class InstanceUploaderTask extends AsyncTask<ArrayList<String>, Integer, InstanceUploadOutcome> {

    private static String t = "InstanceUploaderTask";
    private InstanceUploaderListener mStateListener;
    // it can take up to 27 seconds to spin up Aggregate
    private static final int CONNECTION_TIMEOUT = 45000;
    private static final String fail = "Error: ";
    private String mAuth = "";

    private InstanceUploadOutcome mOutcome = new InstanceUploadOutcome();

    private void setAuth(String auth) {
        this.mAuth = auth;
    }

    /**
     * Uploads to urlString the submission identified by id with filepath of instance
     * @param urlString destination URL
     * @param id
     * @param instanceFilePath
     * @param toUpdate - Instance URL for recording status update.
     * @param httpclient - client connection
     * @param localContext - context (e.g., credentials, cookies) for client connection
     * @param uriRemap - mapping of Uris to avoid redirects on subsequent invocations
     * @return false if credentials are required and we should terminate immediately.
     */
    private boolean uploadOneSubmission(String urlString, String id, FileSet instanceFiles,
    			Uri toUpdate, HttpClient httpclient, HttpContext localContext, Map<URI, URI> uriRemap) {

        ContentValues cv = new ContentValues();
        URI u = null;
        try {
            URL url = new URL(URLDecoder.decode(urlString, "utf-8"));
            u = url.toURI();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            mOutcome.mResults.put(id,
                fail + "invalid url: " + urlString + " :: details: " + e.getMessage());
            cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
            Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
            return true;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            mOutcome.mResults.put(id,
                fail + "invalid uri: " + urlString + " :: details: " + e.getMessage());
            cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
            Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
            return true;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            mOutcome.mResults.put(id,
                fail + "invalid url: " + urlString + " :: details: " + e.getMessage());
            cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
            Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
            return true;
        }

        boolean openRosaServer = false;
        if (uriRemap.containsKey(u)) {
            // we already issued a head request and got a response,
            // so we know the proper URL to send the submission to
            // and the proper scheme. We also know that it was an
            // OpenRosa compliant server.
            openRosaServer = true;
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
                            URL url =
                                new URL(URLDecoder.decode(locations[0].getValue(), "utf-8"));
                            URI uNew = url.toURI();
                            if (u.getHost().equalsIgnoreCase(uNew.getHost())) {
                                openRosaServer = true;
                                // trust the server to tell us a new location
                                // ... and possibly to use https instead.
                                uriRemap.put(u, uNew);
                                u = uNew;
                            } else {
                                // Don't follow a redirection attempt to a different host.
                                // We can't tell if this is a spoof or not.
                            	mOutcome.mResults.put(
                                    id,
                                    fail
                                            + "Unexpected redirection attempt to a different host: "
                                            + uNew.toString());
                                cv.put(InstanceColumns.XML_PUBLISH_STATUS,
                                    InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                                Survey.getInstance().getContentResolver()
                                        .update(toUpdate, cv, null, null);
                                return true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            mOutcome.mResults.put(id, fail + urlString + " " + e.getMessage());
                            cv.put(InstanceColumns.XML_PUBLISH_STATUS,
                                InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                            Survey.getInstance().getContentResolver()
                                    .update(toUpdate, cv, null, null);
                            return true;
                        }
                    }
                } else {
                    // may be a server that does not handle
                	WebUtils.discardEntityBytes(response);

                    Log.w(t, "Status code on Head request: " + statusCode);
                    if (statusCode >= 200 && statusCode <= 299) {
                    	mOutcome.mResults.put(
                            id,
                            fail
                                    + "Invalid status code on Head request.  If you have a web proxy, you may need to login to your network. ");
                        cv.put(InstanceColumns.XML_PUBLISH_STATUS,
                            InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                        Survey.getInstance().getContentResolver()
                                .update(toUpdate, cv, null, null);
                        return true;
                    }
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                Log.e(t, e.getMessage());
                mOutcome.mResults.put(id, fail + "Client Protocol Exception");
                cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            } catch (ConnectTimeoutException e) {
                e.printStackTrace();
                Log.e(t, e.getMessage());
                mOutcome.mResults.put(id, fail + "Connection Timeout");
                cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            } catch (UnknownHostException e) {
                e.printStackTrace();
                mOutcome.mResults.put(id, fail + e.getMessage() + " :: Network Connection Failed");
                Log.e(t, e.getMessage());
                cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            } catch (SocketTimeoutException e) {
                e.printStackTrace();
                Log.e(t, e.getMessage());
                mOutcome.mResults.put(id, fail + "Connection Timeout");
                cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                mOutcome.mResults.put(id, fail + "Generic Exception");
                Log.e(t, e.getMessage());
                cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
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
            cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
            Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
            return true;
        }

        // find all files in parent directory
        // add media files
        List<File> files = new ArrayList<File>();
        for (File f : instanceFiles.attachmentFiles) {
            String fileName = f.getName();

            int dotIndex = fileName.lastIndexOf(".");
            String extension = "";
            if (dotIndex != -1) {
                extension = fileName.substring(dotIndex + 1);
            }

            if (fileName.startsWith(".")) {
                // ignore invisible files
                continue;
            }
            if (fileName.equals(instanceFile.getName())) {
                continue; // the xml file has already been added
            } else if (openRosaServer) {
                files.add(f);
            } else if (extension.equals("jpg")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("3gpp")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("3gp")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("mp4")) { // legacy 0.9x
                files.add(f);
            } else {
                Log.w(t, "unrecognized file type " + f.getName());
            }
        }

        boolean first = true;
        int j = 0;
        int lastJ;
        while (j < files.size() || first) {
        	lastJ = j;
            first = false;

            HttpPost httppost = WebUtils.createOpenRosaHttpPost(u, mAuth);

            MimeTypeMap m = MimeTypeMap.getSingleton();

            long byteCount = 0L;

            // mime post
            MultipartEntity entity = new MultipartEntity();

            // add the submission file first...
            FileBody fb = new FileBody(instanceFile, "text/xml");
            entity.addPart("xml_submission_file", fb);
            Log.i(t, "added xml_submission_file: " + instanceFile.getName());
            byteCount += instanceFile.length();

            for (; j < files.size(); j++) {
                File f = files.get(j);
                String fileName = f.getName();
                int idx = fileName.lastIndexOf(".");
                String extension = "";
                if (idx != -1) {
                    extension = fileName.substring(idx + 1);
                }
                String contentType = m.getMimeTypeFromExtension(extension);

                // we will be processing every one of these, so
                // we only need to deal with the content type determination...
                if (extension.equals("xml")) {
                    fb = new FileBody(f, "text/xml");
                    entity.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added xml file " + f.getName());
                } else if (extension.equals("jpg")) {
                    fb = new FileBody(f, "image/jpeg");
                    entity.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added image file " + f.getName());
                } else if (extension.equals("3gpp")) {
                    fb = new FileBody(f, "audio/3gpp");
                    entity.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added audio file " + f.getName());
                } else if (extension.equals("3gp")) {
                    fb = new FileBody(f, "video/3gpp");
                    entity.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added video file " + f.getName());
                } else if (extension.equals("mp4")) {
                    fb = new FileBody(f, "video/mp4");
                    entity.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added video file " + f.getName());
                } else if (extension.equals("csv")) {
                    fb = new FileBody(f, "text/csv");
                    entity.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added csv file " + f.getName());
                } else if (f.getName().endsWith(".amr")) {
                    fb = new FileBody(f, "audio/amr");
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added audio file " + f.getName());
                } else if (extension.equals("xls")) {
                    fb = new FileBody(f, "application/vnd.ms-excel");
                    entity.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added xls file " + f.getName());
                } else if (contentType != null) {
                    fb = new FileBody(f, contentType);
                    entity.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t,
                        "added recognized filetype (" + contentType + ") " + f.getName());
                } else {
                    contentType = "application/octet-stream";
                    fb = new FileBody(f, contentType);
                    entity.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.w(t, "added unrecognized file (" + contentType + ") " + f.getName());
                }

                // we've added at least one attachment to the request...
                if (j + 1 < files.size()) {
                    if ((j-lastJ+1 > 100) || (byteCount + files.get(j + 1).length() > 10000000L)) {
                        // the next file would exceed the 10MB threshold...
                        Log.i(t, "Extremely long post is being split into multiple posts");
                        try {
                            StringBody sb = new StringBody("yes", Charset.forName("UTF-8"));
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
                    	mOutcome.mResults.put(id, fail + response.getStatusLine().getReasonPhrase()
                                + " (" + responseCode + ") at " + urlString);
                    }
                    cv.put(InstanceColumns.XML_PUBLISH_STATUS,
                        InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                    Survey.getInstance().getContentResolver()
                            .update(toUpdate, cv, null, null);
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
                mOutcome.mResults.put(id, fail + "Generic Exception. " + e.getMessage());
                cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);
                Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            }
        }

        // if it got here, it must have worked
        mOutcome.mResults.put(id, Survey.getInstance().getString(R.string.success));
        cv.put(InstanceColumns.XML_PUBLISH_STATUS, InstanceProviderAPI.STATUS_SUBMITTED);
        Survey.getInstance().getContentResolver().update(toUpdate, cv, null, null);
        return true;
    }

    /**
     * This method actually writes the xml to disk.
     * @param payload
     * @param path
     * @return
     */
    private static boolean exportXmlFile(String payload, File outputFilePath) {
        // write xml file
    	FileOutputStream os = null;
    	OutputStreamWriter osw = null;
        try {
        	os = new FileOutputStream(outputFilePath);
        	osw = new OutputStreamWriter(os, "UTF-8");
        	osw.write(payload);
        	osw.flush();
        	osw.close();
            return true;

        } catch (IOException e) {
            Log.e(t, "Error writing XML file");
            e.printStackTrace();
            return false;
        } finally {
        	try {
				osw.close();
	        	os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    }

    /**
     * Generate the XML submission record from the data in cursor.
     * As we traverse cursor, collect the list of files that should
     * be sent along with the submission into the Fileset.
     *
     * @param fi
     * @param instanceId
     * @param c
     * @param freturn
     * @return
     */
    private String getSubmissionXml(FormInfo fi, String instanceId, Cursor c, FileSet freturn) {
    	return "";
    }

    /**
     * Write's the data to the sdcard, and updates the instances content provider.
     * In theory we don't have to write to disk, and this is where you'd add
     * other methods.
     * @param markCompleted
     * @return
     */
    private FileSet constructSubmissionFiles(FormInfo fi, String instanceId, Cursor c) {

    	File instanceFolder = new File(MainMenuActivity.getInstanceFolder(instanceId));
    	File instanceXml = new File(instanceFolder, "instance.xml");
    	File submissionXml = new File(instanceFolder, "submission.xml");

    	FileSet freturn = new FileSet();
    	freturn.instanceFile = instanceXml;

        boolean isEncrypted = false;

        instanceXml.delete();
        submissionXml.delete();

        // build a submission.xml to hold the data being submitted
        // and (if appropriate) encrypt the files on the side

        // pay attention to the ref attribute of the submission profile...
        String payload = getSubmissionXml(fi, instanceId, c, freturn);

        // see if the form is encrypted and we can encrypt it...
        EncryptedFormInformation formInfo = EncryptionUtils.getEncryptedFormInformation(fi, instanceId);
        if ( formInfo != null ) {
            // write out submission.xml -- the data to encrypt before sending to aggregate
            exportXmlFile(payload, submissionXml);

            // if we are encrypting, the form cannot be reopened afterward
            // and encrypt the submission (this is a one-way operation)...
            if ( !EncryptionUtils.generateEncryptedSubmission(submissionXml, instanceXml, formInfo) ) {
                return null;
            }
            isEncrypted = true;
        } else {
            exportXmlFile(payload, instanceXml);

        }

        // At this point, we have:
        // 1. the saved instanceXml to be sent to server,
        // 2. all the encrypted attachments if encrypting (isEncrypted = true).
        // 3. all the plaintext attachments
        // 4. and the plaintext instance.xml (as submission.xml) if encrypting
        //

        // if encrypted, delete all plaintext files
        // (anything not named instanceXml or anything not ending in .enc)
        if ( isEncrypted ) {
            if ( !EncryptionUtils.deletePlaintextFiles(instanceXml) ) {
                Log.e(t, "Error deleting plaintext files for " + instanceXml.getAbsolutePath());
            }
        }
        return freturn;
    }


    // TODO: This method is like 350 lines long, down from 400.
    // still. ridiculous. make it smaller.
    @Override
    protected InstanceUploadOutcome doInBackground(ArrayList<String>... values) {
    	mOutcome.mResults = new HashMap<String, String>();
    	mOutcome.mAuthRequestingServer = null;

    	SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(Survey.getInstance());
        String auth = settings.getString(PreferencesActivity.KEY_AUTH, "");
        setAuth(auth);

        FormInfo fi = new FormInfo(MainMenuActivity.currentForm.formDefFile);
        // get shared HttpContext so that authentication and cookies are retained.
        HttpContext localContext = Survey.getInstance().getHttpContext();
        HttpClient httpclient = WebUtils.createHttpClient(CONNECTION_TIMEOUT);

        Map<URI, URI> uriRemap = new HashMap<URI, URI>();

        ArrayList<String> toUpload = values[0];

        for ( int i = 0 ; i < toUpload.size() ; ++i ) {
            if (isCancelled()) {
                return mOutcome;
            }
	        Cursor c = null;
	        try {
	        	// construct the Uri for the instance record
	            Uri forTable = Uri.withAppendedPath(InstanceColumns.CONTENT_URI,
	            		MainMenuActivity.currentForm.tableId + "/" + StringEscapeUtils.escapeHtml4(toUpload.get(i)));
	        	c = Survey.getInstance().getContentResolver().query(forTable, null, null, null, null);

	        	if ( c.getCount() != 1 || !c.moveToNext() ) {
	        		Log.w(t, "Unexpected failure to retrieve instance: " + toUpload.get(i));
	        		mOutcome.mResults.put(toUpload.get(i), fail + "unable to access: " + forTable.toString());
	        	} else {
	                publishProgress(i + 1, c.getCount());
	                String id = c.getString(c.getColumnIndex(InstanceColumns._ID));
	                FileSet instanceFiles = constructSubmissionFiles(fi, id, c);
	                String urlString = fi.xmlSubmissionUrl;
	                if (urlString == null) {
	                    urlString = settings.getString(PreferencesActivity.KEY_SERVER_URL, null);
	                    // NOTE: /submission must not be translated! It is the well-known path on the server.
	                    String submissionUrl =
	                        settings.getString(PreferencesActivity.KEY_SUBMISSION_URL, "/submission");
	                    urlString = urlString + submissionUrl;
	                }

	                if ( !uploadOneSubmission(urlString, id, instanceFiles, forTable, httpclient, localContext, uriRemap) ) {
	                	return null; // get credentials...
	                }
	            }
	        } finally {
	            if (c != null) {
	                c.close();
	            }
	        }
        }

        return mOutcome;
    }


    @Override
    protected void onPostExecute(InstanceUploadOutcome outcome) {
        synchronized (this) {
            if (mStateListener != null) {
                mStateListener.uploadingComplete(mOutcome);
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


    public void setUploaderListener(InstanceUploaderListener sl) {
        synchronized (this) {
            mStateListener = sl;
        }
    }

	public InstanceUploadOutcome getResult() {
		return mOutcome;
	}
}
