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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class BeaconModeActivity extends ListActivity {
	private ProjectSidekickApp 			_app 				= null;
	private Messenger 					_service 			= null;
	private BluetoothAdapter			_bluetoothAdapter 	= BluetoothAdapter.getDefaultAdapter();
	private boolean 					_bIsBound 			= false;
	private ArrayAdapter<GuardedItem> 	_guardedListAdapter	= null;
	private BeaconState					_eBState			= BeaconState.INACTIVE;
	private TextView					_txvState			= null;
	
	private enum BeaconState { INACTIVE, SETUP, GUARD };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Logger.info("onCreate() called for " + this.getLocalClassName());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_beacon_main);
		
		if ( _bluetoothAdapter.isEnabled() == false ) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, ProjectSidekickApp.REQUEST_CODE_BLUETOOTH_ENABLE);
		}
		
		Intent discoverableIntent 
			= new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		startActivityForResult(discoverableIntent, ProjectSidekickApp.REQUEST_BLUETOOTH_DISCOVERABLE);
		
		final ImageButton btnStartGuardMode 
			= (ImageButton) findViewById(R.id.btn_start_guard_mode);
		btnStartGuardMode.setColorFilter(Color.RED);
		btnStartGuardMode.setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Status status = Status.FAILED;
					if (getState() == BeaconState.INACTIVE) {
						status = callService(ProjectSidekickService.MSG_START_GUARD);
						if (status != Status.OK) {
							display("Failed to activate GUARD Mode");
							return;
						}
						
						ImageButton btn = (ImageButton) v;
						btn.setImageResource(android.R.drawable.ic_lock_idle_lock);
						btn.setColorFilter(Color.GREEN);
						
						//_isActive = true;
						setState(BeaconState.GUARD);
						display("GUARD Mode has been activated");
					} else if (getState() == BeaconState.GUARD) {
						status = callService(ProjectSidekickService.MSG_STOP);
						if (status != Status.OK) {
							display("Failed to deactivate GUARD Mode");
							return;
						}
						
						ImageButton btn = (ImageButton) v;
						btn.setImageResource(android.R.drawable.ic_lock_lock);
						btn.setColorFilter(Color.RED);
						
						//_isActive = false;
						setState(BeaconState.INACTIVE);
						display("GUARD Mode has been deactivated");
					}
					return;
				}
			}
		);

		ImageButton btnMode
			= (ImageButton) findViewById(R.id.btn_mode);
		btnMode.setColorFilter(Color.RED);
		btnMode.setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Status status = Status.FAILED;
					if (getState() == BeaconState.INACTIVE) {
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
					} else if (getState() == BeaconState.SETUP) {

						status = callService(ProjectSidekickService.MSG_STOP);
						if (status != Status.OK) {
							display("Failed to Stop.");
							return;
						}
					}
					
					return;
				}
			}
		);
		
		_txvState = (TextView) findViewById(R.id.txv_state);
		
		_guardedListAdapter = new ArrayAdapter<GuardedItem>(this, android.R.layout.simple_list_item_1);
		setListAdapter(_guardedListAdapter);
		
		restoreGuardedItems();
		
		if (!_bIsBound) {
			Intent bindServiceIntent = new Intent(this, ProjectSidekickService.class);
			bindService(bindServiceIntent, _serviceConnection, Context.BIND_AUTO_CREATE);
		}

		return;
	}

	@Override
	protected void onStart() {
		Logger.info("onStart() called for " + this.getLocalClassName());
		super.onStart();
		
		if (_receiver != null) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(ProjectSidekickService.ACTION_REGISTERED);
			filter.addAction(ProjectSidekickService.ACTION_UNREGISTERED);
			filter.addAction(ProjectSidekickService.ACTION_UPDATE_LOST);
			filter.addAction(ProjectSidekickService.ACTION_UPDATE_FOUND);
			filter.addAction(ProjectSidekickService.ACTION_REG_STARTED);
			filter.addAction(ProjectSidekickService.ACTION_REG_FINISHED);
			registerReceiver(_receiver, filter);
		}
		
		return;
	}

	@Override
	protected void onResume() {
		Logger.info("onResume() called for " + this.getLocalClassName());
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		super.onResume();
	}

	@Override
	protected void onPause() {
		Logger.info("onPause() called for " + this.getLocalClassName());
		super.onPause();
		return;
	}
	
	@Override
	protected void onStop() {
		Logger.info("onStop() called for " + this.getLocalClassName());
		if (_receiver != null) {
			unregisterReceiver(_receiver);
		}
		
		super.onStop();
		return;
	}

	@Override
	protected void onDestroy() {
		Logger.info("onDestroy() called for " + this.getLocalClassName());
		if (_bIsBound) {
			unbindService(_serviceConnection);
			_bIsBound = false;
		}
		
		super.onDestroy();
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
    		if (kd.addressMatches(device.getAddress())) {
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
		if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			Logger.err("Master List Restore Not supported");
			return;
		}

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
	
	private BeaconState getState() {
		return _eBState;
	}
	
	private void setState(BeaconState state) {
		_eBState = state;
		
		if (_txvState != null) {
			_txvState.setText("State: " + state.toString());
		}
		
		return;
	}
	
	private Status restoreGuardedItems() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		/* Clear our guarded items list */
		_guardedListAdapter.clear();
		
		/* Restore the list from our records */
		List<KnownDevice> registeredDevices = _app.getRegisteredDevices();
		for (KnownDevice device : registeredDevices) {
			GuardedItem item = new GuardedItem(device.getName(), device.getAddress());
			item.setIsLost(true);
			_guardedListAdapter.add(item);
		}
		
		/* Refresh the adapter */
		_guardedListAdapter.notifyDataSetChanged();
		
		return Status.OK;
	}

	private void updateGuiToRegistrationStarted() {
		ImageButton btn = (ImageButton) findViewById(R.id.btn_mode);
		btn.setColorFilter(Color.GREEN);
		setState(BeaconState.SETUP);
		display("SETUP Mode Started.");
		return;
	}

	private void updateGuiToRegistrationFinished() {
		ImageButton btn = (ImageButton) findViewById(R.id.btn_mode);
		btn.setColorFilter(Color.RED);
		setState(BeaconState.INACTIVE);
		display("Stopped.");
		return;
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
	        		if (item.addressMatches(address)) {
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
	        		if (item.addressMatches(address)) {
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
	        		if (item.addressMatches(address)) {
	        			item.setIsLost(isLost);
	        			break;
	        		}
	        	}
	        	
	        	_guardedListAdapter.notifyDataSetChanged();
	        } else if (ProjectSidekickService.ACTION_UPDATE_FOUND.equals(action)) {
	        	String address = intent.getStringExtra("ADDRESS");
				short rssi = intent.getShortExtra("RSSI", (short) 0);
	        	boolean isLost = intent.getBooleanExtra("LOST_STATUS", false);
	        	
	        	for (int i = 0; i < _guardedListAdapter.getCount(); i++) {
	        		GuardedItem item = _guardedListAdapter.getItem(i);
	        		if (item.addressMatches(address)) {
	        			item.setIsLost(isLost);
						item.setRssi(rssi);
	        			break;
	        		}
	        	}
	        	_guardedListAdapter.notifyDataSetChanged();
	        } else if (ProjectSidekickService.ACTION_REG_STARTED.equals(action)) {
				updateGuiToRegistrationStarted();
			} else if (ProjectSidekickService.ACTION_REG_FINISHED.equals(action)) {
				updateGuiToRegistrationFinished();
			}
	        
	        return;
	    }
	};
}
