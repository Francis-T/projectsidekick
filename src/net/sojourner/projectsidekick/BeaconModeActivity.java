package net.sojourner.projectsidekick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sojourner.projectsidekick.types.GuardedItem;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.Status;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Toast;

public class BeaconModeActivity extends ListActivity {
	private ProjectSidekickApp 			_app 				= null;
	private Messenger 					_service 			= null;
	private BluetoothAdapter			_bluetoothAdapter 	= BluetoothAdapter.getDefaultAdapter();
	private boolean			 			_isActive 			= false;
	private boolean 					_bIsBound 			= false;
	private boolean 					_bIsSettingUp 		= false;
	private ArrayAdapter<GuardedItem> 	_guardedListAdapter	= null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_beacon_main);
		
		if ( _bluetoothAdapter.isEnabled() == false ) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, ProjectSidekickApp.REQUEST_CODE_BLUETOOTH_ENABLE);
		}
		
		Intent discoverableIntent 
			= new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		startActivityForResult(discoverableIntent, ProjectSidekickApp.REQUEST_BLUETOOTH_DISCOVERABLE);
		
		final ImageButton btnAllowDiscover 
			= (ImageButton) findViewById(R.id.btn_allow_discovery);
		btnAllowDiscover.setColorFilter(Color.RED);
		btnAllowDiscover.setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Status status = Status.FAILED;
					if (!_isActive) {
						status = callService(ProjectSidekickService.MSG_START_GUARD);
						if (status != Status.OK) {
							display("Failed to activate GUARD Mode");
							return;
						}
						display("GUARD Mode has been activated");
						_isActive = true;
					} else {
						status = callService(ProjectSidekickService.MSG_STOP);
						if (status != Status.OK) {
							display("Failed to deactivate GUARD Mode");
							return;
						}
						display("GUARD Mode has been deactivated");
						_isActive = false;
					}
					return;
				}
			}
		);

		ImageButton btnMode
			= (ImageButton) findViewById(R.id.btn_mode);
		btnMode.setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Status status = Status.FAILED;
					if (!_bIsSettingUp) {
						status = callService(ProjectSidekickService.MSG_SET_AS_SIDEKICK);
						if (status != Status.OK) {
							display("Failed to set beacon to Anti-Loss Mode.");
							return;
						}
						display("Your beacon has been set to Anti-Loss Mode.");
	
						status = callService(ProjectSidekickService.MSG_START_SETUP);
						if (status != Status.OK) {
							display("Failed to start SETUP Mode.");
							return;
						}
						display("SETUP Mode Started.");
						_bIsSettingUp = true;
					} else {

						status = callService(ProjectSidekickService.MSG_STOP);
						if (status != Status.OK) {
							display("Failed to Stop.");
							return;
						}
						display("Stopped.");
						_bIsSettingUp = false;
					}
					
					return;
				}
			}
		);
		
		_guardedListAdapter = new ArrayAdapter<GuardedItem>(this, android.R.layout.simple_list_item_1);
		setListAdapter(_guardedListAdapter);

		return;
	}

	@Override
	protected void onStart() {
		super.onStart();
		
		if (_receiver != null) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(ProjectSidekickService.ACTION_REGISTERED);
			filter.addAction(ProjectSidekickService.ACTION_UNREGISTERED);
			filter.addAction(ProjectSidekickService.ACTION_UPDATE_LOST);
			filter.addAction(ProjectSidekickService.ACTION_UPDATE_FOUND);
			registerReceiver(_receiver, filter);
		}
		
		if (!_bIsBound) {
			Intent bindServiceIntent = new Intent(this, ProjectSidekickService.class);
			bindService(bindServiceIntent, _serviceConnection, Context.BIND_AUTO_CREATE);
		}
		
		return;
	}

	@Override
	protected void onResume() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		super.onResume();
	}

	@Override
	protected void onPause() {
		
		super.onPause();
		return;
	}
	
	@Override
	protected void onStop() {
		if (_bIsBound) {
			unbindService(_serviceConnection);
			_bIsBound = false;
		}
		
		if (_receiver != null) {
			unregisterReceiver(_receiver);
		}
		
		super.onStop();
		return;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Logger.info("onActivityResult Started");
		
		if (requestCode == ProjectSidekickApp.REQUEST_CODE_BLUETOOTH_ENABLE) {
			Logger.info("Correct Request Code: REQUEST_CODE_BLUETOOTH_ENABLE");
			if (resultCode != RESULT_OK) {
				Logger.info("Result is not ok");
				finish();
				return;
			}
		}
		
		if (requestCode == ProjectSidekickApp.REQUEST_BLUETOOTH_DISCOVERABLE) {
			if (resultCode == RESULT_CANCELED) {
				this.finish();
			}
		}
		Logger.info("onActivityResult Finished");
		return;
	}
	
	/* *************** */
	/* Private Methods */
	/* *************** */
    private void display(String msg) {
        Toast.makeText( this, msg,  Toast.LENGTH_SHORT).show();
        return;
    }

    private Status callService(int msgId) {
		return callService(msgId, null);
    }
    
    private Status callService(int msgId, Bundle extras) {
    	if (_service == null) {
    		Logger.err("Service unavailable");
    		return Status.FAILED;
    	}
    	
    	if (!_bIsBound) {
    		Logger.err("Service unavailable");
    		return Status.FAILED;
    	}
    	
		Message msg = Message.obtain(null, msgId, 0, 0);
		msg.setData(extras);
		
		try {
			_service.send(msg);
		} catch (Exception e) {
			Logger.err("Failed to call service: " + e.getMessage());
			return Status.FAILED;
		}
		
		return Status.OK;
    }
	
	private List<KnownDevice> _registeredDevices = new ArrayList<KnownDevice>();

    public Status addRegisteredDevice(KnownDevice device) {
    	/* Check if we already have this device in our list */
    	for (KnownDevice kd : _registeredDevices) {
    		if (kd.getAddress().equals(device.getAddress())) {
    			Logger.warn("Device already registered");
    			return Status.OK;
    		}
    	}
    	
    	if (_registeredDevices.add(device) != true) {
    		Logger.err("Failed to add device");
    		return Status.FAILED;
    	}
    	
    	return Status.OK;
    }

    public void restoreMasterList() {
		SharedPreferences prefs = getSharedPreferences("PROJECT_BEACON__1127182", MODE_WORLD_WRITEABLE);
		
		Set<String> rgdSet = prefs.getStringSet("MASTER_DVC_LIST", null);
		if (rgdSet != null) {
			for (String item : rgdSet) {
				String deviceInfo[] = item.split(",");
				if (deviceInfo.length != 2) {
					Logger.err("Skipping malformed registered device string: " 
								+ item);
					continue;
				}
				
				KnownDevice kd = new KnownDevice(deviceInfo[0], deviceInfo[1]);
				kd.setRegistered(true);
				
				addRegisteredDevice(kd);
			}
		}
		return;
    }
    
	private Status saveMasterList() {
		SharedPreferences prefs = getSharedPreferences("PROJECT_BEACON__1127182", MODE_WORLD_WRITEABLE);

		Set<String> rgdInfoSet = new HashSet<String>();
		for (KnownDevice rd : _registeredDevices) {
			Logger.info("Adding " + rd.getName() + " to set");
			rgdInfoSet.add(rd.getName() + "," + rd.getAddress());
		}
		prefs.edit().putStringSet("MASTER_DVC_LIST", rgdInfoSet).commit();
		
		Logger.info("Saved registered devices");
		
		return Status.OK;
	}

	/* ********************* */
	/* Private Inner Classes */
	/* ********************* */
	private ServiceConnection _serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName className) {
			_service = null;
			_bIsBound = false;
			
			return;
		}
		
		@Override
		public void onServiceConnected(ComponentName className, IBinder binder) {
			_service = new Messenger(binder);
			_bIsBound = true;
			
			return;
		}
	};

	private final BroadcastReceiver _receiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        // When discovery finds a device
	        if (ProjectSidekickService.ACTION_REGISTERED.equals(action)) {
	        	String name = intent.getStringExtra("NAME");
	        	String address = intent.getStringExtra("ADDRESS");
	        	
	        	for (int i = 0; i < _guardedListAdapter.getCount(); i++) {
	        		GuardedItem item = _guardedListAdapter.getItem(i);
	        		if (item.getAddress().equals(address)) {
	        			Logger.warn("Registered device already listed");
	        			item.setIsLost(false);
	    	        	_guardedListAdapter.notifyDataSetChanged();
	        			return;
	        		}
	        	}
	        	
	        	_guardedListAdapter.add(new GuardedItem(name, address));
	        	_guardedListAdapter.notifyDataSetChanged();
	        	
	        } else if (ProjectSidekickService.ACTION_UNREGISTERED.equals(action)) {
	        	String address = intent.getStringExtra("ADDRESS");
	        	
	        	for (int i = 0; i < _guardedListAdapter.getCount(); i++) {
	        		GuardedItem item = _guardedListAdapter.getItem(i);
	        		if (item.getAddress().equals(address)) {
	        			_guardedListAdapter.remove(item);
	        			break;
	        		}
	        	}
	        	_guardedListAdapter.notifyDataSetChanged();
	        } else if (ProjectSidekickService.ACTION_UPDATE_LOST.equals(action)) {
	        	String address = intent.getStringExtra("ADDRESS");
	        	boolean isLost = intent.getBooleanExtra("LOST_STATUS", false);
	        	
	        	for (int i = 0; i < _guardedListAdapter.getCount(); i++) {
	        		GuardedItem item = _guardedListAdapter.getItem(i);
	        		if (item.getAddress().equals(address)) {
	        			item.setIsLost(isLost);
	        			break;
	        		}
	        	}
	        	_guardedListAdapter.notifyDataSetChanged();
	        } else if (ProjectSidekickService.ACTION_UPDATE_FOUND.equals(action)) {
	        	String address = intent.getStringExtra("ADDRESS");
	        	boolean isLost = intent.getBooleanExtra("LOST_STATUS", false);
	        	
	        	for (int i = 0; i < _guardedListAdapter.getCount(); i++) {
	        		GuardedItem item = _guardedListAdapter.getItem(i);
	        		if (item.getAddress().equals(address)) {
	        			item.setIsLost(isLost);
	        			break;
	        		}
	        	}
	        	_guardedListAdapter.notifyDataSetChanged();
	        }
	        
	        return;
	    }
	};
}
