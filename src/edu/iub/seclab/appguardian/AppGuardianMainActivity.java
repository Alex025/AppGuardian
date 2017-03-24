package edu.iub.seclab.appguardian;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import edu.iub.seclab.appguardian.R;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

public class AppGuardianMainActivity extends FragmentActivity implements  ActionBar.TabListener {
	
	String TAG = "SIDE_PROTECTOR";
	
	public static Handler handler;
	private ProgressBar progressBar;
	private int progressStatus = 0;
	private TextView textView;
	public static int FLAG = 1;
	public int killList[];
	public static ArrayList<String> killPkgList = new ArrayList<String>();
	
	PageAdapter mSettingsAdapter;
	ViewPager mViewPager;
	private String[] tabs = { "Settings", "White List", "ProtectionList"};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		final ActionBar actionBar = getActionBar();

	    // Specify that tabs should be displayed in the action bar.
	    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
	    
		setContentView(R.layout.activity_side_protector_main);
		
		if (!isMyServiceRunning(AppGuardianService.class)) {
			startService(new Intent(this, AppGuardianService.class));
		}
		
		mSettingsAdapter =
                new PageAdapter(
                        getSupportFragmentManager());
        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        mViewPager.setAdapter(mSettingsAdapter);
        
        actionBar.setHomeButtonEnabled(false);      
 
        // Adding Tabs
        for (String tab_name : tabs) {
            actionBar.addTab(actionBar.newTab().setText(tab_name)
                    .setTabListener(this));
        }
		
		handler = new Handler(){ 
			 
            @Override 
            public void handleMessage(Message msg) { 
                super.handleMessage(msg); 
				if (msg.what == FLAG) {
				}
            } 
             
        };
        
		TelephonyManager telManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		TelListner listener = new TelListner();
		telManager.listen(listener,
				PhoneStateListener.LISTEN_CALL_STATE);
	}
	
	private boolean isMyServiceRunning(Class<?> serviceClass) {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (serviceClass.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	@Override
	protected void onResume() {
		super.onResume(); 
		String path = getFilesDir().getAbsolutePath() + "/whitelist.txt";
	    File file = new File(path);
		if (!file.exists()) {
			copyAssets("whitelist.txt");
		}
		path = getFilesDir().getAbsolutePath() + "/targetlist.txt";
	    file = new File(path);
		if (!file.exists()) {
			copyAssets("targetlist.txt");
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.side_protector_main, menu);
		return true;
	}

	public native String getMessage();
	public native String[] beginScanning(int maxTime, int interval, int warnLimit);
	public native int beginScanning1();
	public native int[] getKillList();
	public native boolean checkScanStatus(boolean status);
	public native boolean stopTask();
	
	class TelListner extends PhoneStateListener{
		private String number;
	    private boolean isRecord;
	    private MediaRecorder recorder;
	    
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			   switch (state) {
               case TelephonyManager.CALL_STATE_IDLE:
                   number = null;
                   break;
               case TelephonyManager.CALL_STATE_OFFHOOK:
            	   	number=incomingNumber;
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
						showAlert();
                   break;
               case TelephonyManager.CALL_STATE_RINGING:
                   number = incomingNumber;
//                 getContactPeople(incomingNumber);
                   break;

               default:
                   break;
               }
		}
	    public void killProcess(String pkgName) { 	
	        ActivityManager activityManger = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

	        activityManger.killBackgroundProcesses(pkgName);

	    }
	    
	    public void showAlert() {
	    	AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
					getBaseContext());
			alertDialogBuilder.setTitle("App Guardian");
			alertDialogBuilder.setMessage("Your phone call is being taped!");
			AlertDialog alertDialog = alertDialogBuilder.create();
			alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
			alertDialog.show();
	    }
		
	}

	private void copyAssets(String filename) {
		AssetManager assetManager = getAssets();
		InputStream in = null;
		OutputStream out = null;
		try {
			in = assetManager.open(filename);
			out = openFileOutput(filename, Context.MODE_PRIVATE);
			copyFile(in, out);
			in.close();
			in = null;
			out.flush();
			out.close();
			out = null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void copyFile(InputStream in, OutputStream out) throws IOException {
	    byte[] buffer = new byte[1024];
	    int read;
	    while((read = in.read(buffer)) != -1){
	      out.write(buffer, 0, read);
	    }
	}
	
	public class PageAdapter extends FragmentStatePagerAdapter {
		public PageAdapter(FragmentManager fm) {
	        super(fm);
	    }
	 
	    @Override
	    public Fragment getItem(int index) {
	 
	        switch (index) {
	        case 0:
	            return new SettingsFragment();
	        case 1:
	            return new WhiteListFragment();
	        case 2:
	            return new TargetListFragment();
	        }
	        return null;
	    }
	    @Override
	    public int getCount() {
	        return 3;
	    }
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		int position = tab.getPosition();

		switch (position) {
		    case 0:
		    	getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsPreference()).commit();
		        break;
		    case 1:
		    	getFragmentManager().beginTransaction().replace(android.R.id.content, new WhiteListPreference()).commit();
		        break;
		    case 2:
		    	getFragmentManager().beginTransaction().replace(android.R.id.content, new TargetListPreference()).commit();
		        break;

		}
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
		// TODO Auto-generated method stub
		
	}
}
