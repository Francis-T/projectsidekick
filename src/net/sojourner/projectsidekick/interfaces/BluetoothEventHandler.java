package net.sojourner.projectsidekick.interfaces;

public interface BluetoothEventHandler {
	public void onConnected();
	public void onDisconnected();
	public void onDataReceived(byte[] data);
}
