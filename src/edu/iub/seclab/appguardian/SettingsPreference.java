package edu.iub.seclab.appguardian;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class SettingsPreference extends PreferenceFragment implements OnSharedPreferenceChangeListener {
	
	Messenger mService = null;
	
	boolean mBound;
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.settings_pref);
        
        if (!isMyServiceRunning(AppGuardianService.class)) {
        	getActivity().startService(new Intent(getActivity(), AppGuardianService.class));
        }
        
		getActivity().bindService(
				new Intent(getActivity(), AppGuardianService.class),
				mConnection, 0);

        ListPreference listPreference = (ListPreference) findPreference("background_interval");
        if(listPreference.getValue()==null) {
            // to ensure we don't get a null value
            // set first value by default
            String defaultValue = "30";
            PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("background_interval", defaultValue);
            listPreference.setValue(defaultValue);
            listPreference.setSummary("30 minutes");
        } else {
        	listPreference.setSummary(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("background_interval", "") + " minutes");
        }
        
        EditTextPreference editPreference = (EditTextPreference) findPreference("about_us");
        editPreference.setEnabled(false);
        
        Preference button = (Preference)findPreference("reset");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {   
                        	File file = new File(getActivity().getFilesDir().getAbsolutePath() + "/whitelist.txt");
                        	file.delete();
                        	copyAssets();
                            return true;
                        }
                    });
    }
	
	@Override
	public void onResume() {
	    super.onResume();
	    getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

	}

	@Override
	public void onPause() {
	    getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	    super.onPause();
	}
	
	@Override
	public void onDestroy() {
	    if (mBound) {
        	getActivity().unbindService(mConnection);
            mBound = false;
        }
	    super.onDestroy();
	}
	
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        if (!mBound) return;

        if (key.equals("background_interval"))
        {
            // Set summary to be the user-description for the selected value
        	ListPreference listPreference = (ListPreference) findPreference("background_interval");
    		listPreference.setSummary(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("background_interval", "") + " minutes");
        }
        
        Message msg = Message.obtain(null, AppGuardianService.MSG_SETTINGS_CHANGED, 0, 0);
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
	
	private boolean isMyServiceRunning(Class<?> serviceClass) {
	    ActivityManager manager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (serviceClass.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            mService = new Messenger(service);
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mBound = false;
        }
    };
    
	private void copyAssets() {
		AssetManager assetManager = getActivity().getAssets();
		InputStream in = null;
		OutputStream out = null;
		try {
			in = assetManager.open("whitelist.txt");
			out = getActivity().openFileOutput("whitelist.txt", Context.MODE_PRIVATE);
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
}