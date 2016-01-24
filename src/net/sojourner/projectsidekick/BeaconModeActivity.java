package net.sojourner.projectsidekick;

import java.util.ArrayList;
import java.util.List;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.Status;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

public class BeaconModeActivity extends Activity implements BluetoothEventHandler {
	private ProjectSidekickApp _app = null;
	private BluetoothAdapter _bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	private IBluetoothBridge _bluetooth = null;
	
	private List<KnownDevice>	_discoveredDevices = new ArrayList<KnownDevice>();
	private boolean			 	_isActive = false;
	private String			 	_mode 		= "theft";
	
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
		
		if (_receiver != null) {
			IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
			registerReceiver(_receiver, filter);
		}
		
		final ImageButton btnAllowDiscover 
			= (ImageButton) findViewById(R.id.btn_allow_discovery);
		btnAllowDiscover.setColorFilter(Color.RED);
		btnAllowDiscover.setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (!_isActive) {
						startBluetooth();
						_isActive = true;
						Toast.makeText(BeaconModeActivity.this, "Device: ON", Toast.LENGTH_SHORT).show();
						btnAllowDiscover.setColorFilter(Color.GREEN);
					} else {
						stopBluetooth();
						_isActive = false;
						Toast.makeText(BeaconModeActivity.this, "Device: OFF", Toast.LENGTH_SHORT).show();
						btnAllowDiscover.setColorFilter(Color.RED);
					}
				}
			}
		);

		ImageButton btnMode
			= (ImageButton) findViewById(R.id.btn_mode);
		btnMode.setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					sendData("Anti-"+_mode+" mode has been enabled on your beacon.");
					if (_mode.equals("theft")) {
						_mode = "loss";
					} else {
						_mode = "theft";
					}
				}
			}
		);

	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	protected void onResume() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		super.onResume();
	}
	
	@Override
	protected void onStop() {
		if (_bluetooth != null) {
			_bluetooth.destroy();
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
	
	private void startBluetooth() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		if (_bluetooth == null) {
			_bluetooth = _app.getBluetoothBridge();
		}
		
		_bluetooth.setEventHandler(this);
		
		if (_bluetooth.initialize(this, true) != Status.OK) {
			Logger.err("Failed to initialize Bluetooth");
			return;
		}
		
		if (_bluetooth.listen() != Status.OK) {
			Logger.err("Failed to start Bluetooth connection listener");
			return;
		}
		
		return;
	}
	
	private void startDeviceDiscovery() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		if (_bluetooth == null) {
			_bluetooth = _app.getBluetoothBridge();
		}
		
		_bluetooth.setEventHandler(this);
		
		if (_bluetooth.initialize(this, false) != Status.OK) {
			Logger.err("Failed to initialize Bluetooth");
			return;
		}
		_bluetooth.startDeviceDiscovery();
		
		return;
	}
	
	private void stopBluetooth() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		if (_bluetooth == null) {
			_bluetooth = _app.getBluetoothBridge();
		}
		
		if (_bluetooth.destroy() != Status.OK) {
			Logger.err("Failed to disconnect Bluetooth");
			return;
		}
		
		_bluetooth.setEventHandler(null);
		
		return;
	}
	
	private void sendData(String msg) {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		if (_bluetooth == null) {
			_bluetooth = _app.getBluetoothBridge();
		}
		
		if (_bluetooth.broadcast(msg.getBytes())!= Status.OK) {
			Logger.err("Failed to send data");
			Toast.makeText(BeaconModeActivity.this, "Sent: " + msg, Toast.LENGTH_SHORT).show();
			return;
		}
	}
	
	private void addDiscoveredDevice(String name, String address) {
		if (_discoveredDevices == null) {
			Logger.err("Discovered device list not initialized");
			return;
		}
		
		/* Check first if this device already exists in our discovered list */
		for (KnownDevice kd : _discoveredDevices) {
			if (kd.getAddress().equals(address)) {
				Logger.warn("Device is already in the discovered list");
				return;
			}
		}
		
		/* Add it to the discovered list */
		_discoveredDevices.add(new KnownDevice(name,address));
		
		return;
	}

    private void processRequest(byte[] data) {
        String dataStr = new String(data);

        if (dataStr.equals("DISCOVER")) {
            /* Restart discovery of nearby devices */
        	startDeviceDiscovery();
        } else if (dataStr.equals("LIST DISCOVERED")) {
            /* Sends the list of discovered devices (i.e. devices in range) 
             *  back to the client */
            // TODO
        } else if (dataStr.equals("LIST BROUGHT")) {
            /* List the devices which will be "brought" and guarded. Initially 
             *  empty. The beacon can be told to add specific devices to the
             *  bring list but only if it has already discovered them */
            // TODO
        } else if (dataStr.contains("BRING")) {
            /* Tells this beacon to add the device which matches the address
             *  given to the bring list */
            // TODO
        } else if (dataStr.contains("LEAVE")) {
            /* Tells this beacon to remove the device which matches the address
             *  given from the bring list */
            // TODO
        }

        return;
    }
    
	// Create a BroadcastReceiver for ACTION_FOUND
	private final BroadcastReceiver _receiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        // When discovery finds a device
	        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
	            // Get the BluetoothDevice object from the Intent
	            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//	            String uuidStr = "";
//	            for (ParcelUuid p : device.getUuids()) {
//	            	uuidStr += p.getUuid().toString() + " ";
//	            }
	            addDiscoveredDevice(device.getName(), device.getAddress());
	        }
	    }
	};

	@Override
	public void onDataReceived(byte[] data) {
		new AsyncTask<byte[], Void, byte[]> (){

			@Override
			protected byte[] doInBackground(byte[]... params) {
				return params[0];
			}

			@Override
			protected void onPostExecute(byte[] data) {
				Toast.makeText(BeaconModeActivity.this, "Data Received: " + new String(data), Toast.LENGTH_SHORT).show();
				
                processRequest(data);

				super.onPostExecute(data);
				return;
			}
			
		}.execute(data);
		return;
	}

	@Override
	public void onConnected() {
		new AsyncTask<Void, Void, Void> (){

			@Override
			protected Void doInBackground(Void... arg0) {
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				Toast.makeText(BeaconModeActivity.this, "Connected!", Toast.LENGTH_SHORT).show();
				
				super.onPostExecute(result);
			}
			
		}.execute();
		return;
	}

	@Override
	public void onDisconnected() {
		new AsyncTask<Void, Void, Void> (){

			@Override
			protected Void doInBackground(Void... arg0) {
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				Toast.makeText(BeaconModeActivity.this, "Disconnected!", Toast.LENGTH_SHORT).show();
				
				super.onPostExecute(result);
			}
			
		}.execute();
	}
}
