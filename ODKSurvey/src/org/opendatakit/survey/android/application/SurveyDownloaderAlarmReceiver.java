package org.opendatakit.survey.android.application;

import com.google.android.vending.expansion.downloader.DownloaderClientMarshaller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;

public class SurveyDownloaderAlarmReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
        try {
            DownloaderClientMarshaller.startDownloadServiceIfRequired(context, intent,
                    SurveyDownloaderService.class);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
	}

}
