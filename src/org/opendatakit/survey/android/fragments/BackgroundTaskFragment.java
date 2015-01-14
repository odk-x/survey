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

import org.opendatakit.common.android.listener.LicenseReaderListener;
import org.opendatakit.dbshim.DbShimConsts;
import org.opendatakit.dbshim.service.OdkDbShimInterface;
import org.opendatakit.survey.android.R;
import org.opendatakit.survey.android.listeners.DeleteFormsListener;
import org.opendatakit.survey.android.listeners.FormDownloaderListener;
import org.opendatakit.survey.android.listeners.FormListDownloaderListener;
import org.opendatakit.survey.android.listeners.InitializationListener;
import org.opendatakit.survey.android.listeners.InstanceUploaderListener;
import org.opendatakit.survey.android.logic.FormDetails;
import org.opendatakit.survey.android.logic.InstanceUploadOutcome;
import org.opendatakit.survey.android.tasks.DeleteFormsTask;
import org.opendatakit.survey.android.tasks.DownloadFormListTask;
import org.opendatakit.survey.android.tasks.DownloadFormsTask;
import org.opendatakit.survey.android.tasks.InitializationTask;
import org.opendatakit.survey.android.tasks.InstanceUploaderTask;
import org.opendatakit.survey.android.tasks.LicenseReaderTask;
import org.opendatakit.survey.android.views.ODKWebView;
import org.opendatakit.webkitserver.WebkitServerConsts;
import org.opendatakit.webkitserver.service.OdkWebkitServerInterface;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
public class BackgroundTaskFragment extends Fragment implements LicenseReaderListener, DeleteFormsListener,
    FormListDownloaderListener, FormDownloaderListener, InstanceUploaderListener,
    InitializationListener {
  private static final String LOGTAG = "BackgroundTaskFragment";

  /**
   * Wrapper class for service activation management.
   * 
   * @author mitchellsundt@gmail.com
   *
   */
  private final class ServiceConnectionWrapper implements ServiceConnection {

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      BackgroundTaskFragment.this.doServiceConnected(name, service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      BackgroundTaskFragment.this.doServiceDisconnected(name);
    }
  }

  /**
   * Task instances that are preserved until the application dies.
   * 
   * @author mitchellsundt@gmail.com
   *
   */
  public static final class BackgroundTasks {
    LicenseReaderTask mLicenseReaderTask = null;
    DeleteFormsTask mDeleteFormsTask = null;
    DownloadFormListTask mDownloadFormListTask = null;
    DownloadFormsTask mDownloadFormsTask = null;
    InstanceUploaderTask mInstanceUploaderTask = null;
    InitializationTask mInitializationTask = null;

    BackgroundTasks() {
    };
  }

  /**
   * Service connections that are preserved until the application dies.
   * 
   * @author mitchellsundt@gmail.com
   *
   */
  public static final class BackgroundServices {

    private ServiceConnectionWrapper webkitfilesServiceConnection = null;
    private OdkWebkitServerInterface webkitfilesService = null;
    private ServiceConnectionWrapper dbShimServiceConnection = null;
    private OdkDbShimInterface dbShimService = null;
    private boolean isDestroying = false;

    BackgroundServices() {
    };
  }

  // handed across orientation changes
  public BackgroundTasks mBackgroundTasks; 

  // handed across orientation changes
  public BackgroundServices mBackgroundServices; 

  // These are expected to be broken down and set up during orientation changes.
  public LicenseReaderListener mLicenseReaderListener = null;
  public DeleteFormsListener mDeleteFormsListener = null;
  public FormListDownloaderListener mFormListDownloaderListener = null;
  public FormDownloaderListener mFormDownloaderListener = null;
  public InstanceUploaderListener mInstanceUploaderListener = null;
  public InitializationListener mInitializationListener = null;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBackgroundTasks = new BackgroundTasks();
    mBackgroundServices = new BackgroundServices();
    
    setRetainInstance(true);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    setRetainInstance(true);

    bindToService();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return new View(getActivity());
  }

  @Override
  public void onDestroy() {
    shutdownServices();
    
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
    // run the disk sync task once...
    // startDiskSyncListener(((ODKActivity) getActivity()).getAppName(), null);
    bindToService();

    configureView();
  }

  @Override
  public void onPause() {
    mLicenseReaderListener = null;
    mDeleteFormsListener = null;
    mFormListDownloaderListener = null;
    mFormDownloaderListener = null;
    mInstanceUploaderListener = null;
    mInitializationListener = null;

    if (mBackgroundTasks.mLicenseReaderTask != null) {
      mBackgroundTasks.mLicenseReaderTask.setLicenseReaderListener(null);
    }
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
    if (mBackgroundTasks.mInitializationTask != null) {
      mBackgroundTasks.mInitializationTask.setInitializationListener(null);
    }
    super.onPause();
  }

  @Override
  public void onResume() {
    super.onResume();

    if (mBackgroundTasks.mLicenseReaderTask != null) {
      mBackgroundTasks.mLicenseReaderTask.setLicenseReaderListener(this);
    }
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
    if (mBackgroundTasks.mInitializationTask != null) {
      mBackgroundTasks.mInitializationTask.setInitializationListener(this);
    }
    
    // be sure the services are connected...
    mBackgroundServices.isDestroying = false;

    configureView();

    // failsafe -- ensure that the services are active...
    bindToService();
  }

  // /////////////////////////////////////////////////////////////////////////
  // service interactions
  private void unbindWebkitfilesServiceWrapper() {
    try {
      ServiceConnectionWrapper tmp = mBackgroundServices.webkitfilesServiceConnection;
      mBackgroundServices.webkitfilesServiceConnection = null;
      if ( tmp != null ) {
        getActivity().unbindService(tmp);
      }
    } catch ( Exception e ) {
      // ignore
      e.printStackTrace();
    }
  }
  
  private void unbindDbShimBinderWrapper() {
    try {
      ServiceConnectionWrapper tmp = mBackgroundServices.dbShimServiceConnection;
      mBackgroundServices.dbShimServiceConnection = null;
      if ( tmp != null ) {
        getActivity().unbindService(tmp);
      }
    } catch ( Exception e ) {
      // ignore
      e.printStackTrace();
    }
  }
  
  private void shutdownServices() {
    Log.i(LOGTAG, "shutdownServices - Releasing WebServer and DbShim service");
    mBackgroundServices.isDestroying = true;
    mBackgroundServices.webkitfilesService = null;
    mBackgroundServices.dbShimService = null;
    // release interfaces held by the view
    configureView();
    // release the webkitfilesService
    unbindWebkitfilesServiceWrapper();
    unbindDbShimBinderWrapper();
  }
  
  private void bindToService() {
    if (!this.isRemoving() && !mBackgroundServices.isDestroying && getActivity() != null) {
      if (mBackgroundServices.webkitfilesService == null && 
          mBackgroundServices.webkitfilesServiceConnection == null) {
        Log.i(LOGTAG, "Attempting bind to WebServer service");
        mBackgroundServices.webkitfilesServiceConnection = new ServiceConnectionWrapper();
        Intent bind_intent = new Intent();
        bind_intent.setClassName(WebkitServerConsts.WEBKITSERVER_SERVICE_PACKAGE,
            WebkitServerConsts.WEBKITSERVER_SERVICE_CLASS);
        getActivity().bindService(
            bind_intent,
            mBackgroundServices.webkitfilesServiceConnection,
            Context.BIND_AUTO_CREATE
                | ((Build.VERSION.SDK_INT >= 14) ? Context.BIND_ADJUST_WITH_ACTIVITY : 0));
      }

      if (mBackgroundServices.dbShimService == null && 
          mBackgroundServices.dbShimServiceConnection == null) {
        Log.i(LOGTAG, "Attempting bind to DbShim service");
        mBackgroundServices.dbShimServiceConnection = new ServiceConnectionWrapper();
        Intent bind_intent = new Intent();
        bind_intent.setClassName(DbShimConsts.DBSHIM_SERVICE_PACKAGE,
            DbShimConsts.DBSHIM_SERVICE_CLASS);
        getActivity().bindService(
            bind_intent,
            mBackgroundServices.dbShimServiceConnection,
            Context.BIND_AUTO_CREATE
                | ((Build.VERSION.SDK_INT >= 14) ? Context.BIND_ADJUST_WITH_ACTIVITY : 0));
      }
    }
  }

  private void doServiceConnected(ComponentName className, IBinder service) {
    if (className.getClassName().equals(WebkitServerConsts.WEBKITSERVER_SERVICE_CLASS)) {
      Log.i(LOGTAG, "Bound to WebServer service");
      mBackgroundServices.webkitfilesService = OdkWebkitServerInterface.Stub.asInterface(service);
    }

    if (className.getClassName().equals(DbShimConsts.DBSHIM_SERVICE_CLASS)) {
      Log.i(LOGTAG, "Bound to DbShim service");
      mBackgroundServices.dbShimService = OdkDbShimInterface.Stub.asInterface(service);
    }

    configureView();
  }

  public void configureView() {
    Log.i(LOGTAG, "configureView - possibly updating service information within ODKWebView");
    if (getActivity() != null) {
      View v = getActivity().findViewById(R.id.webkit_view);
      if (v != null && v instanceof ODKWebView) {
        ODKWebView wv = (ODKWebView) v;
        if (mBackgroundServices.isDestroying) {
          wv.serviceChange(false, null);
        } else if (getActivity() != null) {
          wv.serviceChange(mBackgroundServices.webkitfilesService != null && 
              mBackgroundServices.dbShimService != null, mBackgroundServices.dbShimService);
        }
      }
    }
  }

  private void doServiceDisconnected(ComponentName className) {
    if (className.getClassName().equals(WebkitServerConsts.WEBKITSERVER_SERVICE_CLASS)) {
      if (mBackgroundServices.isDestroying) {
        Log.i(LOGTAG, "Unbound from WebServer service (intentionally)");
      } else {
        Log.w(LOGTAG, "Unbound from WebServer service (unexpected)");
      }
      mBackgroundServices.webkitfilesService = null;
      unbindWebkitfilesServiceWrapper();
    }

    if (className.getClassName().equals(DbShimConsts.DBSHIM_SERVICE_CLASS)) {
      if (mBackgroundServices.isDestroying) {
        Log.i(LOGTAG, "Unbound from DbShim service (intentionally)");
      } else {
        Log.w(LOGTAG, "Unbound from DbShim service (unexpected)");
      }
      mBackgroundServices.dbShimService = null;
      unbindDbShimBinderWrapper();
    }

    configureView();

    // the bindToService() method decides whether to connect or not...
    bindToService();
  }

  // /////////////////////////////////////////////////////////////////////////
  // registrations

  public void establishReadLicenseListener(LicenseReaderListener listener) {
    mLicenseReaderListener = listener;
    // async task may have completed while we were reorienting...
    if (mBackgroundTasks.mLicenseReaderTask != null
        && mBackgroundTasks.mLicenseReaderTask.getStatus() == AsyncTask.Status.FINISHED) {
      this.readLicenseComplete(mBackgroundTasks.mLicenseReaderTask.getResult());
    }
  }

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

  public void establishInitializationListener(InitializationListener listener) {
    mInitializationListener = listener;
    // async task may have completed while we were reorienting...
    if (mBackgroundTasks.mInitializationTask != null
        && mBackgroundTasks.mInitializationTask.getStatus() == AsyncTask.Status.FINISHED) {
      this.initializationComplete(mBackgroundTasks.mInitializationTask.getOverallSuccess(),
          mBackgroundTasks.mInitializationTask.getResult());
    }
  }

  // ///////////////////////////////////////////////////
  // actions

  public synchronized void readLicenseFile(String appName, LicenseReaderListener listener) {
    mLicenseReaderListener = listener;
    if (mBackgroundTasks.mLicenseReaderTask != null
        && mBackgroundTasks.mLicenseReaderTask.getStatus() != AsyncTask.Status.FINISHED) {
      Toast.makeText(this.getActivity(), getString(R.string.still_reading_license_file), Toast.LENGTH_LONG).show();
    } else {
      LicenseReaderTask lrt = new LicenseReaderTask();
      lrt.setApplication(getActivity().getApplication());
      lrt.setAppName(appName);
      lrt.setLicenseReaderListener(this);
      mBackgroundTasks.mLicenseReaderTask = lrt;
      executeTask(mBackgroundTasks.mLicenseReaderTask);
    }
  }

  public synchronized void deleteSelectedForms(String appName, DeleteFormsListener listener, String[] toDelete, boolean deleteFormAndData) {
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

  public synchronized void initializeAppName(String appName, InitializationListener listener) {
    mInitializationListener = listener;
    if (mBackgroundTasks.mInitializationTask != null
        && mBackgroundTasks.mInitializationTask.getStatus() != AsyncTask.Status.FINISHED) {
      // Toast.makeText(this.getActivity(),
      // getString(R.string.expansion_in_progress),
      // Toast.LENGTH_LONG).show();
    } else {
      InitializationTask cf = new InitializationTask();
      cf.setApplication(getActivity().getApplication());
      cf.setAppName(appName);
      cf.setInitializationListener(this);
      mBackgroundTasks.mInitializationTask = cf;
      executeTask(mBackgroundTasks.mInitializationTask, (Void) null);
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

  public synchronized void clearInitializationTask() {
    mInitializationListener = null;
    if (mBackgroundTasks.mInitializationTask != null) {
      mBackgroundTasks.mInitializationTask.setInitializationListener(null);
      if (mBackgroundTasks.mInitializationTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mInitializationTask.cancel(true);
      }
    }
    mBackgroundTasks.mInitializationTask = null;
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

  public synchronized void cancelInitializationTask() {
    if (mBackgroundTasks.mInitializationTask != null) {
      if (mBackgroundTasks.mInitializationTask.getStatus() != AsyncTask.Status.FINISHED) {
        mBackgroundTasks.mInitializationTask.cancel(true);
      }
    }
  }

  // /////////////////////////////////////////////////////////////////////////
  // callbacks

  @Override
  public void readLicenseComplete(String result) {
    if (mLicenseReaderListener != null) {
      mLicenseReaderListener.readLicenseComplete(result);
    }
    mBackgroundTasks.mLicenseReaderTask = null;
  }

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

  @Override
  public void initializationComplete(boolean overallSuccess, ArrayList<String> result) {
    if (mInitializationListener != null) {
      mInitializationListener.initializationComplete(overallSuccess, result);
    }
  }

  @Override
  public void initializationProgressUpdate(String status) {
    if (mInitializationListener != null) {
      mInitializationListener.initializationProgressUpdate(status);
    }
  }
}
