package net.sojourner.projectsidekick.utils;

import net.sojourner.projectsidekick.types.PSStatus;
import net.sojourner.projectsidekick.types.ReportModeInfo;

import java.io.StringReader;

/**
 * Created by francis on 3/24/16.
 */
public class SKUtils {
    public static final long DEFAULT_RW_INTERVAL    = 6000;
    public static final int  MAX_DEVICE_NAME_LEN 	= 20;
    public static final int MAX_TX_LEN              = 20;
    public static final int IDX_PAYLOAD_DATA_OFFS   = 4;

    private static final long DEFAULT_RW_INTERVAL_OFFS  = 6000;
    private static final long DEFAULT_SLEEP_TIME 		= 15000;
    private static final long SEC_TO_MS                 = 1000;

    public static String compressDeviceName(String name) {
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

    public static String compressDeviceAddress(String addr) {
        return addr.replace(":", "");
    }
    public static String restoreDeviceAddress(String addr) {
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

    public static long calculateChannelWindow(int iChannel) {
        return (iChannel * DEFAULT_RW_INTERVAL) - DEFAULT_RW_INTERVAL_OFFS;
    }

    public static PSStatus updateReportParams(ReportModeInfo reportInfo, byte[] data, int iOffs) {
        int iBytesToRead = data.length - iOffs;
        if (iBytesToRead < 0) {
            Logger.err("No bytes to read after header");
            return PSStatus.FAILED;
        }

        String dataStr = new String(data, 4, iBytesToRead).trim().replace(";", "");
        String dataPart[] = dataStr.split(",");

        int iChannel = 0;
        int iSyncTimeSecs = 0;
        long lMaxTime = 0;
        try {
            iChannel = Integer.parseInt(dataPart[0]);
            iSyncTimeSecs = Integer.parseInt(dataPart[1]);
            lMaxTime = Long.parseLong(dataPart[2]);
        } catch (NumberFormatException e) {
            Logger.err("Failed to parse report param data: " + dataStr);
            return PSStatus.FAILED;
        }

        /* Clamp sync time to at least one second */
        if (iSyncTimeSecs < 1) {
            iSyncTimeSecs = 1;
        }

        reportInfo.setChannel( iChannel );
        reportInfo.setMaxWaitTime( lMaxTime );
        reportInfo.setSyncTime( System.currentTimeMillis() + (iSyncTimeSecs * SEC_TO_MS) +
                calculateChannelWindow(iChannel) );

        return PSStatus.OK;
    }

    /* TODO !!! This part badly needs refactoring */
    public static String decodeBinaryDeviceList(byte[] list, int iInitOffs) {
        String dvcListStr = "";
        String rawListStr = new String(list, iInitOffs, (list.length-iInitOffs) );

        String rawListPart[] = rawListStr.split(",");

        for (int iIdx = 0; iIdx < rawListPart.length; iIdx++) {
            StringReader sr = new StringReader(rawListPart[iIdx]);

            Logger.info("Raw Device String: " + rawListPart[iIdx]);

            try {
                int iDvcId = sr.read();
				/* Waste an extra read if our byte is '0' */
                if (iDvcId == 0) {
                    sr.read();
                }

				/* If our device id is in ASCII, map it to the actual integer value through subtraction */
                if (iDvcId >= 49) {
                    iDvcId -= 48;
                }
                Logger.info("Device Id: " + iDvcId);

                int iStatus = sr.read();
                Logger.info("Device Status: " + (iStatus > '0' ? "Guarded" : "Not Guarded"));
				/* Waste an extra read if our byte is '0' */
                if (iStatus == '0') {
                    sr.read();
                }

                char cHexPair[] = new char[2];

				/* Capture the device name */
                String deviceName = "";
                int cConv = 0;
                int iCount = 0;
                while (sr.read(cHexPair, 0, 2) > 0) {
					/* Check if our Hex Pair is sane */
                    if ((cHexPair[0] < ' ') || (cHexPair[0] > '~')) {
                        break;
                    }

                    if ((cHexPair[1] < ' ') || (cHexPair[1] > '~')) {
                        break;
                    }

                    cConv = Integer.decode("0x" + cHexPair[0] + "" + cHexPair[1]);
                    deviceName += (char) cConv;

                    if ((cHexPair[1] == '0') && (cHexPair[0] == '0')) {
                        break;
                    } else if ((cHexPair[0] == ';') || (cHexPair[1] == ';')) {
                        break;
                    }

					/* In the fixed width implementation, we can use the MAX_DEVICE_NAME_LEN */
                    iCount++;
                    if (iCount >= MAX_DEVICE_NAME_LEN) {
                        break;
                    }
                }
                Logger.info("Device Name: " + deviceName.trim());

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
                Logger.info("Device Address: " + SKUtils.restoreDeviceAddress(deviceAddr));

                dvcListStr += iDvcId +
                        "|" + deviceName.trim() +
                        "|" + SKUtils.restoreDeviceAddress(deviceAddr) +
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
}
