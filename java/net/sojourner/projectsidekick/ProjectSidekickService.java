package net.sojourner.projectsidekick;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.AlarmState;
import net.sojourner.projectsidekick.types.BTState;
import net.sojourner.projectsidekick.types.GuardedItem;
import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.types.ReceivedData;
import net.sojourner.projectsidekick.types.ReportModeInfo;
import net.sojourner.projectsidekick.types.ServiceState;
import net.sojourner.projectsidekick.utils.SKUtils;
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
//	public static final int MSG_START_GUARD 		= 2;
	public static final int MSG_START_REPORT 		= 3;
	public static final int MSG_STOP 				= 4;
	public static final int MSG_START_DISCOVER 		= 5;
	public static final int MSG_STOP_DISCOVER 		= 6;
	public static final int MSG_SEND_REGISTER 		= 7;
//	public static final int MSG_SET_AS_SIDEKICK 	= 8;
//	public static final int MSG_SET_AS_MOBILE 		= 9;
	public static final int MSG_DISCONNECT 			= 10;
	public static final int MSG_CONNECT 			= 11;
	public static final int MSG_SEND_GET_LIST 		= 12;
	public static final int MSG_QUERY_STATE 		= 13;
	public static final int MSG_QUERY_BT_STATE 		= 14;
	public static final int MSG_DELETE_DEVICE 		= 15;
//	public static final int MSG_SET_CHECK_MODE 		= 16;
//	public static final int MSG_SET_SLEEP_TIME 		= 17;
	public static final int MSG_SET_ALARM_TOGGLE	= 18;
	public static final int MSG_EDIT_GUARD_LST 		= 19;
	public static final int MSG_RENAME_SIDEKICK 	= 20;
	public static final int MSG_GET_ALARM_STATE		= 21;
	public static final int MSG_TRIGGER_ALARM		= 22;

	public static final int MSG_RESP_SERVICE_STATE		= 100;
	public static final int MSG_RESP_BLUETOOTH_STATE	= 101;
	public static final int MSG_RESP_ALARM_STATE		= 102;

	private static final String SERVICE_ACTION 		= "net.sojourner.projectsidekick.action.";
	public static final String ACTION_CONNECTED 	= SERVICE_ACTION + "CONNECTED";
	public static final String ACTION_DATA_RECEIVE	= SERVICE_ACTION + "DATA_RECEIVED";
	public static final String ACTION_REGISTERED 	= SERVICE_ACTION + "REGISTERED";
	public static final String ACTION_UNREGISTERED 	= SERVICE_ACTION + "UNREGISTERED";
	public static final String ACTION_UPDATE_LOST 	= SERVICE_ACTION + "UPDATE_LOST";
	public static final String ACTION_UPDATE_FOUND 	= SERVICE_ACTION + "UPDATE_FOUND";
	public static final String ACTION_DISCONNECTED 	= SERVICE_ACTION + "DISCONNECTED";
	public static final String ACTION_LIST_RECEIVED = SERVICE_ACTION + "LIST_RECEIVED";
	public static final String ACTION_REP_STARTED	= SERVICE_ACTION + "REP_STARTED";
	public static final String ACTION_REP_FINISHED	= SERVICE_ACTION + "REP_FINISHED";
	public static final String ACTION_DELETED		= SERVICE_ACTION + "DELETED";
	public static final String ACTION_LIST_CHANGED	= SERVICE_ACTION + "LIST_CHANGED";
	public static final String ACTION_ALARM_CHANGED = SERVICE_ACTION + "ALARM_CHANGED";
	public static final String ACTION_STATE_CHANGED	= SERVICE_ACTION + "STATE_CHANGED";

	private static final String RXX_TERM			= ";";
	private static final String RXX_DELIM			= ",";
	private static final String RXX_ACK				= "1" + RXX_TERM;
	private static final String	RXX_EOT				= Character.toString((char)(0x4));

	private static final String REQ_PREF_REGISTER 		= "XREG";
	private static final String REQ_PREF_GET_LIST 		= "XLST";
	private static final String REQ_PREF_SET_NAME 		= "XNME";
	private static final String REQ_PREF_DELETE 		= "XDEL";
	private static final String REQ_PREF_GUARD_THEF_SET = "XGSL";
	private static final String REQ_PREF_GUARD_THEF 	= "XATM";
	private static final String REQ_PREF_REPORT_THEF 	= "XATO";
	private static final String REQ_PREF_GUARD_LOSS_SET = "XALS";
	private static final String REQ_PREF_GUARD_LOSS 	= "XALM";
	private static final String REQ_PREF_REPORT_LOSS	= "XALO";
	private static final String REQ_PREF_TRIGGER_ALARM	= "XMAL";

	private static final String REQ_PREF_DISCONNECT		= "DISC;";

	private static final String RES_PREF_REGISTER		= "RREG";
	private static final String RES_PREF_GET_LIST 		= "RLST";
	private static final String RES_PREF_SET_NAME 		= "RNME";
	private static final String RES_PREF_DELETE 		= "RDEL";
	private static final String RES_PREF_GUARD_THEF_SET = "RGSL";
	private static final String RES_PREF_GUARD_THEF 	= "RATM";
	private static final String RES_PREF_REPORT_THEF 	= "RATO";
	private static final String RES_PREF_GUARD_LOSS_SET = "RALS";
	private static final String RES_PREF_GUARD_LOSS 	= "RALM";
	private static final String RES_PREF_REPORT_LOSS	= "RALO";
	private static final String RES_PREF_TRIGGER_ALARM	= "RMAL";
	private static final String RES_PREF_SK_TRIG_ALARM	= "RRED";

	public static final String ALARM_CODE_DISABLED		= "0";
	public static final String ALARM_CODE_BEEP			= "1";
	public static final String ALARM_CODE_EMERGENCY		= "2";

	public static final int		RES_CODE_REG_FAIL 		= '0';
	public static final int 	RES_CODE_REG_OK 		= '1';
	public static final int 	RES_CODE_REG_DUP 		= '2';

	private static final long DEFAULT_AWAIT_REPORT_CONN_TIME	= 32000;
	private static final long DEFAULT_AWAIT_RESPONSE_TIME		= 1000;
	private static final long DEFAULT_AWAIT_SIDEKICK_CONN		= 6000;
	private static final long DEFAULT_ALARM_INTERVAL			= 1000;

	private ProjectSidekickApp _app = null;
	private final Messenger _messenger = new Messenger(new MessageHandler());
	private ReportModeWakeReceiver _reportModeWakeAlarm = null;
	private IBluetoothBridge _bluetoothBridge = null;
	private ReentrantLock _tStateLock = new ReentrantLock();
	private ReentrantLock _tRequestLock = new ReentrantLock();
	private Ringtone _alarmSound = null;

	/* Common Parameters */
	private ServiceState _eState = ServiceState.UNKNOWN;
	private boolean _bAlarmsEnabled = true;
	private AlarmState _pastAlarmState = AlarmState.QUIET;
	private AlarmState _alarmState = AlarmState.QUIET;
	private List<GuardedItem> _foundDevices = new ArrayList<GuardedItem>();
	private AntiTheftReportCyclicTask _cyclicAntiTheftReportTask = null;
	private AlarmCyclicTask _cyclicAlarmTask = null;

	private String _guardDeviceAddress = "";
	private ReportModeInfo _reportInfo = null;
	private ReentrantLock _tReportInfoLock = new ReentrantLock();

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
		if (_cyclicAntiTheftReportTask != null) {
			_cyclicAntiTheftReportTask.interruptForConnection(name, address);
		}

		/* Broadcast our received data for our receivers */
		Intent intent = new Intent(ACTION_CONNECTED);
		intent.putExtra("SENDER_NAME", name);
		intent.putExtra("SENDER_ADDR", address);
		sendBroadcast(intent);

		return;
	}

	@Override
	public void onDisconnected(String name, String address) {
		if (_cyclicAntiTheftReportTask != null) {
			_cyclicAntiTheftReportTask.interruptForDisconnection();
		}

		/* Broadcast our received data for our receivers */
		Intent intent = new Intent(ACTION_DISCONNECTED);
		intent.putExtra("SENDER_NAME", name);
		intent.putExtra("SENDER_ADDR", address);
		sendBroadcast(intent);

		return;
	}

	@Override
	public void onDeactivated() {
		/* TODO */
		return;
	}

	@Override
	public void onDataReceived(String name, String address, byte[] data) {
		Logger.info("onDataReceived() invoked");
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
			/* This is also a case for DISCONNECTION since this is a known
			 *  buffer overloading failure case on some devices */
			Logger.warn("Received data overloads our buffers");
			return;
		}

		/* Start a new AsyncTask to handle it */
		new HandleReceivedDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
				new ReceivedData(name, address, data));

		return;
	}

	/* *********************** */
	/* Command Handler Methods */
	/* *********************** */
	private PSStatus startSetupMode() {
		setState(ServiceState.SETUP);
		return PSStatus.OK;
	}

	private PSStatus startReportMode(String address) {
		if (_cyclicAntiTheftReportTask == null) {
			_cyclicAntiTheftReportTask = new AntiTheftReportCyclicTask(address);
			_cyclicAntiTheftReportTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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

	private PSStatus disconnect(String address) {
		/* Set state to UNKNOWN */
		setState(ServiceState.UNKNOWN);

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		/* Halt ongoing device discovery operations */
		_bluetoothBridge.stopDeviceDiscovery();

		/* Broadcast a DISCONNECT signal */
		_bluetoothBridge.broadcast(REQ_PREF_DISCONNECT.getBytes());

		/* Sleep for a bit before actually disconnecting */
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			/* Interruptions are fine */
		}

		/* Disconnect this device in particular */

		return _bluetoothBridge.disconnectDeviceByAddress(address);
	}

	private PSStatus stop() {
		/* Set state to UNKNOWN */
		setState(ServiceState.UNKNOWN);

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		/* Halt ongoing device discovery operations */
		_bluetoothBridge.stopDeviceDiscovery();

		/* Broadcast a DISCONNECT signal */
		_bluetoothBridge.broadcast(REQ_PREF_DISCONNECT.getBytes());

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

		/* Cancel ongoing report tasks */
		if (_cyclicAntiTheftReportTask != null) {
			_cyclicAntiTheftReportTask.cancel(true);
			_cyclicAntiTheftReportTask = null;
		}

		return PSStatus.OK;
	}

	private PSStatus sendReportRequest(String address) {
		_tRequestLock.lock();
		if (getState() != ServiceState.REPORT) {
			Logger.err("Invalid state for REPORT request");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		PSStatus status;
		if (getBluetoothState() == BTState.UNKNOWN) {
			status = _bluetoothBridge.initialize(this, false);
			if (status != PSStatus.OK) {
				Logger.err("Failed to initialize Bluetooth Bridge");
				_tRequestLock.unlock();
				return PSStatus.FAILED;
			}
		}

		_bluetoothBridge.setEventHandler(this);

		if (getBluetoothState() != BTState.CONNECTED) {
			status = _bluetoothBridge.connectDeviceByAddress(address);
			if (status != PSStatus.OK) {
				Logger.err("Failed to connect to device: " + address);
				_tRequestLock.unlock();
				return PSStatus.FAILED;
			}
		}

		setState(ServiceState.REPORT);

		status = _bluetoothBridge.broadcast("REPORT".getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast REPORT command to " + address);
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_tRequestLock.unlock();
		return PSStatus.OK;
	}

	private PSStatus connectToDevice(String address) {
		_tRequestLock.lock();
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		PSStatus status;
		status = _bluetoothBridge.initialize(this, false);
		if (status != PSStatus.OK) {
			Logger.err("Failed to initialize Bluetooth Bridge");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_bluetoothBridge.setEventHandler(this);

		status = _bluetoothBridge.connectDeviceByAddress(address);
		if (status != PSStatus.OK) {
			Logger.err("Failed to connect to device: " + address);
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_tRequestLock.unlock();
		return PSStatus.OK;
	}

	private PSStatus sendRegisterRequest(String remoteAddr) {
		_tRequestLock.lock();
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for REGISTER request");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		/* Fix the device name at exactly MAX_DEVICE_NAME_LEN chars */
		String dvcName = SKUtils.compressDeviceName(_bluetoothBridge.getLocalName());

		/* Sent addresses should not contain separators */
		String dvcAddr = SKUtils.compressDeviceAddress(_bluetoothBridge.getLocalAddress());

		/* Create the request string */
		String request = dvcName + RXX_DELIM + dvcAddr + RXX_TERM;

		setState(ServiceState.REGISTERING);

		PSStatus status;
		status = performBroadcast(_bluetoothBridge, REQ_PREF_REGISTER.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast REGISTER command header to " + remoteAddr);
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast REGISTER command data to " + remoteAddr);
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_tRequestLock.unlock();
		return PSStatus.OK;
	}

	private PSStatus sendGetListRequest(String address) {
		_tRequestLock.lock();
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		String request = RXX_ACK;

		PSStatus status;
		status = performBroadcast(_bluetoothBridge, REQ_PREF_GET_LIST.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast LIST command header to " + address);
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast LIST command data to " + address);
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_tRequestLock.unlock();
		return PSStatus.OK;
	}

	private PSStatus sendUnregisterRequest(int deviceId) {
		_tRequestLock.lock();
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		PSStatus status;
		status = performBroadcast(_bluetoothBridge, REQ_PREF_DELETE.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast DELETE command header");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		String request = deviceId + ";";
		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast DELETE command data");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_tRequestLock.unlock();
		return PSStatus.OK;
	}

	private PSStatus sendModifyGuardListRequest(String guardListMod) {
		_tRequestLock.lock();
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		PSStatus status;
		status = performBroadcast(_bluetoothBridge, REQ_PREF_GUARD_THEF_SET.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast MODIFY GUARD LIST command header");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		String request = guardListMod + ";";
		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast MODIFY GUARD LIST command data");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_tRequestLock.unlock();
		return PSStatus.OK;
	}

	private PSStatus sendRenameRequest(String newName) {
		_tRequestLock.lock();
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		PSStatus status;
		status = performBroadcast(_bluetoothBridge, REQ_PREF_SET_NAME.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast RENAME command header");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		String request = newName + ";";
		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast RENAME command data");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_tRequestLock.unlock();
		return PSStatus.OK;
	}

	private PSStatus sendTriggerAlarmRequest(String address, String mode) {
		_tRequestLock.lock();
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		PSStatus status;
		if (getBluetoothState() == BTState.UNKNOWN) {
			status = _bluetoothBridge.initialize(this, false);
			if (status != PSStatus.OK) {
				Logger.err("Failed to initialize Bluetooth Bridge");
				_tRequestLock.unlock();
				return PSStatus.FAILED;
			}
		}

		_bluetoothBridge.setEventHandler(this);

		if (getBluetoothState() != BTState.CONNECTED) {
			status = _bluetoothBridge.connectDeviceByAddress(address);
			if (status != PSStatus.OK) {
				Logger.err("Failed to connect to device: " + address);
				_tRequestLock.unlock();
				return PSStatus.FAILED;
			}
		}

		try {
			Thread.sleep(600);
		} catch (InterruptedException e) {
			/* Do Nothing */
		}

		status = performBroadcast(_bluetoothBridge, REQ_PREF_TRIGGER_ALARM.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast TRIGGER ALARM command header");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		try {
			Thread.sleep(300);
		} catch (InterruptedException e) {
			/* Do Nothing */
		}

		String request = mode + RXX_DELIM + _bluetoothBridge.getRssi() + RXX_TERM;
		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast TRIGGER ALARM command data");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		/* Quietly put us in Alarm State: Emergency so that a PIN code will be needed
		 * 	to deactivate our alarm */
		if (mode.equals(ALARM_CODE_EMERGENCY)) {
			setAlarmState(AlarmState.EMERGENCY);
		} else if (mode.equals(ALARM_CODE_DISABLED)) {
			setAlarmState(AlarmState.QUIET);
		}

		_tRequestLock.unlock();
		return PSStatus.OK;
	}

	private PSStatus sendGuardReadyRequest() {
		_tRequestLock.lock();
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		String deviceAddr = SKUtils.compressDeviceAddress(_bluetoothBridge.getLocalAddress());
		String request = deviceAddr + ";";

		PSStatus status;
		status = performBroadcast(_bluetoothBridge, REQ_PREF_GUARD_THEF.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast GUARD READY command header");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast GUARD READY command data");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_tRequestLock.unlock();
		return PSStatus.OK;
	}

	private PSStatus sendReportRequest(int deviceId, String guardStatus, String alarmStatus) {
		_tRequestLock.lock();
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();

		PSStatus status;
		status = performBroadcast(_bluetoothBridge, REQ_PREF_REPORT_THEF.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast REPORT command header");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		String deviceIdStr = String.valueOf(deviceId);

		String request =  deviceIdStr + RXX_DELIM + guardStatus + RXX_DELIM + alarmStatus +
				RXX_DELIM + _bluetoothBridge.getRssi() + RXX_DELIM + "1" + RXX_DELIM + "1" +
				RXX_TERM;
		status = performBroadcast(_bluetoothBridge, request.getBytes());
		if (status != PSStatus.OK) {
			Logger.err("Failed to broadcast REPORT command data");
			_tRequestLock.unlock();
			return PSStatus.FAILED;
		}

		_tRequestLock.unlock();
		return PSStatus.OK;
	}

	private PSStatus replyBluetoothState(Messenger replyTo) {
		Bundle data = new Bundle();
		data.putString("STATE", getBluetoothState().toString());

		Message msg = Message.obtain(null, MSG_RESP_BLUETOOTH_STATE, 0, 0);
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

		Message msg = Message.obtain(null, MSG_RESP_SERVICE_STATE, 0, 0);
		msg.setData(data);

		try {
			replyTo.send(msg);
		} catch (Exception e) {
			Logger.err("Exception occurred: " + e.getMessage());
		}
		return PSStatus.OK;
	}

	private PSStatus replyAlarmState(Messenger replyTo) {
		Bundle data = new Bundle();

		/* If the modified and past alarm state are not in sync, then always choose the
		 *	past alarm state since we're not sure if the alarm has been sent to the device
		 *	or not */
		if (_alarmState == _pastAlarmState) {
			data.putString("STATE", _alarmState.toString());
		} else {
			data.putString("STATE", _pastAlarmState.toString());
		}

		Message msg = Message.obtain(null, MSG_RESP_ALARM_STATE, 0, 0);
		msg.setData(data);

		try {
			replyTo.send(msg);
		} catch (Exception e) {
			Logger.err("Exception occurred: " + e.getMessage());
		}
		return PSStatus.OK;
	}

	/* *************** */
	/* Private Methods */
	/* *************** */
	private PSStatus setAlarmsEnabled(boolean bEnabled) {
		_bAlarmsEnabled = bEnabled;
		return PSStatus.OK;
	}

	private void setAlarmState(AlarmState newState) {
		_pastAlarmState = _alarmState;
		_alarmState = newState;

		return;
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

		broadcastServiceStateChanged();
		return;
	}
	
	private Ringtone getAlarmSound() {
		Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
		return RingtoneManager.getRingtone(this, ringtoneUri);
	}

	private PSStatus startAlarm(String mode) {
		if (!_bAlarmsEnabled) {
			return PSStatus.FAILED;
		}

		/* Update our alarm state based on the given alarm mode */
		if (mode.equals(ALARM_CODE_EMERGENCY)) {
			setAlarmState(AlarmState.EMERGENCY);
		} else {
			return PSStatus.OK;
		}

		if (_cyclicAlarmTask == null) {
			_cyclicAlarmTask = new AlarmCyclicTask();
			_cyclicAlarmTask.execute();
		}

		/* Notify any receivers that the alarm state has changed */
		broadcastAlarmStateChanged();

		return PSStatus.OK;
	}

	private PSStatus stopAlarm() {
		if (!_bAlarmsEnabled) {
			return PSStatus.FAILED;
		}

		/* Change the alarm state */
		setAlarmState(AlarmState.QUIET);

		if (_cyclicAlarmTask != null) {
			_cyclicAlarmTask.interrupt();
			_cyclicAlarmTask = null;
		}

		/* Notify any receivers that the alarm state has changed */
		broadcastAlarmStateChanged();

		return PSStatus.OK;
	}

	private ProjectSidekickApp getAppRef() {
		return (ProjectSidekickApp) getApplication();
	}

	private BTState getBluetoothState() {
		if (_app == null) {
			_app = getAppRef();
		}
		if (_bluetoothBridge == null) {
			_bluetoothBridge = _app.getBluetoothBridge();
		}
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

		return PSStatus.OK;
	}

	private PSStatus broadcastAlarmStateChanged() {
		/* Broadcast our received data for our receivers */
		Intent foundIntent = new Intent(ACTION_ALARM_CHANGED);
		foundIntent.putExtra("MODE", _alarmState.toString());
		sendBroadcast(foundIntent);

		return PSStatus.OK;
	}

	private PSStatus broadcastServiceStateChanged() {
		/* Broadcast our received data for our receivers */
		Intent foundIntent = new Intent(ACTION_STATE_CHANGED);
		foundIntent.putExtra("STATE", getState().toString());
		sendBroadcast(foundIntent);

		return PSStatus.OK;
	}

	private PSStatus performBroadcast(IBluetoothBridge btBridge, byte data[]) {
		if (data.length > SKUtils.MAX_TX_LEN) {
			return broadcastAsStream(btBridge, data);
		}

		return broadcastDirectly(btBridge, data);
	}

	private PSStatus broadcastAsStream(IBluetoothBridge btBridge, byte data[]) {
		ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
		byte sendBuffer[] = new byte[SKUtils.MAX_TX_LEN];

		int bytesRemaining = byteStream.available();
		while (bytesRemaining > 0) {
			Arrays.fill(sendBuffer, (byte)(0));

			/* Read up to MAX_TX_LEN bytes from the stream */
			byteStream.read(sendBuffer, 0, SKUtils.MAX_TX_LEN);
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
	private class MessageHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			PSStatus status = PSStatus.FAILED;

			Bundle data = msg.getData();

			switch (msg.what) {
			case MSG_START_SETUP:
				status = startSetupMode();
				break;
			case MSG_START_REPORT:
				if (data == null) {
					Logger.err("Connect Failed: No device address provided");
					break;
				}
				String reportAddr = data.getString("DEVICE_ADDR", "");
				status = startReportMode(reportAddr);
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
				if (data == null) {
					Logger.err("Connect Failed: No device address provided");
					break;
				}
				String connAddr = data.getString("DEVICE_ADDR", "");
				status = connectToDevice(connAddr);
				break;
			case MSG_SEND_REGISTER:
				if (data == null) {
					Logger.err("Register Failed: No device address provided");
					break;
				}
				String remoteAddr = data.getString("DEVICE_ADDR", "");
				status = sendRegisterRequest(remoteAddr);
				break;
			case MSG_SEND_GET_LIST:
				if (data == null) {
					Logger.err("Get List Failed: No device address provided");
					break;
				}
				String listFromAddr = data.getString("DEVICE_ADDR", "");
				status = sendGetListRequest(listFromAddr);
				break;
			case MSG_DISCONNECT:
				if (data == null) {
					Logger.err("Disconnect Failed: No device address provided");
					break;
				}
				String discAddr = data.getString("DEVICE_ADDR", "");
				status = disconnect(discAddr);
				break;
			case MSG_QUERY_STATE:
				status = replyServiceState(msg.replyTo);
				break;
			case MSG_QUERY_BT_STATE:
				status = replyBluetoothState(msg.replyTo);
				break;
			case MSG_DELETE_DEVICE:
				if (data == null) {
					Logger.err("Delete Failed: No device id provided");
					break;
				}
				int unregDvcId = data.getInt("DEVICE_ID", -1);
				status = sendUnregisterRequest(unregDvcId);
				break;
			case MSG_SET_ALARM_TOGGLE:
				if (data == null) {
					Logger.err("Set Alarm Toggle Failed: No setting provided");
					break;
				}
				boolean bAlarmToggle = data.getBoolean("ALARM", false);
				status = setAlarmsEnabled(bAlarmToggle);
				break;
			case MSG_EDIT_GUARD_LST:
				if (data == null) {
					Logger.err("Edit Guard List Failed: No guard list data provided");
					break;
				}
				String edtGuardDataStr = data.getString("GUARD_SETUP", "");
				status = sendModifyGuardListRequest(edtGuardDataStr);
				break;
			case MSG_RENAME_SIDEKICK:
				if (data == null) {
					Logger.err("Rename Failed: No device name provided");
					break;
				}
				String renameStr = data.getString("DEVICE_NAME", "");
				status = sendRenameRequest(renameStr);
				break;
			case MSG_TRIGGER_ALARM:
				if (data == null) {
					Logger.err("Trigger Alarm Failed: No alarm mode provided");
					break;
				}
				String alarmAddrStr = data.getString("DEVICE_ADDR", "");
				String alarmModeStr = data.getString("MODE", ALARM_CODE_DISABLED);
				status = sendTriggerAlarmRequest(alarmAddrStr, alarmModeStr);
				break;
			case MSG_GET_ALARM_STATE:
				status = replyAlarmState(msg.replyTo);
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

	private class AntiTheftReportCyclicTask extends AsyncTask<Void, String, Void> {
		private Thread 			_runThread = null;
		private ReentrantLock 	_runThreadLock = new ReentrantLock();
		private boolean 		_bIsCycleRunning = false;

		private String _sidekickAddress = null;

		private boolean _bIsLost = false;

		public AntiTheftReportCyclicTask(String address) {
			_sidekickAddress = address;
			return;
		}

		@Override
		protected Void doInBackground(Void... params) {
			if (initializeTask() != PSStatus.OK) {
				return null;
			}

			if (startSidekickGuardMode() != PSStatus.OK) {
				publishProgress("Failed to start SIDEKICK guard mode");
				/* Run the cleanup task */
				cleanup();
				return null;
			}

			/* Tell all receivers that report mode has started */
			broadcastAction(ACTION_REP_STARTED);
			publishProgress("Report Mode has started");

			if (runReportModeLoop() != PSStatus.OK) {
				Logger.err("Fatal Error during Report Mode Operation");
			}

			/* Run the cleanup task */
			cleanup();

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

		public void interruptForDisconnection() {
//			_runThreadLock.lock();
//			if (_runThread != null) {
//				_runThread.interrupt();
//			}
//			_runThreadLock.unlock();
			return;
		}

		public void interruptForConnection(String name, String address) {
			if (_bIsLost) {
				/* If the device is lost, allow connection interrupts since we're
				 *  expecting it to be re-established */
				Logger.info("Interrupting for connection");
				_runThreadLock.lock();
				if (_runThread != null) {
					_runThread.interrupt();
				}
				_runThreadLock.unlock();
			}
			return;
		}

		public void interruptForCycleFinished() {
			_bIsCycleRunning = false;
			_runThreadLock.lock();
			if (_runThread != null) {
				_runThread.interrupt();
			}
			_runThreadLock.unlock();
			Logger.info("Cycle finished");
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

		/* ********************** */
		/* Main Procedure Methods */
		/* ********************** */
		private PSStatus initializeTask() {
			if (!isValidSidekick()) {
				return PSStatus.FAILED;
			}

			_runThreadLock.lock();
			_runThread = Thread.currentThread();
			_runThreadLock.unlock();

			return PSStatus.OK;
		}

		private PSStatus startSidekickGuardMode() {
			setState(ServiceState.AWAIT_GUARD_START);

			/* Send the initial Anti-Theft Mode Guard Start Request */
			if (sendGuardReadyRequest() != PSStatus.OK) {
				return PSStatus.FAILED;
			}

			/* Wait for the Anti-Theft Mode Guard Start Response */
			try {
				Thread.sleep(DEFAULT_AWAIT_REPORT_CONN_TIME);
			} catch (InterruptedException e) {
				Logger.warn("Interrupted");
			} catch (Exception e) {
				Logger.err("Exception occurred: " + e.getMessage());
				return PSStatus.FATAL_ERROR;
			}

			/* Proceed only if we are now in the REPORTing state */
			ServiceState state = getState();
			if (state != ServiceState.REPORT) {
				Logger.err("Invalid State: " + state + ". Report task cannot proceed");
				return PSStatus.FAILED;
			}

			return PSStatus.OK;
		}

		private PSStatus runReportModeLoop() {
			PSStatus status;
			int iChannel = 0;
			long lSyncTime = 0;

			_bIsCycleRunning = true;

			/* Loop continuously while we are still in REPORT state */
			while ((getState() == ServiceState.REPORT) && (_bIsCycleRunning)) {
				if (_bIsLost) {
					/* If this device has been lost, then we should attempt to re-sync
					 * 	every few seconds (DEFAULT_RW_INTERVAL) */
					lSyncTime = System.currentTimeMillis() + SKUtils.DEFAULT_RW_INTERVAL;
				} else {
					/* If this device is not yet lost, then we base our sync time on
					 *	the info we previously received from SIDEKICK */
					_tReportInfoLock.lock();
					iChannel = _reportInfo.getChannel();
					lSyncTime = _reportInfo.getSyncTime();
					_tReportInfoLock.unlock();
				}

				/* Sleep until our next cycle */
				status = sleepUntilNextCycle(System.currentTimeMillis(), lSyncTime);
				if (status != PSStatus.OK) {
					break;
				}

				/* Re-connect to the sidekick device if needed */
				if (getBluetoothState() != BTState.CONNECTED) {
					publishProgress("Attempting to reconnect to sidekick...");
					status = connectToSidekick();
					if (status == PSStatus.FAILED) {
						notifyConnectionLost();
						continue;
					} else if (status == PSStatus.INTERRUPTED) {
						break;
					}
				}

				/* Report to the SIDEKICK device */
				status = reportToSidekick(iChannel);
				if (status == PSStatus.OK) {
					/* If we receive a response from the sidekick device while the
					 *	_bIsLost is still up, then we probably have been found. */
					if (_bIsLost) {
						notifyConnectionRegained();
					}
					continue;
				} else if (status == PSStatus.FAILED) {
					/* DO NOTHING */
				} else {
					break;
				}

//				/* Disconnect from the sidekick device if we have to */
//				if (_bluetoothBridge.disconnectDeviceByAddress(_skAddress) != PSStatus.OK) {
//					Logger.err("Failed to disconnect from sidekick device: " + _skAddress);
//					_cyclicAntiTheftReportTask = null;
//					break;
//				}

				/* If we didn't receive any response, then the sidekick device might be
				 *	out-of-range. We can start triggering the alarm at this point. */
				notifyConnectionLost();
			}

			if (!_bIsCycleRunning) {
				stopSidekickReporting(iChannel);
			}

			_bIsCycleRunning = false;

			return PSStatus.OK;
		}

		private PSStatus sleepUntilNextCycle(long lStartTime, long lSyncTime) {
			try {
				if (lStartTime > lSyncTime) {
					Thread.sleep(1000);
				} else {
					Thread.sleep(lSyncTime - lStartTime);
				}
			} catch (InterruptedException e) {
				Logger.warn("Interrupted sleepUntilNextCycle()");
				/* Handle interrupts due to termination */
				if (getState() != ServiceState.REPORT) {
					return PSStatus.FATAL_ERROR;
				}
				/* Normal interruptions should proceed as usual */
			} catch (Exception e) {
				Logger.err("Exception occurred: " + e.getMessage());
				return PSStatus.FATAL_ERROR;
			}
			Logger.info("Slept for " +
					Long.toString(System.currentTimeMillis() - lStartTime) + "ms");

			return PSStatus.OK;
		}

		private PSStatus connectToSidekick() {
			/* Attempt to Connect to the SIDEKICK device */
			if (connectToDevice(_sidekickAddress) != PSStatus.OK) {
				Logger.err("Failed to connect to sidekick device: " + _sidekickAddress);
				disconnectFromSidekick();
				return PSStatus.FAILED;
			}

			/* Wait for the connection to complete */
			try {
				Thread.sleep(DEFAULT_AWAIT_SIDEKICK_CONN);
			} catch (InterruptedException e) {
				Logger.warn("Interrupted connectToSidekick()");
				/* Handle interrupts due to termination */
				if (getState() != ServiceState.REPORT) {
					return PSStatus.FATAL_ERROR;
				}
			} catch (Exception e) {
				Logger.err("Exception occurred: " + e.getMessage());
				return PSStatus.FATAL_ERROR;
			}

			if (getBluetoothState() == BTState.CONNECTED) {
				/* Return OK if we are now connected */
				Logger.info("Connection realized");
				return PSStatus.OK;
			}

			Logger.err("Connection failed");
			return PSStatus.FAILED;
		}

		private PSStatus disconnectFromSidekick() {
			_app = getAppRef();
			_bluetoothBridge = _app.getBluetoothBridge();
			return _bluetoothBridge.disconnectDeviceByAddress(_sidekickAddress);
		}

		private PSStatus reportToSidekick(int iChannel) {
			if (iChannel <= 0) {
				Logger.err("Invalid Response Channel: " + iChannel);
				return PSStatus.FAILED;
			}

			String alarmStatus = "0";
			if (_alarmState != AlarmState.QUIET) {
				alarmStatus = "1";
			}

			/* Send a report request to the SIDEKICK device */
			if (sendReportRequest(iChannel, "1", alarmStatus) != PSStatus.OK) {
				Logger.err("Failed to send REPORT request");
				return PSStatus.FAILED;
			}

			Logger.info("Waiting for response...");
			try {
				Thread.sleep(DEFAULT_AWAIT_RESPONSE_TIME);
			} catch (InterruptedException e) {
				Logger.warn("Interrupted");
				/* Handle interrupts due to termination */
				if (getState() != ServiceState.REPORT) {
					return PSStatus.INTERRUPTED;
				}

				/* Normal interruptions should proceed as usual */
				Logger.info("Response received.");
				return PSStatus.OK;
			} catch (Exception e) {
				Logger.err("Exception occurred: " + e.getMessage());
				return PSStatus.FATAL_ERROR;
			}

			/* Return FAILED if we do not receive any responses before the timeout */
			return PSStatus.FAILED;
		}

		private PSStatus stopSidekickReporting(int iChannel) {
			if (iChannel <= 0) {
				Logger.err("Invalid Response Channel: " + iChannel);
				return PSStatus.FAILED;
			}

			String alarmStatus = "0";
			if (_alarmState != AlarmState.QUIET) {
				alarmStatus = "1";
			}

			/* Send a report request to the SIDEKICK device */
			if (sendReportRequest(iChannel, "0", alarmStatus) != PSStatus.OK) {
				Logger.err("Failed to send REPORT END request");
				return PSStatus.FAILED;
			}

			/* Return FAILED if we do not receive any responses before the timeout */
			return PSStatus.FAILED;
		}

		/* *************** */
		/* Utility Methods */
		/* *************** */
		private void notifyConnectionRegained() {
			_bIsLost = false;

			publishProgress("SIDEKICK communication re-established");
			return;
		}

		private void notifyConnectionLost() {
			if (disconnectFromSidekick() != PSStatus.OK) {
				Logger.err("Disconnection from SIDEKICK failed");
			}

			_bIsLost = true;
			startAlarm(ALARM_CODE_EMERGENCY);

			publishProgress("Warning: Could not communicate with SIDEKICK");
			return;
		}

		private void cleanup() {
			/* Set our task back to NULL */
			_cyclicAntiTheftReportTask = null;

			/* Drop all ongoing connections */
			stop();

			_runThreadLock.lock();
			_runThread = null;
			_runThreadLock.unlock();

			/* Tell all receivers that registration has ended */
			broadcastAction(ACTION_REP_FINISHED);
			publishProgress("Report Mode has ended");

			return;
		}

		private void broadcastAction(String action) {
			Intent intent = new Intent(action);
			sendBroadcast(intent);
			return;
		}

		private boolean isValidSidekick() {
			if (_sidekickAddress == null) {
				return false;
			}

			if (_sidekickAddress.equals("")) {
				return false;
			}
			return true;
		}
	}

	private class AlarmCyclicTask extends AsyncTask<Void, String, Void> {
		private Thread _currentThread = null;
		@Override
		protected Void doInBackground(Void... params) {
			_currentThread = Thread.currentThread();

			while (_alarmState != AlarmState.QUIET) {
				if (_alarmSound == null) {
					_alarmSound = getAlarmSound();
				}

				if (!_alarmSound.isPlaying()) {
					_alarmSound.play();
				}

				try {
					Thread.sleep(DEFAULT_ALARM_INTERVAL);
				} catch (InterruptedException e) {
					Logger.info("Cyclic alarm task interrupted");
					continue;
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
					break;
				}
			}

			/* Update the alarm state */
			if (_alarmState != AlarmState.QUIET) {
				setAlarmState(AlarmState.QUIET);
			}

			/* Stop the alarm sound */
			if (_alarmSound == null) {
				_alarmSound = getAlarmSound();
			}
			_alarmSound.stop();

			_currentThread = null;

			return null;
		}

		public void interrupt() {
			if (_currentThread != null) {
				_currentThread.interrupt();
			}

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
			byte data[] = recvData.getData();
			if (data.length < 4) {
				Logger.err("Invalid data received: " + new String(data).trim());
				return null;
			}
			String cmdPrefix = new String(data, 0, 4).trim();
			boolean suppressDisplay = false;

			/* Check request contents */
			if (cmdPrefix.startsWith(RES_PREF_REGISTER)) {
				handleRegisterResponse(sender, address, data);
				suppressDisplay = true;
			} else if (cmdPrefix.startsWith(RES_PREF_GET_LIST)) {
				handleGetListResponse(sender, address, data);
				suppressDisplay = true;
			} else if (cmdPrefix.startsWith(RES_PREF_DELETE)) {
				handleDeleteResponse(sender, address, data);
				suppressDisplay = true;
			} else if (cmdPrefix.startsWith(RES_PREF_GUARD_THEF_SET)) {
				handleModifyGuardListResponse(sender, address, data);
				suppressDisplay = true;
			} else if (cmdPrefix.startsWith(RES_PREF_GUARD_THEF)) {
				handleGuardContinueResponse(sender, address, data);
				suppressDisplay = true;
			} else if (cmdPrefix.startsWith(RES_PREF_REPORT_THEF)) {
				handleGuardContinueResponse(sender, address, data);
				suppressDisplay = true;
			} else if (cmdPrefix.startsWith(RES_PREF_TRIGGER_ALARM)) {
				handleAlarmResponse(sender, address, data);
				suppressDisplay = true;
			} else if (cmdPrefix.startsWith(RES_PREF_SK_TRIG_ALARM)) {
				handleSkTriggerAlarmResponse(sender, address, data);
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

		private void handleDeleteResponse(String name, String addr, byte[] data) {
			if (getState() != ServiceState.SETUP) {
				Logger.warn("Invalid state for DELETE responses");
				return;
			}

			/* Response code is a single-char in the payload, followed by a semi-colon */
			int iRespCode = data[SKUtils.IDX_PAYLOAD_DATA_OFFS];

			if (iRespCode == RES_CODE_REG_FAIL) {
				// Delete failed
				publishProgress("Deletion failed");
				return;
			} else if (iRespCode == RES_CODE_REG_OK) {
				// Registration OK
				publishProgress("Deleted");
			} else {
				Logger.err("Unknown Response: " + new String(data));
			}

			Intent intent = new Intent(ACTION_DELETED);
			sendBroadcast(intent);

			return;
		}

		private void handleRegisterResponse(String name, String addr, byte[] data) {
			if (getState() != ServiceState.REGISTERING) {
				Logger.warn("Invalid state for REGISTER responses");
				return;
			}

			/* Response code is a single-char in the payload, followed by a semi-colon */
			int iRespCode = data[SKUtils.IDX_PAYLOAD_DATA_OFFS];
			if (iRespCode == RES_CODE_REG_FAIL) {
				// Registration failed
				Logger.err("Registration failed");
				publishProgress("Registration failed");

				Intent intent = new Intent(ACTION_UNREGISTERED);
				sendBroadcast(intent);
			} else if (iRespCode == RES_CODE_REG_DUP) {
				// Already Registered
				Logger.warn("Already registered");
				publishProgress("Already registered");

				Intent intent = new Intent(ACTION_REGISTERED);
				sendBroadcast(intent);
			} else if (iRespCode == RES_CODE_REG_OK) {
				// Registration OK
				Logger.info("Registered");
				publishProgress("Registered");

				Intent intent = new Intent(ACTION_REGISTERED);
				sendBroadcast(intent);
			} else {
				Logger.err("Unknown Response: " + new String(data));
			}

			setState(ServiceState.SETUP);

			return;
		}

		private void handleModifyGuardListResponse(String name, String addr, byte[] data) {
			if (getState() != ServiceState.SETUP) {
				Logger.warn("Invalid state for MODIFY GUARD LIST responses");
				return;
			}

			/* Response code is a single-char in the payload, followed by a semi-colon */
			int iRespCode = data[SKUtils.IDX_PAYLOAD_DATA_OFFS];

			if (iRespCode == RES_CODE_REG_FAIL) {
				// Registration failed
				publishProgress("Guard List Modification failed");
				return;
			} else if (iRespCode == RES_CODE_REG_OK) {
				// Registration OK
				publishProgress("Guard List Modified");
			} else {
				Logger.err("Unknown Response: " + new String(data));
			}

			Intent intent = new Intent(ACTION_LIST_CHANGED);
			sendBroadcast(intent);

			return;
		}

		private void handleGuardContinueResponse(String name, String addr, byte[] data) {
			if ((getState() != ServiceState.AWAIT_GUARD_START) &&
					(getState() != ServiceState.REPORT)) {
				Logger.warn("Invalid state for GUARD CONTINUE responses");
				return;
			}

			if (data[SKUtils.IDX_PAYLOAD_DATA_OFFS] == RES_CODE_REG_FAIL) {
				publishProgress("Sidekick has terminated guard mode");
				setState(ServiceState.UNKNOWN);
				if (_cyclicAntiTheftReportTask != null) {
					_cyclicAntiTheftReportTask.interruptForCycleFinished();
				}
				return;
			}

			if (data[SKUtils.IDX_PAYLOAD_DATA_OFFS+1] == ';') {
				if (data[SKUtils.IDX_PAYLOAD_DATA_OFFS] == '2') {
					publishProgress("Device not in guard list");
				} else if (data[SKUtils.IDX_PAYLOAD_DATA_OFFS] == '3') {
					publishProgress("Device already in guard list");
				} else if (data[SKUtils.IDX_PAYLOAD_DATA_OFFS] == '4') {
					publishProgress("Device not yet registered");
				}
				setState(ServiceState.UNKNOWN);
				if (_cyclicAntiTheftReportTask != null) {
					_cyclicAntiTheftReportTask.interruptForCycleFinished();
				}
				return;
			}

			/* Update our report parameters from the received data */
			_tReportInfoLock.lock();
			if (_reportInfo == null) {
				_reportInfo = new ReportModeInfo(0, 0, 0);
			}
			SKUtils.updateReportParams(_reportInfo, data, SKUtils.IDX_PAYLOAD_DATA_OFFS);
			_tReportInfoLock.unlock();

			/* Display the sync information */
			long lNextSync = (_reportInfo.getSyncTime() - System.currentTimeMillis() +
					SKUtils.calculateChannelWindow(_reportInfo.getChannel())) / 1000;
			publishProgress("Next sync in " + lNextSync + " secs");

			/* Update the state if we're coming from AWAIT_GUARD_START */
			if (getState() == ServiceState.AWAIT_GUARD_START) {
				setState(ServiceState.REPORT);
			}

			if (_cyclicAntiTheftReportTask != null) {
				_cyclicAntiTheftReportTask.interrupt();
			}

			return;
		}

		private void handleGetListResponse(String name, String addr, byte[] data) {
			/* Handle the GET LIST response */
			String dvcListStr = SKUtils.decodeBinaryDeviceList(data, SKUtils.IDX_PAYLOAD_DATA_OFFS);
			String devices[] = dvcListStr.split(",");

			publishProgress("Device list received");
			
			/* Notify any interested activities */
			Intent intent = new Intent(ACTION_LIST_RECEIVED);
			intent.putExtra("DEVICES", devices);
			sendBroadcast(intent);

			return;
		}

		private void handleSkTriggerAlarmResponse(String name, String addr, byte[] data) {
			/* Response is a single-char in the payload, followed by a semi-colon,
			 * 	representing the alarm mode */
			int iAlarmMode = data[SKUtils.IDX_PAYLOAD_DATA_OFFS];

			if (iAlarmMode == ALARM_CODE_DISABLED.charAt(0)) {
				stopAlarm();
			} else {
				startAlarm(Integer.toString(iAlarmMode));
			}

			return;
		}

		private void handleAlarmResponse(String name, String addr, byte[] data) {
			/* Response code is a single-char in the payload, followed by a semi-colon */
			int iRespCode = data[SKUtils.IDX_PAYLOAD_DATA_OFFS];

			if (iRespCode == RES_CODE_REG_FAIL) {
				// Trigger Alarm failed
				publishProgress("Trigger Alarm failed");
				setAlarmState(_pastAlarmState);
				return;
			} else if (iRespCode == RES_CODE_REG_OK) {
				/* Overwrite our past alarm state on success */
				setAlarmState(_alarmState);

				/* Notify any receivers that the alarm state has changed */
				broadcastAlarmStateChanged();
				publishProgress("Alarm State Changed: " + _alarmState.toString());
			} else {
				Logger.err("Unknown Response: " + new String(data));
			}

			return;
		}
	}

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
