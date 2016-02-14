package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.Status;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class AppModeConfigBeaconActivity extends Activity {
	private ProjectSidekickApp 	_app 				= (ProjectSidekickApp) getApplication();
	private Messenger 			_service 			= null;
    private String 				_deviceName 		= "";
    private String 				_deviceAddr 		= "";
    private boolean 			_isConnected 		= false;
	private boolean 			_bIsBound 			= false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_config_beacon);
        
        Intent initializer = getIntent();
        if (initializer == null) {
        	return;
        }
        
        Bundle b = initializer.getBundleExtra("DEVICE_INFO");
        if (b == null) {
        	return;
        }
        
        /* Get reference to the underlying Application */
		_app = getAppRef();
        
        /* Expect the Device Name and Address to be passed on to
         *  this activity from the previous one */
        _deviceName = b.getString("DEVICE_NAME");
        if (_deviceName == null) {
        	Logger.err("Device name is null");
        	return;
        }
        
        _deviceAddr = b.getString("DEVICE_ADDRESS");
        if (_deviceAddr == null) {
        	Logger.err("Device address is null");
        	return;
        }
        
        reloadDeviceInfo();

        final Button btnConnect = 
            (Button) findViewById(R.id.btn_connect);
        btnConnect.setOnClickListener(
            new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (_isConnected) {
						disconnectDevice();
						updateGuiToDisconnected();
						_isConnected = false;
						return;
					}
					
					if (connectToDevice() != Status.OK) {
                        display("Connection Failed!");
                    }
                    return;
				}
            }
        );

        final Button btnRename  = 
            (Button) findViewById(R.id.btn_rename);
        if (_app.findRegisteredDevice(_deviceAddr) != null) {
        	btnRename.setEnabled(true);
        } else {
        	btnRename.setEnabled(false);
        }
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
        
        final Button btnRegister =
        	(Button) findViewById(R.id.btn_register);
        btnRegister.setOnClickListener(
        	new View.OnClickListener() {
				@Override
				public void onClick(View arg0) {
					if (registerDevice() != Status.OK) {
						display("Registration Failed!");
					}
					
					btnRename.setEnabled(true);
					
					return;
				}
			}
        );

        final Button btnDelete  = 
            (Button) findViewById(R.id.btn_delete);
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

        final Button btnReqGuardList = 
            (Button) findViewById(R.id.btn_req_guard_list);
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

		
		if (!_bIsBound) {
			Intent bindServiceIntent = new Intent(this, ProjectSidekickService.class);
			bindService(bindServiceIntent, _serviceConnection, Context.BIND_AUTO_CREATE);
		}
		
        return;
    }

	@Override
	protected void onStart() {
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
	protected void onPause() {
		
		super.onPause();
        return;
	}

	@Override
	protected void onResume() {
        super.onResume();
		
        return;
    }

	@Override
	protected void onStop() {
		super.onStop();
		
		Status status;
		status = callService(ProjectSidekickService.MSG_DISCONNECT);
		if (status != Status.OK) {
			display("Failed to disconnect");
			return;
		}
		display("Disconnected");

		if (_receiver != null) {
			unregisterReceiver(_receiver);
		}
		
		return;
	}
	
	

	@Override
	protected void onDestroy() {
		Status status;
		status = callService(ProjectSidekickService.MSG_STOP);
		if (status != Status.OK) {
			display("Failed to stop service");
			return;
		}
		display("Stopped service");

		if (_bIsBound) {
			unbindService(_serviceConnection);
			_bIsBound = false;
		}
		
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
	
    private Status connectToDevice() {
		Status status;
		status = callService(ProjectSidekickService.MSG_SET_AS_MOBILE);
		if (status != Status.OK) {
			display("Failed set role to MOBILE");
			return Status.FAILED;
		}
		
		status = callService(ProjectSidekickService.MSG_START_SETUP);
		if (status != Status.OK) {
			display("Failed start SETUP Mode");
			return Status.FAILED;
		}

		Bundle extras = new Bundle();
		extras.putString("DEVICE_ADDR", _deviceAddr);
		status = callService(ProjectSidekickService.MSG_CONNECT, extras);
		if (status != Status.OK) {
			display("Failed attempt connection");
			return Status.FAILED;
		}

        return Status.OK;
    }
    
    private Status registerDevice() {
    	if (!_isConnected) {
    		display("Not yet connected!");
    		return Status.FAILED;
    	}
    	
		Status status;

		Bundle extras = new Bundle();
		extras.putString("DEVICE_ADDR", _deviceAddr);
		status = callService(ProjectSidekickService.MSG_SEND_REGISTER, extras);
		if (status != Status.OK) {
			display("Failed send register request");
			return Status.FAILED;
		}
		display("Register request sent");
        
		_app = getAppRef();
		_app.addRegisteredDevice(new KnownDevice(_deviceName, _deviceAddr));
        _app.saveRegisteredDevices();
        
    	return Status.OK;
    }
    
    private Status requestGuardList() {
    	if (!_isConnected) {
    		display("Not yet connected!");
    		return Status.FAILED;
    	}
    	
		Status status;

		Bundle extras = new Bundle();
		extras.putString("DEVICE_ADDR", _deviceAddr);
		status = callService(ProjectSidekickService.MSG_SEND_GET_LIST, extras);
		if (status != Status.OK) {
			display("Failed send retrieve guard list request");
			return Status.FAILED;
		}
		display("Retrieving guard list...");
    	
    	return Status.OK;
    }
    
    private Status disconnectDevice() {
		Status status;
		status = callService(ProjectSidekickService.MSG_DISCONNECT);
		if (status != Status.OK) {
			display("Failed to disconnectdevice");
			return Status.FAILED;
		}
		display("Device has been disconnected");
        
        return Status.OK;
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
        
        return;
    }
	
	private void reloadDeviceInfo() {
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
							if (_app.updateRegisteredDevice(fkd) != Status.OK) {
								Logger.err("Device not registered. Renaming is meaningless.");
							}
							
							_deviceName = nameStr;
							reloadDeviceInfo();
							
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

							if (_app.removeRegisteredDevice(fkd.getAddress()) != Status.OK) {
								Logger.err("Device not registered. Deletion is meaningless.");
							}
							
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
	        if (ProjectSidekickService.ACTION_CONNECTED.equals(action)) {
	        	updateGuiToConnected();
	        	_isConnected = true;
	        } else if (ProjectSidekickService.ACTION_DISCONNECTED.equals(action)) {
	        	updateGuiToDisconnected();
	        	_isConnected = false;
	        } else if (ProjectSidekickService.ACTION_DATA_RECEIVE.equals(action)) {
	        	String msg = intent.getStringExtra("DATA");
	        	display(msg);
	        } else if (ProjectSidekickService.ACTION_LIST_RECEIVED.equals(action)) {
	        	Intent listIntent 
	        		= new Intent(AppModeConfigBeaconActivity.this, 
	        				AppModeBeaconMasterListActivity.class);
	        	listIntent.putExtra("DEVICES", intent.getStringArrayExtra("DEVICES"));
	        	startActivity(listIntent);
	        }
	    }
	};
}
