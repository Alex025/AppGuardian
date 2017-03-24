package edu.iub.seclab.appguardian;

import java.util.List;

import edu.iub.seclab.appguardian.R;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;

public class NotificationReceiverActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_notification_receiver);
//		Log.i("NR", "Notification clicked!");
		String pkgName;
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null) {
			pkgName = extras.getString("PKG_NAME");
			try {
				startPackage(pkgName);
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			Log.i("NR", pkgName);
		}
	}
	
    public void startPackage(String recoveryPackageName) throws NameNotFoundException {
//    	Log.i("NR", "Start " + recoveryPackageName);
		PackageInfo pi = getPackageManager().getPackageInfo(
				recoveryPackageName, 0);
		Intent resolveIntent = new Intent(Intent.ACTION_MAIN, null);
		resolveIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		resolveIntent.setPackage(pi.packageName);
		final PackageManager pm = getPackageManager();
		List<ResolveInfo> apps = pm.queryIntentActivities(resolveIntent, 0);
		ResolveInfo ri = apps.iterator().next();
		if (ri != null) {
			String packageName = ri.activityInfo.packageName;
			String className = ri.activityInfo.name;
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_LAUNCHER);
			ComponentName cn = new ComponentName(packageName, className);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.setComponent(cn);
			startActivity(intent);
		}
    }
}