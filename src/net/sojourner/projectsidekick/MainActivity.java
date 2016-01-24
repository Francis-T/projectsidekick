package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.ProjectSidekickApp.Mode;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.Status;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

public class MainActivity extends Activity {
	private ProjectSidekickApp _app = null;
	private IBluetoothBridge _bluetooth = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		ImageButton btnSetTestMode = (ImageButton) findViewById(R.id.btn_set_test_mode);
		btnSetTestMode.setOnClickListener(
				new View.OnClickListener() {
					
					@Override
					public void onClick(View arg0) {
						showChooseModeDialog();
					}
				});
		
		return;
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		return;
	}

	@Override
	protected void onStop() {
		super.onStop();
	}
	
	private void showChooseModeDialog() {
		if (_app == null) {
			_app = (ProjectSidekickApp) getApplication();
		}
		
		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);
		
		dlgBuilder.setTitle("Testing Mode");
		dlgBuilder.setMessage("Please select the desired testing mode")
			.setCancelable(true)
			.setPositiveButton("App", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Toast.makeText(MainActivity.this, "App Mode Started", Toast.LENGTH_SHORT).show();
							_app.setMode(Mode.APP);
							Intent intent = new Intent(MainActivity.this, AppModeActivity.class);
							startActivity(intent);
						}
					})
			.setNeutralButton("Beacon", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Toast.makeText(MainActivity.this, "Beacon Mode Started", Toast.LENGTH_SHORT).show();
							_app.setMode(Mode.BEACON);
							Intent intent = new Intent(MainActivity.this, BeaconModeActivity.class);
							startActivity(intent);
						}
					})
			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Toast.makeText(MainActivity.this, "Cancelled", Toast.LENGTH_SHORT).show();
							dialog.cancel();
						}
					});
		
		dlgBuilder.create().show();
		
		return;
	}
	
	private void startBluetoothForBeaconMode() {
		if (_bluetooth.initialize(this, false) != Status.OK) {
			Logger.err("Failed to initialize Bluetooth");
			return;
		}
		return;
	}
	
}
