package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.Status;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class AppModeConfigBeaconActivity extends Activity implements BluetoothEventHandler {
	private ProjectSidekickApp _app = (ProjectSidekickApp) getApplication();
    private String _deviceName = "";
    private String _deviceAddr = "";
    private boolean _isConnected = false;

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

        Button btnConnect = 
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

        Button btnRename  = 
            (Button) findViewById(R.id.btn_rename);
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

        Button btnDelete  = 
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

        Button btnReqDiscover = 
            (Button) findViewById(R.id.btn_req_discover);
        btnReqDiscover.setOnClickListener(
            new View.OnClickListener() {
				@Override
				public void onClick(View v) {
                    String reqStr = "DISCOVER";
                    if (sendRequest(reqStr) != Status.OK) {
                        display("Request discovery failed!");
                    }
                    return;
				}
            }
        );

        Button btnReqDiscoverList = 
            (Button) findViewById(R.id.btn_req_discover_list);
        btnReqDiscoverList.setOnClickListener(
            new View.OnClickListener() {
				@Override
				public void onClick(View v) {
                    String reqStr = "LIST DISCOVERED";
                    if (sendRequest(reqStr) != Status.OK) {
                        display("Request discovered list failed!");
                    }

                    /* Present a progress bar while we wait for the 
                     *  list to be received */
                    // TODO
                    return;
				}
            }
        );

        Button btnReqBringList = 
            (Button) findViewById(R.id.btn_req_bring_list);
        btnReqBringList.setOnClickListener(
            new View.OnClickListener() {
				@Override
				public void onClick(View v) {
                    String reqStr = "LIST BROUGHT";
                    if (sendRequest(reqStr) != Status.OK) {
                        display("Request brought list failed!");
                    }

                    /* Present a progress bar while we wait for the 
                     *  list to be received */
                    // TODO
                    return;
				}
            }
        );

        return;
    }

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
        return;
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
        super.onResume();
        return;
    }

    /* Private Methods */
    private Status connectToDevice() {
        IBluetoothBridge bluetooth = getBluetoothBridge();
        if (bluetooth == null) {
            Logger.err("Could not obtain bluetooth bridge");
            return Status.FAILED;
        }

        if (bluetooth.initialize(this, false) != Status.OK) {
            Logger.err("Failed to initialize bluetooth bridge");
            return Status.FAILED;
        }
        
        bluetooth.setEventHandler(this);

        if (bluetooth.connectDeviceByAddress(_deviceAddr) != Status.OK) {
            Logger.err("Failed to connect via bluetooth bridge");
            return Status.FAILED;
        }
        
        /* A successful connection means we should add this as a 
         *  registered device */
        if (_app.addRegisteredDevice(new KnownDevice(_deviceName, _deviceAddr)) 
        		!= Status.OK) {
        	Logger.err("Failed to add device to the registered device list");
        	return Status.FAILED;
        }
        
        _app.saveRegisteredDevices();

        return Status.OK;
    }
    
    private Status disconnectDevice() {
        IBluetoothBridge bluetooth = getBluetoothBridge();
        if (bluetooth == null) {
            Logger.err("Could not obtain bluetooth bridge");
            return Status.FAILED;
        }
        
        if (bluetooth.destroy() != Status.OK) {
            Logger.err("Could not disconnect bluetooth device");
        	return Status.FAILED;
        }
        
        return Status.OK;
    }

    private Status sendRequest(String message) {
        IBluetoothBridge bluetooth = getBluetoothBridge();
        if (bluetooth == null) {
            Logger.err("Could not obtain bluetooth bridge");
            return Status.FAILED;
        }

        if (bluetooth.initialize(this, false) != Status.OK) {
            Logger.err("Failed to initialize bluetooth bridge");
            return Status.FAILED;
        }
        
        bluetooth.setEventHandler(this);

        if (bluetooth.broadcast(message.getBytes()) != Status.OK) {
            Logger.err("Failed to send request through bluetooth bridge");
            return Status.FAILED;
        }

        return Status.OK;

    }

    private IBluetoothBridge getBluetoothBridge() {
        if (_app == null) {
            _app = (ProjectSidekickApp) getApplication();
        }

        return _app.getBluetoothBridge();
    }

    private void display(String msg) {
        Toast.makeText( this, msg,  Toast.LENGTH_SHORT).show();
        return;
    }
    
    private void updateGuiToConnected() {
        Button btnConnect = 
                (Button) findViewById(R.id.btn_connect);
        btnConnect.setText("Disconnect from Device");
        
        Button btnRename  = 
                (Button) findViewById(R.id.btn_rename);
        btnRename.setEnabled(true);
        
        Button btnDelete  = 
            (Button) findViewById(R.id.btn_delete);
        btnDelete.setEnabled(true);

//        Button btnReqDiscover = 
//            (Button) findViewById(R.id.btn_req_discover);
//        btnReqDiscover.setEnabled(true);
//        
//        Button btnReqDiscoverList = 
//            (Button) findViewById(R.id.btn_req_discover_list);
//        btnReqDiscoverList.setEnabled(true);
        
        Button btnReqBringList = 
            (Button) findViewById(R.id.btn_req_bring_list);
        btnReqBringList.setEnabled(true);
        
        return;
    }
    
    private void updateGuiToDisconnected() {
        Button btnConnect = 
                (Button) findViewById(R.id.btn_connect);
        btnConnect.setText("Connect to Device");
        
        Button btnRename  = 
                (Button) findViewById(R.id.btn_rename);
        btnRename.setEnabled(false);
        
        Button btnDelete  = 
            (Button) findViewById(R.id.btn_delete);
        btnDelete.setEnabled(false);

//        Button btnReqDiscover = 
//            (Button) findViewById(R.id.btn_req_discover);
//        btnReqDiscover.setEnabled(false);
//        
//        Button btnReqDiscoverList = 
//            (Button) findViewById(R.id.btn_req_discover_list);
//        btnReqDiscoverList.setEnabled(false);
        
        Button btnReqBringList = 
            (Button) findViewById(R.id.btn_req_bring_list);
        btnReqBringList.setEnabled(false);
        
        return;
    }

	@Override
	public void onConnected(String name, String address) {
		new AsyncTask<Void, Void, Void> (){

			@Override
			protected Void doInBackground(Void... arg0) {
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				display("Connected!");
				updateGuiToConnected();
				_isConnected = true;
				super.onPostExecute(result);
			}
			
		}.execute();
		return;
	}

	@Override
	public void onDisconnected(String name, String address) {
		new AsyncTask<Void, Void, Void> (){

			@Override
			protected Void doInBackground(Void... arg0) {
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				display("Disconnected!");
				updateGuiToDisconnected();
				_isConnected = false;
				super.onPostExecute(result);
			}
			
		}.execute();
		return;
	}

	@Override
	public void onDataReceived(byte[] data) {
		new AsyncTask<byte[], Void, byte[]> (){

			@Override
			protected byte[] doInBackground(byte[]... params) {
				return params[0];
			}

			@Override
			protected void onPostExecute(byte[] data) {
				Toast.makeText(AppModeConfigBeaconActivity.this, "Data Received: " + new String(data), Toast.LENGTH_SHORT).show();
				
				processResponse(data);
				
				super.onPostExecute(data);
				return;
			}
			
		}.execute(data);
	}
	

    private void processResponse(byte[] data) {
        String dataStr = new String(data);

        if (dataStr.contains("BROUGHT")) {
        	if (dataStr.length() <= 8) {
        		Logger.warn("No devices found");
        		return;
        	}
        	
        	String devicesSubStr = dataStr.substring(8);
        	
        	Intent intent = new Intent(AppModeConfigBeaconActivity.this,
        								AppModeBeaconMasterListActivity.class);
        	intent.putExtra("MASTER_DVC_LIST", devicesSubStr);
        	startActivity(intent);
        }
        	
        return;
    }
	
	private void reloadDeviceInfo() {
        TextView txvDvcInfo = (TextView) findViewById(R.id.txv_dvc_info);
        txvDvcInfo.setText(_deviceName + "\n" + _deviceAddr);
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
}
