package net.sojourner.projectsidekick;

import android.app.ListActivity;
import android.os.Bundle;

public class AppModeBeaconDiscoverListActivity extends ListActivity {
    public AppModeBeaconDiscoverListActivity() {
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_config_beacon);

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
}
