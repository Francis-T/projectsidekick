package net.sojourner.projectsidekick.types;

import net.sojourner.projectsidekick.utils.Logger;

public class KnownDevice {

	public static enum DeviceStatus { UNKNOWN, NOT_FOUND, FOUND };
	
	private String 		 _name 	 			= "";
	private String 		 _addr 	 			= "";
	private String 		 _reverseAddr 	 	= "";
	private DeviceStatus _status 			= DeviceStatus.UNKNOWN;
	private boolean 	 _isRegistered  	= false;
	private boolean		 _isAddrReversed	= false;
	
	public KnownDevice(String name, String address, DeviceStatus status, boolean isRegistered) {
		this(name, address, status);
		_isRegistered = isRegistered;
		
		return;
	}
	
	public KnownDevice(String name, String address, DeviceStatus status) {
		this(name, address);
		_status = status;
		return;
	}
	
	public KnownDevice(String name, String address) {
		_name = name;
		_addr = address;
		_reverseAddr = KnownDevice.getReversedMacAddress(address);
		
		return;
	}
	
	public void setName(String name) {
		_name = name;
	}
	
	public void setStatus(DeviceStatus status) {
		_status = status;
	}
	
	public void setRegistered(boolean isRegistered) {
		_isRegistered = isRegistered;
	}
	
	public void setAddressReversed(boolean isReversed) {
		Logger.warn("The local bluetooth device may be reversing MAC addresses");
		_isAddrReversed = isReversed;
	}
	
	public boolean isRegistered() {
		return _isRegistered;
	}
	
	public boolean isAddressReversed() {
		return _isAddrReversed;
	}
	
	public boolean addressMatches(String address) {
		/* Check if we can match with the reversed address as well */
		if (_addr.equals(address)) {
			return true;
		}
		
		/* Check if we can match with the reversed address as well */
		if (_reverseAddr.equals(address)) {
			setAddressReversed(true);
			return true;
		}
		
		return false;
	}
	
	public String getName() {
		return _name;
	}
	
	public String getAddress() {
		return _addr;
	}
	
	public DeviceStatus getStatus() {
		return _status;
	}
	
	public String toString() {
		String prefix = _isRegistered ? "[R] " : "[U] ";
		return (prefix + _name + "\n" + _addr + "\n" + _status.toString());
	}

	private static String getReversedMacAddress(String address) {
		String newAddr = "";

		int targIdx = 0;
		int offset = 2;

		targIdx = address.length() - offset;
		while (targIdx >= 0) {

			newAddr += address.charAt(targIdx++);
			newAddr += address.charAt(targIdx);

			offset += 3;
			targIdx = address.length() - offset;

			if (targIdx >= 0) {
				newAddr += ":";
			}
		}

		return newAddr;
	}
}
