package nl.dobots.crownstone.gui;

import android.app.AlertDialog;
import android.bluetooth.le.ScanSettings;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.DeviceListAdapter;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.gui.control.ControlActivity;

/**
 * Select crownstones to control
 * chose to scan for other device types
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class SelectControlFragment extends Fragment implements IBleDeviceCallback, IStatusCallback {

	private static final String TAG = SelectControlFragment.class.getCanonicalName();

	private Button _btnScan;
	private ListView _lvScanList;
	private Spinner _spFilter;

	private BleDeviceList _bleDeviceList;

	private static final int GUI_UPDATE_INTERVAL = 500;
	private long _lastUpdate;
	private BleDeviceFilter _selectedItem;

	private BleExt _bleExt;
	private boolean _scanning;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		_bleExt = CrownstoneDevApp.getInstance().getBle();

		if (Build.VERSION.SDK_INT >= 21) {
			_bleExt.getBleBase().setScanMode(ScanSettings.SCAN_MODE_BALANCED);
		}
	}

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

	private void stopScan() {
		_btnScan.setText(getString(R.string.main_scan));
		_bleExt.stopScan(this);
	}

	private void startScan(BleDeviceFilter filter) {
		_btnScan.setText(getString(R.string.main_stop_scan));
		_bleExt.setScanFilter(filter);
		_bleExt.startScan(true, this);
		_scanning = true;
	}

	private void updateDeviceList() {
		// update the device list. since we are not keeping up a list of devices ourselves, we
		// get the list of devices from the service

		_bleDeviceList = _bleExt.getDeviceMap().getRssiSortedList();
		if (!_bleDeviceList.isEmpty()) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// update the list view
					DeviceListAdapter adapter = ((DeviceListAdapter) _lvScanList.getAdapter());
					adapter.updateList(_bleDeviceList);
					adapter.notifyDataSetChanged();
				}
			});
		}
	}

	@Override
	public void onDeviceScanned(BleDevice device) {
		// by registering to the service as a ScanDeviceListener, the service triggers an
		// event every time a device is scanned. the device in the parameter is already updated
		// i.e. the average RSSI and estimated distance are recalculated.

		// but in this example we are only interested in the list of devices, which can be easily
		// obtained from the library, without the need of keeping up a list ourselves
		if (System.currentTimeMillis() > _lastUpdate + GUI_UPDATE_INTERVAL) {
			_lastUpdate = System.currentTimeMillis();
			updateDeviceList();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == 123) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				startScan(_selectedItem);
			} else {
				Log.e(TAG, "Can't write fingerprints without access to storage!");
			}
		} else if (!_bleExt.handlePermissionResult(requestCode, permissions, grantResults,
				new IStatusCallback() {

					@Override
					public void onError(int error) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
								builder.setTitle("Fatal Error")
										.setMessage("Cannot scan for devices without permissions. Please " +
												"grant permissions or uninstall the app again!")
										.setNeutralButton("OK", new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface dialog, int which) {
												getActivity().finish();
											}
										});
								builder.create().show();
							}
						});
					}

					@Override
					public void onSuccess() {
					}
				})) {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	@Override
	public void onError(int error) {
		_scanning = false;
	}

	@Override
	public void onSuccess() {
		_scanning = false;
	}
}
