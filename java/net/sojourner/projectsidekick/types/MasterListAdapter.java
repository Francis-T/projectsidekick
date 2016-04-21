package net.sojourner.projectsidekick.types;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import net.sojourner.projectsidekick.ProjectSidekickApp;
import net.sojourner.projectsidekick.ProjectSidekickService;
import net.sojourner.projectsidekick.R;
import net.sojourner.projectsidekick.utils.Logger;

/**
 * Created by francis on 3/16/16.
 */
public class MasterListAdapter extends ArrayAdapter<MasterListItem> {
    private Context _context = null;
    private String _lastDeleted = "";
    private boolean _isGuardStatusSaved = true;

    public MasterListAdapter(Context context) {
        super(context, R.layout.master_list_item);

        _context = context;

        return;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater =
                    (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.master_list_item, parent, false );
        }

        MasterListItem item = getItem(position);
        if (item == null) {
            return convertView;
        }

        TextView txvDvcName = (TextView) convertView.findViewById(R.id.txv_dvc_name);
        TextView txvDvcAddr = (TextView) convertView.findViewById(R.id.txv_dvc_addr);

        txvDvcName.setText(item.getName());
        txvDvcAddr.setText(item.getAddress());
        if (item.isModified()) {
            txvDvcName.setTextColor(Color.parseColor("#660E7A"));
            txvDvcAddr.setTextColor(Color.parseColor("#660E7A"));
        } else {
            txvDvcName.setTextColor(Color.BLACK);
            txvDvcAddr.setTextColor(Color.BLACK);
        }

        ImageButton imgGuardStatus = (ImageButton) convertView.findViewById(R.id.img_guard_status);
        if (item.isGuarded()) {
            imgGuardStatus.setColorFilter(Color.parseColor("#008000"));
        } else {
            imgGuardStatus.setColorFilter(Color.GRAY);
        }

        final int itemPos = position;

        ImageButton btnGuardStatus = (ImageButton) convertView.findViewById(R.id.img_guard_status);
        btnGuardStatus.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        MasterListItem listItem = getItem(itemPos);
                        listItem.setGuardStatus(!listItem.isGuarded());
                        listItem.setModifiedStatus(true);
                        notifyDataSetChanged();
                    }
                }
        );

        ImageButton btnDelete = (ImageButton) convertView.findViewById(R.id.btn_delete);
        btnDelete.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!_lastDeleted.equals("")) {
                            display("Last deletion not yet confirmed!");
                            return;
                        }
						/* Pick the master list item for removal and pass it to a dialog box */
                        MasterListItem listItem = getItem(itemPos);
                        showRemoveFromMasterListDialog(listItem.getAddress(), listItem.getId());

                        return;
                    }
                }
        );

        return convertView;
    }

    public void confirmDeletion() {
        if (!_lastDeleted.equals("")) {
            display("Latest deletion confirmed");

            MasterListItem item = getMasterListItem(_lastDeleted);
            if (item != null) {
                this.remove(item);
                this.notifyDataSetChanged();
            }

            _lastDeleted = "";
        }

        return;
    }

    public void confirmModifications() {
        int iSize = this.getCount();

        for (int iIdx = 0; iIdx < iSize; iIdx++) {
            MasterListItem item = getItem(iIdx);
            item.setModifiedStatus(false);
        }

        return;
    }

    public String getGuardSetup() {
        String guardStr = "";
        int iSize = this.getCount();

        for (int iIdx = 0; iIdx < iSize; iIdx++) {
            MasterListItem item = getItem(iIdx);
            guardStr += item.getId();
            guardStr += (item.isGuarded() ? "1" : "0");
        }

        return guardStr;
    }

    private MasterListItem getMasterListItem(String address) {
        int iSize = this.getCount();

        for (int iIdx = 0; iIdx < iSize; iIdx++) {
            MasterListItem item = getItem(iIdx);
            if (item != null) {
                if (address.equals(item.getAddress())) {
                    return item;
                }
            }
        }

        return null;
    }

    private void showRemoveFromMasterListDialog(String address, int id) {
        final String deviceAddr = address;
        final int deviceId = id;

        AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(_context);

        dlgBuilder.setTitle("Remove from Master List");
        dlgBuilder.setMessage(deviceAddr + " will be removed from the SIDEKICK's master list. Is this OK?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Bundle data = new Bundle();
                        data.putInt("DEVICE_ID", deviceId);

                        try {
                            ServiceBindingActivity parentActivity =
                                    (ServiceBindingActivity) _context;
                            parentActivity.callService(ProjectSidekickService.MSG_DELETE_DEVICE, data, null);
                        } catch (Exception e) {
                            Logger.err("Exception occurred: " + e.getMessage());
                            display("Delete failed!");
                            return;
                        }

                        _lastDeleted = deviceAddr;
                        display("Delete requested!");
                        return;
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        display("Cancelled!");
                        dialog.cancel();
                        return;
                    }
                });

        dlgBuilder.create().show();

        return;
    }

    private void display(String msg) {
        Toast.makeText(_context, msg, Toast.LENGTH_SHORT).show();
        return;
    }
}
