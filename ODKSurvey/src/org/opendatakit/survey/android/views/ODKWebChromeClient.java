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

package org.opendatakit.survey.android.views;

import org.opendatakit.survey.android.activities.MainMenuActivity;
import org.opendatakit.survey.android.application.Survey;
import org.opendatakit.survey.android.utilities.WebLogger;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage.QuotaUpdater;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.VideoView;

public class ODKWebChromeClient extends WebChromeClient implements
		MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

	private static final String t = "ODKWebChromeClient";
	private Activity a;
	private VideoView video = null;
	private WebChromeClient.CustomViewCallback callback = null;
	private WebLogger log = Survey.getInstance().getLogger();

	public ODKWebChromeClient(Context context) {
		this.a = (Activity) context;
	}

	public void setActivity(Activity a) {
		this.a = a;
	}

	@Override
	public void onShowCustomView(View view, CustomViewCallback callback) {
    	if ( this.callback != null ) {
    		this.callback.onCustomViewHidden();
    	}
		this.callback = callback;
		if (view instanceof FrameLayout) {
			FrameLayout frame = (FrameLayout) view;
			if (frame.getFocusedChild() instanceof VideoView) {
				log.i(t,"onShowCustomView: FrameLayout Video");
				video = (VideoView) frame.getFocusedChild();
				video.setOnCompletionListener(this);
				video.setOnErrorListener(this);
				((MainMenuActivity) a).swapToCustomView(view);
				super.onShowCustomView(view, callback);
//				video.seekTo(0);// reset to start of video...
//				video.start();
			} else {
				log.i(t,"onShowCustomView: FrameLayout not Video " + frame.getFocusedChild().getClass().getCanonicalName());
				((MainMenuActivity) a).swapToCustomView(view);
				super.onShowCustomView(view, callback);
			}
		} else {
			log.i(t,"onShowCustomView: not FrameLayout " + view.getClass().getCanonicalName());
			((MainMenuActivity) a).swapToCustomView(view);
			super.onShowCustomView(view, callback);
		}
	}

    @Override
    public void onHideCustomView() {
    	log.d(t,"onHideCustomView");
    	((MainMenuActivity) a).swapOffCustomView();
    	if ( video != null ) {
    		video.stopPlayback();
    	}
    	video = null;
    	if ( callback != null ) {
    		callback.onCustomViewHidden();
    		callback = null;
    	}
    }

	@Override
	public void onCompletion(MediaPlayer mp) {
		log.d(t,"Video ended");
		onHideCustomView();
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		log.d(t,"Video error");
		onHideCustomView();
		return true;
	}

    /**
     * Ask the browser for an icon to represent a <video> element.
     * This icon will be used if the Web page did not specify a poster attribute.
     * @return Bitmap The icon or null if no such icon is available.
     */
    @Override
    public Bitmap getDefaultVideoPoster() {
    	return ((MainMenuActivity) a).getDefaultVideoPoster();
    }

    /**
     * Ask the host application for a custom progress view to show while
     * a <video> is loading.
     * @return View The progress view.
     */
    @Override
    public View getVideoLoadingProgressView() {
    	return ((MainMenuActivity) a).getVideoLoadingProgressView();
    }

	@Override
	public void getVisitedHistory(ValueCallback<String[]> callback) {
		callback.onReceiveValue(new String[] {});
	}

	@Override
	public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
		if (consoleMessage.sourceId() == null
				|| consoleMessage.sourceId().length() == 0) {
			log.e(t,"onConsoleMessage: Javascript exception: "
					+ consoleMessage.message());
			return true;
		} else {
			log.e(t,consoleMessage.message());
			return true;
		}
	}

	@Override
	public void onExceededDatabaseQuota(String url, String databaseIdentifier,
			long currentQuota, long estimatedSize, long totalUsedQuota,
			QuotaUpdater quotaUpdater) {
		long space = (4 + (currentQuota / 65536L)) * 65536L;
		log.i(t,"Extending Database quota to: " + Long.toString(space));
		quotaUpdater.updateQuota(space);
	}

	@Override
	public void onReachedMaxAppCacheSize(long spaceNeeded, long totalUsedQuota,
			QuotaUpdater quotaUpdater) {
		long space = (4 + (spaceNeeded / 65536L)) * 65536L;
		log.i(t,"Extending AppCache quota to: " + Long.toString(space));
		quotaUpdater.updateQuota(space);
	}

	@Override
	public void onConsoleMessage(String message, int lineNumber, String sourceID) {
		log.i(t,sourceID + "[" + lineNumber + "]: " + message);
	}

	@Override
	public boolean onJsAlert(WebView view, String url, String message,
			JsResult result) {
		log.w(t,url + ": " + message);
		return false;
	}

}
