package net.sojourner.projectsidekick.types;

/**
 * Created by francis on 3/23/16.
 */
public class ReportModeInfo {
    private int _channel = 0;
    private long _maxWaitTime = 60000;
    private long _syncTime = 0;

    public ReportModeInfo(int channel, long waitTime, long syncTime) {
        _channel = channel;
        _maxWaitTime = waitTime;
        _syncTime = syncTime;

        return;
    }

    public int getChannel() {
        return _channel;
    }

    public long getMaxWaitTime() {
        return _maxWaitTime;
    }

    public long getSyncTime() {
        return _syncTime;
    }

    public void setChannel(int channel) {
        _channel = channel;
        return;
    }

    public void setMaxWaitTime(long time) {
        _maxWaitTime = time;
        return;
    }

    public void setSyncTime(long time) {
        _syncTime = time;
        return;
    }
}
