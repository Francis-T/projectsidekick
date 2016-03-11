package net.sojourner.projectsidekick.types;

import net.sojourner.projectsidekick.utils.Logger;

/**
 * Created by francis on 2/20/16.
 */
public class MasterListItem {
    private int     _id = -1;
    private String 	_name = "";
    private String 	_addr = "";
    private boolean _bIsGuarded = false;
    private static String _delim = "\\|";

    /**
     * A convenience function which directly parses an item string intended
     *  for the master list into a MasterListItem object.
     *
     *  Format: "<device name>|<address>|<guardStatus>"
     *
     * @param itemStr
     * @return
     */
    public static MasterListItem parse(String itemStr) {
        String itemStrPart[] = itemStr.split(_delim);
        if (itemStrPart.length != 4) {
            Logger.err("Invalid input string: " + itemStrPart.length);
            return null;
        }
        int id = -1;
        try {
            id = Integer.valueOf(itemStrPart[0]);
        } catch (NumberFormatException e) {
            id = -1;
        }
        return new MasterListItem(id, itemStrPart[1], itemStrPart[2], itemStrPart[3]);
    }

    public MasterListItem(String name, String address, String guardStatus) {
        _name = name;
        _addr = address;

        if (guardStatus.equalsIgnoreCase("Guarded")) {
            _bIsGuarded = true;
        } else {
            _bIsGuarded = false;
        }

        return;
    }

    public MasterListItem(int id, String name, String address, String guardStatus) {
        _id = id;
        _name = name;
        _addr = address;

        if (guardStatus.equalsIgnoreCase("Guarded")) {
            _bIsGuarded = true;
        } else {
            _bIsGuarded = false;
        }

        return;
    }

    public int getId() {
        return _id;
    }

    public String getAddress() {
        return _addr;
    }

    public String getName() {
        return _name;
    }

    public boolean isGuarded() {
        return _bIsGuarded;
    }

    private String getGuardStatus() {
        return ( _bIsGuarded ? "Guarded" : "Not Guarded");
    }

    public String toString() {
        return getId() + ": " + getName() + "\n" +
                getAddress() + "\n" +
                getGuardStatus();
    }
}