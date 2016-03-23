package net.sojourner.projectsidekick.types;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import net.sojourner.projectsidekick.AppModeConfigBeaconActivity;
import net.sojourner.projectsidekick.R;

import java.util.List;

/**
 * Created by francis on 3/24/16.
 */
public class SidekickListAdapter extends ArrayAdapter<KnownDevice> {
    private Context _context = null;

    public SidekickListAdapter(Context context, List<KnownDevice> list) {
        super(context, R.layout.sidekick_list_item, list);
        _context = context;

        return;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater =
                    (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.sidekick_list_item, parent, false);
        }

        final KnownDevice kd = getItem(position);
        if (kd == null) {
            return convertView;
        }

        ImageButton btnSidekickReg = (ImageButton) convertView.findViewById(R.id.btn_sidekick_reg);
        if (kd.isRegistered()) {
            btnSidekickReg.setImageResource(R.drawable.ic_registered);
        } else {
            btnSidekickReg.setImageResource(R.drawable.ic_unregistered);
        }

        TextView txvName = (TextView) convertView.findViewById(R.id.txv_sidekick_name);
        txvName.setText(kd.getName());

        TextView txvAddr = (TextView) convertView.findViewById(R.id.txv_sidekick_addr);
        txvAddr.setText(kd.getAddress());

        ImageView imgFound = (ImageView) convertView.findViewById(R.id.img_sidekick_found);
        if (kd.getStatus() == KnownDevice.DeviceStatus.FOUND) {
            imgFound.setImageResource(R.drawable.ic_found);
        } else {
            imgFound.setImageResource(R.drawable.ic_not_found);
        }

        convertView.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
					    /* Package the device name and address */
                        Bundle extras = new Bundle();
                        extras.putString("DEVICE_NAME", kd.getName());
                        extras.putString("DEVICE_ADDRESS", kd.getAddress());

					    /* Create the intent */
                        Intent intent = new Intent(_context, AppModeConfigBeaconActivity.class);
                        intent.putExtra("DEVICE_INFO", extras);

					    /* Start the next activity */
                        _context.startActivity(intent);

                        return;
                    }
                }
        );

        return convertView;
    }
}
