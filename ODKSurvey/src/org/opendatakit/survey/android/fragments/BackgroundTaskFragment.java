/*
 * Copyright (C) 2012 University of Washington
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

package org.opendatakit.survey.android.fragments;

import java.util.ArrayList;
import java.util.HashMap;

import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.listeners.CopyExpansionFilesListener;
import org.opendatakit.survey.android.listeners.DeleteFormsListener;
import org.opendatakit.survey.android.listeners.DiskSyncListener;
import org.opendatakit.survey.android.listeners.FormDownloaderListener;
import org.opendatakit.survey.android.listeners.FormListDownloaderListener;
import org.opendatakit.survey.android.listeners.InstanceUploaderListener;
import org.opendatakit.survey.android.logic.FormDetails;
import org.opendatakit.survey.android.logic.FormIdStruct;
import org.opendatakit.survey.android.logic.InstanceUploadOutcome;
import org.opendatakit.survey.android.tasks.CopyExpansionFilesTask;
import org.opendatakit.survey.android.tasks.DeleteFormsTask;
import org.opendatakit.survey.android.tasks.DiskSyncTask;
import org.opendatakit.survey.android.tasks.DownloadFormListTask;
import org.opendatakit.survey.android.tasks.DownloadFormsTask;
import org.opendatakit.survey.android.tasks.InstanceUploaderTask;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.widget.Toast;

/**
 * Wrapper that holds all the background tasks that might be in-progress at any time.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class BackgroundTaskFragment extends Fragment
	implements DeleteFormsListener, DiskSyncListener,
				FormListDownloaderListener, FormDownloaderListener,
				InstanceUploaderListener, CopyExpansionFilesListener {

	static public class BackgroundTasks {
		DiskSyncTask mDiskSyncTask = null;
		DeleteFormsTask mDeleteFormsTask = null;
		DownloadFormListTask mDownloadFormListTask = null;
		DownloadFormsTask mDownloadFormsTask = null;
		InstanceUploaderTask mInstanceUploaderTask = null;
		CopyExpansionFilesTask mCopyExpansionFilesTask = null;

		BackgroundTasks() {
		};
	}

	public BackgroundTasks mBackgroundTasks; // handed across orientation changes

	public DiskSyncListener mDiskSyncListener = null;
	public DeleteFormsListener mDeleteFormsListener = null;
	public FormListDownloaderListener mFormListDownloaderListener = null;
	public FormDownloaderListener mFormDownloaderListener = null;
	public InstanceUploaderListener mInstanceUploaderListener = null;
	public CopyExpansionFilesListener mCopyExpansionFilesListener = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setRetainInstance(true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		mBackgroundTasks = new BackgroundTasks();
		setRetainInstance(true);
	}

	private <T> void executeTask( AsyncTask<T,?,?> task, T... args) {

		int androidVersion = android.os.Build.VERSION.SDK_INT;
		if (androidVersion < 11) {
			task.execute(args);
		} else {
			// TODO: execute on serial executor in version 11 onward...
			task.execute(args);
			// task.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, (Void[]) null);
		}

	}
	@Override
	public void onStart() {
		super.onStart();
		// run the disk sync task once...
		startDiskSyncListener(null);
	}

	@Override
	public void onPause() {
		mBackgroundTasks.mDiskSyncTask.setDiskSyncListener(null);
		if (mBackgroundTasks.mDeleteFormsTask != null) {
			mBackgroundTasks.mDeleteFormsTask.setDeleteListener(null);
		}
		if (mBackgroundTasks.mDownloadFormListTask != null) {
			mBackgroundTasks.mDownloadFormListTask.setDownloaderListener(null);
		}
		if (mBackgroundTasks.mDownloadFormsTask != null) {
			mBackgroundTasks.mDownloadFormsTask.setDownloaderListener(null);
		}
		if (mBackgroundTasks.mInstanceUploaderTask != null) {
			mBackgroundTasks.mInstanceUploaderTask.setUploaderListener(null);
		}
		if (mBackgroundTasks.mCopyExpansionFilesTask != null) {
			mBackgroundTasks.mCopyExpansionFilesTask.setCopyExpansionFilesListener(null);
		}
		super.onPause();
	}

	///////////////////////////////////////////////////////////////////////////
	// registrations

	public void establishDeleteFormsListener(DeleteFormsListener listener) {
		mDeleteFormsListener = listener;
		// async task may have completed while we were reorienting...
		if (mBackgroundTasks.mDeleteFormsTask != null &&
			mBackgroundTasks.mDeleteFormsTask.getStatus() == AsyncTask.Status.FINISHED) {
			this.deleteFormsComplete(mBackgroundTasks.mDeleteFormsTask.getDeleteCount());
		}
	}

	public void establishDiskSyncListener(DiskSyncListener listener) {
		mDiskSyncListener = listener;
		// async task may have completed while we were reorienting...
		if (mBackgroundTasks.mDiskSyncTask.getStatus() == AsyncTask.Status.FINISHED) {
			this.SyncComplete(mBackgroundTasks.mDiskSyncTask.getStatusMessage());
		}
	}

	public void establishFormListDownloaderListener(FormListDownloaderListener listener) {
		mFormListDownloaderListener = listener;
		// async task may have completed while we were reorienting...
		if (mBackgroundTasks.mDownloadFormListTask != null &&
			mBackgroundTasks.mDownloadFormListTask.getStatus() == AsyncTask.Status.FINISHED) {
			this.formListDownloadingComplete(mBackgroundTasks.mDownloadFormListTask.getFormList());
		}
	}

	public void establishFormDownloaderListener(FormDownloaderListener listener) {
		mFormDownloaderListener = listener;
		// async task may have completed while we were reorienting...
		if (mBackgroundTasks.mDownloadFormsTask != null &&
			mBackgroundTasks.mDownloadFormsTask.getStatus() == AsyncTask.Status.FINISHED) {
			this.formsDownloadingComplete(mBackgroundTasks.mDownloadFormsTask.getResult());
		}
	}

	public void establishInstanceUploaderListener(InstanceUploaderListener listener) {
		mInstanceUploaderListener = listener;
		// async task may have completed while we were reorienting...
		if (mBackgroundTasks.mInstanceUploaderTask != null &&
			mBackgroundTasks.mInstanceUploaderTask.getStatus() == AsyncTask.Status.FINISHED) {
			this.uploadingComplete(mBackgroundTasks.mInstanceUploaderTask.getResult());
		}
	}

	public void establishCopyExpansionFilesListener(CopyExpansionFilesListener listener) {
		mCopyExpansionFilesListener = listener;
		// async task may have completed while we were reorienting...
		if (mBackgroundTasks.mCopyExpansionFilesTask != null &&
			mBackgroundTasks.mCopyExpansionFilesTask.getStatus() == AsyncTask.Status.FINISHED) {
			this.copyExpansionFilesComplete(mBackgroundTasks.mCopyExpansionFilesTask.getResult());
		}
	}

	/////////////////////////////////////////////////////
	// actions

	public void startDiskSyncListener(DiskSyncListener listener) {
		if ( mBackgroundTasks.mDiskSyncTask != null &&
				mBackgroundTasks.mDiskSyncTask.getStatus() != AsyncTask.Status.FINISHED ) {
			Toast.makeText(this.getActivity(), getString(R.string.disksync_in_progress),
					Toast.LENGTH_LONG).show();
			mBackgroundTasks.mDiskSyncTask.cancel(true);
			mBackgroundTasks.mDiskSyncTask.setDiskSyncListener(null);
		}

		mDiskSyncListener = listener;
		mBackgroundTasks.mDiskSyncTask = new DiskSyncTask();
		mBackgroundTasks.mDiskSyncTask.setDiskSyncListener(this);
		executeTask(mBackgroundTasks.mDiskSyncTask, (Void[]) null);
	}

	public void deleteSelectedForms(DeleteFormsListener listener, Long[] toDelete) {
		if ( mBackgroundTasks.mDeleteFormsTask != null &&
				mBackgroundTasks.mDeleteFormsTask.getStatus() != AsyncTask.Status.FINISHED ) {
			Toast.makeText(this.getActivity(), getString(R.string.file_delete_in_progress),
					Toast.LENGTH_LONG).show();
			mBackgroundTasks.mDeleteFormsTask.cancel(true);
			mBackgroundTasks.mDeleteFormsTask.setDeleteListener(null);
		}

		mDeleteFormsListener = listener;
		mBackgroundTasks.mDeleteFormsTask = new DeleteFormsTask();
		mBackgroundTasks.mDeleteFormsTask
				.setContentResolver(getActivity().getContentResolver());
		mBackgroundTasks.mDeleteFormsTask.setDeleteListener(this);
		executeTask(mBackgroundTasks.mDeleteFormsTask, toDelete);
	}

	public void downloadFormList(FormListDownloaderListener listener) {
		if ( mBackgroundTasks.mDownloadFormListTask != null &&
				mBackgroundTasks.mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED ) {
			Toast.makeText(this.getActivity(), getString(R.string.download_in_progress),
					Toast.LENGTH_LONG).show();
			mBackgroundTasks.mDownloadFormListTask.cancel(true);
			mBackgroundTasks.mDownloadFormListTask.setDownloaderListener(null);
		}

		mFormListDownloaderListener = listener;
		mBackgroundTasks.mDownloadFormListTask = new DownloadFormListTask();
		mBackgroundTasks.mDownloadFormListTask.setDownloaderListener(this);
		executeTask(mBackgroundTasks.mDownloadFormListTask, (Void[]) null);
	}

	public void downloadForms(FormDownloaderListener listener, FormDetails[] filesToDownload) {
		if ( mBackgroundTasks.mDownloadFormsTask != null &&
				mBackgroundTasks.mDownloadFormsTask.getStatus() != AsyncTask.Status.FINISHED ) {
			Toast.makeText(this.getActivity(), getString(R.string.download_in_progress),
					Toast.LENGTH_LONG).show();
			mBackgroundTasks.mDownloadFormsTask.cancel(true);
			mBackgroundTasks.mDownloadFormsTask.setDownloaderListener(null);
		}

		mFormDownloaderListener = listener;
		mBackgroundTasks.mDownloadFormsTask = new DownloadFormsTask();
		mBackgroundTasks.mDownloadFormsTask.setDownloaderListener(this);
		executeTask(mBackgroundTasks.mDownloadFormsTask, filesToDownload);
	}

	public void uploadInstances(InstanceUploaderListener listener, FormIdStruct form, String[] instancesToUpload) {
		if ( mBackgroundTasks.mInstanceUploaderTask != null &&
				mBackgroundTasks.mInstanceUploaderTask.getStatus() != AsyncTask.Status.FINISHED ) {
			Toast.makeText(this.getActivity(), getString(R.string.upload_in_progress),
					Toast.LENGTH_LONG).show();
			mBackgroundTasks.mInstanceUploaderTask.cancel(true);
			mBackgroundTasks.mInstanceUploaderTask.setUploaderListener(null);
		}

		mInstanceUploaderListener = listener;
		mBackgroundTasks.mInstanceUploaderTask = new InstanceUploaderTask(form);
		mBackgroundTasks.mInstanceUploaderTask.setUploaderListener(this);
		executeTask(mBackgroundTasks.mInstanceUploaderTask, instancesToUpload);
	}

	public void copyExpansionFiles(CopyExpansionFilesListener listener) {
		if ( mBackgroundTasks.mCopyExpansionFilesTask != null &&
				mBackgroundTasks.mCopyExpansionFilesTask.getStatus() != AsyncTask.Status.FINISHED ) {
			Toast.makeText(this.getActivity(), getString(R.string.expansion_in_progress),
					Toast.LENGTH_LONG).show();
			mBackgroundTasks.mCopyExpansionFilesTask.cancel(true);
			mBackgroundTasks.mCopyExpansionFilesTask.setCopyExpansionFilesListener(null);
		}

		mCopyExpansionFilesListener = listener;
		mBackgroundTasks.mCopyExpansionFilesTask = new CopyExpansionFilesTask();
		mBackgroundTasks.mCopyExpansionFilesTask.setCopyExpansionFilesListener(this);
		executeTask(mBackgroundTasks.mCopyExpansionFilesTask, (Void) null);
	}

	///////////////////////////////////////////////////////////////////////////
	// clearing tasks
	//
	// NOTE: clearing any of these does not entirely cancel their actions,
	// so they may still be operating in parallel with whatever new actions
	// the user may initiate.

	public void clearDownloadFormListTask() {
		mFormListDownloaderListener = null;
		if ( mBackgroundTasks.mDownloadFormListTask != null ) {
			mBackgroundTasks.mDownloadFormListTask.setDownloaderListener(null);
			if ( mBackgroundTasks.mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED ) {
				mBackgroundTasks.mDownloadFormListTask.cancel(true);
			}
		}
		mBackgroundTasks.mDownloadFormListTask = null;
	}

	public void clearDownloadFormsTask() {
		mFormDownloaderListener = null;
		if ( mBackgroundTasks.mDownloadFormsTask != null ) {
			mBackgroundTasks.mDownloadFormsTask.setDownloaderListener(null);
			if ( mBackgroundTasks.mDownloadFormsTask.getStatus() != AsyncTask.Status.FINISHED ) {
				mBackgroundTasks.mDownloadFormsTask.cancel(true);
			}
		}
		mBackgroundTasks.mDownloadFormsTask = null;
	}

	public void clearUploadInstancesTask() {
		mInstanceUploaderListener = null;
		if ( mBackgroundTasks.mInstanceUploaderTask != null ) {
			mBackgroundTasks.mInstanceUploaderTask.setUploaderListener(null);
			if ( mBackgroundTasks.mInstanceUploaderTask.getStatus() != AsyncTask.Status.FINISHED ) {
				mBackgroundTasks.mInstanceUploaderTask.cancel(true);
			}
		}
		mBackgroundTasks.mInstanceUploaderTask = null;
	}

	public void clearCopyExpansionFilesTask() {
		mCopyExpansionFilesListener = null;
		if ( mBackgroundTasks.mCopyExpansionFilesTask != null ) {
			mBackgroundTasks.mCopyExpansionFilesTask.setCopyExpansionFilesListener(null);
			if ( mBackgroundTasks.mCopyExpansionFilesTask.getStatus() != AsyncTask.Status.FINISHED ) {
				mBackgroundTasks.mCopyExpansionFilesTask.cancel(true);
			}
		}
		mBackgroundTasks.mCopyExpansionFilesTask = null;
	}

	///////////////////////////////////////////////////////////////////////////
	// callbacks

	@Override
	public void deleteFormsComplete(int deletedForms) {
		if ( mDeleteFormsListener != null ) {
			mDeleteFormsListener.deleteFormsComplete(deletedForms);
		}
		mBackgroundTasks.mDeleteFormsTask = null;
	}

	@Override
	public void SyncComplete(String result) {
		if ( mDiskSyncListener != null ) {
			mDiskSyncListener.SyncComplete(result);
		}
	}

	@Override
	public void formListDownloadingComplete(HashMap<String, FormDetails> value) {
		if ( mFormListDownloaderListener != null ) {
			mFormListDownloaderListener.formListDownloadingComplete(value);
		}
	}

	@Override
	public void formsDownloadingComplete(HashMap<String, String> result) {
		if ( mFormDownloaderListener != null ) {
			mFormDownloaderListener.formsDownloadingComplete(result);
		}
	}

	@Override
	public void formDownloadProgressUpdate(String currentFile, int progress, int total) {
		if ( mFormDownloaderListener != null ) {
			mFormDownloaderListener.formDownloadProgressUpdate(currentFile, progress, total);
		}
	}

	@Override
	public void uploadingComplete(InstanceUploadOutcome result) {
		if ( mInstanceUploaderListener != null ) {
			mInstanceUploaderListener.uploadingComplete(result);
		}
	}

	@Override
	public void progressUpdate(int progress, int total) {
		if ( mInstanceUploaderListener != null ) {
			mInstanceUploaderListener.progressUpdate(progress, total);
		}
	}

	@Override
	public void copyExpansionFilesComplete(ArrayList<String> result) {
		if ( mCopyExpansionFilesListener != null ) {
			mCopyExpansionFilesListener.copyExpansionFilesComplete(result);
		}
	}

	@Override
	public void copyProgressUpdate(String status, int progress, int total) {
		if ( mCopyExpansionFilesListener != null ) {
			mCopyExpansionFilesListener.copyProgressUpdate(status, progress, total);
		}
	}
}
