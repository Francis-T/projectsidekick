package net.sojourner.projectsidekick.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.BTState;
import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.utils.Logger;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

public class AndroidBluetoothBridge implements IBluetoothBridge {
	private final String NAME_SECURE = "BluetoothSecure";
	private final UUID MY_UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private final String NAME_INSECURE = "BluetoothInsecure";
	private final UUID MY_UUID_INSECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private static final int RSSI_VALUE_UNKNOWN = -65535;
	
	private static AndroidBluetoothBridge _androidBluetoothBridge = null;
	
	private BluetoothAdapter 	_bluetoothAdapter 	= BluetoothAdapter.getDefaultAdapter();
	private BroadcastReceiver 	_broadcastReceiver 	= null;
	private boolean 			_isServer 			= false;
	private BTState				_state 				= BTState.UNKNOWN;

	/* Device list maps */
	private HashMap<String, String> _pairedDevices 						= null;
	private HashMap<String, String> _discoveredDevices 					= null;
	private HashMap<String, BluetoothConnection> _currentConnections 	= null;
	
	/* Threads and Event Handlers */
	private BluetoothEventHandler _eventHandler 			= null;
	private BluetoothConnectThread _bluetoothConnectThread 	= null;
	private BluetoothListenerThread _bluetoothListener 		= null;
	
	private Context _context = null;

	/* Locks */
	private Lock _connThreadLock 	= new ReentrantLock();
	private Lock _listenThreadLock 	= new ReentrantLock();
	
	private AndroidBluetoothBridge() {
		return;
	}
	
	public static AndroidBluetoothBridge getInstance() {
		if (_androidBluetoothBridge == null) {
			_androidBluetoothBridge =  new AndroidBluetoothBridge();
		}
		
		return _androidBluetoothBridge;
	}

	@Override
	public String getId() {
		return "bluetooth";
	}

	@Override
	public String getPlatform() {
		return "android";
	}

	@Override
	public String getLocalName() {
		if (_bluetoothAdapter == null) {
			return "Unknown";
		}

		return _bluetoothAdapter.getName();
	}

	@Override
	public String getLocalAddress() {
		if (_bluetoothAdapter == null) {
			return "Unknown";
		}

		return _bluetoothAdapter.getAddress();
	}

	@Override
	public synchronized BTState getState() {
		return _state;
	}
	
	@Override
	public PSStatus initialize(Object initObject, boolean isServer) {
		/* Allow connect only if we're coming from the unknown state */
		if (_state != BTState.UNKNOWN) {
			Logger.warn("Already initialized()");
			return PSStatus.OK;
		}
		
		if (initObject == null) {
			return PSStatus.FAILED;
		}
		
		/* Treat the initObject as the _context */
		_context = (Context) initObject;
		if ( _bluetoothAdapter.isEnabled() == false ) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			_context.startActivity(enableBtIntent);
//			(enableBtIntent, BLUETOOTH_ENABLE_REQUEST); //TODO consider removing?
			return PSStatus.FAILED;
		}
		
		setupDeviceLists();
		
		/* Remember initialization settings (either server or client) */
		_isServer = isServer;
		
		/* Set the initial state to DISCONNECTED */
		setState(BTState.DISCONNECTED);
		
		return PSStatus.OK;
	}

	@Override
	public void startDeviceDiscovery() {
		_bluetoothAdapter.startDiscovery();
		Logger.info("Service Discovery started.");
		return;
	}

	@Override
	public void stopDeviceDiscovery() {
		_bluetoothAdapter.cancelDiscovery();
		Logger.info("Service Discovery stopped.");
		return;
	}

	@Override
	public PSStatus listen() {
		/* Allow listen only if started as a Bluetooth server */
		if (!_isServer) {
			Logger.err("Not initialized as a Bluetooth Server");
			return PSStatus.FAILED;
		}
		
		/* Allow listen only if we're coming from the disconnected state */
		if (_state != BTState.DISCONNECTED) {
			Logger.err("Invalid state for listen()");
			return PSStatus.FAILED;
		}
		
		/* Prevent other threads from manipulating the listen thread 
		 * 	while we are still setting it up */
		_listenThreadLock.lock();
		
		// start or re-start
		if (_bluetoothListener != null) {
			this.stop();
		}
		
		setState(BTState.LISTENING);
	
		_bluetoothListener = new BluetoothListenerThread(true);
		_bluetoothListener.start();
		
		/* Release the lock on the listen thread */
		_listenThreadLock.unlock();
		
		if (_broadcastReceiver == null) {
			_broadcastReceiver = new BluetoothBroadcastReceiver();
		}
		
		_context.registerReceiver(_broadcastReceiver, 
				new IntentFilter(BluetoothDevice.ACTION_FOUND));
		_context.registerReceiver(_broadcastReceiver, 
				new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
		
		this.getPairedDeviceNames();
		
		return PSStatus.OK;
	}
	
	@Override
	public PSStatus connectDeviceByAddress(String address) {
		/* Allow listen only if started as a Bluetooth client */
		if (_isServer) {
			Logger.err("Not initialized as a Bluetooth client");
			return PSStatus.FAILED;
		}
		
		/* Allow connect only if we're coming from the disconnected state */
		if (_state != BTState.DISCONNECTED) {
			Logger.err("Invalid state for connectDeviceByAddress()");
			return PSStatus.FAILED;
		}
		
		if ( BluetoothAdapter.checkBluetoothAddress(address) == false ) {
			Logger.err("Invalid bluetooth hardware address: " + address);
			return PSStatus.FAILED;
		}
		
		BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(address);
		boolean isSecure = true; // Defaulting to true
		
		if (device == null) {
			Logger.err("Invalid Bluetooth device address");
			return PSStatus.FAILED;
		}
		
		if (_currentConnections.containsKey(address) == true) {
			Logger.err("Already connected");
			return PSStatus.OK;
		}
		
		/* Prevent other threads from manipulating the conn thread 
		 * 	while we are still connecting */
		_connThreadLock.lock();
		
		if (_bluetoothConnectThread != null) {
			_bluetoothConnectThread.cancel();
		}

		BluetoothConnectThread connThread = this.getNewConnectThread(address, isSecure);
		if (connThread == null) {
			Logger.err("Failed to obtain a connect thread");
			return PSStatus.FAILED;
		}
		
		_bluetoothConnectThread = connThread;
		_bluetoothConnectThread.start();
		
		/* Block until the connection is successful */
		try {
			_bluetoothConnectThread.join(15000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		_connThreadLock.unlock();
		
		/* Check if the address being connected to is already in the
		 *  current connections list. If not, the connect operation
		 *  was unsuccessful. */
		if (_currentConnections.containsKey(address) == false) {
			if (_bluetoothConnectThread != null) {
				_bluetoothConnectThread.cancel();
			}
			Logger.err("Connection unsuccessful: Exiting at state " + this.getState());
			return PSStatus.FAILED;
		}
		
		return PSStatus.OK;
	}

	@Override
	public PSStatus connectDeviceByName(String name) {
		/* Allow listen only if started as a Bluetooth client */
		if (_isServer) {
			Logger.err("Not initialized as a Bluetooth client");
			return PSStatus.FAILED;
		}
		
		/* Allow connect only if we're coming from the disconnected state */
		if (_state != BTState.DISCONNECTED) {
			Logger.err("Invalid state for connectDeviceByName()");
			return PSStatus.FAILED;
		}
		
		if (_pairedDevices == null) {
			Logger.err("Paired devices list unavailable");
			return PSStatus.FAILED;
		}
		
		if (_discoveredDevices == null) {
			Logger.err("Discovered devices list unavailable");
			return PSStatus.FAILED;
		}
		
		if (_currentConnections == null) {
			Logger.err("Current connection list unavailable");
			return PSStatus.FAILED;
		}
		
		String address = "";
		if (_pairedDevices.containsKey(name)) {
			address = _pairedDevices.get(name);
		} else if (_discoveredDevices.containsKey(name)) {
			address = _discoveredDevices.get(name);
		}
		
		if (address.length() > 0 && _currentConnections.containsKey(address)) {
			Logger.info("Already connected");
			return PSStatus.OK;
		}
		
		Logger.info("Found device address: " + address);

		return (this.connectDeviceByAddress(address));
	}

	@Override
	public PSStatus disconnectDeviceByAddress(String address) {
		if (!_currentConnections.containsKey(address)) {
			return PSStatus.FAILED;
		}
		
		BluetoothConnection conn = _currentConnections.get(address);
		conn.cancel();

		_currentConnections.remove(conn);
		
		return PSStatus.OK;
	}

	@Override
	public PSStatus broadcast(byte[] data) {
		/* Allow broadcast only if we're in the connected state */
		if (_state != BTState.CONNECTED) {
			Logger.err("Invalid state for broadcast()");
			return PSStatus.FAILED;
		}
		
		if (_currentConnections == null) {
			Logger.err("Current connections list unavailable");
			return PSStatus.FAILED;
		}
		
		if (_currentConnections.isEmpty()) {
			Logger.err("Current connections list is empty");
			return PSStatus.FAILED;
		}
		
		for (Map.Entry<String, BluetoothConnection> conn : _currentConnections.entrySet()) {
			conn.getValue().write(data);
		}
		
		return PSStatus.OK;
	}

	@Override
	public PSStatus read(String address){
		return PSStatus.FAILED;
	}

	@Override
	public PSStatus destroy() {
		if (stop() != PSStatus.OK)
		{
			Logger.err("Failed to destroy BluetoothBridge");
		}
		
		return PSStatus.OK;
	}

	@Override
	public PSStatus setEventHandler(BluetoothEventHandler eventHandler) {
		this._eventHandler = eventHandler;
		return PSStatus.OK;
	}

	
	public ArrayList<String> getDiscoveredDeviceNames() {
		ArrayList<String> devices = new ArrayList<String>();

		for (String key : _discoveredDevices.keySet()) {
			if (key != null) {
				devices.add(key);
			}
		}
		return devices;
	}
	
	public ArrayList<String> getPairedDeviceAddresses() {
		ArrayList<String> devices = new ArrayList<String>();

		_pairedDevices.clear();
		Set<BluetoothDevice> bondedDevices = _bluetoothAdapter.getBondedDevices();
		if (bondedDevices.size() > 0) {
			for (BluetoothDevice device : bondedDevices) {
				_pairedDevices.put(device.getName(), device.getAddress());
				devices.add(device.getAddress());
			}
		}
		return devices;
	}
	
	public ArrayList<String> getPairedDeviceNames() {
		ArrayList<String> devices = new ArrayList<String>();

		_pairedDevices.clear();
		Set<BluetoothDevice> bondedDevices = _bluetoothAdapter.getBondedDevices();
		if (bondedDevices.size() > 0) {
			for (BluetoothDevice device : bondedDevices) {
				_pairedDevices.put(device.getName(), device.getAddress());
				devices.add(device.getName());
			}
		}
		return devices;
	}

	public ArrayList<String> getConnectedDeviceNames() {
		ArrayList<String> devices = new ArrayList<String>();
		Set<String> connectedDevices = _currentConnections.keySet();

		if (connectedDevices.size() > 0) {
			for (String device : connectedDevices) {
				BluetoothConnection c = _currentConnections.get(device);
				devices.add(c.getDeviceName() + "(" + device + ")");
			}
		}
		return devices;
	}


	@Override
	public boolean isReady() {
		if (_bluetoothAdapter.isEnabled()) {
			return true;
		}
		
		return false;
	}
	@Override
	public int getRssi() {
		return RSSI_VALUE_UNKNOWN;
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private PSStatus stop() {
		if (_broadcastReceiver != null) {
			_context.unregisterReceiver(_broadcastReceiver);
			_broadcastReceiver = null;
		}

		/* Prevent other threads from manipulating the listen thread 
		 * 	while we are closing it */
		_listenThreadLock.lock();
		if (_bluetoothListener != null) {
			Logger.info("Terminating Bluetooth Listener Thread...");
			_bluetoothListener.cancel();
			try {
				_bluetoothListener.join(1000);
			} catch (InterruptedException e) {
				Logger.warn("_bluetoothListener join() interrupted");
			}
		}
		/* Release the lock on the listen thread */
		_listenThreadLock.unlock();

		/* Prevent other threads from manipulating the conn thread 
		 * 	while we are closing it */
		_connThreadLock.lock();
		if (_bluetoothConnectThread != null) {
			Logger.info("Terminating Bluetooth Connect Thread...");
			_bluetoothConnectThread.cancel();
			try {
				_bluetoothConnectThread.join(1000);
			} catch (InterruptedException e) {
				Logger.warn("_btConnectThread join() interrupted");
			}
		}
		/* Release the lock on the listen thread */
		_connThreadLock.unlock();
	
		if (_currentConnections != null) {
			for (String key : _currentConnections.keySet()) {
				_currentConnections.get(key).cancel();
				if (_currentConnections.get(key) != null) {
					try {
						_currentConnections.get(key).join(1000);
					} catch (InterruptedException e) {
						Logger.warn("connection thread join() interrupted");
					}
				}
			}
			
			_currentConnections.clear();	/* Clear active connections */
		}
		
		_bluetoothListener = null;		/* Started by start() */
		_bluetoothConnectThread = null; /* Started by connectDevice() */
		_broadcastReceiver = null;		/* Initialized by start() */
		_eventHandler = null;			/* Initialized by setEventHandler() */
		
		clearDeviceLists();
		
		setState(BTState.UNKNOWN);
		
		return PSStatus.OK;
	}
	
	private BluetoothConnectThread getNewConnectThread(String deviceAddr, boolean useSecureRfComm) {
		/* If a connection thread already exists */
		if (_bluetoothConnectThread != null) {
			Logger.err("BluetoothConnectThread already exists");
			return null;
		}
		
		if ( BluetoothAdapter.checkBluetoothAddress(deviceAddr) == false ) {
			Logger.err("Invalid bluetooth hardware address: " + deviceAddr);
			return null;
		}
		
		BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(deviceAddr);
		if (device == null){
			Logger.err("Invalid remote device address: " + deviceAddr);
			return null;
		}
		
		return new BluetoothConnectThread(device, useSecureRfComm);
	}
	
	private synchronized PSStatus connectDevice(BluetoothSocket socket) {
		BluetoothDevice device = socket.getRemoteDevice();
		String deviceAddr = device.getAddress();
		
		/* Prevent redundant connections -- disconnect the current connection
		 *  before proceeding on to re-establishing the connection */
		if (_currentConnections.containsKey(deviceAddr)) {
			Logger.warn(deviceAddr + " is already connected.");
			
			_currentConnections.get(deviceAddr).cancel();
			_currentConnections.remove(deviceAddr);
		}
		
		/* Cancel ongoing connect threads (client only case) */
		if (_bluetoothConnectThread != null) {
			_bluetoothConnectThread.cancel();
			_bluetoothConnectThread = null;
		}
		
		if (_bluetoothListener != null) {
			_bluetoothListener.cancel();
			_bluetoothListener = null;
		}
		
		BluetoothConnection bluetoothConn = new BluetoothConnection(socket);
		bluetoothConn.start();
		
		_currentConnections.put(deviceAddr, bluetoothConn);
		setState(BTState.CONNECTED);
		
		Logger.info("Bluetooth connected!");
		return PSStatus.OK;
	}
	
	private PSStatus removeConnection(BluetoothConnection conn){
		if (conn == null) {
			Logger.err("Invalid input parameter/s" +
					" in AndroidBluetoothBridge.removeConnection()");
			return PSStatus.FAILED;
		}
		
		if (_currentConnections == null) {
			return PSStatus.FAILED;
		}
		
		if (_currentConnections.isEmpty()) {
			return PSStatus.FAILED;
		}
		
		if (_currentConnections.containsKey(conn.getDeviceAddress())) {
//			conn.cancel();
//			try {
//				conn.join(1000);
//			} catch (InterruptedException e) {
//				System.out.println("[E] connection join() interrupted. ");
//			}
			
			_currentConnections.remove(conn.getDeviceAddress());
		}
		
		setState(BTState.DISCONNECTED);
		
		return PSStatus.OK;
	}
	
	private void clearDeviceLists() {
		
		if (_pairedDevices != null) {
			if (_pairedDevices.isEmpty() == false) {
				_pairedDevices.clear();
			}
			_pairedDevices = null;
		}
		
		if (_discoveredDevices != null) {
			if (_discoveredDevices.isEmpty() == false) {
				_discoveredDevices.clear();
			}
			_discoveredDevices = null;
		}
		
		if (_currentConnections != null) {
			if (_currentConnections.isEmpty() == false) {
				_currentConnections.clear();
			}
			_currentConnections = null;
		}
		
		return;
	}
	
	private void setupDeviceLists() {
		_pairedDevices = new HashMap<String, String>();
		_discoveredDevices = new HashMap<String, String>();
		_currentConnections = new HashMap<String, BluetoothConnection>();
		
		return;
	}

	private synchronized void setState(BTState state) {
		_state = state;
		Logger.info("Bluetooth Bridge State is now " + _state.toString());
		return;
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
	/***************************/
	/** Private Inner Classes **/
	/***************************/
	private class BluetoothBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device != null) {
					_discoveredDevices.put(device.getName(), 
							device.getAddress());
					Logger.info("New Device Discovered: " + 
							device.getName());
				}
			}
	        
	        if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
	        	Logger.info("Service Discovery Started (receiver)");
	        } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
	        	Logger.info("Service Discovery Finished (receiver)");
	        }
		}
		
	}
	
	private class BluetoothConnection extends Thread {
		private BluetoothSocket _socket = null;
		private InputStream _inputStream = null;
		private OutputStream _outputStream = null;

		private String _remoteName = "";
		private String _remoteAddress = "";
		
		public BluetoothConnection(BluetoothSocket socket) {
			if (socket == null) {
				Logger.err("Invalid bluetooth connection socket");
				return;
			}

			Logger.info("Using socket: " + socket.toString());

			_socket = socket;
			_remoteName = _socket.getRemoteDevice().getName();
			_remoteAddress = _socket.getRemoteDevice().getAddress();
			
			try {
				_inputStream = _socket.getInputStream();
			} catch (IOException e) {
				_inputStream = null;
				Logger.err("Could not get bluetooth conn socket input stream");
			}

			try {
				_outputStream = _socket.getOutputStream();
			} catch (IOException e) {
				_outputStream = null;
				Logger.err("Could not get bluetooth conn socket output stream");
			}
			
			return;
		}
		
		public String getDeviceAddress() {
			return this._remoteAddress;
		}
		
		public String getDeviceName() {
			return this._remoteName;
		}
		
		public void run() {
			int readableBytes = 0;
			if ( (_inputStream == null) || (_outputStream == null) ) {
				Logger.err("No streams available for this BluetoothConnection");
				return;
			}
			
			correctRemoteDeviceInfo(_socket);
			
			/* Notify the event handler that we've connected */
			if (_eventHandler != null) {
				_eventHandler.onConnected(_remoteName, _remoteAddress);
			}
			
			Logger.info("New connection started: " + _remoteName + "/" + _remoteAddress);
			
			while (_state == BTState.CONNECTED) {
				try {
					readableBytes = _inputStream.available();
				} catch (IOException e) {
					Logger.err("Encountered an IOEXCEPTION upon checking stream");
					e.printStackTrace();
					break;
				}
				
				if (readableBytes > 255) {
					/* Disconnect if we have to many bytes buffered on the inputStream as
					 *  this may indicate a socket failure */
					Logger.err("Buffer overload");
					break;
				} else if (readableBytes > 0) {
					int bytesRead = 0;
					byte[] buffer = new byte[readableBytes];
					
					try {
						bytesRead = _inputStream.read(buffer, 0, readableBytes);
					} catch (IOException e) {
						Logger.err("Encountered an IOEXCEPTION upon reading from stream");
					}
					
					/* Notify the BluetoothController here */
					if (_eventHandler != null) {
						Logger.info("Notifying handlers from AndroidBluetoothBridge...");
						Logger.info("    Buffer Len: " + bytesRead);
						_eventHandler.onDataReceived(getDeviceName(), getDeviceAddress(), buffer);
					} else {
						Logger.warn("AndroidBluetoothBridge event handler not set");
					}
					
				} else {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						Logger.info("BluetoothConnection thread interrupted");
						if (_state != BTState.CONNECTED) {
							break;
						}
					}
				}
				
//				if (_socket.isConnected() == false) {
//					Logger.err("Connection lost!");
//					break;
//				}
			}
			detachConnection();
			
			Logger.info("Disconnected from " + this.getDeviceName() + "/" +this.getDeviceAddress());
			
			/* Notify the event handler that we've connected */
			if (_eventHandler != null) {
				_eventHandler.onDisconnected(_remoteName, _remoteAddress);
			} else {
				Logger.warn("No event handlers to notify!");
			}

			/* Close all streams and sockets */
			closeStreams();
			
			return;
		}
		
		public void write(byte[] buffer) {
			if (buffer == null) {
				Logger.err("Invalid parameters");
				return;
			}
			
			if (_outputStream == null) {
				Logger.err("Output stream unavailable");
				return;
			}
			
			try {
				_outputStream.write(buffer);
			} catch (IOException e) {
				Logger.err("Encountered an IOEXCEPTION upon writing to stream: " + e.getMessage());
				setState(BTState.DISCONNECTED);
			}
			
			return;
		}
		
		public void cancel() {
//			if (_socket == null) {
//				Logger.err("Invalid BluetoothConnection socket");
//				detachConnection();
//				return;
//			}
//			
//			try {
//				_socket.close();
//			} catch (IOException e) {
//				Logger.err("Failed to close BluetoothConnection socket");
//			}
//			
//			detachConnection();
			
			setState(BTState.DISCONNECTED);
			
			Logger.info("Bluetooth Connection cancelled");
			
			return;
		}

		private void closeStreams() {
			if (_outputStream != null) {
				Logger.info("Disconnecting Output Stream...");
				try {
					_outputStream.close();
				} catch (IOException e) {
					Logger.err("Failed to close Output Stream");
				}
			}

			if (_inputStream != null) {
				Logger.info("Disconnecting Input Stream...");
				try {
					_inputStream.close();
				} catch (IOException e) {
					Logger.err("Failed to close Input Stream");
				}
			}

			if (_socket != null) {
				Logger.info("Disconnecting Bluetooth Socket...");
				try {
					_socket.close();
				} catch (IOException e) {
					Logger.err("Failed to close BluetoothConnection socket");
				}
			}
			return;
		}
		
		private void detachConnection() {
			removeConnection(this);
			return;
		}
		
		private void correctRemoteDeviceInfo(BluetoothSocket socket) {
			if (_bluetoothAdapter == null) {
				Logger.err("BluetoothAdapter Unavailable");
				return;
			}
			
			String deviceAddr = socket.getRemoteDevice().getAddress();
			/* Match the device address against our lists of paired,
			 * 	discovered, and connected devices */
			if (_pairedDevices.containsValue(deviceAddr)) {
				_remoteName = retrieveName(deviceAddr, _pairedDevices);
				return;
			}
			if (_discoveredDevices.containsKey(deviceAddr)) {
				_remoteName = retrieveName(deviceAddr, _pairedDevices);
				return;
			}
			
			Logger.warn("Not matched against current lists of devices. " +
						"Attempting with a reversed MAC address instead...");
			
			String reversedAddr = getReversedMacAddress(deviceAddr);
			/* If no matches have been found yet, attempt to use the
			 * 	reversed mac address instead */
			if (_pairedDevices.containsValue(reversedAddr)) {
				_remoteAddress = reversedAddr;
				_remoteName = retrieveName(reversedAddr, _pairedDevices);
				return;
			}
			if (_discoveredDevices.containsValue(reversedAddr)) {
				_remoteAddress = reversedAddr;
				_remoteName = retrieveName(reversedAddr, _discoveredDevices);
				return;
			}
			
			Logger.err("No matches found for address: " + deviceAddr);
			
			return;
		}
		
		private String retrieveName(String address, Map<String, String> deviceMap) {
			for (Map.Entry<String, String> entry : deviceMap.entrySet()) {
				String entryAddress = entry.getValue();
				if (entryAddress.equals(address)) {
					return entry.getKey();
				}
			}
			
			return "";
		}
	}
	
	private class BluetoothListenerThread extends Thread {
		private BluetoothServerSocket _bluetoothServerSocket = null;
		private boolean _useSecureRfComm = false;
		
		public BluetoothListenerThread(boolean useSecureRfComm) {
			_bluetoothServerSocket = getServerSocket(useSecureRfComm);
			if (_bluetoothServerSocket == null) {
				return;
			}
				
			_useSecureRfComm = useSecureRfComm;
			
			return;
		}

		public void run() {
			BluetoothSocket connSocket = null;
			
			Logger.info("BluetoothListenerThread started.");

			while (_state != BTState.CONNECTED) {
				if (_bluetoothServerSocket == null) {
					Logger.err("No server socket found");
					this.cancel();
					return;
				}
				
				try {
					connSocket = _bluetoothServerSocket.accept();
					if (connSocket != null) {
						synchronized (AndroidBluetoothBridge.this) {
							Logger.info("Incoming connection from: " + 
										connSocket.getRemoteDevice().getName() + "/" +
										connSocket.getRemoteDevice().getAddress());
							Logger.info("Current state: " + _state.toString());
							switch (_state) {
								case LISTENING:
								case CONNECTING:
									/* Attempt to accept the incoming connection */
									connectDevice(connSocket);
									break;
								case CONNECTED:
								case UNKNOWN:
									/* If a connection has been established, 
									 * then we can now close the server socket */
									this.cancel();
								default:
									/* Do Nothing */
									break;
							}
						}
					}
				} catch (IOException e) {
					Logger.err("Failed to accept an incoming Bluetooth socket connection: " +
							"type=" + (_useSecureRfComm ? "Secure" : "Insecure"));
					this.cancel();
				}
			}
			
			Logger.info("BluetoothListenerThread finished.");
			return;
		}

		public void cancel() {
			Logger.info("Closing BluetoothListenerThread...");
			
			try {
				if (_bluetoothServerSocket != null) {
					_bluetoothServerSocket.close();
				}
				_bluetoothServerSocket = null;
			} catch (IOException e) {
				Logger.err("Failed to close the Bluetooth server socket: " +
						"type=" + (_useSecureRfComm ? "Secure" : "Insecure"));
			}
			
			Logger.info("BluetoothListenerThread closed.");
			return;
		}
		
		/** Private Methods **/
		@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
		private BluetoothServerSocket getServerSocket(boolean useSecureRfComm) {
			BluetoothServerSocket tempSocket = null;
			
			// Create a new listening server socket
			try {
				if (useSecureRfComm) {
					tempSocket = _bluetoothAdapter.listenUsingRfcommWithServiceRecord(
							NAME_SECURE, MY_UUID_SECURE);
				} else {
					tempSocket = _bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
							NAME_INSECURE, MY_UUID_INSECURE);
				}
			} catch (IOException e) {
				Logger.err("Failed to create Bluetooth Listening Socket: " +
						"type=" + (useSecureRfComm ? "Secure" : "Insecure"));
			}
			
			return tempSocket;
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
	private class BluetoothConnectThread extends Thread {
		private BluetoothSocket _bluetoothSocket = null;
		private BluetoothDevice _bluetoothDevice = null;
		private String _deviceAddress = "";
		private String _deviceName = "";
		private boolean _useSecureRFComm = false;
		
		public BluetoothConnectThread(BluetoothDevice device, boolean secure) {
			BluetoothSocket socket = null;
			
			_bluetoothDevice = device;
			_useSecureRFComm = secure;

			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try {
				if (secure) {
					socket = _bluetoothDevice
							.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
				} else {
					socket = _bluetoothDevice
							.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
				}
			} catch (IOException e) {
				Logger.err("Failed to create Bluetooth Connect Thread: " +
						"type=" + (_useSecureRFComm ? "Secure" : "Insecure"));
			}
			_bluetoothSocket = socket;
			_deviceAddress = device.getAddress();
			
			Logger.info("ConnectThread created");
			return;
		}
		
		@SuppressLint("NewApi")
		public void run() {
			Logger.info("ConnectThread started.");
			while (_bluetoothSocket == null) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			
			// Always cancel discovery because it will slow down a connection
			_bluetoothAdapter.cancelDiscovery();
	
			// Make a connection to the BluetoothSocket
			try {
				// This is a blocking call and will only return on a
				// successful connection or an exception
				if (_bluetoothSocket != null) {
					Logger.info("Attempting connection to " +
									_deviceName + "/" + _deviceAddress);
					_bluetoothSocket.connect();
					
					if (!_bluetoothSocket.isConnected()) {
						Logger.warn("Socket state: Not Connected");
					}
					Logger.warn("Attempt done.");
				}
			} catch (IOException e) {
				// Close the socket
				try {
					if (_bluetoothSocket != null) {
						_bluetoothSocket.close();
					}
				} catch (IOException e2) {
					Logger.err("Failed to close Bluetooth Connect Thread: " +
							"type=" + (_useSecureRFComm ? "Secure" : "Insecure"));
				}
				
				Logger.err("Failed to connect to BluetoothSocket: " + 
						e.getMessage() );

				if (_bluetoothSocket != null) {
					try {
						_bluetoothSocket.close();
					} catch (IOException e1) {
						Logger.err("Failed to close Bluetooth Connect Thread: " +
								"type=" + (_useSecureRFComm ? "Secure" : "Insecure"));
					}
				}
				
				_bluetoothConnectThread = null;
				return;
			}
			
			synchronized (AndroidBluetoothBridge.this) {
				_bluetoothConnectThread = null;	
			}
	
			// Start the connected thread
			connectDevice(_bluetoothSocket);
			Logger.info("ConnectThread finished.");
			
			return;
		}
		
		public void cancel() {
			Logger.info("ConnectThread cancelled.");
			if (_bluetoothSocket == null) {
				return;
			}
			
			try {
				_bluetoothSocket.close();
			} catch (IOException e) {
				Logger.err("Failed to close Bluetooth Connect Thread: " +
						"type=" + (_useSecureRFComm ? "Secure" : "Insecure"));
			}
			Logger.info("Finished cancelling ConnectThread.");
		}
	}
}
