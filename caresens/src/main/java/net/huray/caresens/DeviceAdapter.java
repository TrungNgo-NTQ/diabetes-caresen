package net.huray.caresens;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import net.huray.caresens.ble.ExtendedDevice;

import java.util.ArrayList;

/**
 * Created by user on 2016-08-10.
 */
public class DeviceAdapter extends BaseAdapter {

    private final Context mContext;
    private final ArrayList<ExtendedDevice> _listItems = new ArrayList<ExtendedDevice>();


    public DeviceAdapter(Context context) {
        mContext = context;
    }

    public void clearDevices() {
        _listItems.clear();
        notifyDataSetChanged();
    }

    public void addDevice(ExtendedDevice device) {
        final int indexInNotBonded = _listItems.indexOf(device);
        if (indexInNotBonded >= 0) {
            ExtendedDevice previousDevice = _listItems.get(indexInNotBonded);
            previousDevice.rssi = device.rssi;
            notifyDataSetChanged();
            return;
        }
        _listItems.add(device);
        notifyDataSetChanged();

    }

    @Override
    public int getCount() {
        return _listItems.size();
    }

    @Override
    public Object getItem(int position) {
        return _listItems.get(position);
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View oldView, ViewGroup parent) {
        final LayoutInflater inflater = LayoutInflater.from(mContext);

        View view = oldView;

        if (view == null) {
            view = inflater.inflate(R.layout.device_list_row, parent, false);
            final ViewHolder holder = new ViewHolder();
            holder.name = (TextView) view.findViewById(R.id.name);
            holder.address = (TextView) view.findViewById(R.id.address);
            holder.rssi = (ImageView) view.findViewById(R.id.rssi);
            view.setTag(holder);
        }

        final ExtendedDevice device = (ExtendedDevice) getItem(position);
        final ViewHolder holder = (ViewHolder) view.getTag();
        holder.name.setText(device.device.getName());
        holder.address.setText(device.device.getAddress());

        return view;

    }

    private class ViewHolder {
        private TextView name;
        private TextView address;
        private ImageView rssi;
    }
}
