package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.types.MasterListItem;
import net.sojourner.projectsidekick.types.ServiceBindingListActivity;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class AppModeBeaconMasterListActivity extends ServiceBindingListActivity {
	private ProjectSidekickApp _app = (ProjectSidekickApp) getApplication();
    private ArrayAdapter<MasterListItem> _masterListAdapter = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_beacon_master_list);

		/* Instantiate the adapter for the master list */
        _masterListAdapter = new ArrayAdapter<MasterListItem>(this, android.R.layout.simple_list_item_1);
        setListAdapter(_masterListAdapter);

		/* Extract the raw master list from the intent and parse it */
        Intent intent = getIntent();
        String masterListArr[] = intent.getStringArrayExtra("DEVICES");
        parseMasterList(masterListArr);

		/* Add a listener for our list view */
		ListView listGui = getListView();
		listGui.setOnItemClickListener(
				new AdapterView.OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> av, View v, int pos, long id) {
						if (_masterListAdapter == null) {
							Logger.err("Master list adapter is unavailable");
							return;
						}

						/* Pick the master list item for removal and pass it to a dialog box */
						MasterListItem listItem = _masterListAdapter.getItem(pos);
						showRemoveFromMasterListDialog(listItem.getAddress());

						return;
					}
				}
		);

		/* Invoke onCreate() on our superclass to start the service */
		super.onCreate(savedInstanceState);

        return;
    }

	private void parseMasterList(String masterListArray[]) {
		if (_masterListAdapter == null) {
			Logger.err("Master list adapter is unavailable");
			return;
		}

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
        Toast.makeText( this, msg,  Toast.LENGTH_SHORT).show();
        return;
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
}
