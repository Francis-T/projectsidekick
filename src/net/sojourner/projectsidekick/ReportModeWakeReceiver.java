package net.sojourner.projectsidekick;

import net.sojourner.projectsidekick.utils.Logger;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

public class ReportModeWakeReceiver extends WakefulBroadcastReceiver {
	private AlarmManager _wakeAlarm = null;
	private PendingIntent _wakeAlarmIntent = null;

	@Override
	public void onReceive(Context context, Intent intent) {
		Logger.info("ReportModeWakeReceiver onReceive() started");
		Intent sidekickServiceIntent  = new Intent("net.sojourner.projectsidekick.action.WAKE_TO_REPORT");
		sidekickServiceIntent.putExtra("FROM_WAKE_ALARM", true);
		
		ComponentName cn = context.startService(sidekickServiceIntent);
		if (cn == null) {
			Logger.err("Failed to start service: " + ProjectSidekickService.class.getName());
		} else {
			Logger.info("Started service: " + cn.getShortClassName());
		}
		
		Logger.info("ReportModeWakeReceiver onReceive() finished");
		
		return;
	}
	
	public void setAlarm(Context context, long interval) {
		if (context == null) {
			Logger.err("Invalid context");
			return;
		}
		
		_wakeAlarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		Intent recvTriggerIntent = new Intent(context, ReportModeWakeReceiver.class);
		_wakeAlarmIntent = PendingIntent.getBroadcast(context, 0, recvTriggerIntent, 0);
		
		/* Set the alarm to trigger once after a fifteen minute interval */
		_wakeAlarm.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
				SystemClock.elapsedRealtime() + interval, interval,
				_wakeAlarmIntent);
		Logger.info("Report Mode Wake Alarm set to trigger " + interval + " ms from now");
		return;
	}

	public void cancelAlarm(Context context) {
		if (context == null) {
			Logger.warn("Cancel alarm warning: Invalid context param");
		}
		
		if (_wakeAlarm == null) {
			_wakeAlarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		}
		
		if (_wakeAlarmIntent == null) {
			Intent recvTriggerIntent = new Intent(context, ReportModeWakeReceiver.class);
			_wakeAlarmIntent = PendingIntent.getBroadcast(context, 0, recvTriggerIntent, 0);
		}
		
		_wakeAlarm.cancel(_wakeAlarmIntent);
		
		Logger.info("WakeAlarm cancelled");
		return;
	}
}
