package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.Status;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class AppModeBeaconMasterListActivity extends ListActivity implements BluetoothEventHandler {
	private ProjectSidekickApp _app = (ProjectSidekickApp) getApplication();
    private ArrayAdapter<String> _masterListAdapter = null;
	private Messenger 			_service 			= null;
	private boolean 			_bIsBound 			= false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_beacon_master_list);
        
        _masterListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        setListAdapter(_masterListAdapter);

        Intent intent = getIntent();
        String masterListArr[] = intent.getStringArrayExtra("DEVICES");
        parseMasterList(masterListArr);
		
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
					String listItem = _masterListAdapter.getItem(pos);
					String itemDataPart[] = listItem.replace("\n", "|").split("\\|");
					if (itemDataPart.length != 3) {
						Logger.err("Cannot extract address from " + listItem.replace("\n", "|") + " [length: " + itemDataPart.length + "]");
						return;
					}
					String address = itemDataPart[1];
					showRemoveFromMasterListDialog(address);
					
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

	@Override
	protected void onDestroy() {
		if (_bIsBound) {
			unbindService(_serviceConnection);
			_bIsBound = false;
		}

		super.onDestroy();
		return;
	}

	private Status callService(int msgId) {
		return callService(msgId, null, null);
	}

	private Status callService(int msgId, Bundle extras, Messenger localMessenger) {
		if (_service == null) {
			Logger.err("Service unavailable");
			return Status.FAILED;
		}

		if (!_bIsBound) {
			Logger.err("Service unavailable");
			return Status.FAILED;
		}

		Message msg = Message.obtain(null, msgId, 0, 0);
		msg.replyTo = localMessenger;
		msg.setData(extras);

		try {
			_service.send(msg);
		} catch (Exception e) {
			Logger.err("Failed to call service: " + e.getMessage());
			return Status.FAILED;
		}

		return Status.OK;
	}

	private void parseMasterList(String masterList[]) {
		if (_masterListAdapter == null) {
			Logger.err("Master list adapter is unavailable");
			return;
		}
		
		for (String s : masterList) {
			_masterListAdapter.add(s.replace("|", "\n"));
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
					Bundle data = new Bundle();
					data.putString("DEVICE_ADDR", deviceAddr);
					callService(ProjectSidekickService.MSG_UNREG_DEVICE, data, null);
					display("Delete requested!");
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
}
