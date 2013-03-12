/*
 * Copyright (C) 2013 University of Washington
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
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebUtils;
import org.opendatakit.httpclientandroidlib.HttpResponse;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.client.methods.HttpGet;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.listeners.CopyExpansionFilesListener;

import android.app.Application;
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
public class CopyExpansionFilesTask extends
		AsyncTask<Void, String, ArrayList<String>> {

	private static final String t = "CopyExpansionFilesTask";

   private Application appContext;
	private CopyExpansionFilesListener mStateListener;
   private String appName;

   private boolean mSuccess = false;
	private ArrayList<String> mResult = new ArrayList<String>();

	private boolean mPendingSuccess = false;
	private ArrayList<String> mPendingResult = new ArrayList<String>();

	/**
	 * For debugging APK expansion, we check for the existence and process the
	 * local file without also confirming that it matches that on the Google
	 * Play site.
	 *
	 * @param expansionFiles
	 */
	private void addLocalAPKExpansionFile(
			ArrayList<Map<String, Object>> expansionFiles) {
		File f = Survey.debugAPKExpansionFile();
		if (f != null && f.exists()) {
			Map<String, Object> expansionFile = new HashMap<String, Object>();
			expansionFile.put(Survey.EXPANSION_FILE_PATH, f.getAbsolutePath());
			expansionFile.put(Survey.EXPANSION_FILE_LENGTH, Long.valueOf(f.length()).toString());
			expansionFiles.add(expansionFile);
		}
	}

	@Override
	protected ArrayList<String> doInBackground(Void... values) {
		mPendingResult = new ArrayList<String>();
		mPendingSuccess = true;
		ArrayList<String> result = new ArrayList<String>();

		int count = 1;
		ArrayList<Map<String, Object>> expansionFiles = Survey.getInstance()
				.expansionFiles();
		if (expansionFiles == null) {
			expansionFiles = new ArrayList<Map<String, Object>>();
			addLocalAPKExpansionFile(expansionFiles);
		}
		int total = expansionFiles.size();

		for (int i = 0; i < total; i++) {
			Map<String, Object> expansionFile = expansionFiles.get(i);
			String name = (String) expansionFile
					.get(Survey.EXPANSION_FILE_PATH);
			Object olen = expansionFile.get(Survey.EXPANSION_FILE_LENGTH);
			Long length = Long.valueOf(olen.toString());
			String url = (String) expansionFile.get(Survey.EXPANSION_FILE_URL);

			File f = new File(name);

			if (isCancelled()) {
				mPendingSuccess = false;
				result.add("Cancelled");
				return result;
			}

			publishProgress(
					appContext.getString(R.string.expanding_file,
							f.getName()), Integer.valueOf(count).toString(),
					Integer.valueOf(total).toString());

			String message = "";

			try {
				try {

					if (!f.exists() || f.length() != length.longValue()) {
						// download the file from the URL to the location...
						downloadFile(f, url);
					} else {
						Log.i(t, "file already exists -- skip download");
					}

					// OK the expansion file is downloaded -- now explode it...
					// TODO: error-proof this. For now, we assume
					// this always completes.
					String error = explodeZips(f, count, total);
					if (error != null) {
						mPendingSuccess = false;
						message += error;
					}
					// TODO: do additional configuration now -- e.g.,
					// read one of these files and initialize the app
					// preferences from the contents of the file.
				} catch (SocketTimeoutException se) {
					se.printStackTrace();
					mPendingSuccess = false;
					message += se.getMessage();
				} catch (Exception e) {
					mPendingSuccess = false;
					e.printStackTrace();
					if (e.getCause() != null) {
						message += e.getCause().getMessage();
					} else {
						message += e.getMessage();
					}
				}

				// OK. Everything is downloaded and present in the tempMediaPath
				// directory...
			} finally {
				if (!message.equalsIgnoreCase("")) {
					// failure...
					mPendingSuccess = false;
				} else {
					message = appContext.getString(R.string.success);
				}
			}
			count++;

			result.add(f.getName() + " " + message);
		}

		return result;
	}

	private String explodeZips(File zipfile, int count, int total) {
		String message = "";

		{
			ZipFile f = null;

			try {
				f = new ZipFile(zipfile, ZipFile.OPEN_READ);
				Enumeration<? extends ZipEntry> entries = f.entries();
				int nFiles = 0;
				while (entries.hasMoreElements()) {
					if (isCancelled()) {
						message += "cancelled";
						break;
					}
					++nFiles;
					ZipEntry entry = entries.nextElement();
					File tempFile = new File(ODKFileUtils.getAppFolder(appName), entry.getName());
					String formattedString = appContext.getString(
							R.string.expansion_unzipping, zipfile.getName(),
							entry.getName());
					publishProgress(formattedString, Integer.valueOf(nFiles)
							.toString(), Integer.valueOf(f.size()).toString());
					if (entry.isDirectory()) {
						tempFile.mkdirs();
					} else {
						int bufferSize = 8192;
						InputStream in = new BufferedInputStream(
								f.getInputStream(entry), bufferSize);
						OutputStream out = new BufferedOutputStream(
								new FileOutputStream(tempFile, false),
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

		f.getParentFile().mkdirs();

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

			// Unlike the ODK Aggregate server, allow the Google Play servers
			// to redirect us many times (255 for now).
			HttpClient httpclient = WebUtils
					.createHttpClient(WebUtils.CONNECTION_TIMEOUT, 255);

			// set up request...
			HttpGet req = new HttpGet();
			req.setURI(uri);

			HttpResponse response = null;
			try {
				response = httpclient.execute(req, localContext);
				int statusCode = response.getStatusLine().getStatusCode();

				if (statusCode != 200) {
					WebUtils.discardEntityBytes(response);
					String errMsg = appContext.getString(
							R.string.file_fetch_failed, downloadUrl,
							response.getStatusLine().getReasonPhrase(),
							statusCode);
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
				if (attemptCount != 1) {
					throw e;
				}
			}
		}
	}

	@Override
	protected void onPostExecute(ArrayList<String> result) {
		synchronized (this) {
			mResult = result;
			mSuccess = mPendingSuccess;
			if (mStateListener != null) {
				mStateListener.copyExpansionFilesComplete(mSuccess, mResult);
			}
		}
	}

	@Override
	protected void onCancelled(ArrayList<String> result) {
		synchronized (this) {
			// can be null if cancelled before task executes
			mResult = (result == null) ? new ArrayList<String>() : result;
			mSuccess = false;
			if (mStateListener != null) {
				mStateListener.copyExpansionFilesComplete(mSuccess, mResult);
			}
		}
	}

	@Override
	protected void onProgressUpdate(String... values) {
		synchronized (this) {
			if (mStateListener != null) {
				// update progress and total
				mStateListener.copyProgressUpdate(values[0],
						Integer.valueOf(values[1]), Integer.valueOf(values[2]));
			}
		}

	}

	public boolean getOverallSuccess() {
		return mSuccess;
	}

	public ArrayList<String> getResult() {
		return mResult;
	}

	public void setCopyExpansionFilesListener(CopyExpansionFilesListener sl) {
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
