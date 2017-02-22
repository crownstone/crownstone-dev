package nl.dobots.crownstone.gui.utils;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.crownstone.R;

/**
 * This is just a simple list adapter to show the list of scanned devices. It checks the
 * type of device, and displays additional information for iBeacon devices (such as minor,
 * major, proximity UUID, and estimated distance). The devices are color coded to show
 * what type of device it is:
 * 		* Green: Crownstone
 * 		* Yellow: Guidestone
 * 		* Blue: iBeacon
 * 		* Black: any other BLE device
 *
 * Provides multi-selection of devices. a selected device will be shown in red.
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class DeviceListAdapter extends BaseAdapter {

	private static final String TAG = DeviceListAdapter.class.getCanonicalName();

	// the context associated with the adapter
	private Context _context;
	// the list of devices in the adapter
	private BleDeviceList _arrayList;
	// the list of selected devices (MAC address)
	private ArrayList<String> _selection = new ArrayList<>();

	public DeviceListAdapter(Context context, BleDeviceList array) {
		_context = context;
		_arrayList = array;
	}

	/**
	 * Select a device
	 * @param address the mac address of the device to be selected
	 */
	public void select(String address) {
		_selection.add(address);
		notifyDataSetInvalidated();
	}

	/**
	 * DeSelect a device
	 * @param address the mac address of the device to be deselected
	 */
	public void deselect(String address) {
		_selection.remove(address);
		notifyDataSetInvalidated();
	}

	/**
	 * Toggle the selection state of a device
	 * @param address the mac address of the device to be deselected
	 * @return true if the device was selected, false otherwise
	 */
	public boolean toggleSelection(String address) {
		boolean selected;
		if (_selection.contains(address)) {
			_selection.remove(address);
			selected = false;
		} else {
			_selection.add(address);
			selected = true;
		}
		notifyDataSetInvalidated();
		return selected;
	}

	/**
	 * How many items are in the data set represented by this Adapter.
 	 */
	@Override
	public int getCount() {
		return _arrayList.size();
	}

	/**
	 * Get the data item associated with the specified position in the data set.
	 * @param position the position of the device
	 * @return the device
	 */
	@Override
	public Object getItem(int position) {
		Log.i(TAG, String.valueOf(_arrayList.get(position)));
		return _arrayList.get(position);
	}

	/**
	 * Return the id of the device at the position (which is the same as the position)
	 * @param position the position
	 * @return the id (position)
	 */
	@Override
	public long getItemId(int position) {
		return position;
	}

	/**
	 * Update the adapter with a new list of devices
	 * @param list the new list
	 */
	public void updateList(BleDeviceList list) {
		_arrayList = list;
		notifyDataSetInvalidated();
	}

	/**
	 * Get the list of selected devices as a list of MAC addresses
	 * @return list of MAC addresses
	 */
	public String[] getSelection() {
		String[] result = new String[_selection.size()];
		_selection.toArray(result);
		return result;
	}

	/**
	 * Get the list of selected devices
	 * @return list of selected devices
	 */
	public BleDevice[] getSelectedDevices() {
		BleDevice[] result = new BleDevice[_selection.size()];
		for (int i = 0; i < _selection.size(); ++i) {
			result[i] = _arrayList.getDevice(_selection.get(i));
		}
		return result;
	}

	/**
	 * Get the number of selected devices
	 * @return
	 */
	public int getSelectedCount() {
		return _selection.size();
	}

	/**
	 * Clear the adapter
	 */
	public void clear() {
		_selection.clear();
		_arrayList.clear();
	}

	// View Holder improves display of list view elements
	private class ViewHolder {

		TextView devName;
		TextView devAddress;
		TextView devRssi;
		TextView devUUID;
		TextView devMajor;
		TextView devMinor;
		LinearLayout layIBeacon;
		TextView devDistance;

	}

	// Get a View that displays the data at the specified position in the data set.
	// You can either create a View manually or inflate it from an XML layout file.
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		if(convertView == null){
			// LayoutInflater class is used to instantiate layout XML file into its corresponding View objects.
			LayoutInflater layoutInflater = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = layoutInflater.inflate(R.layout.list_row, null);
			final ViewHolder viewHolder = new ViewHolder();

			viewHolder.devName = (TextView) convertView.findViewById(R.id.devName);
			viewHolder.devAddress = (TextView) convertView.findViewById(R.id.devAddress);
			viewHolder.devRssi = (TextView) convertView.findViewById(R.id.devRssi);
			viewHolder.devUUID = (TextView) convertView.findViewById(R.id.devUUID);
			viewHolder.devMajor = (TextView) convertView.findViewById(R.id.devMajor);
			viewHolder.devMinor = (TextView) convertView.findViewById(R.id.devMinor);
			viewHolder.layIBeacon = (LinearLayout) convertView.findViewById(R.id.layIBeacon);
			viewHolder.devDistance = (TextView) convertView.findViewById(R.id.devDistance);

			convertView.setTag(viewHolder);
		}

		ViewHolder viewHolder = (ViewHolder) convertView.getTag();

		if (!_arrayList.isEmpty()) {
			BleDevice device = _arrayList.get(position);
			viewHolder.devName.setText(device.getName());
			viewHolder.devRssi.setText(String.valueOf(device.getAverageRssi()));
			viewHolder.devAddress.setText("[" + device.getAddress() + "]");

			if (device.isIBeacon()) {
				// if the device is an iBeacon, show additional information
				viewHolder.layIBeacon.setVisibility(View.VISIBLE);

				viewHolder.devUUID.setText("UUID: " + device.getProximityUuid());
				viewHolder.devMajor.setText("Major: " + String.valueOf(device.getMajor()));
				viewHolder.devMinor.setText("Minor: " + String.valueOf(device.getMinor()));
				viewHolder.devDistance.setText("Distance: " + String.valueOf(device.getDistance()));

				// a guidestone is also an iBeacon
				if (device.isGuidestone()) {
					convertView.setBackgroundColor(0x66FFFF00);
				} else {
					convertView.setBackgroundColor(0x660000FF);
				}
			} else {
				viewHolder.layIBeacon.setVisibility(View.GONE);
				// is it a crownstone?
				if (device.isCrownstonePlug()) {
					convertView.setBackgroundColor(0x6600FF00);
				} else if (device.isCrownstoneBuiltin()) {
					convertView.setBackgroundColor(0x66008800);
				} else {
					convertView.setBackgroundColor(0x00000000);
				}
			}
			if (_selection.contains(device.getAddress())) {
				convertView.setBackgroundColor(0x66FF0000);
			}
		}

		return convertView;
	}

}