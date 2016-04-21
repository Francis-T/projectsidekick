package net.sojourner.projectsidekick.types;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.v7.app.AppCompatActivity;

import net.sojourner.projectsidekick.ProjectSidekickService;
import net.sojourner.projectsidekick.utils.Logger;

/**
 * Created by francis on 2/20/16.
 */
public abstract class ServiceBindingActivity extends AppCompatActivity {
    private Messenger _messenger    = null;
    private Messenger _service      = null;
    private boolean   _bIsBound     = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		/* Bind to the service if not yet bound */
        if (!_bIsBound) {
            Intent bindServiceIntent = new Intent(this, ProjectSidekickService.class);
            bindService(bindServiceIntent, _serviceConnection, Context.BIND_AUTO_CREATE);
        }

        return;
    }

    @Override
    protected void onDestroy() {
		/* Unbind from our service */
        if (_bIsBound) {
            unbindService(_serviceConnection);
            _bIsBound = false;
        }

        super.onDestroy();
        return;
    }

    /* ******************* */
    /* Protected Functions */
    /* ******************* */
    protected PSStatus setMessageHandler(Handler h) {
        _messenger = new Messenger(h);
        return PSStatus.OK;
    }

    protected PSStatus unsetMessageHandler() {
        _messenger = null;
        return PSStatus.OK;
    }

    protected PSStatus queryService(int msgId) {
        return callService(msgId, null, _messenger);
    }

    protected PSStatus queryService(int msgId, Bundle extras) {
        return callService(msgId, extras, _messenger);
    }

    protected PSStatus callService(int msgId) {
        return callService(msgId, null, null);
    }

    protected PSStatus callService(int msgId, Bundle extras, Messenger localMessenger) {
        if (_service == null) {
            Logger.err("Service unavailable");
            return PSStatus.FAILED;
        }

        if (!_bIsBound) {
            Logger.err("Service unavailable");
            return PSStatus.FAILED;
        }

        Message msg = Message.obtain(null, msgId, 0, 0);
        msg.replyTo = localMessenger;
        msg.setData(extras);

        try {
            _service.send(msg);
        } catch (Exception e) {
            Logger.err("Failed to call service: " + e.getMessage());
            return PSStatus.FAILED;
        }

        return PSStatus.OK;
    }

    /* ********************* */
    /* Private Inner Classes */
    /* ********************* */
    private ServiceConnection _serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName className) {
            _service = null;
            _bIsBound = false;

            return;
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            _service = new Messenger(binder);
            _bIsBound = true;

            return;
        }
    };
}
