package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.types.AlarmState;
import net.sojourner.projectsidekick.types.BTState;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.ServiceBindingActivity;
import net.sojourner.projectsidekick.types.ServiceState;
import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class AppModeConfigBeaconActivity extends ServiceBindingActivity {
	private ProjectSidekickApp 	_app 				= (ProjectSidekickApp) getApplication();
    private String 				_deviceName 		= "";
    private String 				_deviceAddr 		= "";
    private boolean 			_isConnected 		= false;
	private AlarmState			_deviceAlarmState	= AlarmState.QUIET;
	private static final long	DEFAULT_COMMAND_TIMEOUT	= 20000;

	private ProgressDialog 		_progress				= null;
	private Handler 			_progressTimeoutHandler = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Logger.info("onCreate called for " + this.getLocalClassName());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_config_beacon);

		/* Extract device info from the intent */
        Intent initializer = getIntent();
        Bundle b = initializer.getBundleExtra("DEVICE_INFO");
        if (b == null) {
			Logger.err("Failed to get device info from Intent");
			finish();
        	return;
        }

        /* Get reference to the underlying Application */
		_app = getAppRef();

        /* Expect the Device Name and Address to be passed on to
         *  this activity from the previous one */
        _deviceName = b.getString("DEVICE_NAME");
        if (_deviceName == null) {
        	Logger.err("Device name is null");
			finish();
        	return;
        }

        _deviceAddr = b.getString("DEVICE_ADDRESS");
        if (_deviceAddr == null) {
        	Logger.err("Device address is null");
			finish();
        	return;
        }

		/* Setup our GUI */
		guiReloadDeviceInfo();

		_progress = new ProgressDialog(this);
		_progress.setIndeterminate(true);
		_progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);

        final Button btnConnect = (Button) findViewById(R.id.btn_connect);
        btnConnect.setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (_isConnected) {
							disconnectDevice();
							//updateGuiToDisconnected();
							_isConnected = false;
							launchProgressBar("Disconnecting...", "Disconnect failed", DEFAULT_COMMAND_TIMEOUT);
						} else {
							if (connectToDevice() != PSStatus.OK) {
								display("Connection Failed!");
							}
							launchProgressBar("Connecting...", "Connect failed", DEFAULT_COMMAND_TIMEOUT);
						}
						return;
					}
				}
		);

        final Button btnRename = (Button) findViewById(R.id.btn_rename);
        btnRename.setOnClickListener(
            new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					KnownDevice kd = _app.findRegisteredDevice(_deviceAddr);
					if (kd == null) {
						display(_deviceName + " has not yet been registered");
						return;
					}
					showRenameDeviceDialog(kd);
					return;
				}
            }
        );

        final Button btnRegister = (Button) findViewById(R.id.btn_register);
        btnRegister.setOnClickListener(
        	new View.OnClickListener() {
				@Override
				public void onClick(View arg0) {
					if (registerDevice() != PSStatus.OK) {
						display("Registration Failed!");
					}

					btnRename.setEnabled(true);
					/* Present a progress bar */
					launchProgressBar("Registering Device...", "Register failed", DEFAULT_COMMAND_TIMEOUT);

					return;
				}
			}
        );

        final Button btnDelete = (Button) findViewById(R.id.btn_delete);
        btnDelete.setOnClickListener(
            new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					KnownDevice kd = _app.findRegisteredDevice(_deviceAddr);
					if (kd == null) {
						display(_deviceName + " has not yet been registered");
						return;
					}
					showDeleteDeviceDialog(kd);
					return;
				}
            }
        );

        final Button btnReqGuardList = (Button) findViewById(R.id.btn_req_guard_list);
        btnReqGuardList.setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						requestGuardList();
						/* Present a progress bar while we wait for the
						 *  list to be retrieved */
						launchProgressBar("Obtaining Device List...", "Failed to get device list", DEFAULT_COMMAND_TIMEOUT);
						return;
					}
				}
		);

		final Button btnReqStartGuard = (Button) findViewById(R.id.btn_req_guard_start);
		btnReqStartGuard.setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						requestGuardStart();
						/* Present a progress bar */
						launchProgressBar("Starting Sidekick Anti-theft Mode...", "Failed to start Anti-Theft Mode", DEFAULT_COMMAND_TIMEOUT);

						return;
					}
				}
		);

		final Button btnReqAlarm = (Button) findViewById(R.id.btn_req_alarm);
		btnReqAlarm.setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (_deviceAlarmState == AlarmState.QUIET) {
							requestLocateStart();
							/* Present a progress bar */
							launchProgressBar("Triggering Alarm...", "Failed to Trigger Alarm", DEFAULT_COMMAND_TIMEOUT);
						} else if (_deviceAlarmState == AlarmState.EMERGENCY) {
							showAlarmDisableSecurityDialog();
						} else {
							requestAlarmStop();
							/* Present a progress bar */
							launchProgressBar("Stopping Alarm...", "Failed to Stop Alarm", DEFAULT_COMMAND_TIMEOUT);
						}
						return;
					}
				}
		);
		btnReqAlarm.setOnLongClickListener(
				new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						if (_deviceAlarmState == AlarmState.QUIET) {
							requestAlarmStart();
							/* Present a progress bar */
							launchProgressBar("Triggering Alarm...", "Failed to Trigger Alarm", DEFAULT_COMMAND_TIMEOUT);
						} else if (_deviceAlarmState == AlarmState.EMERGENCY) {
							showAlarmDisableSecurityDialog();
						} else {
							requestAlarmStop();
							/* Present a progress bar */
							launchProgressBar("Triggering Alarm...", "Failed to Stop Alarm", DEFAULT_COMMAND_TIMEOUT);
						}
						return true;
					}
				}
		);

		/* Set our local message handler for use with service queries */
		setMessageHandler(new MessageHandler());

        return;
    }

	@Override
	protected void onStart() {
		Logger.info("onStart called for " + this.getLocalClassName());
		super.onStart();

		if (_receiver != null) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(ProjectSidekickService.ACTION_CONNECTED);
			filter.addAction(ProjectSidekickService.ACTION_DATA_RECEIVE);
			filter.addAction(ProjectSidekickService.ACTION_DISCONNECTED);
			filter.addAction(ProjectSidekickService.ACTION_LIST_RECEIVED);
			filter.addAction(ProjectSidekickService.ACTION_REGISTERED);
			filter.addAction(ProjectSidekickService.ACTION_UNREGISTERED);
			filter.addAction(ProjectSidekickService.ACTION_REP_STARTED);
			filter.addAction(ProjectSidekickService.ACTION_REP_FINISHED);
			filter.addAction(ProjectSidekickService.ACTION_ALARM_CHANGED);
			filter.addAction(ProjectSidekickService.ACTION_STATE_CHANGED);
			registerReceiver(_receiver, filter);
		}

		return;
	}

	@Override
	protected void onResume() {
		Logger.info("onResume called for " + this.getLocalClassName());
		super.onResume();

		/* Query the service state so that we can update the GUI accordingly */
		queryService(ProjectSidekickService.MSG_QUERY_STATE);
		queryService(ProjectSidekickService.MSG_QUERY_BT_STATE);

		return;
	}

	@Override
	protected void onPause() {
		Logger.info("onPause called for " + this.getLocalClassName());
		super.onPause();
        return;
	}

	@Override
	protected void onStop() {
		Logger.info("onStop called for " + this.getLocalClassName());

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

	/* *************** */
	/* Private Methods */
	/* *************** */
    private void display(String msg) {
        Toast.makeText( this, msg,  Toast.LENGTH_SHORT).show();
        return;
    }

    private PSStatus connectToDevice() {
		PSStatus PSStatus;
		PSStatus = callService(ProjectSidekickService.MSG_START_SETUP);
		if (PSStatus != PSStatus.OK) {
			display("Failed start SETUP Mode");
			return PSStatus.FAILED;
		}

		Bundle extras = new Bundle();
		extras.putString("DEVICE_ADDR", _deviceAddr);
		PSStatus = callService(ProjectSidekickService.MSG_CONNECT, extras, null);
		if (PSStatus != PSStatus.OK) {
			display("Failed attempt connection");
			return PSStatus.FAILED;
		}

        return PSStatus.OK;
    }

    private PSStatus registerDevice() {
    	if (!_isConnected) {
    		display("Not yet connected!");
    		return PSStatus.FAILED;
    	}

		PSStatus PSStatus;

		Bundle extras = new Bundle();
		extras.putString("DEVICE_ADDR", _deviceAddr);
		PSStatus = callService(ProjectSidekickService.MSG_SEND_REGISTER, extras, null);
		if (PSStatus != PSStatus.OK) {
			display("Failed send register request");
			return PSStatus.FAILED;
		}
		display("Register request sent");

		_app = getAppRef();
		_app.addRegisteredDevice(new KnownDevice(_deviceName, _deviceAddr));
        _app.saveRegisteredDevices();

    	return PSStatus.OK;
    }

    private PSStatus requestGuardList() {
    	if (!_isConnected) {
    		display("Not yet connected!");
    		return PSStatus.FAILED;
    	}

		PSStatus PSStatus;

		Bundle extras = new Bundle();
		extras.putString("DEVICE_ADDR", _deviceAddr);
		PSStatus = callService(ProjectSidekickService.MSG_SEND_GET_LIST, extras, null);
		if (PSStatus != PSStatus.OK) {
			display("Failed send retrieve guard list request");
			return PSStatus.FAILED;
		}
		display("Retrieving guard list...");

    	return PSStatus.OK;
    }

	private PSStatus requestGuardStart() {
		if (!_isConnected) {
			display("Not yet connected!");
			return PSStatus.FAILED;
		}

		PSStatus PSStatus;

		Bundle extras = new Bundle();
		extras.putString("DEVICE_ADDR", _deviceAddr);
		PSStatus = callService(ProjectSidekickService.MSG_START_REPORT, extras, null);
		if (PSStatus != PSStatus.OK) {
			display("Failed to send Start Guard request");
			return PSStatus.FAILED;
		}
		display("Start Guard request sent");

		return PSStatus.OK;
	}

	private PSStatus requestLocateStart() {
		PSStatus PSStatus;

		Bundle extras = new Bundle();
		extras.putString("DEVICE_ADDR", _deviceAddr);
		extras.putString("MODE", ProjectSidekickService.ALARM_CODE_BEEP);
		PSStatus = callService(ProjectSidekickService.MSG_TRIGGER_ALARM, extras, null);
		if (PSStatus != PSStatus.OK) {
			display("Failed to send Locate request");
			return PSStatus.FAILED;
		}

		return PSStatus.OK;
	}

	private PSStatus requestAlarmStart() {
		PSStatus PSStatus;

		Bundle extras = new Bundle();
		extras.putString("DEVICE_ADDR", _deviceAddr);
		extras.putString("MODE", ProjectSidekickService.ALARM_CODE_EMERGENCY);
		PSStatus = callService(ProjectSidekickService.MSG_TRIGGER_ALARM, extras, null);
		if (PSStatus != PSStatus.OK) {
			display("Failed to send Alarm request");
			return PSStatus.FAILED;
		}

		return PSStatus.OK;
	}

	private PSStatus requestAlarmStop() {
		if (!_isConnected) {
			display("Not yet connected!");
			return PSStatus.FAILED;
		}

		PSStatus PSStatus;

		Bundle extras = new Bundle();
		extras.putString("DEVICE_ADDR", _deviceAddr);
		extras.putString("MODE", ProjectSidekickService.ALARM_CODE_DISABLED);
		PSStatus = callService(ProjectSidekickService.MSG_TRIGGER_ALARM, extras, null);
		if (PSStatus != PSStatus.OK) {
			display("Failed to send Alarm request");
			return PSStatus.FAILED;
		}

		return PSStatus.OK;
	}

	private PSStatus requestRename(String newName) {
		if (!_isConnected) {
			display("Not yet connected!");
			return PSStatus.FAILED;
		}

		PSStatus PSStatus;

		Bundle extras = new Bundle();
		extras.putString("DEVICE_NAME", newName);
		PSStatus = callService(ProjectSidekickService.MSG_RENAME_SIDEKICK, extras, null);
		if (PSStatus != PSStatus.OK) {
			display("Failed to send Rename request");
			return PSStatus.FAILED;
		}
		display("Rename request sent");

		return PSStatus.OK;
	}

    private PSStatus disconnectDevice() {
		PSStatus PSStatus;
		PSStatus = callService(ProjectSidekickService.MSG_STOP);
		if (PSStatus != PSStatus.OK) {
			display("Failed to disconnect device");
			return PSStatus.FAILED;
		}
		display("Device has been disconnected");

        return PSStatus.OK;
    }

    private void updateGuiToConnected() {
        Button btnConnect =
                (Button) findViewById(R.id.btn_connect);
        btnConnect.setText("Disconn");

        Button btnRegister =
            	(Button) findViewById(R.id.btn_register);
        btnRegister.setEnabled(true);

        Button btnRename  =
                (Button) findViewById(R.id.btn_rename);
        btnRename.setEnabled(true);

        Button btnReqGuardList =
            (Button) findViewById(R.id.btn_req_guard_list);
        btnReqGuardList.setEnabled(true);

		Button btnReqStartGuard =
				(Button) findViewById(R.id.btn_req_guard_start);
		btnReqStartGuard.setEnabled(true);

		cancelProgressBar();

        return;
    }

    private void updateGuiToDisconnected() {
        Button btnConnect =
                (Button) findViewById(R.id.btn_connect);
        btnConnect.setText("Connect");

        Button btnRegister =
            	(Button) findViewById(R.id.btn_register);
        btnRegister.setEnabled(false);

        Button btnRename  =
                (Button) findViewById(R.id.btn_rename);
        btnRename.setEnabled(false);

        Button btnReqGuardList =
            (Button) findViewById(R.id.btn_req_guard_list);
        btnReqGuardList.setEnabled(false);

		Button btnReqStartGuard =
				(Button) findViewById(R.id.btn_req_guard_start);
		btnReqStartGuard.setEnabled(false);

		cancelProgressBar();
        return;
    }

	private void guiReloadDeviceInfo() {
        TextView txvDvcName = (TextView) findViewById(R.id.txv_dvc_name);
		txvDvcName.setText(_deviceName);

		TextView txvDvcAddr = (TextView) findViewById(R.id.txv_dvc_addr);
		txvDvcAddr.setText(_deviceAddr);
        return;
	}

	private void showRenameDeviceDialog(KnownDevice kd) {
		_app = getAppRef();

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
					requestRename(nameStr);

					_app = getAppRef();
					if (_app.removeRegisteredDevice(fkd.getAddress())!= PSStatus.OK) {
						Logger.err("Device not registered. Renaming is meaningless.");
					}
					finish();
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

	private void showDeleteDeviceDialog(KnownDevice kd) {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}

		final KnownDevice fkd = kd;

		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);

		dlgBuilder.setTitle("Remove Registered Device");
		dlgBuilder.setMessage(kd.getName() + " will be removed from the registered device list. Is this OK?")
			.setCancelable(false)
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {

					if (_app.removeRegisteredDevice(fkd.getAddress()) != PSStatus.OK) {
						Logger.err("Device not registered. Deletion is meaningless.");
					}
					_app.saveRegisteredDevices();

					display("Deleted!");
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


	private void showAlarmDisableSecurityDialog() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}

		final EditText edtSecurityCode = new EditText(this);

		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);
		dlgBuilder.setTitle("Deactivate Alarm");
		dlgBuilder.setMessage("Enter security code to deactivate the alarm")
				.setCancelable(false)
				.setView(edtSecurityCode)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String securityCode = edtSecurityCode.getText().toString();

						if (!securityCode.equals("QWERTY")) {
							display("Invalid security code!");
							dialog.cancel();
							return;
						}

						display("Deactivating alarm...");
						requestAlarmStop();
						/* Present a progress bar */
						launchProgressBar("Stopping Alarm...", "Failed to Stop Alarm", DEFAULT_COMMAND_TIMEOUT);

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

	private ProjectSidekickApp getAppRef() {
		return (ProjectSidekickApp) getApplication();
	}

	private PSStatus queryServiceState() {
		PSStatus PSStatus;
		PSStatus = callService(ProjectSidekickService.MSG_QUERY_STATE);
		if (PSStatus != PSStatus.OK) {
			display("Failed query the service state");
			return PSStatus.FAILED;
		}
		Logger.info("Querying service state");
		return PSStatus.OK;
	}

	private PSStatus handleServiceState(String stateStr) {
		ServiceState s = ServiceState.valueOf(stateStr);
		Logger.info("Detected Service State to be " + s);

		return PSStatus.OK;
	}

	private PSStatus handleBluetoothState(String stateStr) {
		BTState state = BTState.valueOf(stateStr);
		Logger.info("Detected Bluetooth State to be " + state);

		if (state != BTState.CONNECTED) {
			updateGuiToDisconnected();
			_isConnected = false;
		} else {
			updateGuiToConnected();
			_isConnected = true;
		}

		return PSStatus.OK;
	}

	private PSStatus launchProgressBar(String displayMessage, String timeoutMessage, long timeout) {
		if ( setProgressTimeout(timeout, timeoutMessage) != PSStatus.OK ) {
			return PSStatus.FAILED;
		}

		_progress.setProgress(0);
		_progress.setMessage(displayMessage);
		_progress.show();

		return PSStatus.OK;
	}

	private PSStatus setProgressTimeout(long timeout, final String timeoutMsg) {
		if (_progressTimeoutHandler != null) {
			return PSStatus.FAILED;
		}

		_progressTimeoutHandler = new Handler();
		_progressTimeoutHandler.postDelayed(
				new Runnable() {
					@Override
					public void run() {
						if (_progressTimeoutHandler != null) {
							display(timeoutMsg);
						}
						cancelProgressBar();
					}
				},
				timeout
		);

		return PSStatus.OK;
	}

	private void cancelProgressBar() {
		if (_progressTimeoutHandler != null) {
			_progressTimeoutHandler = null;
		}

		if (_progress.isShowing()) {
			_progress.hide();
		}

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
				case ProjectSidekickService.MSG_RESP_SERVICE_STATE:
					/* Extract the service state */
					String svcState = stateBundle.getString("STATE");
					status = handleServiceState(svcState);
					break;
				case ProjectSidekickService.MSG_RESP_BLUETOOTH_STATE:
					/* Extract the bluetooth state */
					String btState = stateBundle.getString("STATE");
					status = handleBluetoothState(btState);
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

	private final BroadcastReceiver _receiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        // When discovery finds a device
	        if (ProjectSidekickService.ACTION_CONNECTED.equals(action)) {
	        	updateGuiToConnected();
	        	_isConnected = true;
	        } else if (ProjectSidekickService.ACTION_DISCONNECTED.equals(action)) {
	        	updateGuiToDisconnected();
	        	_isConnected = false;
	        } else if (ProjectSidekickService.ACTION_DATA_RECEIVE.equals(action)) {
	        	String msg = intent.getStringExtra("SENDER_DATA");
	        	display(msg);
	        } else if (ProjectSidekickService.ACTION_LIST_RECEIVED.equals(action)) {
				cancelProgressBar();
	        	Intent listIntent
	        		= new Intent(AppModeConfigBeaconActivity.this,
	        				AppModeBeaconMasterListActivity.class);
				listIntent.putExtra("DEVICE_ADDR", _deviceAddr);
				listIntent.putExtra("DEVICES", intent.getStringArrayExtra("DEVICES"));
	        	startActivity(listIntent);
	        } else if (ProjectSidekickService.ACTION_REGISTERED.equals(action)) {
				cancelProgressBar();
			} else if (ProjectSidekickService.ACTION_DATA_RECEIVE.equals(action)) {
				cancelProgressBar();
			} else if (ProjectSidekickService.ACTION_REP_STARTED.equals(action)) {
				cancelProgressBar();
			} else if (ProjectSidekickService.ACTION_REP_FINISHED.equals(action)) {
				cancelProgressBar();
			} else if (ProjectSidekickService.ACTION_ALARM_CHANGED.equals(action)) {
				cancelProgressBar();
				/* Update our known alarm state */
				String alarmMode = intent.getStringExtra("MODE");
				Button btnReqAlarm = (Button) findViewById(R.id.btn_req_alarm);
				if (alarmMode.equals(AlarmState.QUIET.toString())) {
					_deviceAlarmState = AlarmState.QUIET;
					btnReqAlarm.setText("Manual\nAlarm");
				} else if (alarmMode.equals(AlarmState.BEEP.toString())) {
					_deviceAlarmState = AlarmState.BEEP;
				} else if (alarmMode.equals(AlarmState.EMERGENCY.toString())) {
					_deviceAlarmState = AlarmState.EMERGENCY;
					btnReqAlarm.setText("Stop\nAlarm");
				}
				Logger.info("Alarm state changed: " + _deviceAlarmState.toString());
			} else if (ProjectSidekickService.ACTION_STATE_CHANGED.equals(action)) {
				String state = intent.getStringExtra("STATE");

				TextView txvDvcState = (TextView) findViewById(R.id.txv_dvc_state);
				txvDvcState.setText("State: " + state);
			}
	    }
	};
}
