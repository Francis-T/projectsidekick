package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.Status;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class AppModeBeaconMasterListActivity extends ListActivity implements BluetoothEventHandler {
	private ProjectSidekickApp _app = (ProjectSidekickApp) getApplication();
    private ArrayAdapter<String> _masterListAdapter = null; 

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_beacon_master_list);
        
        _masterListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        setListAdapter(_masterListAdapter);

        Intent intent = getIntent();
        String masterListStr = intent.getStringExtra("MASTER_DVC_LIST");
        parseMasterList(masterListStr);
		
		ListView listGui = getListView();
		listGui.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> av, View v,
						int pos, long id) {
					if (_masterListAdapter == null) {
						Logger.err("Master list adapter is unavailable");
						return;
					}
					String address = _masterListAdapter.getItem(pos);
					showRemoveFromMasterListDialog(address);
					
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
	
	private void parseMasterList(String masterListStr) {
		if (_masterListAdapter == null) {
			Logger.err("Master list adapter is unavailable");
			return;
		}
		
		String masterListPart[] = masterListStr.split(",");
		
		for (String s : masterListPart) {
			_masterListAdapter.add(s);
		}
		_masterListAdapter.notifyDataSetChanged();
		
		return;
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

	@Override
	public void onDataReceived(String name, String address, byte[] data) {
		new AsyncTask<byte[], Void, byte[]> (){

			@Override
			protected byte[] doInBackground(byte[]... params) {
				return params[0];
			}

			@Override
			protected void onPostExecute(byte[] data) {
				Toast.makeText(AppModeBeaconMasterListActivity.this, "Data Received: " + new String(data), Toast.LENGTH_SHORT).show();
				
				super.onPostExecute(data);
				return;
			}
			
		}.execute(data);
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
				Toast.makeText(AppModeBeaconMasterListActivity.this, "Connected!", Toast.LENGTH_SHORT).show();
				
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
				Toast.makeText(AppModeBeaconMasterListActivity.this, "Disconnected!", Toast.LENGTH_SHORT).show();
				finish();
				super.onPostExecute(result);
			}
			
		}.execute();
	}


	private void showRemoveFromMasterListDialog(String address) {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		final String deviceAddr = address;
		
		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);
		
		dlgBuilder.setTitle("Remove from Master List");
		dlgBuilder.setMessage(deviceAddr + " will be removed from the beacon's master list. Is this OK?")
			.setCancelable(false)
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (sendRequest("LEAVE " + deviceAddr) != Status.OK) {
								display("Failed to send request to beacon");
								return;
							}
							
							_masterListAdapter.remove(deviceAddr);
							
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
