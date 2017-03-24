/* The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Nan Zhang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
*/
package edu.iub.seclab.appguardian;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import edu.iub.seclab.appguardian.R;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

public class AppGuardianService extends Service {
	static final String TAG = "MONITER_SERVICE";
	static final int TARGET_RUNNING = 1;
	static final int TARGET_TIMEDOUT = 5;
	static final int MSG_SETTINGS_CHANGED = 11;
	static final int MSG_TARGETS_CHANGED = 12;
	boolean LOG_DEBUG = false;
	int NOTIFICATION_ID = 2015;
	boolean appStatus = false;
	int nid = 0;
	static int starttime = 0;
	int measuredtime = 0;
	
	static private Context ctx;
	ArrayList<String> recoverylist = new ArrayList<String>();

	public Integer iuid = 0;
	public Handler handler;
	public boolean dlgShowed = false;
	private ActivityManager activityManager = null;	
	TargetList mTargetList;
	String mActiveTarget;
	
	private static Timer timer = new Timer();

	File sdCardRoot;
	File dir;
	
	public AppGuardianService() {
	}

	@Override
	public void onCreate() {
		Intent notificationIntent = new Intent(this, AppGuardianMainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		Notification notification = new NotificationCompat.Builder(this)
				.setContentTitle("App Guardian")
				.setContentText("App Guardian is protecting your device.")
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentIntent(pendingIntent).setOngoing(true).build();

		startForeground(NOTIFICATION_ID, notification);
		
		mTargetList = new TargetList();
		
		final Context currentctx = this;
		handler = new Handler() {
			public void handleMessage(Message msg) {
				int type = msg.getData().getInt("type");
				switch (type) {
					case TARGET_RUNNING:
						new Thread(new monitorTargetTask()).start();
						break;
					case TARGET_TIMEDOUT:
						restoreTask();
						break;
				}
			}
		};
	}

	@Override
	public void onStart(Intent intent, int startId) {
//		Log.i(TAG, "Service started!");
		// For time consuming an long tasks you can launch a new thread here...
		new Thread(new monitorTopTask()).start();
		timer = new Timer();
		timer.scheduleAtFixedRate(new mainTask(), 0, 1800 * 1000);
	}

    private class mainTask extends TimerTask {
		public void run() {
			startScanner();
		}
	}
 
	@Override
	public void onDestroy() {
		timer.cancel();
		timer = null;
	}
	
	class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SETTINGS_CHANGED:
                	SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                	boolean isChecked = preferences.getBoolean("background_check", true);
                	String  intervalTemp = preferences.getString("background_interval", "30");
                	int interval = Integer.parseInt(intervalTemp) * 60;
                	timer.cancel();
                	if (isChecked) {
                		timer = new Timer();
                		timer.scheduleAtFixedRate(new mainTask(), 60 * 1000, interval * 1000);
                	}
                    break;
                case MSG_TARGETS_CHANGED:
                	mTargetList.updateTargetList();
                	break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }
	
	public void startScanner() {
		String[] rtn;
		//TODO: Low privilege
		stopTask();
		rtn = beginScanning(100, 100, 10);
		Set<String> temp = new HashSet<String>(Arrays.asList(rtn));
		String[] result = temp.toArray(new String[temp.size()]);
	}

    public void killProcess(String pkgName) { 	
        ActivityManager activityManger = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        activityManger.killBackgroundProcesses(pkgName);
    }
    
    public void killProcessByPkg(String [] result) {
    	boolean recover = false;
//		sendStopBroadcast();
		ActivityManager activityManger = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		List<ActivityManager.RunningAppProcessInfo> list = activityManger
				.getRunningAppProcesses();
		if (list != null)
			for (int i = 0; i < list.size(); i++) {
				ActivityManager.RunningAppProcessInfo apinfo = list.get(i);

				for (int j = 0; j < result.length; j++) {
					mTargetList.getTarget(mActiveTarget).killList.add(result[j]);
					int pid = apinfo.pid;
					if (pid == Integer.parseInt(result[j])) {
						String[] pkgList = apinfo.pkgList;
						for (int k = 0; k < pkgList.length; k++) {
							if (apinfo.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
								recover = true;
							} else {
								recover = false;
							}
							activityManger.killBackgroundProcesses(pkgList[k]);
							if (recover) {
								recoverylist.add(pkgList[k]);
							}
						}
					}

				}
			}
	}

	public native String getMessage();
	public native String[] beginScanning(int maxTime, int interval, int warnLimit);
	public native int[] getKillList();
	public native boolean checkScanStatus(boolean status);
	public native boolean stopTask();

	static {
		System.loadLibrary("scanner");
	}
	
	public void showNotification (String message) {
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		
		Intent notificationIntent = new Intent(this, AppGuardianMainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);
		
		Notification notification = new NotificationCompat.Builder(this)
		.setContentTitle("App Guardian")
		.setContentText("App Guardian is protecting your device.")
		.setTicker(message)
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentIntent(pendingIntent).build();

		final int HELLO_ID = 1;
		mNotificationManager.notify(HELLO_ID, notification);
	}
	
	public void cancelNotification () {
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		final int HELLO_ID = 1;
		mNotificationManager.cancel(HELLO_ID);
	}
	
	private class monitorTargetTask implements Runnable {
		public void run() {
			stopTask();
			String [] rtn = beginScanning(20, 100, 2);
			Set<String> temp = new HashSet<String>(Arrays.asList(rtn));
			String[] result = temp.toArray(new String[temp.size()]);
			killProcessByPkg(result);
			int timer = 0;
			while (mTargetList.getType(mActiveTarget) == 1) {
				timer++;
				rtn = beginScanning(1, 3000, 1);
				temp = new HashSet<String>(Arrays.asList(rtn));
				result = temp.toArray(new String[temp.size()]);
				Map<String, Integer> record = mTargetList.getTarget(mActiveTarget).record;
				for (int i = 0; i < result.length; i++) {
					if (record.containsKey(result[i])) {
						record.put(result[i], record.get(result[i]) + 1);
					} else {
						record.put(result[i], 1);
					}
//					Log.e(TAG, "RESULT: " + result[i] + " ==>" + record.get(result[i]).toString() + " / " + timer);
					if (((double)(record.get(result[i]) / timer)) >= 0.8 && timer >= 3) {
						killProcessByPkg(new String[] {result[i]});
//						Log.e(TAG, "ADDED: " + result[i]);
					}
				}
			}
		}
	}
	
	public void restoreTask () {
		mTargetList.setTypeOff();
	}
	
	private class monitorTopTask implements Runnable {
		public void run() {
			while (true) {
				List<RunningTaskInfo> appList = ((ActivityManager) getSystemService("activity"))
						.getRunningTasks(1);
				if ((appList != null) && (appList.size() > 0)) {
					ComponentName localComponentName1 = ((ActivityManager.RunningTaskInfo) appList
							.get(0)).topActivity;
					String packageName = localComponentName1.getPackageName();
					if ((packageName != null)
							&& mTargetList.targetList.contains(packageName)) {
						if (mTargetList.getType(packageName) == 0 || mTargetList.getType(packageName) == 2) {
							mTargetList.setTypeOn(packageName);
							mActiveTarget = packageName;
							Message msgObj = handler.obtainMessage();
							Bundle b = new Bundle();
							b.putInt("type", TARGET_RUNNING);
							b.putString("name", packageName);
							msgObj.setData(b);
							handler.sendMessage(msgObj);
							showNotification(packageName + " is running!");
//  						killProcessByPkg();
						}
					} else if (mTargetList.isRunning()) {
						mTargetList.setTypeWaitAll();
// 						startRecovery();
					}
					if(mTargetList.isTimedOut()) {
						cancelNotification();
						Message msgObj = handler.obtainMessage();
						Bundle b = new Bundle();
						b.putInt("type", TARGET_TIMEDOUT);
						msgObj.setData(b);
						handler.sendMessage(msgObj);
					}
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	private class TargetList {
		ArrayList<String> targetList = new ArrayList<String>();
		ArrayList<Target> targets = new ArrayList<Target>();
		
		public TargetList() {
			updateTargetList();
		}
		
		long timedOut = 5 * 1000;
		
		public void updateTargetList() {
			targetList.clear();
			targets.clear();
			InputStream is;
			try {
				is = openFileInput("targetlist.txt");
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(is));
				String line = reader.readLine();
				while (line != null) {
					targetList.add(line);
					targets.add(new Target(line));
					line = reader.readLine();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public Target getTarget(String packageName) {
			for (Target target : targets) {
				if (target.getPackageName().equals(packageName)) {
					return target;
				}
			}
			return null;
		}
		
		public int getType(String packageName) {
			for (Target target : targets) {
				if (target.getPackageName().equals(packageName)) {
					return target.getType();
				}
			}
			return -1;
		}
		
		public long getQuitTime(String packageName) {
			for (Target target : targets) {
				if (target.getPackageName().equals(packageName)) {
					return target.getQuitTime();
				}
			}
			return -1;
		}
		
		public void setType(String packageName, int type) {
			for (Target target : targets) {
				if (target.getPackageName().equals(packageName)) {
					target.setType(type);
				}
			}
		}
		
		public void setQuitTime(String packageName, int quitTime) {
			for (Target target : targets) {
				if (target.getPackageName().equals(packageName)) {
					target.setQuitTime(quitTime);
				}
			}
		}
		
		public void setTypeOn(String packageName) {
			for (Target target : targets) {
				if (target.getPackageName().equals(packageName)) {
					target.setType(1);
				} else if (target.getType() == 1) {
					target.setType(2);
					target.setQuitTime(System.currentTimeMillis());
				}
			}
		}
		
		public boolean isRunning() {
			for (Target target : targets) {
				if (target.getType() == 1) {
					return true;
				}
			}
			return false;
		}
		
		public void setTypeWaitAll() {
			for (Target target : targets) {
				if (target.getType() == 1) {
					target.setType(2);
					target.setQuitTime(System.currentTimeMillis());
				}
			}
		}
		
		public boolean isTimedOut() {
			long currTime = System.currentTimeMillis();
			for (Target target : targets) {
				if ((target.getQuitTime() != 0) && (currTime - target.getQuitTime() >= timedOut)) {
					return true;
				}
			}
			return false;
		}
		
		public void setTypeOff() {
			long currTime = System.currentTimeMillis();
			for (Target target : targets) {
				if (currTime - target.getQuitTime() >= timedOut) {
					target.setType(0);
					target.setQuitTime(0);
					target.killList.clear();
					target.record.clear();
				}
			}
		}
	}
	
	private class Target {
		public final int OFF = 0;
		public final int ON = 1;
		public final int WAITING = 2;
		
		private String packageName;
		private int type;
		private long quitTime;
		
		public Set<String> killList;
		public Map<String, Integer> record;
		
		public Target(String packageName) {
			this.packageName = packageName;
			this.type = OFF;
			this.quitTime = 0;
			this.killList = new HashSet<String>();
			this.record = new HashMap<String, Integer>();
		}
		
		public void reset() {
			this.type = OFF;
			this.quitTime = 0;
		}
		public int getType() {
			return type;
		}
		public void setType(int type) {
			this.type = type;
		}
		public long getQuitTime() {
			return quitTime;
		}
		public void setQuitTime(long quitTime) {
			this.quitTime = quitTime;
		}
		public String getPackageName() {
			return packageName;
		}
		public void setPackageName(String packageName) {
			this.packageName = packageName;
		}
	}
}
