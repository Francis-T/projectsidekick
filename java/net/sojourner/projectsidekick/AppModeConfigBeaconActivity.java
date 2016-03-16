package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.types.BTState;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.ServiceBindingActivity;
import net.sojourner.projectsidekick.types.ServiceState;
import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.AlertDialog;
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
	public static final int MSG_RESP_SERVICE_STATE		= 1;
	public static final int MSG_RESP_BLUETOOTH_STATE	= 2;

	private ProjectSidekickApp 	_app 				= (ProjectSidekickApp) getApplication();
    private String 				_deviceName 		= "";
    private String 				_deviceAddr 		= "";
    private boolean 			_isConnected 		= false;

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

        final Button btnConnect = (Button) findViewById(R.id.btn_connect);
        btnConnect.setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (_isConnected) {
							disconnectDevice();
							//updateGuiToDisconnected();
							_isConnected = false;
							return;
						}

						if (connectToDevice() != PSStatus.OK) {
							display("Connection Failed!");
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
						display( _deviceName + " has not yet been registered");
						return;
					}
					showRenameDeviceDialog(kd);
					return;
				}
            }
        );
		if (_app.findRegisteredDevice(_deviceAddr) != null) {
			btnRename.setEnabled(true);
		} else {
			btnRename.setEnabled(false);
		}

        final Button btnRegister = (Button) findViewById(R.id.btn_register);
        btnRegister.setOnClickListener(
        	new View.OnClickListener() {
				@Override
				public void onClick(View arg0) {
					if (registerDevice() != PSStatus.OK) {
						display("Registration Failed!");
					}

					btnRename.setEnabled(true);

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
						/* TODO */

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

						return;
					}
				}
		);

		/* Invoke onCreate() on our superclass to start the service */
		super.onCreate(savedInstanceState);

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
		PSStatus = callService(ProjectSidekickService.MSG_SET_AS_MOBILE);
		if (PSStatus != PSStatus.OK) {
			display("Failed set role to MOBILE");
			return PSStatus.FAILED;
		}

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

    private PSStatus disconnectDevice() {
		PSStatus PSStatus;
		PSStatus = callService(ProjectSidekickService.MSG_DISCONNECT);
		if (PSStatus != PSStatus.OK) {
			display("Failed to disconnectdevice");
			return PSStatus.FAILED;
		}
		display("Device has been disconnected");

        return PSStatus.OK;
    }

    private void updateGuiToConnected() {
        Button btnConnect =
                (Button) findViewById(R.id.btn_connect);
        btnConnect.setText("Disconnect");

        Button btnRegister =
            	(Button) findViewById(R.id.btn_register);
        btnRegister.setEnabled(true);

        Button btnRename  =
                (Button) findViewById(R.id.btn_rename);
        btnRename.setEnabled(true);

//        Button btnDelete  = 
//            (Button) findViewById(R.id.btn_delete);
//        btnDelete.setEnabled(true);

        Button btnReqGuardList =
            (Button) findViewById(R.id.btn_req_guard_list);
        btnReqGuardList.setEnabled(true);

		Button btnReqStartGuard =
				(Button) findViewById(R.id.btn_req_guard_start);
		btnReqStartGuard.setEnabled(true);

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

//        Button btnDelete  = 
//            (Button) findViewById(R.id.btn_delete);
//        btnDelete.setEnabled(false);

        Button btnReqGuardList =
            (Button) findViewById(R.id.btn_req_guard_list);
        btnReqGuardList.setEnabled(false);

		Button btnReqStartGuard =
				(Button) findViewById(R.id.btn_req_guard_start);
		btnReqStartGuard.setEnabled(false);

        return;
    }

	private void guiReloadDeviceInfo() {
        TextView txvDvcInfo = (TextView) findViewById(R.id.txv_dvc_info);
        txvDvcInfo.setText(_deviceName + "\n" + _deviceAddr);
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

					fkd.setName(nameStr);

					_app = getAppRef();
					if (_app.updateRegisteredDevice(fkd) != PSStatus.OK) {
						Logger.err("Device not registered. Renaming is meaningless.");
					}

					_deviceName = nameStr;
					guiReloadDeviceInfo();

					display("Renamed!");
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
				case MSG_RESP_BLUETOOTH_STATE:
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
	        	Intent listIntent
	        		= new Intent(AppModeConfigBeaconActivity.this,
	        				AppModeBeaconMasterListActivity.class);
				listIntent.putExtra("DEVICE_ADDR", _deviceAddr);
	        	listIntent.putExtra("DEVICES", intent.getStringArrayExtra("DEVICES"));
	        	startActivity(listIntent);
	        }
	    }
	};
}
