package net.sojourner.projectsidekick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sojourner.projectsidekick.ProjectSidekickApp.Mode;
import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.KnownDevice.DeviceStatus;
import net.sojourner.projectsidekick.types.Status;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

public class AppModeActivity extends ListActivity implements BluetoothEventHandler {
	private ProjectSidekickApp _app = null;
	private BluetoothAdapter _bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	private IBluetoothBridge _bluetooth = null;
	
	private List<KnownDevice> _registeredDevices = new ArrayList<KnownDevice>();
	private ArrayAdapter<KnownDevice> _deviceListAdapter = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_app_main);
		
		ImageButton btnSearch = (ImageButton) findViewById(R.id.btn_discover);
		btnSearch.setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startBluetooth();
				}
			}
		);
		
		ListView listGui = getListView();
		listGui.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> av, View v,
						int pos, long id) {
					if (_deviceListAdapter == null) {
						_deviceListAdapter 
							= (ArrayAdapter<KnownDevice>) 
								AppModeActivity.this.getListAdapter();
					}
					KnownDevice kd = (KnownDevice) _deviceListAdapter.getItem(pos);
					
					Toast.makeText( AppModeActivity.this, 
									"Connecting to " + 
									kd.getName() + 
									"(" + kd.getAddress() + ")", 
									Toast.LENGTH_SHORT).show();
					if (connectToDevice(kd.getAddress()) != Status.OK) {
						return;
					}
					
					KnownDevice rd = findRegisteredDevice(kd.getAddress());
					if (rd == null) {
						kd.setRegistered(true);
						_registeredDevices.add(kd);
						_deviceListAdapter.notifyDataSetChanged();
					}
					
					
					return;
				}
			}
		);
		
		listGui.setOnItemLongClickListener(
			new AdapterView.OnItemLongClickListener() {

				@Override
				public boolean onItemLongClick(AdapterView<?> av, View v,
						int pos, long id) {
					if (_deviceListAdapter == null) {
						_deviceListAdapter 
							= (ArrayAdapter<KnownDevice>) 
								AppModeActivity.this.getListAdapter();
					}
					KnownDevice kd = (KnownDevice) _deviceListAdapter.getItem(pos);
//					KnownDevice rd = findRegisteredDevice(kd.getAddress());
//					if (rd != null) {
//						kd.setRegistered(false);
//						_registeredDevices.remove(rd);
//						Logger.warn("Removed device from list instead");
//						_deviceListAdapter.notifyDataSetChanged();
//					}
					showModifyDeviceDialog(kd);
					
					return true;
				}
			}
		);
		
		/* Create the adapter for the devices to be listed */
		_deviceListAdapter = new ArrayAdapter<KnownDevice>(this,android.R.layout.simple_list_item_1);
		setListAdapter(_deviceListAdapter);
		restoreRegisteredDevices();
		
		return;
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		if (_receiver != null) {
			IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
			registerReceiver(_receiver, filter);
		}
		
		if ( _bluetoothAdapter.isEnabled() == false ) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, ProjectSidekickApp.REQUEST_CODE_BLUETOOTH_ENABLE);
		}
	}

	@Override
	protected void onStop() {
		SharedPreferences prefs = getSharedPreferences("PROJECT_BEACON__1127182", MODE_WORLD_WRITEABLE);

		Set<String> rgdInfoSet = new HashSet<String>();
		for (KnownDevice rd : _registeredDevices) {
			Logger.info("Adding " + rd.getName() + " to set");
			rgdInfoSet.add(rd.getName() + "," + rd.getAddress());
		}
		prefs.edit().putStringSet("REGISTERED_DVC_LIST", rgdInfoSet).commit();
		
		if (_bluetooth != null) {
			_bluetooth.stopDeviceDiscovery();
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
		
		if (_bluetooth.initialize(this, false) != Status.OK) {
			Logger.err("Failed to initialize Bluetooth");
			return;
		}
		_bluetooth.startDeviceDiscovery();
		
		return;
	}
	
	private Status connectToDevice(String addr) {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		if (_bluetooth == null) {
			_bluetooth = _app.getBluetoothBridge();
		}
		
		_bluetooth.setEventHandler(this);
		
		Status status = _bluetooth.initialize(this, false);
		if (status != Status.OK) {
			Logger.err("Failed to initialize Bluetooth");
			return Status.FAILED;
		}
		
		if (_bluetooth.connectDeviceByAddress(addr) != Status.OK) {
			Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show();
			return Status.FAILED;
		}
		
		return Status.OK;
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
	            
	            String uuidStr = "";
//	            for (ParcelUuid p : device.getUuids()) {
//	            	uuidStr += p.getUuid().toString() + " ";
//	            }
	            addKnownDevice(device.getName(), device.getAddress(), true);
	        }
	        
	        if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
	        	Logger.info("Service Discovery Started (receiver)");
	        } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
	        	Logger.info("Service Discovery Finished (receiver)");
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
				Toast.makeText(AppModeActivity.this, "Data Received: " + new String(data), Toast.LENGTH_SHORT).show();
				
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
				Toast.makeText(AppModeActivity.this, "Connected!", Toast.LENGTH_SHORT).show();
				
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
				Toast.makeText(AppModeActivity.this, "Disconnected!", Toast.LENGTH_SHORT).show();
				
				super.onPostExecute(result);
			}
			
		}.execute();
	}
	
	private KnownDevice findRegisteredDevice(String address) {
		for (KnownDevice kd : _registeredDevices) {
			if (kd.getAddress().equals(address)) {
				return kd;
			}
		}
        
        return null;
	}

	private void addKnownDevice(String name, String address, boolean isDiscovered) {
		addKnownDevice(name, address, isDiscovered, false);
		return;
	}
	
	private void addKnownDevice(String name, String address, boolean isDiscovered, boolean isRegistered) {
		DeviceStatus dvcStatus = isDiscovered ? DeviceStatus.FOUND : DeviceStatus.UNKNOWN;
		
        ArrayAdapter<KnownDevice> adapter 
        	= (ArrayAdapter<KnownDevice>) AppModeActivity.this.getListAdapter();
        
        /* Prevent duplicates from being re-added */
        int iCount = adapter.getCount();
        for (int i = 0; i < iCount; i++) {
        	KnownDevice kd = adapter.getItem(i);
        	if (kd.getAddress().equals(address)) {
        		Logger.info("Not adding duplicate entry for " + address);
        		kd.setStatus(dvcStatus);
                adapter.notifyDataSetChanged();
        		return;
        	}
        }
        
        adapter.add(new KnownDevice(name, address, dvcStatus, isRegistered));
        adapter.notifyDataSetChanged();
        
        return;
	}
	
	private void restoreRegisteredDevices() {
		SharedPreferences prefs = getSharedPreferences("PROJECT_BEACON__1127182", MODE_WORLD_WRITEABLE);

		Set<String> rgdInfoSet = prefs.getStringSet("REGISTERED_DVC_LIST", null);
		if (rgdInfoSet != null) {
			for (String item : rgdInfoSet) {
				String deviceInfo[] = item.split(",");
				if (deviceInfo.length != 2) {
					continue;
				}
				
				KnownDevice kd = new KnownDevice(deviceInfo[0], deviceInfo[1]);
				kd.setRegistered(true);
				
				_registeredDevices.add(kd);
			}
		}
		
		for (KnownDevice kd : _registeredDevices) {
			addKnownDevice(kd.getName(), kd.getAddress(), false, kd.isRegistered());
		}
		
		return;
	}
	
	private void showModifyDeviceDialog(KnownDevice kd) {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		final KnownDevice fkd = kd;
		
		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);
		
		dlgBuilder.setTitle("Device Settings");
		dlgBuilder.setMessage("Device Name: " + kd.getName() + "\n" 
				+ "Address: " + kd.getAddress())
			.setCancelable(true)
			.setPositiveButton("Rename", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							showRenameDeviceDialog(fkd);
						}
					})
			.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							KnownDevice rd = findRegisteredDevice(fkd.getAddress());
							if (rd != null) {
								fkd.setRegistered(false);
								_registeredDevices.remove(rd);
								Logger.warn("Removed device from list instead");
								_deviceListAdapter.notifyDataSetChanged();
							}
							
							Toast.makeText(AppModeActivity.this, "Deleted!", Toast.LENGTH_SHORT).show();
						}
					})
			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Toast.makeText(AppModeActivity.this, "Cancelled", Toast.LENGTH_SHORT).show();
							dialog.cancel();
						}
					});
		
		dlgBuilder.create().show();
		
		return;
	}

	private void showRenameDeviceDialog(KnownDevice kd) {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		final EditText nameInput = new EditText(this);
		final KnownDevice fkd = kd;
		
		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);
		
		dlgBuilder.setTitle("Rename Device");
		dlgBuilder.setMessage("Current Name: " + kd.getName())
			.setView(nameInput)
			.setCancelable(true)
			.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							String nameStr = nameInput.getText().toString();
							
							fkd.setName(nameStr);
							_deviceListAdapter.notifyDataSetChanged();

							KnownDevice rd = findRegisteredDevice(fkd.getAddress());
							if (rd != null) {
								rd.setName(nameStr);
							}
							
							Toast.makeText(AppModeActivity.this, "Renamed!", Toast.LENGTH_SHORT).show();
						}
					})
			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Toast.makeText(AppModeActivity.this, "Cancelled", Toast.LENGTH_SHORT).show();
							dialog.cancel();
						}
					});
		
		dlgBuilder.create().show();
		
		return;
	}
}
