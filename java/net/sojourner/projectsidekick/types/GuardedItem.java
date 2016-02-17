package net.sojourner.projectsidekick.types;

public class GuardedItem extends KnownDevice {
	private long 	_lReportWindow = 0;
	private boolean _isLost = false;
	private short	_uRssi	= 0;

	public GuardedItem(String name, String address) {
		super(name, address);
	}
	
	public void updateReportWindow(long offset) {
		_lReportWindow = System.currentTimeMillis() + offset;
		return;
	}
	
	public boolean isReportWindowExceeded() {
		if (System.currentTimeMillis() > _lReportWindow) {
			return true;
		}
		return false;
	}
	
	public boolean isLost() {
		return _isLost;
	}

	public short getRssi() {
		return _uRssi;
	}
	
	public void setIsLost(boolean isLost) {
		_isLost = isLost;
		return;
	}

	public void setRssi(short uRssi) {
		_uRssi = uRssi;
		return;
	}
	
	/* TODO Should probably make all of this thread-safe */
	
	public String toString() {
		String presence = _isLost ? "LOST" : "PRESENT";
		return getName() + " / " + getAddress() + " - " + presence + " " +
				"(" + _uRssi + ")";
	}
}
