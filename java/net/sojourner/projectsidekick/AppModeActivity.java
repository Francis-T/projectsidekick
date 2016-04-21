package net.sojourner.projectsidekick;

import java.util.ArrayList;
import java.util.List;

import net.sojourner.projectsidekick.android.AndroidBluetoothLeBridge;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.KnownDevice.DeviceStatus;
import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.types.ServiceBindingActivity;
import net.sojourner.projectsidekick.types.ServiceState;
import net.sojourner.projectsidekick.types.SidekickListAdapter;
import net.sojourner.projectsidekick.utils.Logger;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

public class AppModeActivity extends ServiceBindingActivity {
	public static final int MSG_RESP_SERVICE_STATE		= 1;

	private ProjectSidekickApp 	_app 				= (ProjectSidekickApp) getApplication();
	private BluetoothAdapter 	_bluetoothAdapter 	= BluetoothAdapter.getDefaultAdapter();
	
	private List<KnownDevice> 			_registeredDevices	= null;
	private ArrayAdapter<KnownDevice> 	_deviceListAdapter 	= null;
	private ServiceState 				_eSvcState 			= ServiceState.UNKNOWN;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_app_main);
		
		ImageButton btnSearch = (ImageButton) findViewById(R.id.btn_discover);
		btnSearch.setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startDiscovery();
						return;
					}
				}
		);

		ImageButton btnGuard = (ImageButton) findViewById(R.id.btn_guard);
		btnGuard.setColorFilter(Color.RED);
		btnGuard.setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						toggleGuardMode();
						return;
					}
				}
		);

		/* Restore the old registered device list and show it in our current list */
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		_app.restoreRegisteredDevices();
		addRegisteredDevicesToList();

		/* Create the adapter for the devices to be listed */
		_deviceListAdapter = new SidekickListAdapter(this, _registeredDevices);
		ListView listDevices = (ListView) findViewById(R.id.list_devices);
		listDevices.setAdapter(_deviceListAdapter);

		/* Set our local message handler for use with service queries */
		setMessageHandler(new MessageHandler());
		
		return;
	}

	@Override
	protected void onResume() {
		super.onResume();

		_app.restoreRegisteredDevices();
		updateDeviceList();
		
		if (_receiver != null) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(BluetoothDevice.ACTION_FOUND);
			filter.addAction(AndroidBluetoothLeBridge.ACTION_LE_DISCOVERED);
			filter.addAction(ProjectSidekickService.ACTION_REP_STARTED);
			filter.addAction(ProjectSidekickService.ACTION_REP_FINISHED);
			registerReceiver(_receiver, filter);
		}
		
		if ( _bluetoothAdapter.isEnabled() == false ) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, ProjectSidekickApp.REQUEST_CODE_BLUETOOTH_ENABLE);
		}

		/* Query the service state so that we can update the GUI accordingly */
		queryService(ProjectSidekickService.MSG_QUERY_STATE);

		return;
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		/* Save the current list of registered devices */
		if (_app != null) {
			_app.saveRegisteredDevices();
		}

		if (_receiver != null) {
			unregisterReceiver(_receiver);
		}
		
		super.onStop();
		return;
	}

	@Override
	protected void onDestroy() {
		Logger.info("onDestroy() called for " + this.getLocalClassName());
		PSStatus PSStatus;
		PSStatus = callService(ProjectSidekickService.MSG_STOP);
		if (PSStatus != PSStatus.OK) {
			display("Failed to stop service");
			return;
		}
		display("Stopped service");

		/* Invoke onDestroy() on our superclass to unbind from the service */
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
		Logger.info("onActivityResult Finished");
		
		return;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_config, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.m_item_set_check_mode:
				return false;
			case R.id.m_item_set_check_interval:
				return false;
			case R.id.m_item_set_alarm_toggle:
				showSetAlarmToggleDialog();
				return true;
			default:
				break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	/* *************** */
	/* Private Methods */
	/* *************** */
    private void display(String msg) {
        Toast.makeText( this, msg,  Toast.LENGTH_SHORT).show();
		Logger.info(msg);
        return;
    }

	private void startDiscovery() {
		PSStatus status;
		status = callService(ProjectSidekickService.MSG_START_DISCOVER);
		if (status != PSStatus.OK) {
			display("Failed start service discovery");
			return;
		}
		display("Service discovery started");
		
		return;
	}
	private BluetoothAdapter.LeScanCallback _leScanCbf = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			Toast.makeText(AppModeActivity.this, "Found device: " + device.getName(), Toast.LENGTH_SHORT).show();
			return;
		}
	};

	private void toggleGuardMode() {
		PSStatus status;
		Logger.info("Toggling guard mode...");
		Logger.info("State: " + _eSvcState);
		if (_eSvcState != ServiceState.REPORT) {
			status = callService(ProjectSidekickService.MSG_START_REPORT);
			if (status != PSStatus.OK) {
				display("Failed to start Guard Mode");
				return;
			}
		} else {
			status = callService(ProjectSidekickService.MSG_STOP);
			if (status != PSStatus.OK) {
				display("Failed to stop Guard Mode");
				return;
			}
		}
		Logger.info("Done.");

		return;
	}

	private void addKnownDevice(String name, String address, boolean isDiscovered) {
		addKnownDevice(name, address, isDiscovered, false);
		return;
	}

	private void addKnownDevice(String name, String address, boolean isDiscovered, boolean isRegistered) {
		DeviceStatus dvcStatus = isDiscovered ? DeviceStatus.FOUND : DeviceStatus.UNKNOWN;

		for (KnownDevice kd : _registeredDevices) {
			if (kd.addressMatches(address)) {
				Logger.info("Not adding duplicate entry for " + address);
				kd.setName(name);
				kd.setStatus(dvcStatus);
				_deviceListAdapter.notifyDataSetChanged();
				return;
			}
		}

		KnownDevice newDevice = new KnownDevice(name, address, dvcStatus);
		_registeredDevices.add(newDevice);

		_deviceListAdapter.notifyDataSetChanged();

		return;
	}

	private void addRegisteredDevicesToList() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		_registeredDevices = new ArrayList<KnownDevice>(_app.getRegisteredDevices());

		return;
	}

	private void updateDeviceList() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}

		List<KnownDevice> savedDevices = _app.getRegisteredDevices();

		for (KnownDevice rkd : _registeredDevices) {
			rkd.setRegistered(false);
			for (KnownDevice skd : savedDevices) {
				if (rkd.addressMatches(skd.getAddress())) {
					rkd.setRegistered(true);
					break;
				}
			}
		}

		if (_deviceListAdapter != null) {
			_deviceListAdapter.notifyDataSetChanged();
		}

		return;
	}

	private PSStatus updateGuiToReportStarted() {
		ImageButton btnGuard = (ImageButton) findViewById(R.id.btn_guard);
		btnGuard.setImageResource(android.R.drawable.ic_lock_idle_lock);
		btnGuard.setColorFilter(Color.GREEN);

		return PSStatus.OK;
	}

	private PSStatus updateGuiToReportFinished() {
		ImageButton btnGuard = (ImageButton) findViewById(R.id.btn_guard);
		btnGuard.setImageResource(android.R.drawable.ic_lock_lock);
		btnGuard.setColorFilter(Color.RED);

		return PSStatus.OK;
	}

	private PSStatus handleServiceState(String stateStr) {
		ServiceState s = ServiceState.valueOf(stateStr);
		Logger.info("Detected Service State to be " + s);

		/* Update the service state our activity knows */
		_eSvcState = s;
		if (_eSvcState == ServiceState.REPORT) {
			updateGuiToReportStarted();
		} else {
			updateGuiToReportFinished();
		}

		return PSStatus.OK;
	}

	private void showSetAlarmToggleDialog() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}

		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);

		dlgBuilder.setTitle("Toggle sound alarm");
		dlgBuilder.setMessage("Do you wish to enable sound alarms for this device?")
				.setCancelable(false)
				.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Bundle data = new Bundle();
						data.putBoolean("ALARM", true);
						callService(ProjectSidekickService.MSG_SET_ALARM_TOGGLE, data, null);
						display("Alarms Enabled!");
						return;
					}
				})
				.setNegativeButton("No", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Bundle data = new Bundle();
						data.putBoolean("ALARM", false);
						callService(ProjectSidekickService.MSG_SET_ALARM_TOGGLE, data, null);
						display("Alarms Disabled!");
						return;
					}
				});

		dlgBuilder.create().show();

		return;
	}

	/* ********************* */
	/* Private Inner Classes */
	/* ********************* */
	private class MessageHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			PSStatus status = PSStatus.FAILED;

			Bundle stateBundle = msg.getData();
			switch (msg.what) {
				case MSG_RESP_SERVICE_STATE:
					/* Extract the service state */
					String svcState = stateBundle.getString("STATE");
					status = handleServiceState(svcState);
					break;
				default:
					super.handleMessage(msg);
					break;
			}

			if (status != PSStatus.OK) {
				/* TODO Do something */
				Logger.err("Failed to handle message code: " + msg.what);
			}

			return;
		}
	}

	// Create a BroadcastReceiver for ACTION_FOUND
	private final BroadcastReceiver _receiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        // When discovery finds a device
	        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
	            // Get the BluetoothDevice object from the Intent
	            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
	            // Add the name and address to an array adapter to show in a ListView
	            addKnownDevice(device.getName(), device.getAddress(), true);
	        } else if (AndroidBluetoothLeBridge.ACTION_LE_DISCOVERED.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// Add the name and address to an array adapter to show in a ListView
				addKnownDevice(device.getName(), device.getAddress(), true);
			} else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
	        	Logger.info("Service Discovery Started (receiver)");
	        } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
	        	Logger.info("Service Discovery Finished (receiver)");
			} else if (ProjectSidekickService.ACTION_REP_STARTED.equals(action)) {
				updateGuiToReportStarted();
				_eSvcState = ServiceState.REPORT;
				display("Guard Mode Started");
			} else if (ProjectSidekickService.ACTION_REP_FINISHED.equals(action)) {
				updateGuiToReportFinished();
				_eSvcState = ServiceState.UNKNOWN;
				display("Guard Mode Stopped");
			}
	    }
	};
}
