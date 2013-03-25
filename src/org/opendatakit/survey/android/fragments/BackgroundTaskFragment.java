/*
 * Copyright (C) 2012-2013 University of Washington
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
import org.opendatakit.survey.android.listeners.FormDownloaderListener;
import org.opendatakit.survey.android.listeners.FormListDownloaderListener;
import org.opendatakit.survey.android.listeners.InstanceUploaderListener;
import org.opendatakit.survey.android.logic.FormDetails;
import org.opendatakit.survey.android.logic.FormIdStruct;
import org.opendatakit.survey.android.logic.InstanceUploadOutcome;
import org.opendatakit.survey.android.tasks.CopyExpansionFilesTask;
import org.opendatakit.survey.android.tasks.DeleteFormsTask;
import org.opendatakit.survey.android.tasks.DownloadFormListTask;
import org.opendatakit.survey.android.tasks.DownloadFormsTask;
import org.opendatakit.survey.android.tasks.InstanceUploaderTask;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

/**
 * Wrapper that holds all the background tasks that might be in-progress at any
 * time.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class BackgroundTaskFragment extends Fragment implements DeleteFormsListener,
    FormListDownloaderListener, FormDownloaderListener, InstanceUploaderListener,
    CopyExpansionFilesListener {

  public static final class BackgroundTasks {
    DeleteFormsTask mDeleteFormsTask = null;
    DownloadFormListTask mDownloadFormListTask = null;
    DownloadFormsTask mDownloadFormsTask = null;
    InstanceUploaderTask mInstanceUploaderTask = null;
    CopyExpansionFilesTask mCopyExpansionFilesTask = null;

    BackgroundTasks() {
    };
  }

  public BackgroundTasks mBackgroundTasks; // handed across orientation
  // changes

  public DeleteFormsListener mDeleteFormsListener = null;
  public FormListDownloaderListener mFormListDownloaderListener = null;
  public FormDownloaderListener mFormDownloaderListener = null;
  public InstanceUploaderListener mInstanceUploaderListener = null;
  public CopyExpansionFilesListener mCopyExpansionFilesListener = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBackgroundTasks = new BackgroundTasks();

    setRetainInstance(true);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    setRetainInstance(true);
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return new View(getActivity());
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  private <T> void executeTask(AsyncTask<T, ?, ?> task, T... args) {

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
    // startDiskSyncListener(((ODKActivity) getActivity()).getAppName(), null);
  }

  @Override
  public void onPause() {
    mDeleteFormsListener = null;
    mFormListDownloaderListener = null;
    mFormDownloaderListener = null;
    mInstanceUploaderListener = null;
    mCopyExpansionFilesListener = null;

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

  @Override
  public void onResume() {
    super.onResume();
    if (mBackgroundTasks.mDeleteFormsTask != null) {
      mBackgroundTasks.mDeleteFormsTask.setDeleteListener(this);
    }
    if (mBackgroundTasks.mDownloadFormListTask != null) {
      mBackgroundTasks.mDownloadFormListTask.setDownloaderListener(this);
    }
    if (mBackgroundTasks.mDownloadFormsTask != null) {
      mBackgroundTasks.mDownloadFormsTask.setDownloaderListener(this);
    }
    if (mBackgroundTasks.mInstanceUploaderTask != null) {
      mBackgroundTasks.mInstanceUploaderTask.setUploaderListener(this);
    }
    if (mBackgroundTasks.mCopyExpansionFilesTask != null) {
      mBackgroundTasks.mCopyExpansionFilesTask.setCopyExpansionFilesListener(this);
    }
  }

  // /////////////////////////////////////////////////////////////////////////
  // registrations

  public void establishDeleteFormsListener(DeleteFormsListener listener) {
    mDeleteFormsListener = listener;
    // async task may have completed while we were reorienting...
    if (mBackgroundTasks.mDeleteFormsTask != null
        && mBackgroundTasks.mDeleteFormsTask.getStatus() == AsyncTask.Status.FINISHED) {
      this.deleteFormsComplete(mBackgroundTasks.mDeleteFormsTask.getDeleteCount());
    }
  }

  public void establishFormListDownloaderListener(FormListDownloaderListener listener) {
    mFormListDownloaderListener = listener;
    // async task may have completed while we were reorienting...
    if (mBackgroundTasks.mDownloadFormListTask != null
        && mBackgroundTasks.mDownloadFormListTask.getStatus() == AsyncTask.Status.FINISHED) {
      this.formListDownloadingComplete(mBackgroundTasks.mDownloadFormListTask.getFormList());
    }
  }

  public void establishFormDownloaderListener(FormDownloaderListener listener) {
    mFormDownloaderListener = listener;
    // async task may have completed while we were reorienting...
    if (mBackgroundTasks.mDownloadFormsTask != null
        && mBackgroundTasks.mDownloadFormsTask.getStatus() == AsyncTask.Status.FINISHED) {
      this.formsDownloadingComplete(mBackgroundTasks.mDownloadFormsTask.getResult());
    }
  }

  public void establishInstanceUploaderListener(InstanceUploaderListener listener) {
    mInstanceUploaderListener = listener;
    // async task may have completed while we were reorienting...
    if (mBackgroundTasks.mInstanceUploaderTask != null
        && mBackgroundTasks.mInstanceUploaderTask.getStatus() == AsyncTask.Status.FINISHED) {
      this.uploadingComplete(mBackgroundTasks.mInstanceUploaderTask.getResult());
    }
  }

  public void establishCopyExpansionFilesListener(CopyExpansionFilesListener listener) {
    mCopyExpansionFilesListener = listener;
    // async task may have completed while we were reorienting...
    if (mBackgroundTasks.mCopyExpansionFilesTask != null
        && mBackgroundTasks.mCopyExpansionFilesTask.getStatus() == AsyncTask.Status.FINISHED) {
      this.copyExpansionFilesComplete(mBackgroundTasks.mCopyExpansionFilesTask.getOverallSuccess(),
          mBackgroundTasks.mCopyExpansionFilesTask.getResult());
    }
  }

  // ///////////////////////////////////////////////////
  // actions

  public void deleteSelectedForms(String appName, DeleteFormsListener listener, Long[] toDelete) {
    mDeleteFormsListener = listener;
    if (mBackgroundTasks.mDeleteFormsTask != null
        && mBackgroundTasks.mDeleteFormsTask.getStatus() != AsyncTask.Status.FINISHED) {
      Toast.makeText(this.getActivity(), getString(R.string.file_delete_in_progress),
          Toast.LENGTH_LONG).show();
    } else {
      DeleteFormsTask df = new DeleteFormsTask();
      df.setApplication(getActivity().getApplication());
      df.setAppName(appName);
      df.setDeleteListener(this);
      mBackgroundTasks.mDeleteFormsTask = df;
      executeTask(mBackgroundTasks.mDeleteFormsTask, toDelete);
    }
  }

  public void downloadFormList(FormListDownloaderListener listener) {
    mFormListDownloaderListener = listener;
    if (mBackgroundTasks.mDownloadFormListTask != null
        && mBackgroundTasks.mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED) {
      Toast.makeText(this.getActivity(), getString(R.string.download_in_progress),
          Toast.LENGTH_LONG).show();
    } else {
      DownloadFormListTask df = new DownloadFormListTask();
      df.setApplication(getActivity().getApplication());
      df.setDownloaderListener(this);
      mBackgroundTasks.mDownloadFormListTask = df;
      executeTask(mBackgroundTasks.mDownloadFormListTask, (Void[]) null);
    }
  }

  public void downloadForms(String appName, FormDownloaderListener listener,
      FormDetails[] filesToDownload) {
    mFormDownloaderListener = listener;
    if (mBackgroundTasks.mDownloadFormsTask != null
        && mBackgroundTasks.mDownloadFormsTask.getStatus() != AsyncTask.Status.FINISHED) {
      Toast.makeText(this.getActivity(), getString(R.string.download_in_progress),
          Toast.LENGTH_LONG).show();
    } else {
      DownloadFormsTask df = new DownloadFormsTask();
      df.setApplication(getActivity().getApplication());
      df.setAppName(appName);
      df.setDownloaderListener(this);
      mBackgroundTasks.mDownloadFormsTask = df;
      executeTask(mBackgroundTasks.mDownloadFormsTask, filesToDownload);
    }
  }

  public void uploadInstances(InstanceUploaderListener listener, FormIdStruct form,
      String[] instancesToUpload) {
    mInstanceUploaderListener = listener;
    if (mBackgroundTasks.mInstanceUploaderTask != null
        && mBackgroundTasks.mInstanceUploaderTask.getStatus() != AsyncTask.Status.FINISHED) {
      Toast.makeText(this.getActivity(), getString(R.string.upload_in_progress), Toast.LENGTH_LONG)
          .show();
    } else {
      InstanceUploaderTask iu = new InstanceUploaderTask(form);
      iu.setApplication(getActivity().getApplication());
      iu.setUploaderListener(this);
      mBackgroundTasks.mInstanceUploaderTask = iu;
      executeTask(mBackgroundTasks.mInstanceUploaderTask, instancesToUpload);
    }
  }

  public void copyExpansionFiles(String appName, CopyExpansionFilesListener listener) {
    mCopyExpansionFilesListener = listener;
    if (mBackgroundTasks.mCopyExpansionFilesTask != null
        && mBackgroundTasks.mCopyExpansionFilesTask.getStatus() != AsyncTask.Status.FINISHED) {
      // Toast.makeText(this.getActivity(),
      // getString(R.string.expansion_in_progress),
      // Toast.LENGTH_LONG).show();
    } else {
      CopyExpansionFilesTask cf = new CopyExpansionFilesTask();
      cf.setApplication(getActivity().getApplication());
      cf.setAppName(appName);
      cf.setCopyExpansionFilesListener(this);
      mBackgroundTasks.mCopyExpansionFilesTask = cf;
      executeTask(mBackgroundTasks.mCopyExpansionFilesTask, (Void) null);
    }
  }

  // /////////////////////////////////////////////////////////////////////////
  // clearing tasks
  //
  // NOTE: clearing these makes us forget that they are running, but it is
  // up to the task itself to eventually shutdown. i.e., we don't quite
  // know when they actually stop.

  public void clearDownloadFormListTask() {
    mFormListDownloaderListener = null;
    if (mBackgroundTasks.mDownloadFormListTask != null) {
      mBackgroundTasks.mDownloadFormListTask.setDownloaderListener(null);
      if (mBackgroundTasks.mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mDownloadFormListTask.cancel(true);
      }
    }
    mBackgroundTasks.mDownloadFormListTask = null;
  }

  public void clearDownloadFormsTask() {
    mFormDownloaderListener = null;
    if (mBackgroundTasks.mDownloadFormsTask != null) {
      mBackgroundTasks.mDownloadFormsTask.setDownloaderListener(null);
      if (mBackgroundTasks.mDownloadFormsTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mDownloadFormsTask.cancel(true);
      }
    }
    mBackgroundTasks.mDownloadFormsTask = null;
  }

  public void clearUploadInstancesTask() {
    mInstanceUploaderListener = null;
    if (mBackgroundTasks.mInstanceUploaderTask != null) {
      mBackgroundTasks.mInstanceUploaderTask.setUploaderListener(null);
      if (mBackgroundTasks.mInstanceUploaderTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mInstanceUploaderTask.cancel(true);
      }
    }
    mBackgroundTasks.mInstanceUploaderTask = null;
  }

  public void clearCopyExpansionFilesTask() {
    mCopyExpansionFilesListener = null;
    if (mBackgroundTasks.mCopyExpansionFilesTask != null) {
      mBackgroundTasks.mCopyExpansionFilesTask.setCopyExpansionFilesListener(null);
      if (mBackgroundTasks.mCopyExpansionFilesTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mCopyExpansionFilesTask.cancel(true);
      }
    }
    mBackgroundTasks.mCopyExpansionFilesTask = null;
  }

  // /////////////////////////////////////////////////////////////////////////
  // cancel requests
  //
  // These maintain communications paths, so that we get a failure
  // completion callback eventually.

  public void cancelDownloadFormListTask() {
    if (mBackgroundTasks.mDownloadFormListTask != null) {
      if (mBackgroundTasks.mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mDownloadFormListTask.cancel(true);
      }
    }
  }

  public void cancelDownloadFormsTask() {
    if (mBackgroundTasks.mDownloadFormsTask != null) {
      if (mBackgroundTasks.mDownloadFormsTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mDownloadFormsTask.cancel(true);
      }
    }
  }

  public void cancelUploadInstancesTask() {
    if (mBackgroundTasks.mInstanceUploaderTask != null) {
      if (mBackgroundTasks.mInstanceUploaderTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mInstanceUploaderTask.cancel(true);
      }
    }
  }

  public void cancelCopyExpansionFilesTask() {
    if (mBackgroundTasks.mCopyExpansionFilesTask != null) {
      if (mBackgroundTasks.mCopyExpansionFilesTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mCopyExpansionFilesTask.cancel(true);
      }
    }
  }

  // /////////////////////////////////////////////////////////////////////////
  // callbacks

  @Override
  public void deleteFormsComplete(int deletedForms) {
    if (mDeleteFormsListener != null) {
      mDeleteFormsListener.deleteFormsComplete(deletedForms);
    }
    mBackgroundTasks.mDeleteFormsTask = null;
  }

  @Override
  public void formListDownloadingComplete(HashMap<String, FormDetails> value) {
    if (mFormListDownloaderListener != null) {
      mFormListDownloaderListener.formListDownloadingComplete(value);
    }
  }

  @Override
  public void formsDownloadingComplete(HashMap<String, String> result) {
    if (mFormDownloaderListener != null) {
      mFormDownloaderListener.formsDownloadingComplete(result);
    }
  }

  @Override
  public void formDownloadProgressUpdate(String currentFile, int progress, int total) {
    if (mFormDownloaderListener != null) {
      mFormDownloaderListener.formDownloadProgressUpdate(currentFile, progress, total);
    }
  }

  @Override
  public void uploadingComplete(InstanceUploadOutcome result) {
    if (mInstanceUploaderListener != null) {
      mInstanceUploaderListener.uploadingComplete(result);
    }
  }

  @Override
  public void progressUpdate(int progress, int total) {
    if (mInstanceUploaderListener != null) {
      mInstanceUploaderListener.progressUpdate(progress, total);
    }
  }

  @Override
  public void copyExpansionFilesComplete(boolean overallSuccess, ArrayList<String> result) {
    if (mCopyExpansionFilesListener != null) {
      mCopyExpansionFilesListener.copyExpansionFilesComplete(overallSuccess, result);
    }
  }

  @Override
  public void copyProgressUpdate(String status, int progress, int total) {
    if (mCopyExpansionFilesListener != null) {
      mCopyExpansionFilesListener.copyProgressUpdate(status, progress, total);
    }
  }
}
