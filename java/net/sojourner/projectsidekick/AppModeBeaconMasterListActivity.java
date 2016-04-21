package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.types.MasterListAdapter;
import net.sojourner.projectsidekick.types.MasterListItem;
import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.types.ServiceBindingActivity;
import net.sojourner.projectsidekick.utils.Logger;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

public class AppModeBeaconMasterListActivity extends ServiceBindingActivity {
	private ProjectSidekickApp _app = (ProjectSidekickApp) getApplication();
    private MasterListAdapter _masterListAdapter = null;
	private String _deviceAddr = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_beacon_master_list);

		/* Instantiate the adapter for the master list */
		ListView listDevices = (ListView) findViewById(R.id.list_devices);
		_masterListAdapter = new MasterListAdapter(this);
		listDevices.setAdapter(_masterListAdapter);

		/* Extract the raw master list from the intent and parse it */
		Intent intent = getIntent();
		_deviceAddr = intent.getStringExtra("DEVICE_ADDR");
		String masterListArr[] = intent.getStringArrayExtra("DEVICES");
		parseMasterList(masterListArr);

		/* Add functionality for committing the guard status of our devices */
		ImageButton btnSave = (ImageButton) findViewById(R.id.btn_save);
		btnSave.setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String guardSetupStr = _masterListAdapter.getGuardSetup();

						Bundle data = new Bundle();
						data.putString("GUARD_SETUP", guardSetupStr);
						callService(ProjectSidekickService.MSG_EDIT_GUARD_LST, data, null);

						return;
					}
				}
		);

        return;
    }

	@Override
	protected void onStart() {
		Logger.info("onStart called for " + this.getLocalClassName());
		super.onStart();

		if (_receiver != null) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(ProjectSidekickService.ACTION_DELETED);
			filter.addAction(ProjectSidekickService.ACTION_LIST_CHANGED);
			filter.addAction(ProjectSidekickService.ACTION_LIST_RECEIVED);
			registerReceiver(_receiver, filter);
		}

		super.onStart();
		return;
	}

	@Override
	protected void onStop() {
		if (_receiver != null) {
			unregisterReceiver(_receiver);
		}

		super.onStop();
		return;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		return;
	}

	private void parseMasterList(String masterListArray[]) {
		if (_masterListAdapter == null) {
			Logger.err("Master list adapter is unavailable");
			return;
		}
		_masterListAdapter.clear();

		/* Cycle through the master list array and parse each one */
		for (String s : masterListArray) {
			MasterListItem item = MasterListItem.parse(s);
			if (item == null) {
				Logger.warn("Malformed item string: " + s);
				continue;
			}
			_masterListAdapter.add(item);
		}
		_masterListAdapter.notifyDataSetChanged();
		
		return;
	}

    private void display(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        return;
    }

	private PSStatus requestGuardList() {
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

	/* ********************* */
	/* Private Inner Classes */
	/* ********************* */
	private final BroadcastReceiver _receiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ProjectSidekickService.ACTION_DELETED.equals(action)) {
				_masterListAdapter.confirmDeletion();
			} else if (ProjectSidekickService.ACTION_LIST_CHANGED.equals(action)) {
				new HandleDeviceListChangedTask().execute();
			} else if (ProjectSidekickService.ACTION_LIST_RECEIVED.equals(action)) {
				new HandleDeviceListReceivedTask(intent.getStringArrayExtra("DEVICES")).execute();
			}
		}
	};

	private class HandleDeviceListChangedTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			return null;
		}

		@Override
		protected void onPostExecute(Void Void) {
			requestGuardList();
			super.onPostExecute(Void);
		}
	}

	private class HandleDeviceListReceivedTask extends AsyncTask<Void, Void, Void> {
		private String _deviceList[];

		public HandleDeviceListReceivedTask(String listArray[]) {
			_deviceList = listArray;
			return;
		}

		@Override
		protected Void doInBackground(Void... params) {
			return null;
		}

		@Override
		protected void onPostExecute(Void Void) {
			parseMasterList(_deviceList);
			_masterListAdapter.notifyDataSetChanged();
			super.onPostExecute(Void);
		}
	}
}
