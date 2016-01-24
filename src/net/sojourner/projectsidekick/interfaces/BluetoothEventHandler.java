package net.sojourner.projectsidekick.interfaces;

public interface BluetoothEventHandler {
	public void onConnected(String name, String address);
	public void onDisconnected(String name, String address);
	public void onDataReceived(byte[] data);
}
