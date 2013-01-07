package org.opendatakit.survey.android.application;

import com.google.android.vending.expansion.downloader.impl.DownloaderService;

public class SurveyDownloaderService extends DownloaderService {

	@Override
	public String getPublicKey() {
		return Survey.getInstance().getBase64PublicKey();
	}

	@Override
	public byte[] getSALT() {
		return Survey.getInstance().getSalt();
	}

	@Override
	public String getAlarmReceiverClassName() {
		// TODO Auto-generated method stub
		return SurveyDownloaderAlarmReceiver.class.getName();
	}

}
