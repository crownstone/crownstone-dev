package nl.dobots.crownstone.gui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
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
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.bluenet.service.callbacks.EventListener;
import nl.dobots.bluenet.service.callbacks.IntervalScanListener;
import nl.dobots.bluenet.service.callbacks.ScanDeviceListener;
import nl.dobots.crownstone.DeviceListAdapter;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.gui.control.ControlActivity;

/**
 * This example activity shows the use of the bluenet library through the BleScanService. The
 * service is created on startup. The service takes care of initialization of the bluetooth
 * adapter, listens to state changes of the adapter, notifies listeners about these changes
 * and provides an interval scan. This means the service scans for some time, then pauses for
 * some time before starting another scan (this reduces battery consumption)
 *
 * The following steps are shown:
 *
 * 1. Start and connect to the BleScanService
 * 2. Set the scan interval and scan pause time
 * 3. Scan for devices and set a scan device filter
 * 4a. Register as a listener to get an update for every scanned device, or
 * 4b. Register as a listener to get an event at the start and end of each scan interval
 * 5. How to get the list of scanned devices, sorted by RSSI.
 *
 * For an example of how to read the current PWM state and how to power On, power Off, or toggle
 * the device switch, see ControlActivity.java
 * For an example of how to use the library directly, without using the service, see MainActivity.java
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class SelectControlFragment extends Fragment implements IntervalScanListener, EventListener, ScanDeviceListener {

	private static final String TAG = SelectControlFragment.class.getCanonicalName();

	// scan for 1 second every 3 seconds
	public static final int LOW_SCAN_INTERVAL = 10000; // 1 second scanning
	public static final int LOW_SCAN_PAUSE = 2000; // 2 seconds pause

	private BleScanService _service;

	private Button _btnScan;
	private ListView _lvScanList;
	private Spinner _spFilter;

	private boolean _bound = false;

	private BleDeviceList _bleDeviceList;

	private static final int GUI_UPDATE_INTERVAL = 500;
	private long _lastUpdate;
	private BleDeviceFilter _selectedItem;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// create and bind to the BleScanService
		Intent intent = new Intent(getActivity(), BleScanService.class);
		getActivity().bindService(intent, _connection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		getActivity().unbindService(_connection);
	}

	// if the service was connected successfully, the service connection gives us access to the service
	private ServiceConnection _connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "connected to ble scan service ...");
			// get the service from the binder
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_service = binder.getService();

			// register as event listener. Events, like bluetooth initialized, and bluetooth turned
			// off events will be triggered by the service, so we know if the user turned bluetooth
			// on or off
			_service.registerEventListener(SelectControlFragment.this);

			// register as a scan device listener. If you want to get an event every time a device
			// is scanned, then this is the choice for you.
			_service.registerScanDeviceListener(SelectControlFragment.this);
			// register as an interval scan listener. If you only need to know the list of scanned
			// devices at every end of an interval, then this is better. additionally it also informs
			// about the start of an interval.
			_service.registerIntervalScanListener(SelectControlFragment.this);

			// set the scan interval (for how many ms should the service scan for devices)
			_service.setScanInterval(LOW_SCAN_INTERVAL);
			// set the scan pause (how many ms should the service wait before starting the next scan)
			_service.setScanPause(LOW_SCAN_PAUSE);

			_bound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "disconnected from service");
			_bound = false;
		}
	};

	// is scanning returns true if the service is "running", not if it is currently in a
	// scan interval or a scan pause
	private boolean isScanning() {
		if (_bound) {
			return _service.isRunning();
		}
		return false;
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

				if (!isScanning()) {
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
				if (isScanning()) {
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
		if (_bound) {
			_btnScan.setText(getString(R.string.main_scan));
			// stop scanning for devices
			_service.stopIntervalScan();
		}
	}

	private void startScan(BleDeviceFilter filter) {
		if (_bound) {
			_btnScan.setText(getString(R.string.main_stop_scan));
			// start scanning for devices, only return devices defined by the filter
			_service.clearDeviceMap();
			_service.startIntervalScan(filter);
		}
	}

	private void onBleEnabled() {
		_btnScan.setEnabled(true);
	}

	private void onBleDisabled() {
		_btnScan.setEnabled(false);
	}

	private void updateDeviceList() {
		// update the device list. since we are not keeping up a list of devices ourselves, we
		// get the list of devices from the service

		_bleDeviceList = _service.getDeviceMap().getRssiSortedList();
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
	public void onScanStart() {
		// by registering to the service as an IntervalScanListener, the service informs us
		// whenever a new scan interval is started.

		// but we don't really care about that here
	}

	@Override
	public void onScanEnd() {
		// by registering to the service as an IntervalScanListener, the service informs us
		// whenever a scan interval ends.

		// at this point we can obtain the list of scanned devices from the library to update
		// the list view.
		// Note: this happens much less frequently than the onDeviceScanned event. if you need
		// instant updates for scanned devices, use the ScanDeviceListener instead.
		updateDeviceList();
	}

	@Override
	public void onEvent(Event event) {
		// by registering to the service as an EventListener, we will be informed whenever the
		// user turns bluetooth on or off, or even refuses to enable bluetooth
		switch (event) {
			case BLUETOOTH_INITIALIZED: {
				onBleEnabled();
				break;
			}
			case BLUETOOTH_TURNED_OFF: {
				onBleDisabled();
				break;
			}
			case BLE_PERMISSIONS_MISSING: {
				_service.requestPermissions(getActivity());
			}
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
		} else if (!_service.getBleExt().handlePermissionResult(requestCode, permissions, grantResults,
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
}
