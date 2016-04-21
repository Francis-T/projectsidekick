package net.sojourner.projectsidekick.interfaces;

import net.sojourner.projectsidekick.types.BTState;
import net.sojourner.projectsidekick.types.PSStatus;

public interface IBluetoothBridge {
	public String getId();
	public String getPlatform();
	public BTState getState();
	public String getLocalName();
	public String getLocalAddress();
	public boolean isReady();
	public int getRssi();
	public PSStatus initialize(Object initObject, boolean isServer);
	public void startDeviceDiscovery();
	public void stopDeviceDiscovery();
	public PSStatus listen();
	public PSStatus connectDeviceByAddress(String address);
	public PSStatus connectDeviceByName(String name);
	public PSStatus disconnectDeviceByAddress(String address);
	public PSStatus broadcast(byte[] data);
	public PSStatus read(String address);
	public PSStatus destroy();
	public PSStatus setEventHandler(BluetoothEventHandler eventHandler);
}
