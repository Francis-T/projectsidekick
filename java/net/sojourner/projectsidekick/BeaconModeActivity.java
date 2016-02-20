package net.sojourner.projectsidekick;

import java.util.ArrayList;
import java.util.List;

import net.sojourner.projectsidekick.types.GuardedItem;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.types.ServiceBindingListActivity;
import net.sojourner.projectsidekick.utils.Logger;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class BeaconModeActivity extends ServiceBindingListActivity {
	private ProjectSidekickApp 			_app 				= null;
	private BluetoothAdapter			_bluetoothAdapter 	= BluetoothAdapter.getDefaultAdapter();
	private ArrayAdapter<GuardedItem> 	_guardedListAdapter	= null;
	private List<GuardedItem>			_guardedItems		= null;
	private BeaconState					_eBState			= BeaconState.INACTIVE;
	private TextView					_txvState			= null;
	
	private enum BeaconState { INACTIVE, SETUP, GUARD }
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Logger.info("onCreate() called for " + this.getLocalClassName());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_beacon_main);
		
		if (!_bluetoothAdapter.isEnabled()) {
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
						PSStatus status;
						if (getState() == BeaconState.INACTIVE) {
							status = callService(ProjectSidekickService.MSG_START_GUARD);
							if (status != PSStatus.OK) {
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
							if (status != PSStatus.OK) {
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
						PSStatus status;
						if (getState() == BeaconState.INACTIVE) {
							status = callService(ProjectSidekickService.MSG_SET_AS_SIDEKICK);
							if (status != PSStatus.OK) {
								display("Failed to set beacon to Anti-Loss Mode.");
								return;
							}
							display("Your beacon has been set to Anti-Loss Mode.");

							status = callService(ProjectSidekickService.MSG_START_SETUP);
							if (status != PSStatus.OK) {
								display("Failed to start SETUP Mode.");
								return;
							}
						} else if (getState() == BeaconState.SETUP) {
							status = callService(ProjectSidekickService.MSG_STOP);
							if (status != PSStatus.OK) {
								display("Failed to Stop.");
								return;
							}
						}

						return;
					}
				}
		);

		/* Setup list view interactivity */
		ListView lstDevices = getListView();
		lstDevices.setOnItemLongClickListener(
				new AdapterView.OnItemLongClickListener() {
					@Override
					public boolean onItemLongClick(AdapterView<?> parent, View view,
												   int position, long id) {
						GuardedItem item = _guardedItems.get(position);
						if (item == null) {
							display("Invalid device selected");
						}

						if (item.getAddress() == null) {
							display("Invalid device address: NULL");
							return true;
						}

						showRemoveFromDeviceListDialog(item.getAddress());
						return true;
					}
				}
		);
		
		_txvState = (TextView) findViewById(R.id.txv_state);

		/* Setup the list adapter */
		_guardedItems = new ArrayList<GuardedItem>();
		_guardedListAdapter =
				new ArrayAdapter<GuardedItem>(this,
						android.R.layout.simple_list_item_1,
						_guardedItems);
		setListAdapter(_guardedListAdapter);

		/* Re-populate the guarded items list and update the GUI */
		restoreGuardedItems();

		/* Invoke onCreate() on our superclass to start the service */
		super.onCreate(savedInstanceState);

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
		
		if (requestCode == ProjectSidekickApp.REQUEST_BLUETOOTH_DISCOVERABLE) {
			if (resultCode == RESULT_CANCELED) {
				this.finish();
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
				showSetDeviceCheckModeDialog();
				return true;
			case R.id.m_item_set_check_interval:
				showSetDeviceCheckIntervalDialog();
				return true;
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
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        return;
    }

	private  void showSetDeviceCheckIntervalDialog() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}

		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);
		final EditText editSleepInterval = new EditText(this);
		dlgBuilder.setTitle("Set checking interval");
		dlgBuilder.setMessage("Set the interval (in milliseconds) between device checks conducted during GUARD mode")
				.setView(editSleepInterval)
				.setCancelable(false)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String sleepIntervalStr = editSleepInterval.getText().toString();

						long lSleepInterval = 15000;
						try {
							lSleepInterval = Long.parseLong(sleepIntervalStr);
						} catch (NumberFormatException e) {
							display("Invalid check interval: " + sleepIntervalStr + " ms");
							return;
						}

						Bundle data = new Bundle();
						data.putLong("TIME", lSleepInterval);
						callService(ProjectSidekickService.MSG_SET_SLEEP_TIME, data, null);
						display("Set check interval to " + sleepIntervalStr + " ms");
						return;
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						display("Cancelled!");
						dialog.cancel();
						return;
					}
				});

		dlgBuilder.create().show();
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

	private void showSetDeviceCheckModeDialog() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}

		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);

		dlgBuilder.setTitle("Set device check mode");
		dlgBuilder.setMessage("Select the check mode for use with Sidekick's GUARD mode")
				.setCancelable(false)
				.setPositiveButton("Connect", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Bundle data = new Bundle();
						data.putString("CHECK_MODE", "BT_CONNECT");
						callService(ProjectSidekickService.MSG_SET_CHECK_MODE, data, null);
						display("Bluetooth Connect will now be used for device checking");
						return;
					}
				})
				.setNeutralButton("SDP", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Bundle data = new Bundle();
						data.putString("CHECK_MODE", "SDP");
						callService(ProjectSidekickService.MSG_SET_CHECK_MODE, data, null);
						display("Bluetooth SDP will now be used for device checking");
						return;
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						display("Cancelled!");
						dialog.cancel();
						return;
					}
				});

		dlgBuilder.create().show();

		return;
	}

	private void showRemoveFromDeviceListDialog(String address) {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}

		final String deviceAddr = address;

		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);

		dlgBuilder.setTitle("Remove from Device List");
		dlgBuilder.setMessage(deviceAddr + " will permanently be removed from the device list. " +
					"This means you'll have to register this device to the Sidekick again. " +
					"Is this OK?")
				.setCancelable(false)
				.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						for (GuardedItem item : _guardedItems) {
							if (item.addressMatches(deviceAddr)) {
								/* Remove it from the list of guarded items */
								_guardedItems.remove(item);

								/* Remove it from the registered device list as well */
								_app = (ProjectSidekickApp) getApplication();
								_app.removeRegisteredDevice(deviceAddr);
								_app.saveRegisteredDevices();

								/* Update the list adapter */
								_guardedListAdapter.notifyDataSetChanged();
								display("Deleted!");
								break;
							}
						}
						return;
					}
				})
				.setNegativeButton("No", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						display("Cancelled!");
						dialog.cancel();
						return;
					}
				});

		dlgBuilder.create().show();

		return;
	}
	
	private BeaconState getState() {
		return _eBState;
	}
	
	private void setState(BeaconState state) {
		_eBState = state;
		
		if (_txvState != null) {
			String stateStr = "State: " + state.toString();
			_txvState.setText(stateStr);
		}
		
		return;
	}
	
	private PSStatus restoreGuardedItems() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		/* Clear our guarded items list */
		_guardedItems.clear();
		
		/* Restore the list from our records */
		List<KnownDevice> registeredDevices = _app.getRegisteredDevices();
		for (KnownDevice device : registeredDevices) {
			GuardedItem item = new GuardedItem(device.getName(), device.getAddress());
			item.setIsLost(true);
			item.setRegistered(true);
			_guardedItems.add(item);
		}
		
		/* Refresh the adapter */
		_guardedListAdapter.notifyDataSetChanged();
		
		return PSStatus.OK;
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
	private final BroadcastReceiver _receiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        // When discovery finds a device
	        if (ProjectSidekickService.ACTION_REGISTERED.equals(action)) {
	        	String name = intent.getStringExtra("NAME");
	        	String address = intent.getStringExtra("ADDRESS");

				for (GuardedItem item : _guardedItems) {
					if (item.addressMatches(address)) {
						Logger.warn("Registered device already listed");
						item.setIsLost(false);
						item.setRegistered(true);
						_guardedListAdapter.notifyDataSetChanged();
						return;
					}
				}

				GuardedItem newItem = new GuardedItem(name, address);
				newItem.setIsLost(false);
				newItem.setRegistered(true);

				_guardedItems.add(newItem);
	        	_guardedListAdapter.notifyDataSetChanged();
	        	
	        } else if (ProjectSidekickService.ACTION_UNREGISTERED.equals(action)) {
	        	String address = intent.getStringExtra("ADDRESS");

				for (GuardedItem item : _guardedItems) {
					if (item.addressMatches(address)) {
						item.setRegistered(false);
						break;
					}
				}
	        	_guardedListAdapter.notifyDataSetChanged();
	        } else if (ProjectSidekickService.ACTION_UPDATE_LOST.equals(action)) {
	        	String address = intent.getStringExtra("ADDRESS");
	        	boolean isLost = intent.getBooleanExtra("LOST_STATUS", false);

				for (GuardedItem item : _guardedItems) {
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

				for (GuardedItem item : _guardedItems) {
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
