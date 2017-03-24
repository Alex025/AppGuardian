package edu.iub.seclab.appguardian;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Message;

public class TelReceiver extends BroadcastReceiver {

	final String tel="android.intent.action.PHONE_STATE";
	@Override
	public void onReceive(Context context, Intent intent) {

		if (intent != null) {
			if (intent.getAction().equals(tel)) {
				Message msg = new Message();
				msg.what = AppGuardianMainActivity.FLAG;

				AppGuardianMainActivity.handler.sendMessage(msg);

			}
		}
	}

}