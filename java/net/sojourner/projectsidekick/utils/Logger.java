package net.sojourner.projectsidekick.utils;

import android.util.Log;

public class Logger {
	public static void err(String msg) {
		Log.e("Error", msg);
		return;
	}
	public static void info(String msg) {
		Log.i("Info", msg);
		return;
	}
	public static void warn(String msg) {
		Log.w("Warn", msg);
		return;
	}

	public static void dbg(String msg) {
		Log.d("Debug", msg);
		return;
	}
}
