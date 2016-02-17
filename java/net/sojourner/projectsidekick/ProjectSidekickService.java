package net.sojourner.projectsidekick;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.BTState;
import net.sojourner.projectsidekick.types.GuardedItem;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.ServiceState;
import net.sojourner.projectsidekick.types.Status;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.Toast;

public class ProjectSidekickService extends Service implements
		BluetoothEventHandler {
	public static final int MSG_START_SETUP		= 1;
	public static final int MSG_START_GUARD 	= 2;
	public static final int MSG_START_REPORT 	= 3;
	public static final int MSG_STOP 			= 4;
	public static final int MSG_START_DISCOVER 	= 5;
	public static final int MSG_STOP_DISCOVER 	= 6;
	public static final int MSG_SEND_REGISTER 	= 7;
	public static final int MSG_SET_AS_SIDEKICK = 8;
	public static final int MSG_SET_AS_MOBILE 	= 9;
	public static final int MSG_DISCONNECT 		= 10;
	public static final int MSG_CONNECT 		= 11;
	public static final int MSG_SEND_GET_LIST 	= 12;
	public static final int MSG_QUERY_STATE 	= 13;
	public static final int MSG_QUERY_BT_STATE 	= 14;
	public static final int MSG_UNREG_DEVICE 	= 15;
	
	public static final String ACTION_CONNECTED 	= "net.sojourner.projectsidekick.action.CONNECTED";
	public static final String ACTION_DATA_RECEIVE	= "net.sojourner.projectsidekick.action.DATA_RECEIVED";
	public static final String ACTION_REGISTERED 	= "net.sojourner.projectsidekick.action.REGISTERED";
	public static final String ACTION_UNREGISTERED 	= "net.sojourner.projectsidekick.action.UNREGISTERED";
	public static final String ACTION_UPDATE_LOST 	= "net.sojourner.projectsidekick.action.UPDATE_LOST";
	public static final String ACTION_UPDATE_FOUND 	= "net.sojourner.projectsidekick.action.UPDATE_FOUND";
	public static final String ACTION_DISCONNECTED 	= "net.sojourner.projectsidekick.action.DISCONNECTED";
	public static final String ACTION_LIST_RECEIVED = "net.sojourner.projectsidekick.action.LIST_RECEIVED";
	public static final String ACTION_REG_STARTED	= "net.sojourner.projectsidekick.action.REG_STARTED";
	public static final String ACTION_REG_FINISHED	= "net.sojourner.projectsidekick.action.REG_FINISHED";

	private static final long DEFAULT_SLEEP_TIME 		= 15000;
	private static final long DEFAULT_AWAIT_CONN_TIME	= 60000;
	private static final long MAX_AWAIT_CONN_TIME 		= 60000;
	private static final long DEFAULT_RW_INTERVAL 		= 10000;

	private static enum Role {
		UNKNOWN, SIDEKICK, MOBILE
	};

	private ProjectSidekickApp _app = null;
	private final Messenger _messenger = new Messenger(new MessageHandler());
	private ReportModeWakeReceiver _reportModeWakeAlarm = null;
	private IBluetoothBridge _bluetoothBridge = null;
	private GuardCyclicTask _cyclicGuardTask = null;
	private RegisterCyclicTask _cyclicRegisterTask = null;
	private HandleReceivedDataTask _recvDataTask = null;
	private ReentrantLock _tStateLock = new ReentrantLock();
	private Ringtone _alarmSound = null;
	/* Common Parameters */
	private Role _eRole = Role.UNKNOWN;
	private ServiceState _eState = ServiceState.UNKNOWN;
	/* REPORT Mode Parameters */
	private String _guardDeviceAddress = "";
	private long _lReportWindow = DEFAULT_RW_INTERVAL;
	/* GUARD Mode Parameters */
	private long _lGuardWindow = DEFAULT_RW_INTERVAL;
	private List<GuardedItem> _guardedItems = new ArrayList<GuardedItem>();

	@Override
	public IBinder onBind(Intent intent) {
		display("Service bound");
		return _messenger.getBinder();
	}

	@Override
	public void onCreate() {
		super.onCreate();

		/* Get the app */
		_app = this.getAppRef();

		/* Check if our bluetooth bridge is up */
		_bluetoothBridge = _app.getBluetoothBridge();
		if (_bluetoothBridge.isReady() == false) {
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(enableBtIntent);
		}

		/* Set this service as an event handler for Bluetooth events */
		_bluetoothBridge.setEventHandler(this);

		if (_receiver != null) {
			IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
			registerReceiver(_receiver, filter);
			Logger.info("[ProjectSidekickService] Receiver registered");
		}
		
		/* Restore our guarded items list */
		restoreGuardedItems();

		return;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null) {
			Logger.err("Invalid intent");
			return START_NOT_STICKY;
		}

		if (intent.getBooleanExtra("FROM_WAKE_ALARM", false)) {
			Status status;
			status = sendReportRequest(_guardDeviceAddress);
			if (status != Status.OK) {
				Logger.err("Failed to send report request");
				return super.onStartCommand(intent, flags, startId);
			}
		}

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		if (_receiver != null) {
			unregisterReceiver(_receiver);
			Logger.info("[ProjectSidekickService] Receiver unregistered");
		}

		super.onDestroy();
		return;
	}

	@Override
	public void onConnected(String name, String address) {
		/* Broadcast our received data for our receivers */
		/*
		 * TODO We may have to limit this at some point for security purposes
		 */
		Intent intent = new Intent(ACTION_CONNECTED);
		intent.putExtra("SENDER_NAME", name);
		intent.putExtra("SENDER_ADDR", address);
		sendBroadcast(intent);

		return;
	}

	@Override
	public void onDisconnected(String name, String address) {
		if (_cyclicRegisterTask != null) {
			_cyclicRegisterTask.interrupt();
		}
		/* Broadcast our received data for our receivers */
		/*
		 * TODO We may have to limit this at some point for security purposes
		 */
		Intent intent = new Intent(ACTION_DISCONNECTED);
		intent.putExtra("SENDER_NAME", name);
		intent.putExtra("SENDER_ADDR", address);
		sendBroadcast(intent);

		return;
	}

	@Override
	public void onDataReceived(String name, String address, byte[] data) {
		if (address == null) {
			Logger.warn("Received data has an invalid sender");
			return;
		}

		if (address.isEmpty() == true) {
			Logger.warn("Received data has an invalid sender");
			return;
		}

		if (data == null) {
			Logger.warn("Received data is invalid");
			return;
		}

		if (data.length == 0) {
			Logger.warn("Received data is empty");
			return;
		}

		if (data.length > 1024) {
			Logger.warn("Received data overloads our buffers");
			/*
			 * TODO This is also a case for DISCONNECTION since this is a known
			 * buffer overloading failure on some devices
			 */
			return;
		}

		/* Start a new AsyncTask to handle it */
		_recvDataTask = new HandleReceivedDataTask();
		_recvDataTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
				new ReceivedData(name, address, data));

		return;
	}

	/* *************** */
	/* Private Methods */
	/* *************** */
	private Status restoreGuardedItems() {
		_app = getAppRef();
		if (_app == null) {
			Logger.err("Could not obtain app reference");
			return Status.FAILED;
		}
		
		/* Clear our guarded items list */
		_guardedItems.clear();
		
		/* Restore the list from our records */
		List<KnownDevice> registeredDevices = _app.getRegisteredDevices();
		for (KnownDevice device : registeredDevices) {
			GuardedItem item = new GuardedItem(device.getName(), device.getAddress());
			item.setRegistered(true);
			_guardedItems.add(item);
		}
		
		return Status.OK;
	}
	
	private Status startSetupMode() {
		/* Make the device discoverable if role is SIDEKICK or UNSET */
		requestDiscoverability();
		if (getRole() != Role.MOBILE) {

			if (_cyclicRegisterTask == null) {
				_cyclicRegisterTask = new RegisterCyclicTask();
				_cyclicRegisterTask
						.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
		} else {
			setState(ServiceState.SETUP);
		}

		return Status.OK;
	}

	private Status startGuardMode() {
		if (_cyclicGuardTask == null) {
			_cyclicGuardTask = new GuardCyclicTask();
			_cyclicGuardTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}

		return Status.OK;
	}

	private Status startReportMode(String address, long offset) {
		if (getState() != ServiceState.REPORT) {
			Logger.err("Invalid state for starting REPORT mode");
			return Status.FAILED;
		}

		if (_reportModeWakeAlarm == null) {
			_reportModeWakeAlarm = new ReportModeWakeReceiver();
		}
		_reportModeWakeAlarm.cancelAlarm(this);
		_reportModeWakeAlarm.setAlarm(this, offset);

		return Status.OK;
	}

	private Status startDiscovery() {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();
		_bluetoothBridge.startDeviceDiscovery();
		return Status.OK;
	}

	private Status stopDiscovery() {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();
		_bluetoothBridge.stopDeviceDiscovery();
		return Status.OK;
	}

	private Status disconnect() {
		/* Set state to UNKNOWN */
		setState(ServiceState.UNKNOWN);

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		/* Halt ongoing device discovery operations */
		_bluetoothBridge.stopDeviceDiscovery();

		/* Disconnect all devices */
		_bluetoothBridge.destroy();

		/* Cancel ongoing alarms */
		if (_reportModeWakeAlarm != null) {
			_reportModeWakeAlarm.cancelAlarm(this);
			_reportModeWakeAlarm = null;
		}

		/* Cancel ongoing guard tasks */
		if (_cyclicGuardTask != null) {
			_cyclicGuardTask.cancel(true);
			_cyclicGuardTask = null;
		}

		/* Cancel ongoing register tasks */
		if (_cyclicRegisterTask != null) {
			_cyclicRegisterTask.cancel(true);
			_cyclicRegisterTask = null;
		}

		return Status.OK;
	}

	private Status stop() {
		/* Set state to UNKNOWN */
		setState(ServiceState.UNKNOWN);

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		/* Halt ongoing device discovery operations */
		_bluetoothBridge.stopDeviceDiscovery();

		/* Disconnect all devices */
		_bluetoothBridge.destroy();

		/* Cancel ongoing alarms */
		if (_reportModeWakeAlarm != null) {
			_reportModeWakeAlarm.cancelAlarm(this);
			_reportModeWakeAlarm = null;
		}

		/* Cancel ongoing guard tasks */
		if (_cyclicGuardTask != null) {
			_cyclicGuardTask.cancel(true);
			_cyclicGuardTask = null;
		}

		/* Cancel ongoing register tasks */
		if (_cyclicRegisterTask != null) {
			_cyclicRegisterTask.cancel(true);
			_cyclicRegisterTask = null;
		}

		return Status.OK;
	}

	private void requestDiscoverability() {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		/* Check if our bluetooth bridge is up */
		if (_bluetoothBridge.isReady() == false) {
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(enableBtIntent);
		}

		/* Show discoverabiltiy request */
		Intent makeDiscoverableIntent = new Intent(
				BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		makeDiscoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		makeDiscoverableIntent.putExtra(
				BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
		startActivity(makeDiscoverableIntent);

		return;
	}

	private Status sendReportRequest(String address) {
		if (getState() != ServiceState.REPORT) {
			Logger.err("Invalid state for REGISTER request");
			return Status.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		Status status = null;
		if (_bluetoothBridge.getState() == BTState.UNKNOWN) {
			status = _bluetoothBridge.initialize(this, false);
			if (status != Status.OK) {
				Logger.err("Failed to initialize Bluetooth Bridge");
				return Status.FAILED;
			}
		}

		_bluetoothBridge.setEventHandler(this);

		if (_bluetoothBridge.getState() != BTState.CONNECTED) {

			status = _bluetoothBridge.connectDeviceByAddress(address);
			if (status != Status.OK) {
				Logger.err("Failed to connect to device: " + address);
				return Status.FAILED;
			}
		}

		setState(ServiceState.REPORT);

		status = _bluetoothBridge.broadcast("REPORT".getBytes());
		if (status != Status.OK) {
			Logger.err("Failed to broadcast REPORT command to " + address);
			return Status.FAILED;
		}

		return Status.OK;
	}

	private Status connectToDevice(String address) {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		Status status = null;
		status = _bluetoothBridge.initialize(this, false);
		if (status != Status.OK) {
			Logger.err("Failed to initialize Bluetooth Bridge");
			return Status.FAILED;
		}

		_bluetoothBridge.setEventHandler(this);

		status = _bluetoothBridge.connectDeviceByAddress(address);
		if (status != Status.OK) {
			Logger.err("Failed to connect to device: " + address);
			return Status.FAILED;
		}

		return Status.OK;
	}

	private Status sendRegisterRequest(String address) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for REGISTER request");
			return Status.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		setState(ServiceState.REGISTERING);

		Status status = null;
		status = _bluetoothBridge.broadcast("REGISTER".getBytes());
		if (status != Status.OK) {
			Logger.err("Failed to broadcast REGISTER command to " + address);
			return Status.FAILED;
		}

		return Status.OK;
	}

	private Status sendGetListRequest(String address) {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		Status status = null;
		status = _bluetoothBridge.broadcast("LIST".getBytes());
		if (status != Status.OK) {
			Logger.err("Failed to broadcast LIST command to " + address);
			return Status.FAILED;
		}

		return Status.OK;
	}

	private Status sendUnregisterRequest(String address) {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		Status status = null;
		String msg = "DELETE " + address;
		status = _bluetoothBridge.broadcast(msg.getBytes());
		if (status != Status.OK) {
			Logger.err("Failed to broadcast DELETE command to " + address);
			return Status.FAILED;
		}

		return Status.OK;
	}

	private Status replyBluetoothState(Messenger replyTo) {
		Bundle data = new Bundle();
		data.putString("STATE", _bluetoothBridge.getState().toString());

		Message msg = Message.obtain(null, AppModeConfigBeaconActivity.MSG_RESP_BLUETOOTH_STATE, 0, 0);
		msg.setData(data);

		try {
			replyTo.send(msg);
		} catch (Exception e) {
			Logger.err("Exception occurred: " + e.getMessage());
		}
		return Status.OK;
	}

	private Status replyServiceState(Messenger replyTo) {
		Bundle data = new Bundle();
		data.putString("STATE", getState().toString());

		Message msg = Message.obtain(null, AppModeConfigBeaconActivity.MSG_RESP_SERVICE_STATE, 0, 0);
		msg.setData(data);

		try {
			replyTo.send(msg);
		} catch (Exception e) {
			Logger.err("Exception occurred: " + e.getMessage());
		}
		return Status.OK;
	}
	
	private Status sendListResponse(String address, String list) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for RLIST response");
			return Status.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		if (_bluetoothBridge.isReady() == false) {
			Logger.err("Cannot send RLIST response: Bluetooth Bridge not ready");
			return Status.FAILED;
		}
		
		String response = "RLIST ";
		response += list;
		
		Logger.info("Sending LIST response: " + response);
		
		Status status = null;
		status = _bluetoothBridge.broadcast(response.getBytes());
		if (status != Status.OK) {
			Logger.err("Failed to broadcast RLIST response to " + address);
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	private Status sendErrResponse(String address, String data) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for ERR response");
			return Status.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		if (_bluetoothBridge.isReady() == false) {
			Logger.err("Cannot send ERR response: Bluetooth Bridge not ready");
			return Status.FAILED;
		}
		
		String response = "ERR:";
		response += data;
		
		Status status = null;
		status = _bluetoothBridge.broadcast(response.getBytes());
		if (status != Status.OK) {
			Logger.err("Failed to broadcast ERR response to " + address);
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	private Status sendOkResponse(String address, String data) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for OK response");
			return Status.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		if (_bluetoothBridge.isReady() == false) {
			Logger.err("Cannot send OK response: Bluetooth Bridge not ready");
			return Status.FAILED;
		}
		
		String response = "OK ";
		response += data;
		
		Status status = null;
		status = _bluetoothBridge.broadcast(response.getBytes());
		if (status != Status.OK) {
			Logger.err("Failed to broadcast OK response to " + address);
			return Status.FAILED;
		}
		
		return Status.OK;
	}

	private Status sendRWOResponse(String address) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for RWO response");
			return Status.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		if (_bluetoothBridge.isReady() == false) {
			Logger.err("Cannot send RWO response: Bluetooth Bridge not ready");
			return Status.FAILED;
		}

		String response = "RWO ";
		response += Long.toString(_lGuardWindow);

		Status status = null;
		status = _bluetoothBridge.broadcast(response.getBytes());
		if (status != Status.OK) {
			Logger.err("Failed to broadcast RWO response to " + address);
			return Status.FAILED;
		}

		Logger.info("RWO Response sent: " + response);

//		try {
//			Thread.sleep(100);
//		} catch (InterruptedException e) {
//			/* Do Nothing */
//		}
//
//		status = _bluetoothBridge.destroy();
//		if (status != Status.OK) {
//			Logger.err("Failed to disconnect remote device: " + address);
//			return Status.FAILED;
//		}
		
		return Status.OK;
	}

	private Role getRole() {
		return _eRole;
	}

	private Status setRole(Role newRole) {
		if (getState() != ServiceState.UNKNOWN) {
			Logger.err("Cannot set role outside of UNKNOWN state");
			return Status.FAILED;
		}

		if (_eRole == Role.UNKNOWN) {
			_eRole = newRole;
		} else {
			Logger.warn("Role not changed (already set to " + _eRole.toString()
					+ ")");
			return Status.OK;
		}

		return Status.OK;
	}

	private synchronized ServiceState getState() {
		ServiceState state = ServiceState.UNKNOWN;
		_tStateLock.lock();
		state = _eState;
		_tStateLock.unlock();
		return state;
	}

	private synchronized void setState(ServiceState state) {
		_tStateLock.lock();
		_eState = state;
		_tStateLock.unlock();
		Logger.info("State set to " + _eState.toString());
		return;
	}
	
	private Ringtone getAlarmSound() {
		Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
		return RingtoneManager.getRingtone(this, ringtoneUri);
	}

	private List<GuardedItem> checkForLostItems() {
		List<GuardedItem> lostItems = new ArrayList<GuardedItem>();
		/*
		 * Check if each item in our Guarded Items list is also in the Found
		 * Items list. If not, flag that the device has been lost.
		 */
		for (GuardedItem item : _guardedItems) {
			boolean isFound = false;
			String address = item.getAddress();
			Logger.info("Looking for " + item.getAddress());
			for (GuardedItem foundItem : _foundDevices) {
				if (foundItem.addressMatches(address)) {
					isFound = true;
					break;
				}
			}

			if (!isFound) {
				Logger.warn("Device Lost: " + item.getName() + "/"
						+ item.getAddress());
				lostItems.add(item);
				
				if (_alarmSound == null) {
					_alarmSound = getAlarmSound();
				}
				
				if (!_alarmSound.isPlaying()) {
					_alarmSound.play();
				}
			}
		}

		return lostItems;
	}

	private ProjectSidekickApp getAppRef() {
		return (ProjectSidekickApp) getApplication();
	}

	private boolean startListen() {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		Status status;
		status = _bluetoothBridge.initialize(this, true);
		if (status != Status.OK) {
			Logger.err("Failed to initialize Bluetooth Bridge");
			return false;
		}

		_bluetoothBridge.setEventHandler(this);

		status = _bluetoothBridge.listen();
		if (status != Status.OK) {
			Logger.err("Failed to start listening on Bluetooth Bridge");
			return false;
		}

		return true;
	}

	private BTState getBluetoothState() {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		return _bluetoothBridge.getState();
	}

	private void display(String msg) {
		Toast.makeText(ProjectSidekickService.this, msg, Toast.LENGTH_SHORT)
				.show();
		Logger.info(msg);
		return;
	}

	/* ********************* */
	/* Private Inner Classes */
	/* ********************* */
	private static class ReceivedData {
		private String _senderName = "";
		private String _senderAddress = "";
		private byte[] _aData;

		public ReceivedData(String sender, String address, byte[] data) {
			_aData = new byte[data.length];

			/* Copy the data into a buffer */
			System.arraycopy(data, 0, _aData, 0, _aData.length);

			/* Copy the sender name */
			_senderName = sender;

			/* Copy the sender address */
			_senderAddress = address;//getFixedMacAddress(address);
			
			Logger.info("Data received: " + new String(_aData));

			return;
		}

		public byte[] getData() {
			return _aData;
		}

		public String getSenderAddress() {
			return _senderAddress;
		}

		public String getSenderName() {
			return _senderName;
		}
	}

	private class MessageHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			Status status = Status.FAILED;

			switch (msg.what) {
			case MSG_START_SETUP:
				status = startSetupMode();
				break;
			case MSG_START_GUARD:
				status = startGuardMode();
				break;
			case MSG_START_REPORT:
				status = startReportMode(_guardDeviceAddress, _lReportWindow);
				break;
			case MSG_STOP:
				status = stop();
				break;
			case MSG_START_DISCOVER:
				status = startDiscovery();
				break;
			case MSG_STOP_DISCOVER:
				status = stopDiscovery();
				break;
			case MSG_CONNECT:
				Bundle connData = msg.getData();
				if (connData == null) {
					break;
				}

				String connAddr = connData.getString("DEVICE_ADDR", "");

				status = connectToDevice(connAddr);
				break;
			case MSG_SEND_REGISTER:
				Bundle data = msg.getData();
				if (data == null) {
					break;
				}
				String regAddr = data.getString("DEVICE_ADDR", "");

				status = sendRegisterRequest(regAddr);
				break;
			case MSG_SEND_GET_LIST:
				Bundle listFromData = msg.getData();
				if (listFromData == null) {
					break;
				}
				String listFromAddr = listFromData.getString("DEVICE_ADDR", "");
				
				status = sendGetListRequest(listFromAddr);
				break;
				case MSG_SET_AS_SIDEKICK:
				status = setRole(Role.SIDEKICK);
				break;
			case MSG_SET_AS_MOBILE:
				status = setRole(Role.MOBILE);
				break;
			case MSG_DISCONNECT:
				status = disconnect();
				break;
			case MSG_QUERY_STATE:
				status = replyServiceState(msg.replyTo);
				break;
			case MSG_QUERY_BT_STATE:
				status = replyBluetoothState(msg.replyTo);
				break;
			case MSG_UNREG_DEVICE:
				Bundle unregAddrData = msg.getData();
				if (unregAddrData == null) {
					break;
				}
				String unregAddr = unregAddrData.getString("DEVICE_ADDR", "");
				status = sendUnregisterRequest(unregAddr);
				break;
			default:
				super.handleMessage(msg);
				break;
			}

			if (status != Status.OK) {
				/* TODO Do something */
			}

			return;
		}
	}

	private class RegisterCyclicTask extends AsyncTask<Void, String, Void> {
		private Thread 			_runThread = null;
		private ReentrantLock 	_runThreadLock = new ReentrantLock();

		@Override
		protected Void doInBackground(Void... params) {
			_runThreadLock.lock();
			_runThread = Thread.currentThread();
			_runThreadLock.unlock();

			BTState btState = BTState.UNKNOWN;

			/* Tell all receivers that registration has started */
			broadcastAction(ACTION_REG_STARTED);
			publishProgress("Device Registration has started");

			setState(ServiceState.SETUP);
			long lStartTime = System.currentTimeMillis();

			while (getState() == ServiceState.SETUP) {
				/*
				 * Start listening for connections only if we're not already
				 * listening, connecting, or connected
				 */
				btState = getBluetoothState();
				if ((btState != BTState.LISTENING)
						&& (btState != BTState.CONNECTED)
						&& (btState != BTState.CONNECTING)) {

					startListen();
				}

				publishProgress("Waiting for new connections...");
				try {
					Thread.sleep(DEFAULT_AWAIT_CONN_TIME);
				} catch (InterruptedException e) {
					Logger.err("Interrupted");
					/* Handle interrupts due to termination */
					if (getState() != ServiceState.SETUP) {
						break;
					}

					/* Normal interruptions should proceed as usual */
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
					_cyclicRegisterTask = null;
					break;
				}

				long lCurrentTime = System.currentTimeMillis();
				if ((lCurrentTime - lStartTime) > MAX_AWAIT_CONN_TIME) {
					publishProgress("Connection window exceeded.");
					break;
				}
			}

			_runThreadLock.lock();
			_runThread = null;
			_runThreadLock.unlock();

			_cyclicRegisterTask = null;

			/* Tell all receivers that registration has ended */
			broadcastAction(ACTION_REG_FINISHED);
			publishProgress("Device Registration has ended");

			return null;
		}

		public void interrupt() {
			_runThreadLock.lock();
			if (_runThread != null) {
				_runThread.interrupt();
			}
			_runThreadLock.unlock();
			return;
		}

		private void broadcastAction(String action) {
			Intent intent = new Intent(action);
			sendBroadcast(intent);
			return;
		}

		@Override
		protected void onProgressUpdate(String... values) {
			if (values[0] == null) {
				return;
			}

			display(values[0]);

			super.onProgressUpdate(values);
			return;
		}
	}

	private class GuardCyclicTask extends AsyncTask<Void, String, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			publishProgress("Guard Task Started.");

			setState(ServiceState.GUARD);

			/* Periodically check guarded items */
			while (getState() == ServiceState.GUARD) {
				/* Cancel device discovery if ongoing */
				_bluetoothBridge.stopDeviceDiscovery();

				/* Clear previously found devices */
				_foundDevices.clear();

				/* Re-start device discovery if ongoing */
				_bluetoothBridge.startDeviceDiscovery();

				publishProgress("Sleeping for "
						+ Long.toString(DEFAULT_SLEEP_TIME) + " ms");
				try {
					Thread.sleep(DEFAULT_SLEEP_TIME);
				} catch (InterruptedException e) {
					/* Normal interruptions are ok */
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
					_cyclicGuardTask = null;
					break;
				}

				publishProgress("Checking guarded items...");
				List<GuardedItem> lostItems = checkForLostItems();

				/* Display lost items via Toast */
				if (lostItems != null) {
					if (lostItems.size() > 0) {
						for (GuardedItem item : lostItems) {
							publishProgress("Item Lost: " + item.getName()
									+ "/" + item.getAddress());

							/* Broadcast our received data for our receivers */
							/*
							 * TODO We may have to limit this at some point for
							 * security purposes
							 */
							Intent intent = new Intent(ACTION_UPDATE_LOST);
							intent.putExtra("NAME", item.getName());
							intent.putExtra("ADDRESS", item.getAddress());
							intent.putExtra("LOST_STATUS", true);
							sendBroadcast(intent);
						}
					}
				} else {
					if (_alarmSound == null) {
						_alarmSound = getAlarmSound();
					}
					
					_alarmSound.stop();
				}
			}
			
			if (_alarmSound == null) {
				_alarmSound = getAlarmSound();
			}
			_alarmSound.stop();

			_cyclicGuardTask = null;
			publishProgress("Guard Task Stopped.");

			return null;
		}

		@Override
		protected void onProgressUpdate(String... values) {
			if (values[0] == null) {
				return;
			}

			display(values[0]);

			super.onProgressUpdate(values);
			return;
		}
	}

	private class HandleReceivedDataTask extends
			AsyncTask<ReceivedData, String, Void> {

		@Override
		protected Void doInBackground(ReceivedData... params) {
			// publishProgress("Handle Received Data Task Started");

			ReceivedData recvData = params[0];

			/* SAN-check that the received data is not null */
			if (recvData == null) {
				Logger.err("Received data is NULL");
				return null;
			}

			String sender = recvData.getSenderName();
			String address = recvData.getSenderAddress();
			String request = new String(recvData.getData());
			boolean suppressDisplay = false;
			
			/* Check request contents */
			if (request.contains("REGISTER")) {
				handleRegisterRequest(sender, address, request);
			} else if (request.contains("REPORT")) {
				handleReportRequest(sender, address, request);
			} else if (request.contains("RLIST")) {
				handleGetListResponse(sender, address, request);
				suppressDisplay = true;
			} else if (request.contains("LIST")) {
				handleGetListRequest(sender, address, request);
			} else if (request.contains("DELETE")) {
				handleDeleteRequest(sender, address, request);
			} else if (request.contains("RWO")) {
				handleRWOResponse(sender, address, request);
			} else {
				Logger.warn("Unknown request: " + request);
			}

			/* Broadcast our received data for our receivers */
			if (!suppressDisplay) {
				Intent intent = new Intent(ACTION_DATA_RECEIVE);
				intent.putExtra("SENDER_NAME", sender);
				intent.putExtra("SENDER_ADDR", address);
				intent.putExtra("SENDER_DATA", request);
				sendBroadcast(intent);
			}

			// publishProgress("Handle Received Data Task Finished");
			return null;
		}

		@Override
		protected void onProgressUpdate(String... values) {
			if (values[0] == null) {
				return;
			}

			display(values[0]);

			super.onProgressUpdate(values);
			return;
		}

		private void handleRegisterRequest(String name, String addr, String data) {
			_app = getAppRef();

			/* Handle REGISTER requests from the Sidekick */
			/* Check if the guarded item is already in our list */
			for (GuardedItem item : _guardedItems) {
				if (item.addressMatches(addr)) {
					Logger.warn("Device already registered: " + addr);
					
					/* Set the registered flag for this item */
					item.setRegistered(true);

					/* Broadcast an RWO response */
					sendRWOResponse(addr);

					/* Update its report window */
					item.updateReportWindow(_lGuardWindow);

					/* Broadcast our received data for our receivers */
					Intent intent = new Intent(ACTION_REGISTERED);
					intent.putExtra("NAME", name);
					intent.putExtra("ADDRESS", addr);
					sendBroadcast(intent);

					return;
				}
			}

			/* Broadcast an RWO response */
			sendRWOResponse(addr);

			/* Create a new GuardedItem */
			GuardedItem newItem = new GuardedItem(name, addr);
			newItem.setRegistered(true);
			newItem.updateReportWindow(_lGuardWindow);

			/* Add the guarded item to our list */
			_guardedItems.add(newItem);
			
			/* Add it to our registered device list as well */
			_app.addRegisteredDevice(newItem);
			_app.saveRegisteredDevices();

			Logger.info("Added item to GUARD list: " + newItem.toString());

			/* Broadcast our received data for our receivers */
			Intent intent = new Intent(ACTION_REGISTERED);
			intent.putExtra("NAME", name);
			intent.putExtra("ADDRESS", addr);
			sendBroadcast(intent);

			return;
		}
		
		private void handleGetListRequest(String name, String addr, String data) {
			if (getState() != ServiceState.SETUP) {
				Logger.warn("Invalid state for LIST requests");
				return;
			}
			
			/* Handle LIST request */
			/* Build the list */
			String listStr = "";
			Iterator<GuardedItem> iter = _guardedItems.iterator();
			while (iter.hasNext()) {
				GuardedItem item = iter.next();
				
				String itemStr = "";
				itemStr += item.getName() + "|";
				itemStr += item.getAddress() + "|";
				itemStr += item.isRegistered() ? "Guarded" : "Not Guarded";
				
				if (iter.hasNext()) { 
					itemStr += ",";
				}
				listStr += itemStr;
			}
			
			/* Send the RLIST response */
			sendListResponse(addr, listStr);
			
			return;
		}

		private void handleDeleteRequest(String name, String addr, String data) {
			if (getState() != ServiceState.SETUP) {
				Logger.warn("Invalid state for DELETE requests");
				return;
			}
			
			/* Handle DELETE requests */
			/* Process the request string for the address/es */
			String dataPart[] = data.split(" ");
			if (dataPart.length != 2) {
				Logger.warn("Malformed delete request: " + data);
				return;
			}
			
			String addresses[] = dataPart[1].split(",");
			if (addresses.length <= 0) {
				Logger.warn("Malformed address list: " + dataPart[1]);
				return;
			}
			
			/* For each address in the deletion request, check if there is
			 * 	a match in our guarded items list which has previously
			 * 	been marked as registered. If so, unregister (delete) it. */
			String failed = "";
			for (String address : addresses) {
				GuardedItem targetItem = null;
				for (GuardedItem item : _guardedItems) {
					if (item.addressMatches(address)) {
						item.setRegistered(false);
						targetItem = item;

						/* Notify any interested activities */
						Intent intent = new Intent(ACTION_UNREGISTERED);
						intent.putExtra("ADDRESS", address);
						sendBroadcast(intent);
						break;
					}
				}
				
				/* If this resulted in no devices being unregistered, then
				 * 	list this down as a potential error */
				if (targetItem == null) {
					/* Add a comma if this isn't the first failed address/
					 *  in the list */
					if (!failed.equals("")) {
						failed += ",";
					}
					failed += address;
				}
			}
			
			if (!failed.equals("")) {
				sendErrResponse(addr, "Failed-To-Delete=" + failed);
			} else {
				/* Broadcast an OK response */
				sendOkResponse(addr, "Deleted");
			}
			
			return;
		}

		private void handleReportRequest(String name, String addr, String data) {
			if (getState() != ServiceState.GUARD) {
				Logger.warn("Invalid state for REPORT requests");
				return;
			}
			/* Handle REPORT requests */
			GuardedItem targetItem = null;
			/* Get the item from our GuardedItems list */
			for (GuardedItem item : _guardedItems) {
				if (item.addressMatches(addr)) {
					targetItem = item;
					break;
				}
			}

			/* Check if not found */
			if (targetItem == null) {
				Logger.warn("Reporting item not found: " + name + "/" + addr);
				return;
			}

			/* Broadcast an RWO response */
			sendRWOResponse(addr);

			/* Update the known report window for the guarded item */
			targetItem.updateReportWindow(_lGuardWindow);

			return;
		}

		private void handleRWOResponse(String name, String addr, String data) {
			ServiceState state = getState();
			if ((state != ServiceState.REPORT)
					&& (state != ServiceState.REGISTERING)) {
				Logger.warn("Invalid state for RWO responses");
				return;
			}

			/* Handle RW (Report Window) offset responses */
			_guardDeviceAddress = addr;
			_lReportWindow = extractRWParam(data);

			/* Transition to reporting state */
			if (state == ServiceState.REGISTERING) {
				setState(ServiceState.REPORT);
			}

			/* Re-adjust our REPORT mode alarm */
			// startReportMode(_guardDeviceAddress, _lReportWindow);

			return;
		}

		private long extractRWParam(String request) {
			String reqPart[] = request.split(" ");
			if (reqPart.length != 2) {
				Logger.warn("Malformed RWO request: " + request);
				return DEFAULT_RW_INTERVAL;
			}

			Long lRWParam = DEFAULT_RW_INTERVAL;
			try {
				lRWParam = Long.parseLong(reqPart[1]);
			} catch (Exception e) {
				Logger.warn("Could not parse RW from request: " + request);
				lRWParam = DEFAULT_RW_INTERVAL;
			}

			return lRWParam;
		}

		private void handleGetListResponse(String name, String addr, String data) {
			/* Handle the GET LIST response */
			String dataPart[] = data.split(" ", 2);
			if (dataPart.length != 2) {
				Logger.warn("Malformed response: " + data);
				return;
			}
			/* TODO Improve this black magic regex */
			String devices[] = dataPart[1].split(",");
			
			/* Notify any interested activities */
			Intent intent = new Intent(ACTION_LIST_RECEIVED);
			intent.putExtra("DEVICES", devices);
			sendBroadcast(intent);

			return;
		}
	}

	private List<GuardedItem> _foundDevices = new ArrayList<GuardedItem>();

	private final BroadcastReceiver _receiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				short uRssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short)0);
				String address = device.getAddress();
				String name = device.getName();

				for (GuardedItem item : _foundDevices) {
					if (item.addressMatches(address)) {
						item.setIsLost(false);
						item.setRssi(uRssi);

						Logger.info("Updated found device: " + item.toString());

						/* Broadcast our received data for our receivers */
						Intent foundIntent = new Intent(ACTION_UPDATE_FOUND);
						foundIntent.putExtra("NAME", item.getName());
						foundIntent.putExtra("ADDRESS", item.getAddress());
						foundIntent.putExtra("LOST_STATUS", false);
						foundIntent.putExtra("RSSI", item.getRssi());
						sendBroadcast(foundIntent);
						return;
					}
				}
				GuardedItem newItem = new GuardedItem(name, address);
				newItem.setIsLost(false);
				newItem.setRssi(uRssi);
				_foundDevices.add(newItem);

				Logger.info("Added found device: " + newItem.toString());

				/* Broadcast our received data for our receivers */
				Intent foundIntent = new Intent(ACTION_UPDATE_FOUND);
				foundIntent.putExtra("NAME", newItem.getName());
				foundIntent.putExtra("ADDRESS", newItem.getAddress());
				foundIntent.putExtra("LOST_STATUS", false);
				foundIntent.putExtra("RSSI", newItem.getRssi());
				sendBroadcast(foundIntent);

				return;
			}
		}
	};
}
