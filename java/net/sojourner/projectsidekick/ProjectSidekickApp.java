package net.sojourner.projectsidekick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sojourner.projectsidekick.android.AndroidBluetoothBridge;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;

public class ProjectSidekickApp extends Application {
	private static IBluetoothBridge _bluetoothBridge = AndroidBluetoothBridge.getInstance();
	public static enum Mode { UNSET, APP, BEACON };
	public static final int REQUEST_BLUETOOTH_DISCOVERABLE 	= 0xB0;
	public static final int REQUEST_CODE_BLUETOOTH_ENABLE 	= 0xB1;
	
    private List<KnownDevice> _registeredDevices = new ArrayList<KnownDevice>();
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
	
	/* Registered Device List Manipulation Methods */
    public List<KnownDevice> getRegisteredDevices() {
    	if (_registeredDevices.isEmpty()) {
    		restoreRegisteredDevices();
    	}
    	
    	return new ArrayList<KnownDevice>(_registeredDevices);
    }
    
    public PSStatus addRegisteredDevice(KnownDevice device) {
    	/* Check if we already have this device in our list */
    	for (KnownDevice kd : _registeredDevices) {
    		if (kd.addressMatches(device.getAddress())) {
    			Logger.warn("Device already registered");
    			return PSStatus.OK;
    		}
    	}
    	
    	if (_registeredDevices.add(device) != true) {
    		Logger.err("Failed to add device");
    		return PSStatus.FAILED;
    	}
    	
    	return PSStatus.OK;
    }
    
    public PSStatus updateRegisteredDevice(KnownDevice device) {
    	/* Check if we already have this device in our list */
    	for (KnownDevice kd : _registeredDevices) {
    		if (kd.addressMatches(device.getAddress())) {
    			Logger.warn("Device found");
    			
    			/* Simply update our registered device's name */
    			kd.setName(device.getName());
    			
    			return PSStatus.OK;
    		}
    	}
    	Logger.err("Device not found");
    	return PSStatus.FAILED;
    }
    
    public PSStatus removeRegisteredDevice(String address) {
    	KnownDevice target = findRegisteredDevice(address);
    	if (target == null) {
    		Logger.err("Device not found");
    		return PSStatus.OK;
    	}
    	
    	if (_registeredDevices.remove(target) == false) {
    		Logger.err("Failed to remove registered device from list");
    		return PSStatus.FAILED;
    	}
    	
    	return PSStatus.OK;
    }
    
    public KnownDevice findRegisteredDevice(String address) {
    	for (KnownDevice kd : _registeredDevices) {
    		if (kd.addressMatches(address)) {
    			return kd;
    		}
    	}
    	return null;
    }
    
    public void restoreRegisteredDevices() {
		if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			Logger.err("Master List Restore Not supported");
			return;
		}

		SharedPreferences prefs = getSharedPreferences("PROJECT_BEACON__1127182", MODE_WORLD_WRITEABLE);

		Set<String> rgdSet = prefs.getStringSet("REGISTERED_DVC_LIST", null);
		if (rgdSet != null) {
			for (String item : rgdSet) {
				String deviceInfo[] = item.split(",");
				if (deviceInfo.length != 2) {
					Logger.err("Skipping malformed registered device string: " 
								+ item);
					continue;
				}
				
				KnownDevice kd = new KnownDevice(deviceInfo[0], deviceInfo[1]);
				kd.setRegistered(true);
				
				addRegisteredDevice(kd);
			}
		}
    }

    public void saveRegisteredDevices() {
		if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			Logger.err("Master List Restore Not supported");
			return;
		}

		SharedPreferences prefs = getSharedPreferences("PROJECT_BEACON__1127182", MODE_WORLD_WRITEABLE);

		Set<String> rgdInfoSet = new HashSet<String>();
		for (KnownDevice rd : _registeredDevices) {
			Logger.info("Adding " + rd.getName() + " to set");
			rgdInfoSet.add(rd.getName() + "," + rd.getAddress());
		}
		prefs.edit().putStringSet("REGISTERED_DVC_LIST", rgdInfoSet).commit();
		
		Logger.info("Saved registered devices");
		
		return;
    }
}
