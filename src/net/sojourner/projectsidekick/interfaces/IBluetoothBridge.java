package net.sojourner.projectsidekick.interfaces;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.types.Status;

public interface IBluetoothBridge {
	public String getId();
	public String getPlatform();
	public boolean isReady();
	public Status initialize(Object initObject, boolean isServer);
	public void startDeviceDiscovery();
	public void stopDeviceDiscovery();
	public Status listen();
	public Status connectDeviceByAddress(String address);
	public Status connectDeviceByName(String name);
	public Status broadcast(byte[] data);
	public Status destroy();
	public Status setEventHandler(BluetoothEventHandler eventHandler);
}
