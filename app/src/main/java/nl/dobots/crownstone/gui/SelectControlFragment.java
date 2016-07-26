package nl.dobots.crownstone.gui;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;

import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.gui.control.ControlActivity;
import nl.dobots.crownstone.gui.utils.DeviceListAdapter;
import nl.dobots.crownstone.gui.utils.SelectFragment;

/**
 * Select crownstones to control
 * chose to scan for other device types
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class SelectControlFragment extends SelectFragment {

	private static final String TAG = SelectControlFragment.class.getCanonicalName();

	private Spinner _spFilter;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.frag_select_control, container, false);

		_btnScan = (Button) v.findViewById(R.id.btnScan);
		_btnScan.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				// using the scan filter, we can tell the library to return only specific device
				// types. we are currently distinguish between Crownstones, Guidestones, iBeacons,
				// and FridgeBeacons
				_selectedItem = (BleDeviceFilter) _spFilter.getSelectedItem();

				if (!_scanning) {
					// start a scan with the given filter
					startScan(_selectedItem);
				} else {
					stopScan();
				}
			}
		});

		// create a spinner element with the device filter options
		_spFilter = (Spinner) v.findViewById(R.id.spFilter);
		_spFilter.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_dropdown_item, BleDeviceFilter.values()));
		_spFilter.setSelection(1);

		// create an empty list to assign to the list view. this will be updated whenever a
		// device is scanned
		_bleDeviceList = new BleDeviceList();
		DeviceListAdapter adapter = new DeviceListAdapter(getActivity(), _bleDeviceList);

		_lvScanList = (ListView) v.findViewById(R.id.lvScanList);
		_lvScanList.setAdapter(adapter);
		_lvScanList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// stop scanning for devices. We can't scan and connect to a device at the same time.
				if (_scanning) {
					stopScan();
				}

				BleDevice device = _bleDeviceList.get(position);
				String address = device.getAddress();

				// start the control activity to switch the device
				Intent intent = new Intent(getActivity(), ControlActivity.class);
				intent.putExtra("address", address);
				startActivity(intent);
			}
		});

		return v;
	}

}
