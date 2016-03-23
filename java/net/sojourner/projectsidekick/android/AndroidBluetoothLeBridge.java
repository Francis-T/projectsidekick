package net.sojourner.projectsidekick.android;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.BTState;
import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.utils.Logger;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AndroidBluetoothLeBridge implements IBluetoothBridge {
	public static final String ACTION_LE_DISCOVERED = "net.sojourner.android.AndroidBluetoothLeBridge.DISCOVERED";

	private static final int BLUNO_BAUD_RATE = 115200;
	private static final long MAX_BLE_SCAN_DURATION = 10000;
	private static final String BLUNO_MODEL_NUM_RESP_PREFIX = "DF BLUNO";
	private static final String BLUNO_COMMAND_PASSWORD_STR = "AT+PASSWOR=DFRobot\r\n";
	private static final String BLUNO_COMMAND_BAUD_RATE_STR = "AT+CURRUART=" + BLUNO_BAUD_RATE + "\r\n";

	private final UUID UUID_SERIAL 		= UUID.fromString("0000dfb1-0000-1000-8000-00805F9B34FB");
	private final UUID UUID_COMMAND 	= UUID.fromString("0000dfb2-0000-1000-8000-00805F9B34FB");
	private final UUID UUID_MODEL_NUM	= UUID.fromString("00002a24-0000-1000-8000-00805F9B34FB");

	private static AndroidBluetoothLeBridge _androidBluetoothBridge = null;

	private BluetoothAdapter 	_bluetoothAdapter 	= BluetoothAdapter.getDefaultAdapter();
	private boolean 			_isServer 			= false;
	private boolean				_isScanning			= false;
	private BTState				_state 				= BTState.UNKNOWN;

	/* Device list maps */
	private HashMap<String, String> _pairedDevices 						= null;
	private HashMap<String, String> _discoveredDevices 					= null;
	private HashMap<String, BluetoothLeConnection> _currentConnections 	= null;

	/* Threads and Event Handlers */
	private BluetoothEventHandler 	_eventHandler 			= null;
	private Thread 					_waitingThread 			= null;

	private Context _context = null;

	private AndroidBluetoothLeBridge() {
		return;
	}
	
	public static AndroidBluetoothLeBridge getInstance() {
		if (_androidBluetoothBridge == null) {
			_androidBluetoothBridge =  new AndroidBluetoothLeBridge();
		}
		
		return _androidBluetoothBridge;
	}

	@Override
	public String getId() {
		return "bluetooth_le";
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
		if (!_bluetoothAdapter.isEnabled()) {
			Logger.err("Bluetooth is not enabled");
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
		if (_isScanning) {
			Logger.warn("Service Discovery has already been started");
			return;
		}

		_bluetoothAdapter.startLeScan(_leScanCbf);
		_isScanning = true;

		/* Automatically terminate BLE scan after MAX_BLE_SCAN_DURATION */
		new Handler().postDelayed(
				new Runnable() {
					@Override
					public void run() {
						if (_leScanCbf != null) {
							stopDeviceDiscovery();
						}
					}
				}, MAX_BLE_SCAN_DURATION
		);
		Logger.info("Service Discovery started.");
		return;
	}

	@Override
	public void stopDeviceDiscovery() {
		if (!_isScanning) {
			Logger.warn("Service Discovery has not been started or has already been stopped");
			return;
		}

		_bluetoothAdapter.stopLeScan(_leScanCbf);
		_isScanning = false;
		Logger.info("Service Discovery stopped.");
		return;
	}

	/* TODO Find out how to implement listening on a Bluetooth Gatt Server */
	@Override
	public PSStatus listen() {
		throw new UnsupportedOperationException("listen() is not supported for this implementation");

//		/* Allow listen only if started as a Bluetooth server */
//		if (!_isServer) {
//			Logger.err("Not initialized as a Bluetooth Server");
//			return PSStatus.FAILED;
//		}
//
//		/* Allow listen only if we're coming from the disconnected state */
//		if (_state != BTState.DISCONNECTED) {
//			Logger.err("Invalid state for listen()");
//			return PSStatus.FAILED;
//		}
//
//		setState(BTState.LISTENING);
//
//		this.getPairedDeviceNames();
//
//		return PSStatus.OK;
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
		
		if (!BluetoothAdapter.checkBluetoothAddress(address)) {
			Logger.err("Invalid bluetooth hardware address: " + address);
			return PSStatus.FAILED;
		}
		
		BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			Logger.err("Invalid Bluetooth device address");
			return PSStatus.FAILED;
		}
		BluetoothLeConnection bleConn;
		if (_currentConnections.containsKey(address)) {
			if (_currentConnections.get(address).isConnected()) {
				Logger.err("Already connected");
				return PSStatus.OK;
			}
			/* Attempt to re-connect using the existing inactive BLE connection */
			bleConn = _currentConnections.get(address);
		} else {
			/* Connect using an entirely new BLE connection */
			bleConn = new BluetoothLeConnection(device.getName(), address);
		}

		_waitingThread = Thread.currentThread();
		/* Attempt to connect to this remote device */
		BluetoothGatt bleGatt = device.connectGatt(_context, false, bleConn.getCallbackHandler());
		try {
			Thread.sleep(15000);
		} catch (InterruptedException e) {
			/* Proceed normally for interrupts */
		} catch (Exception e) {
			Logger.err("Exception occurred: " + e.getMessage());
		}
		_waitingThread = null;

		/* If not connected, then exit unsuccessfully */
		if (!bleConn.isConnected()) {
			Logger.err("Connection unsuccessful: Exiting at state " + this.getState());
			return PSStatus.FAILED;
		}

		/* Save the BluetoothGatt reference in our BluetoothLeConnection object */
		bleConn.setBluetoothGatt(bleGatt);

		/* Add it to our list of current connections */
		_currentConnections.put(address, bleConn);
		
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
		
		Logger.info("Found device address: " + address);

		return (this.connectDeviceByAddress(address));
	}

	@Override
	public PSStatus disconnectDeviceByAddress(String address) {
		if (!_currentConnections.containsKey(address)) {
			if (getState() != BTState.DISCONNECTED) {
				setState(BTState.DISCONNECTED);
			}
			return PSStatus.OK;
		}

		BluetoothLeConnection bleConn = _currentConnections.get(address);
		BluetoothGatt bluetoothGatt = bleConn.getBluetoothGatt();

		/* Disconnect and close the connection */
		bluetoothGatt.disconnect();
		bluetoothGatt.close();

		/* Remove it from the list */
		_currentConnections.remove(bleConn);
		
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

		boolean bWriteFailed = false;
		for (Map.Entry<String, BluetoothLeConnection> conn : _currentConnections.entrySet()) {
			if (!conn.getValue().write(data)) {
				bWriteFailed = true;
			}
		}

		if (bWriteFailed) {
			return PSStatus.FAILED;
		}
		
		return PSStatus.OK;
	}

	@Override
	public PSStatus read(String address){
		/* Allow read only if we're in the connected state */
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

		_currentConnections.get(address).read();

		return PSStatus.OK;
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
			for (String address : connectedDevices) {
				BluetoothLeConnection c = _currentConnections.get(address);
				devices.add(c.getDeviceName() + "(" + address + ")");
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

	/*********************/
	/** Private Methods **/
	/*********************/
	private PSStatus stop() {
		if (_currentConnections != null) {
			for (String key : _currentConnections.keySet()) {
				BluetoothLeConnection bleConn = _currentConnections.get(key);

				/* Attempt to disconnect the connection */
				_waitingThread = Thread.currentThread();
				bleConn.disconnect();
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					/* Allow normal interrupts */
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
				}
				_waitingThread = null;

				if (bleConn.isConnected()) {
					Logger.err("Failed to disconnect!");
				}

				/* Close the connection regardless */
				bleConn.close();
			}
			
			_currentConnections.clear();	/* Clear active connections */
		}

		_eventHandler = null;			/* Initialized by setEventHandler() */
		
		clearDeviceLists();
		
		setState(BTState.UNKNOWN);
		
		return PSStatus.OK;
	}

	/* TODO Unused, may come into play once we need handling for disconnects */
	private PSStatus removeConnection(String address){
		if (address == null) {
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

		if (_currentConnections.containsKey(address)) {
			BluetoothLeConnection bleConn = _currentConnections.get(address);
			if (bleConn.isConnected()) {
				/* Attempt to disconnect the connection */
				_waitingThread = Thread.currentThread();
				bleConn.disconnect();
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					/* Allow normal interrupts */
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
				}
				_waitingThread = null;

				if (bleConn.isConnected()) {
					Logger.err("Failed to disconnect!");
				}
			}

			/* Close the connection */
			bleConn.close();

			_currentConnections.remove(address);
		}

		setState(BTState.DISCONNECTED);

		return PSStatus.OK;
	}
	
	private void clearDeviceLists() {
		
		if (_pairedDevices != null) {
			if (!_pairedDevices.isEmpty()) {
				_pairedDevices.clear();
			}
			_pairedDevices = null;
		}
		
		if (_discoveredDevices != null) {
			if (!_discoveredDevices.isEmpty()) {
				_discoveredDevices.clear();
			}
			_discoveredDevices = null;
		}
		
		if (_currentConnections != null) {
			if (!_currentConnections.isEmpty()) {
				_currentConnections.clear();
			}
			_currentConnections = null;
		}
		
		return;
	}
	
	private void setupDeviceLists() {
		_pairedDevices = new HashMap<String, String>();
		_discoveredDevices = new HashMap<String, String>();
		_currentConnections = new HashMap<String, BluetoothLeConnection>();
		
		return;
	}

	private synchronized void setState(BTState state) {
		_state = state;
		Logger.info("Bluetooth Bridge State is now " + _state.toString());
		return;
	}

	private void notifyOnConnected(String name, String address) {
		setState(BTState.CONNECTED);
		/* Notify the event handler that we've connected */
		if (_eventHandler != null) {
			_eventHandler.onConnected(name, address);
		}

		return;
	}

	private void notifyOnDisconnected(String name, String address) {
		setState(BTState.DISCONNECTED);
		/* Notify the event handler that we've disconnected */
		if (_eventHandler != null) {
			_eventHandler.onDisconnected(name, address);
		}

		return;
	}

	/***************************/
	/** Private Inner Classes **/
	/***************************/
	private BluetoothAdapter.LeScanCallback _leScanCbf = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			if (device == null) {
				return;
			}

			if (!_discoveredDevices.containsValue(device.getAddress())) {
				_discoveredDevices.put(device.getName(), device.getAddress());
			}

			if (_context == null) {
				Logger.err("No context for intent broadcast!");
			}

			Intent intent = new Intent(AndroidBluetoothLeBridge.ACTION_LE_DISCOVERED);
			intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
			_context.sendBroadcast(intent);

			return;
		}
	};

	private class BluetoothGattRequest {
		private BluetoothGattCharacteristic _characteristic;
		private boolean _isWrite = false;
		private byte _data[];

		public BluetoothGattRequest(BluetoothGattCharacteristic characteristic,
										 byte data[]) {
			_characteristic = characteristic;
			_data = data;
			_isWrite = true;
			return;
		}

		public BluetoothGattRequest(BluetoothGattCharacteristic characteristic,
										 String data) {
			_characteristic = characteristic;
			_data = data.getBytes();
			_isWrite = true;
			return;
		}

		public BluetoothGattRequest(BluetoothGattCharacteristic characteristic) {
			_characteristic = characteristic;
			_data = null;
			_isWrite = false;
			return;
		}

		public BluetoothGattCharacteristic getCharacteristic() {
			return _characteristic;
		}

		public String getCharacteristicName() {
			UUID uuid = _characteristic.getUuid();
			if (uuid.equals(UUID_COMMAND)) {
				return "COMMAND";
			} else if (uuid.equals(UUID_MODEL_NUM)) {
				return "MODEL NO";
			} else if (uuid.equals(UUID_SERIAL)) {
				return "SERIAL";
			}

			return "UNKNOWN";
		}

		public byte[] getData() {
			return _data;
		}

		public boolean isWrite() { return _isWrite; }
	}

	private class BluetoothLeConnection {
		private String _deviceName = "";
		private String _deviceAddress = "";
		private BluetoothGatt _bluetoothGatt = null;
		private boolean _isConnected = false;
		private ByteArrayOutputStream _incomingData = null;
		private byte[] _dataBuffer = null;
		boolean _bInternalQueueFailed = false;

		private List<BluetoothGattCharacteristic> _gattCharacteristics = new ArrayList<BluetoothGattCharacteristic>();
		private BluetoothGattCharacteristic _modelNumCharacteristic = null;
		private BluetoothGattCharacteristic _serialPortCharacteristic = null;
		private BluetoothGattCharacteristic _commandCharacteristic = null;
		private BluetoothGattCharacteristic _targetCharacteristic = null;

		private List<BluetoothGattRequest> _gattTaskQueue = null;

		private BluetoothGattCallbackHandler _callbackHandler = new BluetoothGattCallbackHandler();

		public BluetoothLeConnection(String name, String address) {
			_deviceName = name;
			_deviceAddress = address;
			_gattTaskQueue = new ArrayList<BluetoothGattRequest>();
			return;
		}

		public BluetoothGattCallback getCallbackHandler() {
			return _callbackHandler;
		}

		public void setBluetoothGatt(BluetoothGatt gatt) {
			_bluetoothGatt = gatt;
			return;
		}

		public BluetoothGatt getBluetoothGatt() {
			return _bluetoothGatt;
		}

		public boolean isConnected() {
			return _isConnected;
		}

		public String getDeviceName() {
			return _deviceName;
		}

		public String getDeviceAddress() {
			return _deviceAddress;
		}

		public boolean disconnect() {
			if (_bluetoothGatt == null) {
				Logger.err("No BluetoothGatt reference for " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			_bluetoothGatt.disconnect();

			return true;
		}

		public boolean close() {
			if (_bluetoothGatt == null) {
				Logger.err("No BluetoothGatt reference for " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			_bluetoothGatt.close();

			return true;
		}

		public boolean write(byte data[]) {
			if (!_isConnected) {
				 Logger.err("Not connected to " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			if (_bluetoothGatt == null) {
				Logger.err("No BluetoothGatt reference for " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			if (_bInternalQueueFailed) {
				Logger.err("Internal queue failed on previous attempts");
				_bInternalQueueFailed = false;
				return false;
			}

			/* Write the data using our serial port characteristic */
			if (_targetCharacteristic != _serialPortCharacteristic) {
				Logger.err("Connection is busy");
				return false;
			}

			/* Add it to the writing queue */
			_gattTaskQueue.add(new BluetoothGattRequest(_targetCharacteristic, data));

			/* Nudge the Queue */
			nudgeQueue();

			return true;
		}

		public boolean read() {
			return read(_serialPortCharacteristic);
		}

		public boolean read(BluetoothGattCharacteristic characteristic) {
			if (!_isConnected) {
				Logger.err("Not connected to " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			if (_bluetoothGatt == null) {
				Logger.err("No BluetoothGatt reference for " + _deviceName + "/" + _deviceAddress);
				return false;
			}

			/* Add it to the read queue */
			_gattTaskQueue.add(new BluetoothGattRequest(characteristic));

			return true;
		}

		private boolean nudgeQueue() {
			synchronized (this) {
				if (_gattTaskQueue.isEmpty()) {
					return true;
				}

				if (!_isConnected) {
					Logger.err("Not connected to " + _deviceName + "/" + _deviceAddress);
					Logger.info("nudgeQueue() invoked; elements in queue: " + _gattTaskQueue.size());
					return false;
				}

				if (_bluetoothGatt == null) {
					Logger.err("No BluetoothGatt reference for " + _deviceName + "/" + _deviceAddress);
					return false;
				}

				/* Retrieve the write request and reconstruct the characteristic */
				BluetoothGattRequest request = _gattTaskQueue.get(0);
				BluetoothGattCharacteristic characteristic = request.getCharacteristic();
				Logger.info("Processing request: " + request.getCharacteristicName() +
						", isWrite=" + request.isWrite());
				if (request.isWrite()) {
					byte data[] = request.getData();
					characteristic.setValue(data);

					/* Write the characteristic through GATT */
					if (!_bluetoothGatt.writeCharacteristic(characteristic)) {
						Logger.err("Write failed: " + new String(data));
						return false;
					}
					Logger.info("Write finished: " + new String(data));
				} else {
					/* Read the characteristic through GATT */
					if (!_bluetoothGatt.readCharacteristic(characteristic)) {
						Logger.err("Read failed");
						return false;
					}
					Logger.info("Read finished");
				}

				/* Remove it from the queue once successfully sent */
				_gattTaskQueue.remove(request);

				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					/* Allow normal interruptions */
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
				}
			}

			return true;
		}

		private boolean parseReceived(BluetoothGattCharacteristic characteristic) {
			if (_incomingData == null) {
				Logger.info("New data incoming...");
				_incomingData = new ByteArrayOutputStream();
			}

			try {
				_incomingData.write(characteristic.getValue());
				if (hasTerminatingChar(characteristic.getValue())) {
					/* Retrieve the byte array */
					_dataBuffer = _incomingData.toByteArray();

					/* Notify our event handler/s */
					if (_eventHandler != null) {
						_eventHandler.onDataReceived(_deviceName, _deviceAddress, _dataBuffer);
					}

					/* Close our old data stream */
					_incomingData.close();
					_incomingData = null;
				}
			} catch (Exception e) {
				Logger.err("Exception occurred: " + e.getMessage());
			}

			return true;
		}

		private boolean hasTerminatingChar(byte[] dataArr) {
			for (byte b : dataArr) {
				if (b == ';') {
					return true;
				}
			}

			return false;
		}

		private class BluetoothGattCallbackHandler extends BluetoothGattCallback {
			@Override
			public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
				if (newState == BluetoothProfile.STATE_CONNECTED) {
					_isConnected = true;

					/* Start discovery of services as well */
					if (gatt.discoverServices() == false) {
						Logger.err("Could not discover services for connection");
					}

					if (gatt.getDevice() == null) {
						Logger.warn("Received NULL BluetoothDevice reference");
						notifyOnConnected(getDeviceName(), getDeviceAddress());
					} else {
						notifyOnConnected(gatt.getDevice().getName(), gatt.getDevice().getAddress());
					}
				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
					_isConnected = false;
					_gattTaskQueue.clear();

					/* Unset our previously known characteristics */
					_modelNumCharacteristic = null;
					_serialPortCharacteristic = null;
					_commandCharacteristic = null;

					if (gatt.getDevice() == null) {
						Logger.warn("Received NULL BluetoothDevice reference");
						notifyOnDisconnected("", "");
					} else {
						notifyOnDisconnected(gatt.getDevice().getName(), gatt.getDevice().getAddress());
					}
				}

				Logger.dbg("Bluetooth LE Conn State Changed: " + newState);

				/* Interrupt any waiting threads */
				if (_waitingThread != null) {
					_waitingThread.interrupt();
				}
				return;
			}

			@Override
			public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
				byte binaryData[] = characteristic.getValue();
				if (_targetCharacteristic == _modelNumCharacteristic) {
					/* Handle data pushed through the model number characteristic */
					String data = new String(binaryData);

					if (data.toUpperCase().startsWith(BLUNO_MODEL_NUM_RESP_PREFIX)) {
						gatt.setCharacteristicNotification(_targetCharacteristic, false);

						_targetCharacteristic = _commandCharacteristic;
						_gattTaskQueue.add(new BluetoothGattRequest(_targetCharacteristic, BLUNO_COMMAND_PASSWORD_STR));
						_gattTaskQueue.add(new BluetoothGattRequest(_targetCharacteristic, BLUNO_COMMAND_BAUD_RATE_STR));

						nudgeQueue();

						_targetCharacteristic = _serialPortCharacteristic;
						gatt.setCharacteristicNotification(_targetCharacteristic, true);
					} else {
						Logger.err("Unexpected data received: " + data);
					}
				} else if (_targetCharacteristic == _serialPortCharacteristic) {
					/* Handle data pushed through the serial port characteristic */
					if (_eventHandler != null) {
						_eventHandler.onDataReceived(_deviceName, _deviceAddress, binaryData);
					}
				}

				Logger.info("Read from " + characteristic.getUuid() + ": " + new String(binaryData));
				return;
			}

			@Override
			public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
				if (status == BluetoothGatt.GATT_SUCCESS) {

					Logger.info("Wrote at " + characteristic.getUuid() + " with " + new String(characteristic.getValue()));
					/* Write the next set of data once this is successful */
					nudgeQueue();
				}
			}

			@Override
			public void onServicesDiscovered(BluetoothGatt gatt, int status) {
				Logger.info("onServicesDiscovered() called");
				if (status != BluetoothGatt.GATT_SUCCESS) {
					Logger.err("Failed to discover services");
					return;
				}
				/* Clear our previous list of characteristics */
				_gattCharacteristics.clear();

				/* Upon successful discovery of services, check and cache the ones we can use */
				UUID uuid;
				for (BluetoothGattService gattService : gatt.getServices()) {
					//uuid = gattService.getUuid();
					/* Loop through the service's characteristics */
					for (BluetoothGattCharacteristic gattChar : gattService.getCharacteristics()) {
						uuid = gattChar.getUuid();
						Logger.info("Processing: " + uuid);

						if (uuid.equals(UUID_MODEL_NUM)) {
							_modelNumCharacteristic = gattChar;
						} else if (uuid.equals(UUID_SERIAL)) {
							_serialPortCharacteristic = gattChar;
						} else if (uuid.equals(UUID_COMMAND)) {
							_commandCharacteristic = gattChar;
						}

						_gattCharacteristics.add(gattChar);
					}
				}

				/* Check if we were able to get all the characteristics needed by DFRobot's Bluno */
				if ((_modelNumCharacteristic == null) || (_serialPortCharacteristic == null) ||
						(_commandCharacteristic == null) ) {
					Logger.err("Missing characteristic required by Bluno devices");
					return;
				}

				Logger.info("Get characteristics done");

				/* Read the model number off our Bluno device */
				_targetCharacteristic = _modelNumCharacteristic;
				gatt.setCharacteristicNotification(_targetCharacteristic, true);
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					Logger.warn("Read delay interrupted");
				}
				read(_targetCharacteristic);
				nudgeQueue();

				return;
			}

			@Override
			public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
				super.onCharacteristicChanged(gatt, characteristic);
				if (!parseReceived(characteristic)) {
					Logger.err("Failed to parse received data");
				}
				return;
			}
		}
	}
}
