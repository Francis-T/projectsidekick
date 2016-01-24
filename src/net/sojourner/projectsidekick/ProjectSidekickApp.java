package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.android.AndroidBluetoothBridge;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.Application;

public class ProjectSidekickApp extends Application {
	private static IBluetoothBridge _bluetoothBridge = AndroidBluetoothBridge.getInstance();
	public static enum Mode { UNSET, APP, BEACON };
	public static final int REQUEST_BLUETOOTH_DISCOVERABLE = 0xB0;
	public static final int REQUEST_CODE_BLUETOOTH_ENABLE = 0xB1;
	
	private Mode _mode = Mode.UNSET;
	
	public IBluetoothBridge getBluetoothBridge() {
		if (_bluetoothBridge == null) {
			Logger.err("Bluetooth Bridge is Unavailable");
		}
		return _bluetoothBridge;
	}
	
	public void setMode(Mode m) {
		_mode = m;
		return;
	}
	
	public Mode getMode() {
		return _mode;
	}
}
