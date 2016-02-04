package net.sojourner.projectsidekick;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import net.sojourner.projectsidekick.interfaces.BluetoothEventHandler;
import net.sojourner.projectsidekick.interfaces.IBluetoothBridge;
import net.sojourner.projectsidekick.types.BTState;
import net.sojourner.projectsidekick.types.GuardedItem;
import net.sojourner.projectsidekick.types.Status;
import net.sojourner.projectsidekick.utils.Logger;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.widget.Toast;

public class ProjectSidekickService extends Service implements BluetoothEventHandler {
	public static final int 	MSG_START_SETUP 		= 1;
	public static final int 	MSG_START_GUARD 		= 2;
	public static final int 	MSG_START_REPORT		= 3;
	public static final int 	MSG_STOP				= 4;
	public static final int 	MSG_START_DISCOVER		= 5;
	public static final int 	MSG_STOP_DISCOVER		= 6;
	public static final int 	MSG_SEND_REGISTER		= 7;
	public static final int 	MSG_SET_AS_SIDEKICK		= 8;
	public static final int 	MSG_SET_AS_MOBILE		= 9;
	public static final int 	MSG_DISCONNECT			= 10;
	public static final String  ACTION_CONNECTED		= "net.sojourner.projectsidekick.action.CONNECTED";
	public static final String  ACTION_DATA_RECEIVE		= "net.sojourner.projectsidekick.action.DATARECEIVED";
	public static final String  ACTION_REGISTERED		= "net.sojourner.projectsidekick.action.REGISTERED";
	public static final String  ACTION_UNREGISTERED		= "net.sojourner.projectsidekick.action.UNREGISTERED";
	public static final String  ACTION_UPDATE_LOST		= "net.sojourner.projectsidekick.action.UPDATE_LOST";
	public static final String  ACTION_UPDATE_FOUND		= "net.sojourner.projectsidekick.action.UPDATE_FOUND";
	public static final String  ACTION_DISCONNECTED		= "net.sojourner.projectsidekick.action.DISCONNECTED";
	private static final long 	DEFAULT_SLEEP_TIME 		= 15000;
	private static final long 	DEFAULT_AWAIT_CONN_TIME = 60000;
	private static final long 	MAX_AWAIT_CONN_TIME 	= 60000;
	private static final long 	DEFAULT_RW_INTERVAL		= 10000;
	public static enum ServiceState { UNKNOWN, SETUP, GUARD, REGISTERING, REPORT };
	private static enum Role { UNKNOWN, SIDEKICK, MOBILE };


	private ProjectSidekickApp 		_app 					= null;
	private final Messenger 		_messenger 				= new Messenger(new MessageHandler());
	private ReportModeWakeReceiver 	_reportModeWakeAlarm	= null;
	private IBluetoothBridge 		_bluetoothBridge 		= null;
	private GuardCyclicTask 		_cyclicGuardTask 		= null;
	private RegisterCyclicTask 		_cyclicRegisterTask 	= null;
	private HandleReceivedDataTask	_recvDataTask 			= null;
	private ReentrantLock			_tStateLock				= new ReentrantLock();
	/* Common Parameters */
	private Role 					_eRole 					= Role.UNKNOWN;
	private ServiceState 			_eState 				= ServiceState.UNKNOWN;
	/* REPORT Mode Parameters */
	private String 					_guardDeviceAddress = "";
	private long 					_lReportWindow 		= DEFAULT_RW_INTERVAL;
	/* GUARD Mode Parameters */
	private long 					_lGuardWindow 		= DEFAULT_RW_INTERVAL;
	private List<GuardedItem> 		_guardedItems 		= new ArrayList<GuardedItem>();

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
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(enableBtIntent);
		}
		
		/* Set this service as an event handler for Bluetooth events */
		_bluetoothBridge.setEventHandler(this);
		
		if (_receiver != null) {
			IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
			registerReceiver(_receiver, filter);
			Logger.info("Receiver registered");
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
			Logger.info("Receiver unregistered");
		}
		
		super.onDestroy();
	}

	@Override
	public void onConnected(String name, String address) {
		/* Broadcast our received data for our receivers */
		/*  TODO We may have to limit this at some point for 
		 * 		 security purposes */
		Intent intent = new Intent(ACTION_CONNECTED);
		intent.putExtra("SENDER_NAME", name);
		intent.putExtra("SENDER_ADDR", address);
		sendBroadcast(intent);
		
		return;
	}

	@Override
	public void onDisconnected(String name, String address) {
		/* Broadcast our received data for our receivers */
		/*  TODO We may have to limit this at some point for 
		 * 		 security purposes */
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
			/* TODO This is also a case for DISCONNECTION since this is a 
			 *  	known buffer overloading failure on some devices */
			return;
		}
		
		/* Start a new AsyncTask to handle it */
		_recvDataTask = new HandleReceivedDataTask();
		_recvDataTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new ReceivedData(name, address, data));
		
		return;
	} 
	
	/* *************** */
	/* Private Methods */
	/* *************** */
	private Status startSetupMode() {
		/* Make the device discoverable if role is SIDEKICK or UNSET */
		requestDiscoverability();
		if (getRole() != Role.MOBILE) {

			if (_cyclicRegisterTask == null) {
				_cyclicRegisterTask = new RegisterCyclicTask();
				_cyclicRegisterTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
			_reportModeWakeAlarm =  new ReportModeWakeReceiver();
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
		/* Set state to SETUP */
		setState(ServiceState.SETUP);

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
		
		return Status.OK;
	}
	
	private Status stop() {
		/* Set state to SETUP */
		setState(ServiceState.SETUP);

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
		
		return Status.OK;
	}
	
	private void requestDiscoverability() {
		_app = getAppRef();
		_bluetoothBridge = _app.getBluetoothBridge();
		
		/* Check if our bluetooth bridge is up */
		if (_bluetoothBridge.isReady() == false) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(enableBtIntent);
		}
		
		/* Show discoverabiltiy request */
		Intent makeDiscoverableIntent 
			= new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		makeDiscoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		makeDiscoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
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
	
	private Status sendRegisterRequest(String address) {
		if (getState() != ServiceState.SETUP) {
			Logger.err("Invalid state for REGISTER request");
			return Status.FAILED;
		}

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
		
		setState(ServiceState.REGISTERING);
		
		status = _bluetoothBridge.broadcast("REGISTER".getBytes());
		if (status != Status.OK) {
			Logger.err("Failed to broadcast REGISTER command to " + address);
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	private Status sendRWOResponse(String address) {
		if (getState() != ServiceState.GUARD) {
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
		
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			/* Do Nothing */
		}
		
		status = _bluetoothBridge.destroy();
		if (status != Status.OK) {
			Logger.err("Failed to disconnect remote device: " + address);
			return Status.FAILED;
		}
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
			Logger.warn("Role not changed (already set to " 
					+ _eRole.toString() + ")");
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
	
	private List<GuardedItem> checkForLostItems() {
		List<GuardedItem> lostItems = new ArrayList<GuardedItem>();
//		
//		for (GuardedItem item : _guardedItems) {
//			if (item.isReportWindowExceeded()) {
//				Logger.err("Device Lost: " + item.getName());
//				lostItems.add(item);
//			}
//		}
		
		for (GuardedItem item : _guardedItems) {
			boolean isFound = false;
			String address = item.getAddress();
			Logger.info("Looking for " + item.getAddress());
			for (GuardedItem found : _foundDevices) {
				if (found.getAddress().equals(address)) {
					isFound = true;
					break;
				}
			}
			
			if (!isFound) {
				Logger.warn("Device Lost: " + item.getName() + "/" + item.getAddress());
				lostItems.add(item);
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
		Toast.makeText(ProjectSidekickService.this, msg, Toast.LENGTH_SHORT).show();
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
			_senderAddress = getFixedMacAddress(address);
			
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
		
		private String getFixedMacAddress(String address) {
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
                case MSG_SEND_REGISTER:
                	Bundle data = msg.getData();
                	if (data == null) {
                		break;
                	}
                	String deviceAddr = data.getString("DEVICE_ADDR", "");
                	
                	status = sendRegisterRequest(deviceAddr);
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

		@Override
		protected Void doInBackground(Void... params) {
			BTState btState = BTState.UNKNOWN;
			
			publishProgress("Device Registration has started");
			setState(ServiceState.SETUP);
			long lStartTime = System.currentTimeMillis();
			
			while (getState() == ServiceState.SETUP) {
				
				/* Start listening for connections only if we're not already
				 *  listening, connecting, or connected */
				btState = getBluetoothState();
				if ((btState != BTState.LISTENING) &&
					(btState != BTState.CONNECTED) &&
					(btState != BTState.CONNECTING)) {
					
					startListen();
				}
				
				publishProgress("Waiting for new connections...");
				try {
					Thread.sleep(DEFAULT_AWAIT_CONN_TIME);
				} catch (InterruptedException e) {
					/* Normal interruptions are ok */
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

			_cyclicRegisterTask = null;
			publishProgress("Device Registration has ended");
			
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
	
	private class GuardCyclicTask extends AsyncTask<Void, String, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			publishProgress("Guard Task Started.");
			setState(ServiceState.GUARD);
			
			/* Periodically check guarded items */
			while (getState() == ServiceState.GUARD) {
				publishProgress("Checking guarded items...");
				
				/* Cancel device discovery if ongoing */
				_bluetoothBridge.stopDeviceDiscovery();
				
				/* Clear previously found devices */
				_foundDevices.clear();
				
				/* Re-start device discovery if ongoing */
				_bluetoothBridge.startDeviceDiscovery();
				
				publishProgress("Sleeping for " + Long.toString(DEFAULT_SLEEP_TIME) + " ms");
				try {
					Thread.sleep(DEFAULT_SLEEP_TIME);
				} catch (InterruptedException e) {
					/* Normal interruptions are ok */
				} catch (Exception e) {
					Logger.err("Exception occurred: " + e.getMessage());
					_cyclicGuardTask = null;
					break;
				}
				
				List<GuardedItem> lostItems = checkForLostItems();
				
				/* Display lost items via Toast */
				if (lostItems != null) {
					if (lostItems.size() > 0) {
						for (GuardedItem item : lostItems) {
							publishProgress("Item Lost: " 
									+ item.getName() + "/" 
									+ item.getAddress());

							/* Broadcast our received data for our receivers */
							/*  TODO We may have to limit this at some point for 
							 * 		 security purposes */
							Intent intent = new Intent(ACTION_UPDATE_LOST);
							intent.putExtra("NAME", item.getName());
							intent.putExtra("ADDRESS", item.getAddress());
							intent.putExtra("LOST_STATUS", true);
							sendBroadcast(intent);
						}
					}
				}
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
	}
	
	private class HandleReceivedDataTask extends AsyncTask<ReceivedData, String, Void> {
		
		@Override
		protected Void doInBackground(ReceivedData... params) {
//			publishProgress("Handle Received Data Task Started");
			
			ReceivedData recvData = params[0];

			/* SAN-check that the received data is not null */
			if (recvData == null) {
				return null;
			}

			String sender = recvData.getSenderName();
			String address = recvData.getSenderAddress();
			String request = new String(recvData.getData());
			if (request.contains("REGISTER")) {
				handleRegisterRequest(sender, address, request);
			} else if (request.contains("REPORT")) {
				handleReportRequest(sender, address, request);
			} else if (request.contains("RWO")) {
				handleRWOResponse(sender, address, request);
			} else {
				Logger.warn("Unknown request: " + request);
			}

			/* Broadcast our received data for our receivers */
			/*  TODO We may have to limit this at some point for 
			 * 		 security purposes */
			Intent intent = new Intent(ACTION_DATA_RECEIVE);
			intent.putExtra("SENDER_NAME", sender);
			intent.putExtra("SENDER_ADDR", address);
			intent.putExtra("SENDER_DATA", request);
			sendBroadcast(intent);
			
//			publishProgress("Handle Received Data Task Finished");			
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
//			ServiceState state = getState();
//			if (state != ServiceState.SETUP) {
//				Logger.warn("Invalid state for REGISTER requests: " + state.toString());
//				return;
//			}
			
			/* Handle REGISTER requests from the Sidekick*/
			/* Check if the guarded item is already in our list */
			for (GuardedItem item : _guardedItems) {
				if (item.getAddress().equals(addr)) {
					Logger.warn("Device already registered: " + addr);
					
					/* Broadcast an RWO response */
					sendRWOResponse(addr);
					
					/* Update its report window */
					item.updateReportWindow(_lGuardWindow);

					/* Broadcast our received data for our receivers */
					/*  TODO We may have to limit this at some point for 
					 * 		 security purposes */
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
			newItem.updateReportWindow(_lGuardWindow);
			
			/* Add the guarded item to our list */
			_guardedItems.add(newItem);
			
			Logger.info("Added item to GUARD list: " + newItem.toString());

			/* Broadcast our received data for our receivers */
			/*  TODO We may have to limit this at some point for 
			 * 		 security purposes */
			Intent intent = new Intent(ACTION_REGISTERED);
			intent.putExtra("NAME", name);
			intent.putExtra("ADDRESS", addr);
			sendBroadcast(intent);
			
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
				if (item.getAddress().equals(addr)) {
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
			if ((state != ServiceState.REPORT) && 
				(state != ServiceState.REGISTERING)) {
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
//			startReportMode(_guardDeviceAddress, _lReportWindow);
			
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
	}

	private List<GuardedItem> _foundDevices = new ArrayList<GuardedItem>();
	
	private final BroadcastReceiver _receiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        // When discovery finds a device
	        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
	        	BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
	        	String address = device.getAddress();
	        	String name = device.getName();
	        	
	        	for (GuardedItem item : _foundDevices) {
	        		if (item.getAddress().equals(address)) {
	        			item.setIsLost(false);
	    	        	
	    	        	Logger.info("Updated found device: " + item.toString());

						/* Broadcast our received data for our receivers */
						/*  TODO We may have to limit this at some point for 
						 * 		 security purposes */
						Intent foundIntent = new Intent(ACTION_UPDATE_FOUND);
						foundIntent.putExtra("NAME", item.getName());
						foundIntent.putExtra("ADDRESS", item.getAddress());
						foundIntent.putExtra("LOST_STATUS", false);
						sendBroadcast(foundIntent);
	        			return;
	        		}
	        	}
	        	GuardedItem newItem = new GuardedItem(name, address);
	        	newItem.setIsLost(false);
	        	_foundDevices.add(newItem);
	        	
	        	Logger.info("Added found device: " + newItem.toString());

				/* Broadcast our received data for our receivers */
				/*  TODO We may have to limit this at some point for 
				 * 		 security purposes */
				Intent foundIntent = new Intent(ACTION_UPDATE_FOUND);
				foundIntent.putExtra("NAME", newItem.getName());
				foundIntent.putExtra("ADDRESS", newItem.getAddress());
				foundIntent.putExtra("LOST_STATUS", false);
				sendBroadcast(foundIntent);
				
	        	return;
	        }
	    }
	};
}
