package net.sojourner.projectsidekick.types;

import net.sojourner.projectsidekick.utils.Logger;

/**
 * Created by francis on 3/23/16.
 */
public class ReceivedData {
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

        Logger.info("Data received: " + new String(_aData).trim());

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