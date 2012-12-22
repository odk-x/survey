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
import org.opendatakit.survey.android.R;
import org.opendatakit.httpclientandroidlib.HttpResponse;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.client.methods.HttpGet;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.listeners.FormDownloaderListener;
import org.opendatakit.survey.android.logic.FormDetails;
import org.opendatakit.survey.android.preferences.PreferencesActivity;
import org.opendatakit.survey.android.provider.FormsProviderAPI;
import org.opendatakit.survey.android.provider.FormsProviderAPI.FormsColumns;
import org.opendatakit.survey.android.utilities.DocumentFetchResult;
import org.opendatakit.survey.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.utilities.WebUtils;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Background task for downloading a given list of forms. We assume right now that the forms are
 * coming from the same server that presented the form list, but theoretically that won't always be
 * true.
 *
 * @author msundt
 * @author carlhartung
 */
public class DownloadFormsTask extends
        AsyncTask<ArrayList<FormDetails>, String, HashMap<String, String>> {

    private static final String t = "DownloadFormsTask";

    private FormDownloaderListener mStateListener;
    private HashMap<String, String> mResult;

    private static final String NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_MANIFEST =
        "http://openrosa.org/xforms/xformsManifest";

    private String mAuth = "";


    private void setAuth(String auth) {
        this.mAuth = auth;
    }


    private boolean isXformsManifestNamespacedElement(Element e) {
        return e.getNamespace().equalsIgnoreCase(NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_MANIFEST);
    }


    @Override
    protected HashMap<String, String> doInBackground(ArrayList<FormDetails>... values) {
        ArrayList<FormDetails> toDownload = values[0];

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(Survey.getInstance());
		String auth = settings.getString(PreferencesActivity.KEY_AUTH, "");
		setAuth(auth);

        int total = toDownload.size();
        int count = 1;

        HashMap<String, String> result = new HashMap<String, String>();

        for (int i = 0; i < total; i++) {
            FormDetails fd = toDownload.get(i);

        	if ( isCancelled() ) {
        		result.put(fd.formName, "cancelled");
        		return result;
        	}

            publishProgress(fd.formName, Integer.valueOf(count).toString(), Integer.valueOf(total)
                    .toString());

            String message = "";

            /*
             * Downloaded forms are placed in a staging directory (STALE_FORMS_PATH).
             * Once they are deemed complete, they are atomically moved into the FORMS_PATH.
             * For atomicity, the Form definition file will be stored WITHIN the media folder.
             */
            File tempFormPath = null; // to be downloaded here... under STALE_FORMS_PATH
            File tempMediaPath = null; // to be downloaded here... under STALE_FORMS_PATH
            File staleMediaPath = null; // existing to be moved here... under STALE_FORMS_PATH
            File formPath = null; // downloaded to be moved here... under FORMS_PATH
            File mediaPath = null; // downloaded to be moved here... under FORMS_PATH
            try {
	            try {
	            	// determine names for the form file and media directory on the device that do not collide with any existing names

	            	{
		            	// clean up friendly form name...
		                String rootName = fd.formName.replaceAll("[^\\p{L}\\p{Digit}]", " "); // remove non-alpha-numerics
		                rootName = rootName.replaceAll("\\p{javaWhitespace}+", ""); // eliminate white space...
		                rootName = rootName.trim();

		                int rev = 2;
		                {
			                // proposed name of form 'media' directory and form file...
			                String tempMediaPathName = Survey.STALE_FORMS_PATH + File.separator + rootName;
			                String mediaPathName = Survey.FORMS_PATH + File.separator + rootName;

			                tempMediaPath = new File(tempMediaPathName);
		                	tempFormPath = new File(tempMediaPath, FormsProviderAPI.FILENAME_XFORMS_XML);
			                mediaPath = new File(mediaPathName);
		                    formPath = new File(mediaPath, FormsProviderAPI.FILENAME_XFORMS_XML);

			                while (tempMediaPath.exists() || mediaPath.exists()) {
		        				try {
		        					if ( tempMediaPath.exists() ) {
		        						FileUtils.deleteDirectory(tempMediaPath);
		        					}
		        					Log.i(t, "Successful delete of stale directory: " + tempMediaPathName);
		        				} catch ( IOException ex ) {
		        					ex.printStackTrace();
		        					Log.i(t, "Unable to delete stale directory: " + tempMediaPathName);
		        				}
			                	tempMediaPathName = Survey.STALE_FORMS_PATH + File.separator + rootName + "_" + rev;
			                	tempMediaPath = new File(tempMediaPathName);
			                	tempFormPath = new File(tempMediaPath, FormsProviderAPI.FILENAME_XFORMS_XML);
			                	mediaPathName = Survey.FORMS_PATH + File.separator + rootName + "_" + rev;
			                    mediaPath = new File(mediaPathName);
			                    formPath = new File(mediaPath, FormsProviderAPI.FILENAME_XFORMS_XML);
			                    rev++;
			                }
		                }

		                // and find a name that any existing directory could be renamed to... (continuing rev counter)
		                String staleMediaPathName = Survey.STALE_FORMS_PATH + File.separator + rootName + "_" + rev;
		                staleMediaPath = new File(staleMediaPathName);

		                while (staleMediaPath.exists()) {
	        				try {
	        					if ( staleMediaPath.exists() ) {
	        						FileUtils.deleteDirectory(staleMediaPath);
	        					}
	        					Log.i(t, "Successful delete of stale directory: " + staleMediaPathName);
	        				} catch ( IOException ex ) {
	        					ex.printStackTrace();
	        					Log.i(t, "Unable to delete stale directory: " + staleMediaPathName);
	        				}
		                	staleMediaPathName = Survey.FORMS_PATH + File.separator + rootName + "_" + rev;
		                    staleMediaPath = new File(staleMediaPathName);
		                    rev++;
		                }
	            	}

	            	// we have a candidate mediaPath and formPath.
	            	// If this is a replacement for an identical existing form, update formPath and mediaPath
	            	// to be the paths for the existing form.  We will be doing atomic moves of the mediaPath
	            	// to swap in the new files.

	                Cursor alreadyExists = null;
	                try {
	                    String[] projection = {
	                            FormsColumns._ID, FormsColumns.FORM_FILE_PATH, FormsColumns.FORM_MEDIA_PATH, FormsColumns.FORM_VERSION
	                    };
	                    //
	                    // find if there is already a form definition with the same formId and formVersion...
	                	String selection = FormsColumns.FORM_ID + "=? AND " +
        						FormsColumns.FORM_VERSION + ((fd.formVersion == null) ? " IS NULL" : "=?");
			    		String[] selectionArgs;
			    		if ( fd.formVersion == null) {
			    			String[] tempArgs = { fd.formID };
			    			selectionArgs = tempArgs;
			    		} else {
			    			String[] tempArgs = { fd.formID, fd.formVersion };
			    			selectionArgs = tempArgs;
			    		}
			    		String orderBy = FormsColumns.DATE + " DESC"; // use the most recently created of the matches (in case DB corrupted)
	                    alreadyExists = Survey.getInstance()
	                            .getContentResolver()
	                            .query(FormsColumns.CONTENT_URI, projection, selection, selectionArgs, orderBy);

	                    if (alreadyExists.getCount() > 0) {
                    		// we found a match...
	                    	alreadyExists.moveToFirst();
		                	int formFilePath = alreadyExists.getColumnIndex(FormsColumns.FORM_FILE_PATH);
		                	int formMediaPath = alreadyExists.getColumnIndex(FormsColumns.FORM_MEDIA_PATH);
                    		formPath = new File(alreadyExists.getString(formFilePath));
                    		mediaPath = new File(alreadyExists.getString(formMediaPath));
		                }
	                } finally {
	                	if ( alreadyExists != null ) {
	                		alreadyExists.close();
	                	}
	                }

	                if (fd.manifestUrl == null) {
	                	message += Survey.getInstance().getString(R.string.no_manifest, fd.formName);
	                    Log.e(t, "No Manifest for: " + fd.formName);
	                } else {
	                	// download the media files -- it is an error if there are none...
	                    String error =
	                        downloadManifestAndMediaFiles(tempMediaPath, mediaPath, fd, count, total);
	                    if (error != null) {
	                        message += error;
	                    } else {
		                	error = explodeZips(fd, tempMediaPath, count, total);
		                	if ( error != null ) {
		                		message += error;
		                	} else if ( tempFormPath.exists() ) {
	                    		message += Survey.getInstance().getString(R.string.xforms_file_exists, FormsProviderAPI.FILENAME_XFORMS_XML, fd.formName);
	    	                    Log.e(t, FormsProviderAPI.FILENAME_XFORMS_XML + " was present in exploded download of " + fd.formName);
	                    	} else {
		                    	// OK so far -- download the form definition file
		                    	// note that this is the reverse order from ODK1
	                    		downloadXform(fd, tempFormPath, formPath);
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

	            // OK. Everything is downloaded and present in the tempMediaPath directory...
	            if (message.equals("")) {
	            	try {
	                	File formDef = new File(tempMediaPath, FormsProviderAPI.FILENAME_FORM_DEF_JSON);

	                	if ( !formDef.exists()) {
		        			message = Survey.getInstance().getString(R.string.no_formdef_json, fd.formName);
    	                    Log.e(t, FormsProviderAPI.FILENAME_FORM_DEF_JSON + " was not found in exploded download of " + fd.formName);
		        		} else {
	                		// move the current directory to the stale tree.
	                		// move the temp directory to the current location.

	                		// Note that the directory change can occur, and then the
	                		// form definition file change could fail.  This is presumably
	                		// OK since the form definition file contains negligible
	                		// useful info w.r.t. ODK2.

		        			if ( mediaPath.exists() ) {
		        				FileUtils.moveDirectory(mediaPath, staleMediaPath);
		        			}
		        			FileUtils.moveDirectory(tempMediaPath, mediaPath);

		                    // and launch update activity to update our data description
		        			message = DiskSyncTask.updateFormInfo(mediaPath);

		        		}
	            	} catch ( IOException ex ) {
	            		ex.printStackTrace();
	            		message += ex.toString();
	            		if ( staleMediaPath.exists() ) {
	            			// try to move this back, since we failed somehow...
		        			try {
								FileUtils.moveDirectory(staleMediaPath, mediaPath);
							} catch (IOException e) {
								e.printStackTrace();
							}
	            		}
	            	}
	            }
            } finally {
	            if ( !message.equalsIgnoreCase("")) {
	    			// failure...
	            	// we should always delete the temp file / directory.
	            	// it is always a new file / directory, so there is no harm doing this.
	    			if ( tempMediaPath.exists() ) {
	    				try {
							FileUtils.deleteDirectory(tempMediaPath);
						} catch (IOException e) {
							e.printStackTrace();
						}
	    			}
	            } else {
	       			message = Survey.getInstance().getString(R.string.success);
	            }
            }
            count++;
            result.put(fd.formName, message);
        }

        return result;
    }

    private String explodeZips( FormDetails fd, File tempMediaPath, int count, int total ) {
    	String message = "";

        File[] zips = tempMediaPath.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				String name = pathname.getName();
				return pathname.isFile() && name.substring(name.lastIndexOf('.')+1).equals("zip");
			}});

        int zipCount = 0;
    	for ( File zipfile : zips ) {
    		ZipFile f = null;
        	zipCount++;

            try {
				f = new ZipFile(zipfile, ZipFile.OPEN_READ);
				Enumeration<? extends ZipEntry> entries = f.entries();
				while ( entries.hasMoreElements() ) {
					ZipEntry entry = entries.nextElement();
					File tempFile = new File(tempMediaPath, entry.getName());
		        	String formattedString = Survey.getInstance().getString(R.string.form_download_unzipping,
		        			fd.formName, zipfile.getName(),
		            		Integer.valueOf(zipCount).toString(), Integer.valueOf(zips.length).toString(), entry.getName());
		            publishProgress(formattedString,
		            		Integer.valueOf(count).toString(), Integer.valueOf(total).toString());
					if ( entry.isDirectory() ) {
						tempFile.mkdirs();
					} else {
						int bufferSize = 8192;
						InputStream in = new BufferedInputStream(f.getInputStream(entry), bufferSize);
						OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile, false), bufferSize);

						// this is slow in the debugger....
						int value;
						while ( (value=in.read()) != -1 ) {
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
				if ( f != null ) {
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
     * Takes the FormDetails, the temp file and any existing file, and downloads the
     * file from the server if it is different from the local copy. Otherwise, it
     * simply does a local copy of the existing file into the temp file.
     *
     * @param fd
     * @param tempFormPath -- file to be created
     * @param formPath -- existing file, if it exists
     * @throws Exception
     */
    private void downloadXform(FormDetails fd, File tempFormPath, File formPath) throws Exception {

    	String currentHash = "-";
    	if ( formPath != null && formPath.exists() ) {
    		currentHash = FormsProviderAPI.MD5_COLON_PREFIX + ODKFileUtils.getMd5Hash(formPath);
    	}
    	if ( currentHash.equals(fd.hash) ) {
    		// no need for the network retrieval -- use local copy
    		FileUtils.copyFile(formPath, tempFormPath);
        	Log.i(t, "Skipping form file fetch -- file hashes identical: " + tempFormPath.getAbsolutePath());
    	} else {
    		downloadFile(tempFormPath, fd.downloadUrl);
    	}
    }


    /**
     * Common routine to download a document from the downloadUrl and save the contents in the file
     * 'f'. Shared by media file download and form file download.
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

        // WiFi network connections can be renegotiated during a large form download sequence.
        // This will cause intermittent download failures.  Silently retry once after each
        // failure.  Only if there are two consecutive failures, do we abort.
        boolean success = false;
        int attemptCount = 1;
        while ( !success && attemptCount++ <= 2 ) {
        	if ( isCancelled() ) {
        		throw new Exception("cancelled");
        	}

	        // get shared HttpContext so that authentication and cookies are retained.
	        HttpContext localContext = Survey.getInstance().getHttpContext();

	        HttpClient httpclient = WebUtils.createHttpClient(WebUtils.CONNECTION_TIMEOUT);

	        // set up request...
	        HttpGet req = WebUtils.createOpenRosaHttpGet(uri, mAuth);

	        HttpResponse response = null;
	        try {
	            response = httpclient.execute(req, localContext);
	            int statusCode = response.getStatusLine().getStatusCode();

	            if (statusCode != 200) {
	            	WebUtils.discardEntityBytes(response);
	                String errMsg =
	                    Survey.getInstance().getString(R.string.file_fetch_failed, downloadUrl,
	                        response.getStatusLine().getReasonPhrase(), statusCode);
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
	        	Log.e(t, e.toString());
	            e.printStackTrace();
	            if ( attemptCount != 1 ) {
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


    private String downloadManifestAndMediaFiles(File tempMediaPath, File mediaPath, FormDetails fd, int count,
            int total) {
        if (fd.manifestUrl == null)
            return null;

        publishProgress(Survey.getInstance().getString(R.string.fetching_manifest, fd.formName),
            Integer.valueOf(count).toString(), Integer.valueOf(total).toString());

        List<MediaFile> files = new ArrayList<MediaFile>();
        // get shared HttpContext so that authentication and cookies are retained.
        HttpContext localContext = Survey.getInstance().getHttpContext();

        HttpClient httpclient = WebUtils.createHttpClient(WebUtils.CONNECTION_TIMEOUT);

        DocumentFetchResult result =
            WebUtils.getXmlDocument(fd.manifestUrl, localContext, httpclient, mAuth);

        if (result.errorMessage != null) {
            return result.errorMessage;
        }

        String errMessage = Survey.getInstance().getString(R.string.access_error, fd.manifestUrl);

        if (!result.isOpenRosaResponse) {
            errMessage += Survey.getInstance().getString(R.string.manifest_server_error);
            Log.e(t, errMessage);
            return errMessage;
        }

        // Attempt OpenRosa 1.0 parsing
        Element manifestElement = result.doc.getRootElement();
        if (!manifestElement.getName().equals("manifest")) {
            errMessage +=
                Survey.getInstance().getString(R.string.root_element_error,
                    manifestElement.getName());
            Log.e(t, errMessage);
            return errMessage;
        }
        String namespace = manifestElement.getNamespace();
        if (!isXformsManifestNamespacedElement(manifestElement)) {
            errMessage += Survey.getInstance().getString(R.string.root_namespace_error, namespace);
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
                    errMessage +=
                        Survey.getInstance().getString(R.string.manifest_tag_error,
                            Integer.toString(i));
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
                publishProgress(
                    Survey.getInstance().getString(R.string.form_download_progress,
                    	fd.formName, toDownload.filename,
                    	Integer.valueOf(mediaCount).toString(), Integer.valueOf(files.size()).toString()),
                    	Integer.valueOf(count).toString(), Integer.valueOf(total).toString());
                try {
                	File existingMediaFile = new File(mediaPath, toDownload.filename);
                    File mediaFile = new File(tempMediaPath, toDownload.filename);

                    String currentHash = "-";
                	if ( existingMediaFile != null && existingMediaFile.exists() ) {
                		currentHash = FormsProviderAPI.MD5_COLON_PREFIX + ODKFileUtils.getMd5Hash(existingMediaFile);
                	}
                	if ( currentHash.equals(toDownload.hash) ) {
                		// no need for the network retrieval -- use local copy
                		FileUtils.copyFile(existingMediaFile, mediaFile);
                    	Log.i(t, "Skipping media file fetch -- file hashes identical: " + mediaFile.getAbsolutePath());
                	} else {
                		downloadFile(mediaFile, toDownload.downloadUrl);
                	}
                } catch (Exception e) {
                    return e.getLocalizedMessage();
                }
            }
        } else {
        	return Survey.getInstance().getString(R.string.no_manifest, fd.formName);
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
    protected void onProgressUpdate(String... values) {
        synchronized (this) {
            if (mStateListener != null) {
                // update progress and total
                mStateListener.progressUpdate(values[0],
                	Integer.valueOf(values[1]),
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

}
