package net.sojourner.projectsidekick;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.BTState;
import net.sojourner.projectsidekick.types.GuardedItem;
import net.sojourner.projectsidekick.types.KnownDevice;
import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.types.ServiceState;
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
import android.widget.Toast;

public class ProjectSidekickService extends Service implements BluetoothEventHandler {
	public static final int MSG_START_SETUP			= 1;
	public static final int MSG_START_GUARD 		= 2;
	public static final int MSG_START_REPORT 		= 3;
	public static final int MSG_STOP 				= 4;
	public static final int MSG_START_DISCOVER 		= 5;
	public static final int MSG_STOP_DISCOVER 		= 6;
	public static final int MSG_SEND_REGISTER 		= 7;
	public static final int MSG_SET_AS_SIDEKICK 	= 8;
	public static final int MSG_SET_AS_MOBILE 		= 9;
	public static final int MSG_DISCONNECT 			= 10;
	public static final int MSG_CONNECT 			= 11;
	public static final int MSG_SEND_GET_LIST 		= 12;
	public static final int MSG_QUERY_STATE 		= 13;
	public static final int MSG_QUERY_BT_STATE 		= 14;
	public static final int MSG_UNREG_DEVICE 		= 15;
	public static final int MSG_SET_CHECK_MODE 		= 16;
	public static final int MSG_SET_SLEEP_TIME 		= 17;
	public static final int MSG_SET_ALARM_TOGGLE	= 18;

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
	public static final String ACTION_REP_STARTED	= "net.sojourner.projectsidekick.action.REP_STARTED";
	public static final String ACTION_REP_FINISHED	= "net.sojourner.projectsidekick.action.REP_FINISHED";

	private static final String RXX_TERM			= ";";
	private static final String RXX_DELIM			= ",";
	private static final String RXX_ACK				= "1" + RXX_TERM;
	private static final String	RXX_EOT				= Character.toString((char)(0x4));

	private static final String REQ_PREF_REGISTER 		= "XREG";
	private static final String REQ_PREF_GET_LIST 		= "XLST";
	private static final String REQ_PREF_SET_NAME 		= "XNME";
	private static final String REQ_PREF_DELETE 		= "XDEL";
	private static final String REQ_PREF_GUARD_THEF_SET = "XATS";	// TODO This is XGSL in design. Bit inconsistent
	private static final String REQ_PREF_GUARD_THEF 	= "XATM";
	private static final String REQ_PREF_REPORT_THEF 	= "XATO";
	private static final String REQ_PREF_GUARD_LOSS_SET = "XALS";
	private static final String REQ_PREF_GUARD_LOSS 	= "XALM";
	private static final String REQ_PREF_REPORT_LOSS	= "XALO";
	private static final String REQ_PREF_DISCONNECT		= "XDSC";

	private static final String RES_PREF_REGISTER		= "RREG";	// TODO No RREG in design?
	private static final String RES_PREF_GET_LIST 		= "RLST";
	private static final String RES_PREF_SET_NAME 		= "RNME";
	private static final String RES_PREF_DELETE 		= "RDEL";	// TODO No RDEL in design?
	private static final String RES_PREF_GUARD_THEF_SET = "RATS";	// TODO This is RGSL in design. Bit inconsistent
	private static final String RES_PREF_GUARD_THEF 	= "RATM";
	private static final String RES_PREF_REPORT_THEF 	= "RATO";
	private static final String RES_PREF_GUARD_LOSS_SET = "RALS";
	private static final String RES_PREF_GUARD_LOSS 	= "RALM";
	private static final String RES_PREF_REPORT_LOSS	= "RALO";

	private static final long DEFAULT_SLEEP_TIME 				= 15000;
	private static final long DEFAULT_AWAIT_SETUP_CONN_TIME		= 180000;
	private static final long DEFAULT_AWAIT_REPORT_CONN_TIME	= 10000;
	private static final long DEFAULT_AWAIT_REPORT_DISCONN_TIME = 5000;
	private static final long MAX_AWAIT_CONN_TIME 				= 180000;
	private static final long DEFAULT_RW_INTERVAL 				= 10000;

	private static final int  MAX_DEVICE_NAME_LEN 				= 20;
	private static final int IDX_DVC_ID 						= 0;
	private static final int IDX_DVC_STAT 						= 2;
	private static final int IDX_DVC_NAME 						= 3;
	private static final int IDX_DVC_ADDR 						= 23;
	private static final int IDX_DVC_LAST 						= 35;
	private static final int IDX_PAYLOAD_DATA_OFFS				= 6;
	private static final int MAX_DEVICE_DATA_LEN 				= 36;
	private static final int  MAX_DEVICE_ADDR_LEN 				= 12;

	private enum Role {
		UNKNOWN, SIDEKICK, MOBILE
	}

	private enum GuardMode {
		SDP, BT_CONNECT, SDP_WITH_BT_CONNECT
	}

	private ProjectSidekickApp _app = null;
	private final Messenger _messenger = new Messenger(new MessageHandler());
	private ReportModeWakeReceiver _reportModeWakeAlarm = null;
	private IBluetoothBridge _bluetoothBridge = null;
	private GuardCyclicTask _cyclicGuardTask = null;
	private ReportCyclicTask _cyclicReportTask = null;
	private RegisterCyclicTask _cyclicRegisterTask = null;
	private ReentrantLock _tStateLock = new ReentrantLock();
	private Ringtone _alarmSound = null;
	/* Common Parameters */
	private Role _eRole = Role.UNKNOWN;
	private ServiceState _eState = ServiceState.UNKNOWN;
	private boolean _bAlarmsEnabled = false;
	/* REPORT Mode Parameters */
	private String _guardDeviceAddress = "";
	private long _lReportWindow = DEFAULT_RW_INTERVAL;
	/* GUARD Mode Parameters */
	private long _lSleepInterval = DEFAULT_SLEEP_TIME;
	private GuardMode _guardMode = GuardMode.BT_CONNECT;
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
		if (!_bluetoothBridge.isReady()) {
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
			PSStatus status;
			status = sendReportRequest(_guardDeviceAddress);
			if (status != PSStatus.OK) {
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
		if (_cyclicReportTask != null) {
			_cyclicReportTask.interruptForConnection(name, address);
		}

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
		if (_cyclicReportTask != null) {
			_cyclicReportTask.interruptForDisconnection();
		}

		if (_cyclicRegisterTask != null) {
			_cyclicRegisterTask.interruptForDisconnection();
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

		if (address.isEmpty()) {
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
		new HandleReceivedDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
				new ReceivedData(name, address, data));

		return;
	}

	/* *************** */
	/* Private Methods */
	/* *************** */
	private PSStatus restoreGuardedItems() {
		_app = getAppRef();
		if (_app == null) {
			Logger.err("Could not obtain app reference");
			return PSStatus.FAILED;
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
		
		return PSStatus.OK;
	}
	
	private PSStatus startSetupMode() {
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

		return PSStatus.OK;
	}

	private PSStatus startGuardMode() {
		if (_cyclicGuardTask == null) {
			_cyclicGuardTask = new GuardCyclicTask();
			_cyclicGuardTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}

		return PSStatus.OK;
	}

	private PSStatus startReportMode() {
		if (_cyclicReportTask == null) {
			_cyclicReportTask = new ReportCyclicTask();
			_cyclicReportTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}

		return PSStatus.OK;
	}

	private PSStatus startDiscovery() {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();
		_bluetoothBridge.initialize(this, false);
		_bluetoothBridge.startDeviceDiscovery();
		return PSStatus.OK;
	}

	private PSStatus stopDiscovery() {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();
		_bluetoothBridge.stopDeviceDiscovery();
		return PSStatus.OK;
	}

	private PSStatus disconnect() {
		/* Set state to UNKNOWN */
		setState(ServiceState.UNKNOWN);

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		/* Halt ongoing device discovery operations */
		_bluetoothBridge.stopDeviceDiscovery();

		/* Broadcast a DISCONNECT signal */
		_bluetoothBridge.broadcast("DISCONNECT".getBytes());

		/* Sleep for a bit before performing the closing the connections */
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			/* Interruptions are fine */
		}

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

		/* Cancel ongoing report tasks */
		if (_cyclicReportTask != null) {
			_cyclicReportTask.cancel(true);
			_cyclicReportTask = null;
		}

		/* Cancel ongoing register tasks */
		if (_cyclicRegisterTask != null) {
			_cyclicRegisterTask.cancel(true);
			_cyclicRegisterTask = null;
		}

		return PSStatus.OK;
	}

	private PSStatus stop() {
		/* Set state to UNKNOWN */
		setState(ServiceState.UNKNOWN);

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		/* Halt ongoing device discovery operations */
		_bluetoothBridge.stopDeviceDiscovery();

		/* Broadcast a DISCONNECT signal */
		_bluetoothBridge.broadcast("DISCONNECT".getBytes());

		/* Sleep for a bit before performing the closing the connections */
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			/* Interruptions are fine */
		}

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

		/* Cancel ongoing report tasks */
		if (_cyclicReportTask != null) {
			_cyclicReportTask.cancel(true);
			_cyclicReportTask = null;
		}

		/* Cancel ongoing register tasks */
		if (_cyclicRegisterTask != null) {
			_cyclicRegisterTask.cancel(true);
			_cyclicRegisterTask = null;
		}

		return PSStatus.OK;
	}

	private void requestDiscoverability() {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		/* Check if our bluetooth bridge is up */
		if (!_bluetoothBridge.isReady()) {
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

	private PSStatus sendReportRequest(String address) {
		if (getState() != ServiceState.REPORT) {
			Logger.err("Invalid state for REPORT request");
			return PSStatus.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		PSStatus status;
		if (_bluetoothBridge.getState() == BTState.UNKNOWN) {
			status = _bluetoothBridge.initialize(this, false);
			if (status != PSStatus.OK) {
				Logger.err("Failed to initialize Bluetooth Bridge");
				return PSStatus.FAILED;
			}
		}

		_bluetoothBridge.setEventHandler(this);

		if (_bluetoothBridge.getState() != BTState.CONNECTED) {
			status = _bluetoothBridge.connectDeviceByAddress(address);
			if (status != PSStatus.OK) {
				Logger.err("Failed to connect to device: " + address);
				return PSStatus.FAILED;
			}
		}

		setState(ServiceState.REPORT);

		status = _bluetoothBridge.broadcast("REPORT".getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast REPORT command to " + address);
			return PSStatus.FAILED;
		}

		return PSStatus.OK;
	}

	private PSStatus connectToDevice(String address) {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		PSStatus status;
		status = _bluetoothBridge.initialize(this, false);
		if (status != PSStatus.OK) {
			Logger.err("Failed to initialize Bluetooth Bridge");
			return PSStatus.FAILED;
		}

		_bluetoothBridge.setEventHandler(this);

		status = _bluetoothBridge.connectDeviceByAddress(address);
		if (status != PSStatus.OK) {
			Logger.err("Failed to connect to device: " + address);
			return PSStatus.FAILED;
		}

		return PSStatus.OK;
	}

	private PSStatus sendRegisterRequest(String remoteAddr) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for REGISTER request");
			return PSStatus.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		/* Fix the device name at exactly MAX_DEVICE_NAME_LEN chars */
		String dvcName = compressDeviceName(_bluetoothBridge.getLocalName());

		/* Sent addresses should not contain separators */
		String dvcAddr = compressDeviceAddress(_bluetoothBridge.getLocalAddress());

		/* Create the request string */
		String request = dvcName + RXX_DELIM + dvcAddr + RXX_TERM;

		setState(ServiceState.REGISTERING);

		PSStatus status;
		status = performBroadcast(_bluetoothBridge, REQ_PREF_REGISTER.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast REGISTER command header to " + remoteAddr);
			return PSStatus.FAILED;
		}

		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast REGISTER command header to " + remoteAddr);
			return PSStatus.FAILED;
		}

		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast REGISTER command data to " + remoteAddr);
			return PSStatus.FAILED;
		}

		return PSStatus.OK;
	}

	private PSStatus sendGetListRequest(String address) {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		String request = RXX_ACK;

		PSStatus status;
		status = performBroadcast(_bluetoothBridge, REQ_PREF_GET_LIST.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast LIST command header to " + address);
			return PSStatus.FAILED;
		}

		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast LIST command data to " + address);
			return PSStatus.FAILED;
		}

		return PSStatus.OK;
	}

	private PSStatus sendUnregisterRequest(int deviceId) {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		PSStatus status;
		//String msg = "DELETE " + address;

		status = performBroadcast(_bluetoothBridge, REQ_PREF_DELETE.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast DELETE command header");
			return PSStatus.FAILED;
		}

		String request = deviceId + ";";
		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast DELETE command data");
			return PSStatus.FAILED;
		}

		return PSStatus.OK;
	}

	private PSStatus replyBluetoothState(Messenger replyTo) {
		Bundle data = new Bundle();
		data.putString("STATE", _bluetoothBridge.getState().toString());

		Message msg = Message.obtain(null, AppModeConfigBeaconActivity.MSG_RESP_BLUETOOTH_STATE, 0, 0);
		msg.setData(data);

		try {
			replyTo.send(msg);
		} catch (Exception e) {
			Logger.err("Exception occurred: " + e.getMessage());
		}
		return PSStatus.OK;
	}

	private PSStatus replyServiceState(Messenger replyTo) {
		Bundle data = new Bundle();
		data.putString("STATE", getState().toString());

		Message msg = Message.obtain(null, AppModeConfigBeaconActivity.MSG_RESP_SERVICE_STATE, 0, 0);
		msg.setData(data);

		try {
			replyTo.send(msg);
		} catch (Exception e) {
			Logger.err("Exception occurred: " + e.getMessage());
		}
		return PSStatus.OK;
	}

	private PSStatus sendListResponse(String address, byte[] list) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for RLIST response");
			return PSStatus.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		if (!_bluetoothBridge.isReady()) {
			Logger.err("Cannot send RLIST response: Bluetooth Bridge not ready");
			return PSStatus.FAILED;
		}

		/* Prepare the response buffer */
		int iPrefixLen = RES_PREF_GET_LIST.getBytes().length;
		int iRespBufLen = iPrefixLen + list.length;
		byte response[] = new byte[iRespBufLen];

		/* Copy the bytes into the response buffer */
		System.arraycopy(RES_PREF_GET_LIST.getBytes(), 0, response, 0, iPrefixLen);
		System.arraycopy(list, 0, response, iPrefixLen, list.length);

		Logger.info("Sending LIST response: " + RES_PREF_GET_LIST + " <bytes>{" +
				decodeBinaryDeviceList(response, IDX_PAYLOAD_DATA_OFFS) + "}");

		PSStatus status;
		status = _bluetoothBridge.broadcast(response);
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast RLIST response to " + address);
			return PSStatus.FAILED;
		}

		return PSStatus.OK;
	}
	
	private PSStatus sendListResponse(String address, String list) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for RLIST response");
			return PSStatus.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		if (!_bluetoothBridge.isReady()) {
			Logger.err("Cannot send RLIST response: Bluetooth Bridge not ready");
			return PSStatus.FAILED;
		}
		
		String response = RES_PREF_GET_LIST;
		response += list;

		Logger.info("Sending LIST response: " + response);
		
		PSStatus status;
		status = _bluetoothBridge.broadcast(response.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast RLIST response to " + address);
			return PSStatus.FAILED;
		}
		
		return PSStatus.OK;
	}
	
	private PSStatus sendErrResponse(String address, String data) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for ERR response");
			return PSStatus.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		if (!_bluetoothBridge.isReady()) {
			Logger.err("Cannot send ERR response: Bluetooth Bridge not ready");
			return PSStatus.FAILED;
		}
		
		String response = "ERR:";
		response += data;
		
		PSStatus status;
		status = _bluetoothBridge.broadcast(response.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast ERR response to " + address);
			return PSStatus.FAILED;
		}
		
		return PSStatus.OK;
	}
	
	private PSStatus sendOkResponse(String address, String data) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for OK response");
			return PSStatus.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		if (!_bluetoothBridge.isReady()) {
			Logger.err("Cannot send OK response: Bluetooth Bridge not ready");
			return PSStatus.FAILED;
		}
		
		String response = "OK ";
		response += data;
		
		PSStatus status;
		status = _bluetoothBridge.broadcast(response.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast OK response to " + address);
			return PSStatus.FAILED;
		}
		
		return PSStatus.OK;
	}

	private PSStatus sendAckResponse(String prefix, String address) {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		if (!_bluetoothBridge.isReady()) {
			Logger.err("Cannot send ACK response: Bluetooth Bridge not ready");
			return PSStatus.FAILED;
		}

		String response = prefix;
		response += RXX_ACK;

		PSStatus status;
		status = _bluetoothBridge.broadcast(response.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast ACK response to " + address);
			return PSStatus.FAILED;
		}

		Logger.info("ACK Response sent: " + response);

		return PSStatus.OK;
	}

	private PSStatus sendRWOResponse(String address) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for RWO response");
			return PSStatus.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		if (!_bluetoothBridge.isReady()) {
			Logger.err("Cannot send RWO response: Bluetooth Bridge not ready");
			return PSStatus.FAILED;
		}

		String response = "RWO ";
		response += Long.toString(_lGuardWindow);

		PSStatus status;
		status = _bluetoothBridge.broadcast(response.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast RWO response to " + address);
			return PSStatus.FAILED;
		}

		Logger.info("RWO Response sent: " + response);
		
		return PSStatus.OK;
	}

	private Role getRole() {
		return _eRole;
	}

	private PSStatus setRole(Role newRole) {
		if (getState() != ServiceState.UNKNOWN) {
			Logger.err("Cannot set role outside of UNKNOWN state");
			return PSStatus.FAILED;
		}

		if (_eRole == Role.UNKNOWN) {
			_eRole = newRole;
		} else {
			Logger.warn("Role not changed (already set to " + _eRole.toString()
					+ ")");
			return PSStatus.OK;
		}

		return PSStatus.OK;
	}

	private PSStatus setDeviceCheckingMode(String checkMode) {
		GuardMode mode = GuardMode.valueOf(checkMode);
		if (mode == null) {
			Logger.err("Invalid guard mode param: " + checkMode);
			return PSStatus.FAILED;
		}
		_guardMode = mode;

		return PSStatus.OK;
	}

	private PSStatus setSleepTime(long lSleepTime) {
		_lSleepInterval = lSleepTime;
		return PSStatus.OK;
	}

	private PSStatus setAlarmsEnabled(boolean bEnabled) {
		_bAlarmsEnabled = bEnabled;
		return PSStatus.OK;
	}

	private synchronized ServiceState getState() {
		ServiceState state;
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

	private PSStatus startAlarm() {
		if (!_bAlarmsEnabled) {
			return PSStatus.FAILED;
		}

		if (_alarmSound == null) {
			_alarmSound = getAlarmSound();
		}

		if (!_alarmSound.isPlaying()) {
			_alarmSound.play();
		}

		return PSStatus.OK;
	}

	private PSStatus stopAlarm() {
		if (!_bAlarmsEnabled) {
			return PSStatus.FAILED;
		}

		if (_alarmSound == null) {
			_alarmSound = getAlarmSound();
		}
		_alarmSound.stop();

		return PSStatus.OK;
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

				/* Start the alarm if it hasn't been started yet */
				startAlarm();
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

		PSStatus status;
		status = _bluetoothBridge.initialize(this, true);
		if (status != PSStatus.OK) {
			Logger.err("Failed to initialize Bluetooth Bridge");
			return false;
		}

		_bluetoothBridge.setEventHandler(this);

		status = _bluetoothBridge.listen();
		if (status != PSStatus.OK) {
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
		Toast.makeText(ProjectSidekickService.this, msg, Toast.LENGTH_SHORT).show();
		Logger.info(msg);
		return;
	}

	private PSStatus broadcastDeviceFound(GuardedItem device) {
		/* Broadcast our received data for our receivers */
		Intent foundIntent = new Intent(ACTION_UPDATE_FOUND);
		foundIntent.putExtra("NAME", device.getName());
		foundIntent.putExtra("ADDRESS", device.getAddress());
		foundIntent.putExtra("LOST_STATUS", false);
		foundIntent.putExtra("RSSI", device.getRssi());
		sendBroadcast(foundIntent);

		return PSStatus.FAILED;
	}

	private PSStatus broadcastDeviceLost(GuardedItem device) {
		/* Broadcast our received data for our receivers */
		Intent foundIntent = new Intent(ACTION_UPDATE_LOST);
		foundIntent.putExtra("NAME", device.getName());
		foundIntent.putExtra("ADDRESS", device.getAddress());
		foundIntent.putExtra("LOST_STATUS", true);
		foundIntent.putExtra("RSSI", (short) (0));
		sendBroadcast(foundIntent);

		return PSStatus.FAILED;
	}

	private String compressDeviceName(String name) {
		String dvcName = "";
		for (int iIdx = 0; iIdx < MAX_DEVICE_NAME_LEN; iIdx++) {
			/* Add spaces if the device name is less than the set maximum */
			if (iIdx >= name.length()) {
				dvcName += ' ';
				continue;
			}

			dvcName += name.charAt(iIdx);
		}

		return dvcName;
	}


	private String compressDeviceAddress(String addr) {
		return addr.replace(":", "");
	}

	private String restoreDeviceAddress(String addr) {
		String dvcAddr = "";

		if (addr.contains(":")) {
			return addr;
		}

		for (int iIdx = 0; iIdx < addr.length(); iIdx++) {
			if ( ((iIdx%2) == 0) && (iIdx > 0) ) {
				dvcAddr += ":";
			}

			dvcAddr += addr.charAt(iIdx);
		}

		return dvcAddr;
	}

	/* TODO !!! This part badly needs refactoring */
	private String decodeBinaryDeviceList(byte[] list, int iInitOffs) {
		String dvcListStr = "";
		String rawListStr = new String(list, iInitOffs, (list.length-iInitOffs) );

		String rawListPart[] = rawListStr.split(",");

		for (int iIdx = 0; iIdx < rawListPart.length; iIdx++) {
			StringReader sr = new StringReader(rawListPart[iIdx]);

			try {
				int iDvcId = sr.read();
				if (iDvcId >= 49) {
					/* If our device id is in ASCII, correct it to the actual value
					 *	through subtraction */
					iDvcId -= 48;
				}
				int iStatus = sr.read();

				char cHexPair[] = new char[2];

				/* Capture the device name */
				String deviceName = "";
				int cConv = 0;
				while (sr.read(cHexPair, 0, 2) > 0) {
					cConv = Integer.decode("0x" + cHexPair[0] + "" + cHexPair[1]);
					deviceName += (char) cConv;

					if (cHexPair[1] == '0') {
						if ( (cHexPair[0] == '2') || (cHexPair[0] == '0') ) {
							break;
						}
					} else if ((cHexPair[0] == ';') || (cHexPair[1] == ';')) {
						break;
					}
				}
				Logger.info("Device Name: " + deviceName);

				/* Skip over the unnecessary */
				boolean isNextPartFound = false;
				if (cHexPair[0] == '0') {
					char cRead = '0';
					while (cRead <= '0') {
						cRead = (char)(sr.read());
						if (cRead < 0) {
							break;
						}
						Logger.dbg("Found: " + cRead);
					}

					if (cRead > '0') {
						cHexPair[0] = cRead;
						cHexPair[1] = (char) sr.read();
						isNextPartFound = true;
						Logger.dbg("Found: " + cHexPair[0] + ", " + cHexPair[1]);
					}
				} else {
					while (sr.read(cHexPair, 0, 2) > 0) {
						if (cHexPair[0] != '2' || cHexPair[1] != '0') {
							isNextPartFound = true;
							break;
						}
					}
				}

				if (!isNextPartFound) {
					Logger.err("Got to end of string without finding device address start");
					sr.close();
					break;
				}

				/* Capture the device address */
				String deviceAddr = "";

				cConv = Integer.decode("0x" + cHexPair[0] + "" + cHexPair[1]);
				deviceAddr += (char) cConv;

				Logger.dbg("Capturing device address...");
				while (sr.read(cHexPair, 0, 2) > 0) {
					Logger.dbg("Found: " + cHexPair[0] + ", " + cHexPair[1]);
					if ((cHexPair[0] == ';') || (cHexPair[1] == ';')) {
						Logger.dbg("Got to end of string");
						break;
					}
					cConv = Integer.decode("0x" + cHexPair[0] + "" + cHexPair[1]);
					deviceAddr += (char) cConv;

					if (cHexPair[1] == '0') {
						if ( (cHexPair[0] == '2') || (cHexPair[0] == '0') ) {
							break;
						}
					}
				}
				Logger.info("Device Address (Raw): " + deviceAddr);
				Logger.info("Device Address: " + restoreDeviceAddress(deviceAddr));

				dvcListStr += iDvcId + "|" + deviceName + "|" + restoreDeviceAddress(deviceAddr) +
						"|" + (iStatus == '1' ? "Guarded" : "Not Guarded");

				sr.close();
			} catch (Exception e) {
				Logger.err("Exception occurred: " + e.getMessage());
			}

			if ((iIdx+1) < rawListPart.length) {
				dvcListStr += ",";
			}
		}

		return dvcListStr;
	}

	private PSStatus performBroadcast(IBluetoothBridge btBridge, byte data[]) {
		if (data.length > 20) {
			return broadcastAsStream(btBridge, data);
		}

		return broadcastDirectly(btBridge, data);
	}

	private PSStatus broadcastAsStream(IBluetoothBridge btBridge, byte data[]) {
		ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
		byte sendBuffer[] = new byte[20];

		int bytesRemaining = byteStream.available();
		while (bytesRemaining > 0) {
			Arrays.fill(sendBuffer, (byte)(0));

			/* Read up to 20 bytes from the stream */
			byteStream.read(sendBuffer, 0, 20);
			if (btBridge.broadcast(sendBuffer) != PSStatus.OK) {
				return PSStatus.FAILED;
			}

			bytesRemaining = byteStream.available();
		}

		return PSStatus.OK;
	}

	private PSStatus broadcastDirectly(IBluetoothBridge btBridge, byte data[]) {
		return btBridge.broadcast(data);
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
			PSStatus status = PSStatus.FAILED;

			switch (msg.what) {
			case MSG_START_SETUP:
				status = startSetupMode();
				break;
			case MSG_START_GUARD:
				status = startGuardMode();
				break;
			case MSG_START_REPORT:
				status = startReportMode();
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
				String remoteAddr = data.getString("DEVICE_ADDR", "");
				status = sendRegisterRequest(remoteAddr);
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
				Bundle unregDvcData = msg.getData();
				if (unregDvcData == null) {
					break;
				}
				int unregDvcId = unregDvcData.getInt("DEVICE_ID", -1);
				status = sendUnregisterRequest(unregDvcId);
				break;
			case MSG_SET_CHECK_MODE:
				Bundle dvcCheckModeData = msg.getData();
				if (dvcCheckModeData == null) {
					break;
				}
				String dvcCheckMode = dvcCheckModeData.getString("CHECK_MODE", "");
				status = setDeviceCheckingMode(dvcCheckMode);
				break;
			case MSG_SET_SLEEP_TIME:
				Bundle sleepTimeData = msg.getData();
				if (sleepTimeData == null) {
					break;
				}
				long lSleepTime = sleepTimeData.getLong("TIME", DEFAULT_SLEEP_TIME);
				status = setSleepTime(lSleepTime);
				break;
			case MSG_SET_ALARM_TOGGLE:
				Bundle alarmToggleData = msg.getData();
				if (alarmToggleData == null) {
					break;
				}
				boolean bAlarmToggle = alarmToggleData.getBoolean("ALARM", false);
				status = setAlarmsEnabled(bAlarmToggle);
				break;
			default:
				super.handleMessage(msg);
				break;
			}

			if (status != PSStatus.OK) {
				/* TODO Do something */
			}

			return;
		}
	}

	private class RegisterCyclicTask extends AsyncTask<Void, String, Void> {
		private Thread 			_runThread = null;
		private ReentrantLock 	_runThreadLock = new ReentrantLock();
		private boolean			_bIsConnected = false;

		@Override
		protected Void doInBackground(Void... params) {
			_runThreadLock.lock();
			_runThread = Thread.currentThread();
			_runThreadLock.unlock();

			BTState btState;

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

				long lCurrentTime = System.currentTimeMillis();
				long lElapsedTime = (lCurrentTime - lStartTime);
				if (lElapsedTime > MAX_AWAIT_CONN_TIME) {
					publishProgress("Connection window exceeded.");
					break;
				}

				publishProgress("Waiting for new connections...");
				try {
					long lSleepTime = DEFAULT_AWAIT_SETUP_CONN_TIME - lElapsedTime;
					Thread.sleep(lSleepTime);
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

				if (!_bIsConnected) {
					_bluetoothBridge.destroy();
				}
			}

			/* Set our task back to NULL */
			_cyclicReportTask = null;

			/* Drop all ongoing connections */
			stop();

			_runThreadLock.lock();
			_runThread = null;
			_runThreadLock.unlock();

			/* Tell all receivers that registration has ended */
			broadcastAction(ACTION_REG_FINISHED);
			publishProgress("Device Registration has ended");

			return null;
		}

		public void interruptForDisconnection() {
			_bIsConnected = false;

			_runThreadLock.lock();
			if (_runThread != null) {
				_runThread.interrupt();
			}
			_runThreadLock.unlock();
			return;
		}

		public void interruptForConnection(String name, String address) {
			_bIsConnected = true;

			_runThreadLock.lock();
			if (_runThread != null) {
				_runThread.interrupt();
			}
			_runThreadLock.unlock();
			return;
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

	private class ReportCyclicTask extends AsyncTask<Void, String, Void> {
		private Thread 			_runThread = null;
		private ReentrantLock 	_runThreadLock = new ReentrantLock();
		private String			_connDeviceName = "";
		private String			_connDeviceAddr = "";
		private boolean			_bIsConnected	= true;

		@Override
		protected Void doInBackground(Void... params) {
			_runThreadLock.lock();
			_runThread = Thread.currentThread();
			_runThreadLock.unlock();

			BTState btState;

			/* Tell all receivers that report mode has started */
			broadcastAction(ACTION_REP_STARTED);
			publishProgress("Report Mode has started");

			setState(ServiceState.REPORT);

			while (getState() == ServiceState.REPORT) {
				/*
				 * Start listening for connections only if we're not already
				 * listening, connecting, or connected
				 */
				btState = getBluetoothState();
				if ((btState != BTState.LISTENING)
						&& (btState != BTState.CONNECTED)
						&& (btState != BTState.CONNECTING)) {
					startListen();
					publishProgress("Waiting for beacon to connect...");
				}
				try {
					Thread.sleep(DEFAULT_AWAIT_REPORT_CONN_TIME);
				} catch (InterruptedException e) {
					Logger.err("Interrupted");
					/* Handle interrupts due to termination */
					if (getState() != ServiceState.REPORT) {
						break;
					}
					/* Normal interruptions should proceed as usual */
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
					_cyclicRegisterTask = null;
					break;
				}

				if (_bIsConnected) {
					publishProgress("Connected: " + _connDeviceName + "/" + _connDeviceAddr);

					/* Wait for the disconnect signal */
					try {
						Thread.sleep(DEFAULT_AWAIT_REPORT_DISCONN_TIME);
					} catch (InterruptedException e) {
						/* Allow normal interruptions */
					}

					/* If still connected, destroy the existing connection ourselves */
					if (_bIsConnected) {
						_bluetoothBridge.destroy();
						_bIsConnected = false;
					}
				}
			}

			/* Set our task back to NULL */
			_cyclicRegisterTask = null;

			/* Drop all ongoing connections */
			stop();

			_runThreadLock.lock();
			_runThread = null;
			_runThreadLock.unlock();

			/* Tell all receivers that registration has ended */
			broadcastAction(ACTION_REP_FINISHED);
			publishProgress("Report Mode has ended");

			return null;
		}

		public void interruptForDisconnection() {
			_connDeviceName = "";
			_connDeviceAddr = "";
			_bIsConnected = false;

			_runThreadLock.lock();
			if (_runThread != null) {
				_runThread.interrupt();
			}
			_runThreadLock.unlock();
			return;
		}

		public void interruptForConnection(String name, String address) {
			_connDeviceName = name;
			_connDeviceAddr = address;
			_bIsConnected = true;

			_runThreadLock.lock();
			if (_runThread != null) {
				_runThread.interrupt();
			}
			_runThreadLock.unlock();
			return;
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

			PSStatus retStatus = PSStatus.FAILED;
			switch (_guardMode) {
				case SDP:
					retStatus = guardDeviceViaSdp();
					break;
				case BT_CONNECT:
					retStatus = guardDeviceViaBtConnect();
					break;
				case SDP_WITH_BT_CONNECT:
					/* Fall through since no implementation yet */
				default:
					Logger.err("Invalid guard mode setting: " + _guardMode);
					break;
			}
			if (retStatus != PSStatus.OK) {
				publishProgress("Device checking method failed");
			}

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

		private PSStatus guardDeviceViaBtConnect() {
			PSStatus retStatus = PSStatus.FAILED;

			/* Unload old bluetooth bridge if it is still active */
			_bluetoothBridge.destroy();

			while (getState() == ServiceState.GUARD) {
				/* Attempt to to connect to each Guarded Device and
				 *	immediately drop the connection after */
				boolean bIsAnyDeviceLost = false;
				for (GuardedItem item : _guardedItems) {
					/* Skip devices that have been flagged as unregistered for this session */
					if (!item.isRegistered()) {
						continue;
					}

					PSStatus connStatus;

					/* Cancel device discovery if ongoing */
					_bluetoothBridge.stopDeviceDiscovery();

					/* Attempt to connect to device */
					connStatus = connectToDevice(item.getAddress());
					if (connStatus != PSStatus.OK) {
						publishProgress("Item Lost: " + item.toString());
						bIsAnyDeviceLost = true;
						broadcastDeviceLost(item);
						continue;
					}

					broadcastDeviceFound(item);

					/* Attempt to disconnect from device */
					connStatus = _bluetoothBridge.disconnectDeviceByAddress(item.getAddress());
					if (connStatus != PSStatus.OK) {
						Logger.err("Failed to disconnect from " + item);
					}
				}

				/* Sound the alarms if devices have been lost */
				if (bIsAnyDeviceLost) {
					startAlarm();
				} else {
					stopAlarm();
				}

				publishProgress("Sleeping for " + Long.toString(_lSleepInterval) + " ms");
				try {
					Thread.sleep(_lSleepInterval);
				} catch (InterruptedException e) {
					/* Normal interruptions are ok */
				} catch (Exception e) {
					retStatus = PSStatus.FAILED;
					Logger.err("Exception occurred: " + e.getMessage());
					_cyclicGuardTask = null;
					break;
				}
			}

			/* Stop the alarm before we leave GUARD mode */
			stopAlarm();

			return retStatus;
		}

		private PSStatus guardDeviceViaSdp() {
			PSStatus retStatus = PSStatus.OK;

			/* Periodically check guarded items */
			while (getState() == ServiceState.GUARD) {
				/* Cancel device discovery if ongoing */
				_bluetoothBridge.stopDeviceDiscovery();

				/* Clear previously found devices */
				_foundDevices.clear();

				/* Re-start device discovery if ongoing */
				_bluetoothBridge.startDeviceDiscovery();

				publishProgress("Sleeping for " + Long.toString(_lSleepInterval) + " ms");
				try {
					Thread.sleep(_lSleepInterval);
				} catch (InterruptedException e) {
					/* Normal interruptions are ok */
				} catch (Exception e) {
					retStatus = PSStatus.FAILED;
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
							broadcastDeviceLost(item);
						}
					}
				} else {
					/* Stop the alarm if there are no longer any lost items */
					stopAlarm();
				}
			}

			/* Stop the alarm before we leave GUARD mode */
			stopAlarm();

			return retStatus;
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
			byte data[] = recvData.getData();
			String prefix = new String(data, 0, 4);
			boolean suppressDisplay = false;

			/* Check request contents */
			if (prefix.startsWith(REQ_PREF_REGISTER)) {
				handleRegisterRequest(sender, address, data);
			} else if (prefix.startsWith(REQ_PREF_REPORT_LOSS)) {
				handleReportRequest(sender, address, data);
			} else if (prefix.startsWith(REQ_PREF_GET_LIST)) {
				handleGetListRequest(sender, address, data);
			} else if (prefix.startsWith(REQ_PREF_DELETE)) {
				handleDeleteRequest(sender, address, data);
			} else if (prefix.startsWith(REQ_PREF_DISCONNECT)) {
				handleDisconnectRequest(address);
			} else if (prefix.startsWith("RWO")) {
				handleRWOResponse(sender, address, data);
			} else if (prefix.startsWith(RES_PREF_GET_LIST)) {
				handleGetListResponse(sender, address, data);
				suppressDisplay = true;
			} else {
				Logger.warn("Unknown handling for received data: " + new String(data));
			}

			/* Broadcast our received data for our receivers */
			if (!suppressDisplay) {
				Intent intent = new Intent(ACTION_DATA_RECEIVE);
				intent.putExtra("SENDER_NAME", sender);
				intent.putExtra("SENDER_ADDR", address);
				intent.putExtra("SENDER_DATA", new String(data));
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

		private boolean checkReceivedHeader(String header) {
			String hdrTbl[] = {
					REQ_PREF_REGISTER, REQ_PREF_REPORT_LOSS, REQ_PREF_GET_LIST, REQ_PREF_DELETE,
					REQ_PREF_DISCONNECT, "RWO", RES_PREF_GET_LIST
			};

			for (String hdrMatch : hdrTbl) {
				if (header.startsWith(hdrMatch)) {
					Logger.info("Header matched: " + hdrMatch);
					return true;
				}
			}

			return false;
		}

		private void handleRegisterRequest(String name, String addr, byte data[]) {
			String dataStr = new String(data);

			final int IDX_PAYLOAD_NAME_START = IDX_PAYLOAD_DATA_OFFS;
			final int IDX_PAYLOAD_NAME_END = IDX_PAYLOAD_NAME_START + MAX_DEVICE_NAME_LEN;
			final int IDX_PAYLOAD_ADDR_START = IDX_PAYLOAD_NAME_END + 1;
			final int IDX_PAYLOAD_ADDR_END = IDX_PAYLOAD_ADDR_START + MAX_DEVICE_ADDR_LEN;

			/* Restore address from data content to simulate actual embedded device */
			String dataAddr = dataStr.substring(IDX_PAYLOAD_ADDR_START, IDX_PAYLOAD_ADDR_END);
			String dvcAddr = restoreDeviceAddress(dataAddr);

			/* Restore device name from data content to simulate actual embedded device */
			String dvcName = dataStr.substring(IDX_PAYLOAD_NAME_START, IDX_PAYLOAD_NAME_END);

			/* Handle REGISTER requests from the Sidekick */
			/* Check if the guarded item is already in our list */
			for (GuardedItem item : _guardedItems) {
				if (item.addressMatches(dvcAddr)) {
					Logger.warn("Device already registered: " + dvcAddr);
					
					/* Set the registered flag for this item */
					item.setRegistered(true);

					/* Broadcast an RWO response */
					sendRWOResponse(dvcAddr);

					/* Update its report window */
					item.updateReportWindow(_lGuardWindow);

					/* Broadcast our received data for our receivers */
					Intent intent = new Intent(ACTION_REGISTERED);
					intent.putExtra("NAME", dvcName);
					intent.putExtra("ADDRESS", dvcAddr);
					sendBroadcast(intent);

					return;
				}
			}

			/* Broadcast a REGISTER OK response */
			sendAckResponse(RES_PREF_REGISTER, dvcAddr);

			/* Create a new GuardedItem */
			GuardedItem newItem = new GuardedItem(dvcName, dvcAddr);
			newItem.setRegistered(true);
			newItem.updateReportWindow(_lGuardWindow);

			/* Add the guarded item to our list */
			_guardedItems.add(newItem);
			
			/* Add it to our registered device list as well */
			_app = getAppRef();
			_app.addRegisteredDevice(newItem);
			_app.saveRegisteredDevices();

			Logger.info("Added item to GUARD list: " + newItem.toString());

			/* Broadcast our received data for our receivers */
			Intent intent = new Intent(ACTION_REGISTERED);
			intent.putExtra("NAME", dvcName);
			intent.putExtra("ADDRESS", dvcAddr);
			sendBroadcast(intent);

			return;
		}
		
		private void handleGetListRequest(String name, String addr, byte[] data) {
			if (getState() != ServiceState.SETUP) {
				Logger.warn("Invalid state for LIST requests");
				return;
			}
			
			/* Handle LIST request */

			/* Prepare the list buffer */
			int iListBuffLen = _guardedItems.size() * 36;
			byte listBuff[] = new byte[iListBuffLen];
			int iOffs = 0;

			/* Build the list */
			String listStr = "";
			Iterator<GuardedItem> iter = _guardedItems.iterator();
			int iItemIdx = 0;
			while (iter.hasNext()) {
				GuardedItem item = iter.next();

				String dvcName = compressDeviceName(item.getName());
				String dvcAddr = compressDeviceAddress(item.getAddress());

				/* Fill the list buffer */
				listBuff[iOffs + IDX_DVC_ID] = (byte)(iItemIdx);
				listBuff[iOffs + IDX_DVC_STAT] = (byte)(item.isRegistered() ? 1 : 0);
				System.arraycopy(dvcName.getBytes(), 0,
						listBuff, (iOffs + IDX_DVC_NAME),
						MAX_DEVICE_NAME_LEN);
				System.arraycopy(dvcAddr.getBytes(), 0,
						listBuff, (iOffs + IDX_DVC_ADDR),
						dvcAddr.length());
				
				if (iter.hasNext()) {
					listBuff[iOffs + IDX_DVC_LAST] = ',';
				} else {
					listBuff[iOffs + IDX_DVC_LAST] = ';';
				}

				iOffs += (IDX_DVC_LAST + 1);
				iItemIdx++;
			}
			
			/* Send the RLIST response */
			sendListResponse(addr, listBuff);
			
			return;
		}

		private void handleDisconnectRequest(String address) {
			if (_bluetoothBridge != null) {
				_bluetoothBridge.disconnectDeviceByAddress(address);
			}

			return;
		}

		/* TODO Not updated to current design */
		private void handleDeleteRequest(String name, String addr, byte[] data) {
			if (getState() != ServiceState.SETUP) {
				Logger.warn("Invalid state for DELETE requests");
				return;
			}

			String dataStr = new String(data);
			
			/* Handle DELETE requests */
			/* Process the request string for the address/es */
			String dataPart[] = dataStr.split(" ");
			if (dataPart.length != 2) {
				Logger.warn("Malformed delete request: " + dataStr);
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

		/* TODO Not updated to current design */
		private void handleReportRequest(String name, String addr, byte[] data) {
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

		/* TODO Not updated to current design */
		private void handleRWOResponse(String name, String addr, byte[] data) {
			ServiceState state = getState();
			if ((state != ServiceState.REPORT)
					&& (state != ServiceState.REGISTERING)) {
				Logger.warn("Invalid state for RWO responses");
				return;
			}

			/* Handle RW (Report Window) offset responses */
			String dataStr = new String(data);

			_guardDeviceAddress = addr;
			_lReportWindow = extractRWParam(dataStr);

			/* Transition back to SETUP state */
			if (state == ServiceState.REGISTERING) {
				setState(ServiceState.SETUP);
			}

			/* Re-adjust our REPORT mode alarm */
			// startReportMode(_guardDeviceAddress, _lReportWindow);

			return;
		}

		private void handleGetListResponse(String name, String addr, byte[] data) {
			/* Handle the GET LIST response */
			String dvcListStr = decodeBinaryDeviceList(data, IDX_PAYLOAD_DATA_OFFS);
			String devices[] = dvcListStr.split(",");
			
			/* Notify any interested activities */
			Intent intent = new Intent(ACTION_LIST_RECEIVED);
			intent.putExtra("DEVICES", devices);
			sendBroadcast(intent);

			return;
		}

		private long extractRWParam(String request) {
			String reqPart[] = request.split(" ");
			if (reqPart.length != 2) {
				Logger.warn("Malformed RWO request: " + request);
				return DEFAULT_RW_INTERVAL;
			}

			Long lRWParam;
			try {
				lRWParam = Long.parseLong(reqPart[1]);
			} catch (Exception e) {
				Logger.warn("Could not parse RW from request: " + request);
				lRWParam = DEFAULT_RW_INTERVAL;
			}

			return lRWParam;
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
						broadcastDeviceFound(item);
						return;
					}
				}
				GuardedItem newItem = new GuardedItem(name, address);
				newItem.setIsLost(false);
				newItem.setRssi(uRssi);
				_foundDevices.add(newItem);

				Logger.info("Added found device: " + newItem.toString());
				broadcastDeviceFound(newItem);

				return;
			}
		}
	};
}
