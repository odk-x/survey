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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.kxml2.kdom.Element;
import org.opendatakit.common.android.logic.FormInfo;
import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.utilities.DocumentFetchResult;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebUtils;
import org.opendatakit.httpclientandroidlib.HttpResponse;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.client.methods.HttpGet;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.listeners.FormDownloaderListener;
import org.opendatakit.survey.android.logic.FormDetails;
import org.opendatakit.survey.android.logic.PropertiesSingleton;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.provider.FormsProviderAPI;

import android.app.Application;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Background task for downloading a given list of forms. We assume right now
 * that the forms are coming from the same server that presented the form list,
 * but theoretically that won't always be true.
 *
 * @author msundt
 * @author carlhartung
 */
public class DownloadFormsTask extends AsyncTask<FormDetails, String, HashMap<String, String>> {

  private static final String t = "DownloadFormsTask";

  private Application appContext;
  private FormDownloaderListener mStateListener;
  private String appName;
  private HashMap<String, String> mResult;

  private static final String NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_MANIFEST = "http://openrosa.org/xforms/xformsManifest";

  private String mAuth = "";

  private void setAuth(String auth) {
    this.mAuth = auth;
  }

  private boolean isXformsManifestNamespacedElement(Element e) {
    return e.getNamespace().equalsIgnoreCase(NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_MANIFEST);
  }

  /** used only within doInBackground */
  static enum DirType {
    FORMS, FRAMEWORK, OTHER
  };

  @Override
  protected HashMap<String, String> doInBackground(FormDetails... values) {

    PropertiesSingleton propSingleton = PropertiesSingleton.INSTANCE;
    String auth = propSingleton.getProperty(PreferencesActivity.KEY_AUTH);
    setAuth(auth);

    int total = values.length;
    int count = 1;

    HashMap<String, String> result = new HashMap<String, String>();

    for (int i = 0; i < total; i++) {
      FormDetails fd = values[i];

      if (isCancelled()) {
        result.put(fd.formID, "cancelled");
        return result;
      }

      publishProgress(fd.formID, Integer.valueOf(count).toString(), Integer.valueOf(total)
          .toString());

      String message = "";
      /*
       * Downloaded forms are placed in a staging directory (STALE_FORMS_PATH).
       * Once they are deemed complete, they are atomically moved into the
       * FORMS_PATH. For atomicity, the Form definition file will be stored
       * WITHIN the media folder.
       */

      /* download to here... under STALE_FORMS_PATH */
      File tempFormPath = null;
      /* download to here.. under STALE_FORMS_PATH */
      File tempMediaPath = null;
      /* existing with exactly the same formVersion is here... */
      File existingMediaPath = null;
      /* existing to be moved here... under STALE_FORMS_PATH */
      File staleMediaPath = null;
      /* after downloaded and unpacked, move it here... */
      // File formPath = null; // don't know tableId yet...
      /* after downloaded and unpacked, move it here... */
      // File mediaPath = null; // don't know tableId yet...
      try {
        /*
         * path to the directory containing the newly downloaded or stale data
         */
        String baseStaleMediaPath;
        boolean isFramework;
        if (fd.formID.equals(FormsColumns.COMMON_BASE_FORM_ID)) {
          /*
           * the Common Javascript Framework is stored in the Framework
           * directories
           */
          baseStaleMediaPath = ODKFileUtils.getStaleFrameworkFolder(getAppName()) + File.separator;
          isFramework = true;
        } else {
          baseStaleMediaPath = ODKFileUtils.getStaleFormsFolder(getAppName()) + File.separator;
          isFramework = false;
        }
        // /* path to the directory containing the live data */
        // String baseMediaPath;
        // if ( isFramework ) {
        // baseMediaPath = ODKFileUtils.getFrameworkFolder(getAppName())
        // + File.separator;
        // } else
        // baseMediaPath = ODKFileUtils.getFormsFolder(getAppName(), tableId,
        // formId)
        // + File.separator;
        // }

        try {
          // clean up friendly form name...
          String rootName = fd.formID;
          if (!rootName.matches("^\\p{L}\\p{M}*(\\p{L}\\p{M}*|\\p{Nd}|_)*$")) {
            // error!
            message += appContext.getString(R.string.invalid_form_id, fd.formID, fd.formName);
            Log.e(t, "Invalid form_id: " + fd.formID + " for: " + fd.formName);
          } else {

            // figure out what to name this when we move it
            // out of /odk/appname/tables/tableId/forms/formId...
            int rev = 2;
            {
              // proposed name of form 'media' directory and form
              // file...
              String tempMediaPathName = baseStaleMediaPath + rootName;

              tempMediaPath = new File(tempMediaPathName);
              tempFormPath = new File(tempMediaPath, ODKFileUtils.FILENAME_XFORMS_XML);

              while (tempMediaPath.exists()) {
                try {
                  if (tempMediaPath.exists()) {
                    FileUtils.deleteDirectory(tempMediaPath);
                  }
                  Log.i(t, "Successful delete of stale directory: " + tempMediaPathName);
                } catch (IOException ex) {
                  ex.printStackTrace();
                  Log.i(t, "Unable to delete stale directory: " + tempMediaPathName);
                }
                tempMediaPathName = baseStaleMediaPath + rootName + "_" + rev;
                tempMediaPath = new File(tempMediaPathName);
                tempFormPath = new File(tempMediaPath, ODKFileUtils.FILENAME_XFORMS_XML);
                rev++;
              }
            }

            // and find a name that any existing directory could be
            // renamed to... (continuing rev counter)
            String staleMediaPathName = baseStaleMediaPath + rootName + "_" + rev;
            staleMediaPath = new File(staleMediaPathName);

            while (staleMediaPath.exists()) {
              try {
                if (staleMediaPath.exists()) {
                  FileUtils.deleteDirectory(staleMediaPath);
                }
                Log.i(t, "Successful delete of stale directory: " + staleMediaPathName);
              } catch (IOException ex) {
                ex.printStackTrace();
                Log.i(t, "Unable to delete stale directory: " + staleMediaPathName);
              }
              staleMediaPathName = baseStaleMediaPath + rootName + "_" + rev;
              staleMediaPath = new File(staleMediaPathName);
              rev++;
            }

            // we have a candidate mediaPath and formPath.
            // If this is a replacement for an identical existing form,
            // update formPath and mediaPath
            // to be the paths for the existing form. We will be doing
            // atomic moves of the mediaPath
            // to swap in the new files.

            Cursor alreadyExists = null;
            try {
              String[] projection = { FormsColumns.FORM_ID,
                  FormsColumns.APP_RELATIVE_FORM_FILE_PATH,
                  FormsColumns.APP_RELATIVE_FORM_MEDIA_PATH, FormsColumns.FORM_VERSION };
              //
              // find if there is already a form definition with the
              // same formId and formVersion...
              String selection = FormsColumns.FORM_ID + "=? AND " + FormsColumns.FORM_VERSION
                  + ((fd.formVersion == null) ? " IS NULL" : "=?");
              String[] selectionArgs;
              if (fd.formVersion == null) {
                String[] tempArgs = { fd.formID };
                selectionArgs = tempArgs;
              } else {
                String[] tempArgs = { fd.formID, fd.formVersion };
                selectionArgs = tempArgs;
              }
              // use the most recently created of the matches
              // (in case DB is corrupted)
              String orderBy = FormsColumns.DATE + " DESC";
              alreadyExists = appContext.getContentResolver().query(
                  Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName), projection,
                  selection, selectionArgs, orderBy);

              if (alreadyExists == null) {
                message += appContext.getString(R.string.invalid_app_name, appName);
                Log.e(t, "Invalid appName: " + appName);
              } else if (alreadyExists.getCount() > 0) {
                // we found a match...
                alreadyExists.moveToFirst();
                int appRelativeFormMediaPathIdx = alreadyExists
                    .getColumnIndex(FormsColumns.APP_RELATIVE_FORM_MEDIA_PATH);
                existingMediaPath = ODKFileUtils.asAppFile(appName,
                    alreadyExists.getString(appRelativeFormMediaPathIdx));
              }
            } finally {
              if (alreadyExists != null) {
                alreadyExists.close();
              }
            }

            if (fd.manifestUrl == null) {
              message += appContext.getString(R.string.no_manifest, fd.formID);
              Log.e(t, "No Manifest for: " + fd.formID);
            } else {
              // download the media files -- it is an error if there
              // are none...
              String error = downloadManifestAndMediaFiles(tempMediaPath, existingMediaPath, fd,
                  count, total);
              if (error != null) {
                message += error;
              } else {
                error = explodeZips(fd, tempMediaPath, count, total);
                if (error != null) {
                  message += error;
                } else if (tempFormPath.exists()) {
                  message += appContext.getString(R.string.xforms_file_exists,
                      ODKFileUtils.FILENAME_XFORMS_XML, fd.formID);
                  Log.e(t, ODKFileUtils.FILENAME_XFORMS_XML
                      + " was present in exploded download of " + fd.formID);
                } else {
                  // OK so far -- download the form definition
                  // file
                  // note that this is the reverse order from ODK1
                  downloadXform(fd, tempFormPath, new File(existingMediaPath,
                      ODKFileUtils.FILENAME_XFORMS_XML));
                }
              }
            }
          }
        } catch (SocketTimeoutException se) {
          se.printStackTrace();
          message += se.getMessage();
        } catch (Exception e) {
          e.printStackTrace();
          if (e.getCause() != null) {
            message += e.getCause().getMessage();
          } else {
            message += e.getMessage();
          }
        }

        // OK. Everything is downloaded and present in the tempMediaPath
        // directory...
        if (message.equals("")) {
          File mediaPath = null;
          try {
            File formDef = new File(tempMediaPath, ODKFileUtils.FORMDEF_JSON_FILENAME);

            if (!formDef.exists()) {
              message = appContext.getString(R.string.no_formdef_json, fd.formID);
              Log.e(t, ODKFileUtils.FORMDEF_JSON_FILENAME
                  + " was not found in exploded download of " + fd.formID);
            } else {
              // we don't know where to move it until we parse
              // the file to extract the tableId.
              FormInfo fi = new FormInfo(appContext, appName, formDef);
              if ( !fd.formID.equals(fi.formId) ) {
                message = appContext.getString(R.string.mismatched_form_id_info, fd.formID);
                Log.e(t, "formDef.json contained a different formId (" + fi.formId + ") than the one requested (" + fd.formID + ").");
              } else if ( fd.formVersion != fi.formVersion && !fd.formVersion.equals(fi.formVersion) ) {
                message = appContext.getString(R.string.mismatched_form_version_info, fd.formID, fd.formVersion);
                Log.e(t, "formDef.json contained a different formId (" + fi.formVersion + ") than the one requested (" + fd.formVersion + ").");
              } else {
                if ( isFramework ) {
                  mediaPath = new File(ODKFileUtils.getFrameworkFolder(appName));
                } else {
                  mediaPath = new File(ODKFileUtils.getFormFolder(appName, fi.tableId, fi.formId));
                }
                // ensure the destination parent directory structure exists
                mediaPath.getParentFile().mkdirs();
                // move any current directory to the stale tree.
                if (mediaPath.exists()) {
                  FileUtils.moveDirectory(mediaPath, staleMediaPath);
                }
                // move the temp directory to the current location.
                FileUtils.moveDirectory(tempMediaPath, mediaPath);
                // the background listener should update the database
              }
            }
          } catch (IOException ex) {
            ex.printStackTrace();
            message += ex.toString();
            if (mediaPath != null && staleMediaPath.exists()) {
              // try to move this back, since we failed somehow...
              try {
                if (mediaPath.exists()) {
                  FileUtils.deleteDirectory(mediaPath);
                }
                FileUtils.moveDirectory(staleMediaPath, mediaPath);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          }
        }
      } finally {
        if (!message.equalsIgnoreCase("")) {
          // failure...
          // we should always delete the temp file / directory.
          // it is always a new file / directory, so there is no harm
          // doing this.
          if (tempMediaPath.exists()) {
            try {
              FileUtils.deleteDirectory(tempMediaPath);
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        } else {
          message = appContext.getString(R.string.success);
        }
      }
      count++;
      result.put(fd.formID, message);
    }

    return result;
  }

  private String explodeZips(FormDetails fd, File tempMediaPath, int count, int total) {
    String message = "";

    File[] zips = tempMediaPath.listFiles(new FileFilter() {

      @Override
      public boolean accept(File pathname) {
        String name = pathname.getName();
        return pathname.isFile() && name.substring(name.lastIndexOf('.') + 1).equals("zip");
      }
    });

    int zipCount = 0;
    for (File zipfile : zips) {
      ZipFile f = null;
      zipCount++;

      try {
        f = new ZipFile(zipfile, ZipFile.OPEN_READ);
        Enumeration<? extends ZipEntry> entries = f.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          File tempFile = new File(tempMediaPath, entry.getName());
          String formattedString = appContext.getString(R.string.form_download_unzipping,
              fd.formID, zipfile.getName(), Integer.valueOf(zipCount).toString(),
              Integer.valueOf(zips.length).toString(), entry.getName());
          publishProgress(formattedString, Integer.valueOf(count).toString(), Integer
              .valueOf(total).toString());
          if (entry.isDirectory()) {
            tempFile.mkdirs();
          } else {
            tempFile.getParentFile().mkdirs();
            int bufferSize = 8192;
            InputStream in = new BufferedInputStream(f.getInputStream(entry), bufferSize);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile, false),
                bufferSize);

            // this is slow in the debugger....
            int value;
            while ((value = in.read()) != -1) {
              out.write(value);
            }
            out.flush();
            out.close();
            in.close();
          }
          Log.i(t, "Extracted ZipEntry: " + entry.getName());
        }
      } catch (IOException e) {
        e.printStackTrace();
        if (e.getCause() != null) {
          message += e.getCause().getMessage();
        } else {
          message += e.getMessage();
        }
      } finally {
        if (f != null) {
          try {
            f.close();
          } catch (IOException e) {
            e.printStackTrace();
            Log.e(t, "Closing of ZipFile failed: " + e.toString());
          }
        }
      }
    }

    return (message.equals("") ? null : message);
  }

  /**
   * Takes the FormDetails, the temp file and any existing file, and downloads
   * the file from the server if it is different from the local copy. Otherwise,
   * it simply does a local copy of the existing file into the temp file.
   *
   * @param fd
   * @param tempFormPath
   *          -- file to be created
   * @param formPath
   *          -- existing file, if it exists
   * @throws Exception
   */
  private void downloadXform(FormDetails fd, File tempFormPath, File formPath) throws Exception {

    String currentHash = "-";
    if (formPath != null && formPath.exists()) {
      currentHash = ODKFileUtils.getMd5Hash(formPath);
    }
    if (currentHash.equals(fd.hash)) {
      // no need for the network retrieval -- use local copy
      FileUtils.copyFile(formPath, tempFormPath);
      Log.i(t,
          "Skipping form file fetch -- file hashes identical: " + tempFormPath.getAbsolutePath());
    } else {
      downloadFile(tempFormPath, fd.downloadUrl);
    }
  }

  /**
   * Common routine to download a document from the downloadUrl and save the
   * contents in the file 'f'. Shared by media file download and form file
   * download.
   *
   * @param f
   * @param downloadUrl
   * @throws Exception
   */
  private void downloadFile(File f, String downloadUrl) throws Exception {
    URI uri = null;
    try {
      // assume the downloadUrl is escaped properly
      URL url = new URL(downloadUrl);
      uri = url.toURI();
    } catch (MalformedURLException e) {
      e.printStackTrace();
      throw e;
    } catch (URISyntaxException e) {
      e.printStackTrace();
      throw e;
    }

    // WiFi network connections can be renegotiated during a large form
    // download sequence.
    // This will cause intermittent download failures. Silently retry once
    // after each
    // failure. Only if there are two consecutive failures, do we abort.
    boolean success = false;
    int attemptCount = 1;
    while (!success && attemptCount++ <= 2) {
      if (isCancelled()) {
        throw new Exception("cancelled");
      }

      // get shared HttpContext so that authentication and cookies are
      // retained.
      HttpContext localContext = WebUtils.getHttpContext();

      HttpClient httpclient = WebUtils.createHttpClient(WebUtils.CONNECTION_TIMEOUT);

      // set up request...
      HttpGet req = WebUtils.createOpenRosaHttpGet(uri, mAuth);

      HttpResponse response = null;
      try {
        response = httpclient.execute(req, localContext);
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != 200) {
          WebUtils.discardEntityBytes(response);
          String errMsg = appContext.getString(R.string.file_fetch_failed, downloadUrl, response
              .getStatusLine().getReasonPhrase(), statusCode);
          Log.e(t, errMsg);
          throw new Exception(errMsg);
        }

        // write connection to file
        InputStream is = null;
        OutputStream os = null;
        try {
          is = response.getEntity().getContent();
          os = new FileOutputStream(f);
          byte buf[] = new byte[1024];
          int len;
          while ((len = is.read(buf)) > 0) {
            os.write(buf, 0, len);
          }
          os.flush();
          success = true;
        } finally {
          if (os != null) {
            try {
              os.close();
            } catch (Exception e) {
            }
          }
          if (is != null) {
            try {
              // ensure stream is consumed...
              final long count = 1024L;
              while (is.skip(count) == count)
                ;
            } catch (Exception e) {
              // no-op
            }
            try {
              is.close();
            } catch (Exception e) {
            }
          }
        }

      } catch (Exception e) {
        WebUtils.clearHttpConnectionManager();
        Log.e(t, e.toString());
        e.printStackTrace();
        if (attemptCount != 1) {
          throw e;
        }
      }
    }
  }

  private static class MediaFile {
    final String filename;
    final String hash;
    final String downloadUrl;

    MediaFile(String filename, String hash, String downloadUrl) {
      this.filename = filename;
      this.hash = hash;
      this.downloadUrl = downloadUrl;
    }
  }

  private String downloadManifestAndMediaFiles(File tempMediaPath, File mediaPath, FormDetails fd,
      int count, int total) {
    if (fd.manifestUrl == null)
      return null;

    publishProgress(appContext.getString(R.string.fetching_manifest, fd.formID),
        Integer.valueOf(count).toString(), Integer.valueOf(total).toString());

    List<MediaFile> files = new ArrayList<MediaFile>();
    // get shared HttpContext so that authentication and cookies are
    // retained.
    HttpContext localContext = WebUtils.getHttpContext();

    HttpClient httpclient = WebUtils.createHttpClient(WebUtils.CONNECTION_TIMEOUT);

    DocumentFetchResult result = WebUtils.getXmlDocument(fd.manifestUrl, localContext, httpclient,
        mAuth);

    if (result.errorMessage != null) {
      return result.errorMessage;
    }

    String errMessage = appContext.getString(R.string.access_error, fd.manifestUrl);

    if (!result.isOpenRosaResponse) {
      errMessage += appContext.getString(R.string.manifest_server_error);
      Log.e(t, errMessage);
      return errMessage;
    }

    // Attempt OpenRosa 1.0 parsing
    Element manifestElement = result.doc.getRootElement();
    if (!manifestElement.getName().equals("manifest")) {
      errMessage += appContext.getString(R.string.root_element_error, manifestElement.getName());
      Log.e(t, errMessage);
      return errMessage;
    }
    String namespace = manifestElement.getNamespace();
    if (!isXformsManifestNamespacedElement(manifestElement)) {
      errMessage += appContext.getString(R.string.root_namespace_error, namespace);
      Log.e(t, errMessage);
      return errMessage;
    }
    int nElements = manifestElement.getChildCount();
    for (int i = 0; i < nElements; ++i) {
      if (manifestElement.getType(i) != Element.ELEMENT) {
        // e.g., whitespace (text)
        continue;
      }
      Element mediaFileElement = (Element) manifestElement.getElement(i);
      if (!isXformsManifestNamespacedElement(mediaFileElement)) {
        // someone else's extension?
        continue;
      }
      String name = mediaFileElement.getName();
      if (name.equalsIgnoreCase("mediaFile")) {
        String filename = null;
        String hash = null;
        String downloadUrl = null;
        // don't process descriptionUrl
        int childCount = mediaFileElement.getChildCount();
        for (int j = 0; j < childCount; ++j) {
          if (mediaFileElement.getType(j) != Element.ELEMENT) {
            // e.g., whitespace (text)
            continue;
          }
          Element child = mediaFileElement.getElement(j);
          if (!isXformsManifestNamespacedElement(child)) {
            // someone else's extension?
            continue;
          }
          String tag = child.getName();
          if (tag.equals("filename")) {
            filename = ODKFileUtils.getXMLText(child, true);
            if (filename != null && filename.length() == 0) {
              filename = null;
            }
          } else if (tag.equals("hash")) {
            hash = ODKFileUtils.getXMLText(child, true);
            if (hash != null && hash.length() == 0) {
              hash = null;
            }
          } else if (tag.equals("downloadUrl")) {
            downloadUrl = ODKFileUtils.getXMLText(child, true);
            if (downloadUrl != null && downloadUrl.length() == 0) {
              downloadUrl = null;
            }
          }
        }
        if (filename == null || downloadUrl == null || hash == null) {
          errMessage += appContext.getString(R.string.manifest_tag_error, Integer.toString(i));
          Log.e(t, errMessage);
          return errMessage;
        }
        files.add(new MediaFile(filename, hash, downloadUrl));
      }
    }

    // OK we now have the full set of files to download...
    Log.i(t, "Downloading " + files.size() + " media files.");
    int mediaCount = 0;
    if (files.size() > 0) {
      tempMediaPath.mkdirs();
      for (MediaFile toDownload : files) {
        if (isCancelled()) {
          return "cancelled";
        }
        ++mediaCount;
        publishProgress(appContext.getString(R.string.form_download_progress, fd.formID,
            toDownload.filename, Integer.valueOf(mediaCount).toString(),
            Integer.valueOf(files.size()).toString()), Integer.valueOf(count).toString(), Integer
            .valueOf(total).toString());
        try {
          File existingMediaFile = new File(mediaPath, toDownload.filename);
          File mediaFile = new File(tempMediaPath, toDownload.filename);

          String currentHash = "-";
          if (existingMediaFile != null && existingMediaFile.exists()) {
            currentHash = ODKFileUtils.getMd5Hash(existingMediaFile);
          }
          if (currentHash.equals(toDownload.hash)) {
            // no need for the network retrieval -- use local copy
            FileUtils.copyFile(existingMediaFile, mediaFile);
            Log.i(
                t,
                "Skipping media file fetch -- file hashes identical: "
                    + mediaFile.getAbsolutePath());
          } else {
            downloadFile(mediaFile, toDownload.downloadUrl);
          }
        } catch (Exception e) {
          return e.getLocalizedMessage();
        }
      }
    } else {
      return appContext.getString(R.string.no_manifest, fd.formID);
    }
    return null;
  }

  @Override
  protected void onPostExecute(HashMap<String, String> value) {
    synchronized (this) {
      mResult = value;
      if (mStateListener != null) {
        mStateListener.formsDownloadingComplete(value);
      }
    }
  }

  @Override
  protected void onCancelled(HashMap<String, String> result) {
    synchronized (this) {
      if (result == null) {
        mResult = new HashMap<String, String>();
        mResult.put("unknown", "cancelled");
      } else {
        mResult = result;
      }
      if (mStateListener != null) {
        mStateListener.formsDownloadingComplete(mResult);
      }
    }
  }

  @Override
  protected void onProgressUpdate(String... values) {
    synchronized (this) {
      if (mStateListener != null) {
        // update progress and total
        mStateListener.formDownloadProgressUpdate(values[0], Integer.valueOf(values[1]),
            Integer.valueOf(values[2]));
      }
    }

  }

  public HashMap<String, String> getResult() {
    return mResult;
  }

  public void setDownloaderListener(FormDownloaderListener sl) {
    synchronized (this) {
      mStateListener = sl;
    }
  }

  public void setAppName(String appName) {
    synchronized (this) {
      this.appName = appName;
    }
  }

  public String getAppName() {
    return appName;
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
