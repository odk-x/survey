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

import java.util.HashMap;

import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.listeners.DeleteFormsListener;
import org.opendatakit.survey.android.listeners.FormDownloaderListener;
import org.opendatakit.survey.android.listeners.FormListDownloaderListener;
import org.opendatakit.survey.android.listeners.InstanceUploaderListener;
import org.opendatakit.survey.android.logic.FormDetails;
import org.opendatakit.survey.android.logic.InstanceUploadOutcome;
import org.opendatakit.survey.android.tasks.DeleteFormsTask;
import org.opendatakit.survey.android.tasks.DownloadFormListTask;
import org.opendatakit.survey.android.tasks.DownloadFormsTask;
import org.opendatakit.survey.android.tasks.InstanceUploaderTask;

import android.app.Fragment;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Wrapper that holds all the background tasks that might be in-progress at any
 * time.
 * 
 * Also holds the service connection to the WebkitFileServer (localhost webserver)
 * and the service binder for the DbShim service (replacement for W3C SQL)
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class BackgroundTaskFragment extends Fragment implements DeleteFormsListener,
    FormListDownloaderListener, FormDownloaderListener, InstanceUploaderListener {
  
  private static final String LOGTAG = "BackgroundTaskFragment";

  /**
   * Task instances that are preserved until the application dies.
   * 
   * @author mitchellsundt@gmail.com
   *
   */
  public static final class BackgroundTasks {
    DeleteFormsTask mDeleteFormsTask = null;
    DownloadFormListTask mDownloadFormListTask = null;
    DownloadFormsTask mDownloadFormsTask = null;
    InstanceUploaderTask mInstanceUploaderTask = null;

    BackgroundTasks() {
    };
  }

  // handed across orientation changes
  public final BackgroundTasks mBackgroundTasks = new BackgroundTasks(); 

  // These are expected to be broken down and set up during orientation changes.
  public DeleteFormsListener mDeleteFormsListener = null;
  public FormListDownloaderListener mFormListDownloaderListener = null;
  public FormDownloaderListener mFormDownloaderListener = null;
  public InstanceUploaderListener mInstanceUploaderListener = null;
  
  public BackgroundTaskFragment() {
    super();
    // call this to record that we want this instance retained
    // otherwise, we miss this on the first initialization.
    setRetainInstance(true);
  }
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    setRetainInstance(true);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    
    setRetainInstance(true);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }
  
  @Override
  public void onDetach() {
    super.onDetach();
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
  }

  @Override
  public void onPause() {
    mDeleteFormsListener = null;
    mFormListDownloaderListener = null;
    mFormDownloaderListener = null;
    mInstanceUploaderListener = null;

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
  }


  // /////////////////////////////////////////////////////////////////////////
  // registrations

  public void establishDeleteFormsListener(DeleteFormsListener listener) {
    mDeleteFormsListener = listener;
    // async task may have completed while we were reorienting...
    if (mBackgroundTasks.mDeleteFormsTask != null
        && mBackgroundTasks.mDeleteFormsTask.getStatus() == AsyncTask.Status.FINISHED) {
      this.deleteFormsComplete(mBackgroundTasks.mDeleteFormsTask.getDeleteCount(), mBackgroundTasks.mDeleteFormsTask.getDeleteFormData());
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

  // ///////////////////////////////////////////////////
  // actions

  public synchronized void deleteSelectedForms(String appName, DeleteFormsListener listener, Uri[] toDelete, boolean deleteFormAndData) {
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
      df.setDeleteFormData(deleteFormAndData);
      mBackgroundTasks.mDeleteFormsTask = df;
      executeTask(mBackgroundTasks.mDeleteFormsTask, toDelete);
    }
  }

  public synchronized void downloadFormList(String appName, FormListDownloaderListener listener) {
    mFormListDownloaderListener = listener;
    if (mBackgroundTasks.mDownloadFormListTask != null
        && mBackgroundTasks.mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED) {
      Toast.makeText(this.getActivity(), getString(R.string.download_in_progress),
          Toast.LENGTH_LONG).show();
    } else {
      DownloadFormListTask df = new DownloadFormListTask(appName);
      df.setApplication(getActivity().getApplication());
      df.setDownloaderListener(this);
      mBackgroundTasks.mDownloadFormListTask = df;
      executeTask(mBackgroundTasks.mDownloadFormListTask, (Void[]) null);
    }
  }

  public synchronized void downloadForms(String appName, FormDownloaderListener listener,
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

  public synchronized void uploadInstances(InstanceUploaderListener listener, String appName, String uploadTableId,
      String[] instancesToUpload) {
    mInstanceUploaderListener = listener;
    if (mBackgroundTasks.mInstanceUploaderTask != null
        && mBackgroundTasks.mInstanceUploaderTask.getStatus() != AsyncTask.Status.FINISHED) {
      Toast.makeText(this.getActivity(), getString(R.string.upload_in_progress), Toast.LENGTH_LONG)
          .show();
    } else {
      InstanceUploaderTask iu = new InstanceUploaderTask(appName, uploadTableId);
      iu.setApplication(getActivity().getApplication());
      iu.setUploaderListener(this);
      mBackgroundTasks.mInstanceUploaderTask = iu;
      executeTask(mBackgroundTasks.mInstanceUploaderTask, instancesToUpload);
    }
  }

  // /////////////////////////////////////////////////////////////////////////
  // clearing tasks
  //
  // NOTE: clearing these makes us forget that they are running, but it is
  // up to the task itself to eventually shutdown. i.e., we don't quite
  // know when they actually stop.

  public synchronized void clearDownloadFormListTask() {
    mFormListDownloaderListener = null;
    if (mBackgroundTasks.mDownloadFormListTask != null) {
      mBackgroundTasks.mDownloadFormListTask.setDownloaderListener(null);
      if (mBackgroundTasks.mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mDownloadFormListTask.cancel(true);
      }
    }
    mBackgroundTasks.mDownloadFormListTask = null;
  }

  public synchronized void clearDownloadFormsTask() {
    mFormDownloaderListener = null;
    if (mBackgroundTasks.mDownloadFormsTask != null) {
      mBackgroundTasks.mDownloadFormsTask.setDownloaderListener(null);
      if (mBackgroundTasks.mDownloadFormsTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mDownloadFormsTask.cancel(true);
      }
    }
    mBackgroundTasks.mDownloadFormsTask = null;
  }

  public synchronized void clearUploadInstancesTask() {
    mInstanceUploaderListener = null;
    if (mBackgroundTasks.mInstanceUploaderTask != null) {
      mBackgroundTasks.mInstanceUploaderTask.setUploaderListener(null);
      if (mBackgroundTasks.mInstanceUploaderTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mInstanceUploaderTask.cancel(true);
      }
    }
    mBackgroundTasks.mInstanceUploaderTask = null;
  }

  // /////////////////////////////////////////////////////////////////////////
  // cancel requests
  //
  // These maintain communications paths, so that we get a failure
  // completion callback eventually.

  public synchronized void cancelDownloadFormListTask() {
    if (mBackgroundTasks.mDownloadFormListTask != null) {
      if (mBackgroundTasks.mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mDownloadFormListTask.cancel(true);
      }
    }
  }

  public synchronized void cancelDownloadFormsTask() {
    if (mBackgroundTasks.mDownloadFormsTask != null) {
      if (mBackgroundTasks.mDownloadFormsTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mDownloadFormsTask.cancel(true);
      }
    }
  }

  public synchronized void cancelUploadInstancesTask() {
    if (mBackgroundTasks.mInstanceUploaderTask != null) {
      if (mBackgroundTasks.mInstanceUploaderTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mInstanceUploaderTask.cancel(true);
      }
    }
  }

  // /////////////////////////////////////////////////////////////////////////
  // callbacks

  @Override
  public void deleteFormsComplete(int deletedForms, boolean deleteFormData) {
    if (mDeleteFormsListener != null) {
      mDeleteFormsListener.deleteFormsComplete(deletedForms, deleteFormData);
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
}
