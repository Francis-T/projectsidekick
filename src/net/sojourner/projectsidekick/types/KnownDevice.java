package net.sojourner.projectsidekick.types;

public class KnownDevice {
	public static enum DeviceStatus { UNKNOWN, NOT_FOUND, FOUND }; 
	
	private String 		 _name 	 		= "";
	private String 		 _addr 	 		= "";
	private DeviceStatus _status 		= DeviceStatus.UNKNOWN;
	private boolean 	 _isRegistered  = false;
	
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
	
	public boolean isRegistered() {
		return _isRegistered;
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
}
