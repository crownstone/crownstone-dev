package nl.dobots.crownstone.gui.utils;

import android.support.v4.app.Fragment;
import android.widget.Button;
import android.widget.ListView;

import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.callbacks.EventListener;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.bluenet.scanner.BleScanner;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.bluenet.scanner.callbacks.ScanDeviceListener;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.gui.SelectControlFragment;

/**
 * Abstract class, can be used by fragments that want to scan for devices.
 * Provides scanning for devices and binding to service.
 * layout has to be created in the child classes. see SelectControlFragment or SelectMonitorFragment
 * Child classes **have** to assign the Button _btnScan and the ListView _lvScanList
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public abstract class SelectFragment extends Fragment implements ScanDeviceListener {

	private static final String TAG = SelectControlFragment.class.getCanonicalName();

	private static final int GUI_UPDATE_INTERVAL = 500;
	private long _lastUpdate;

	protected boolean _scanning = false;

	private CrownstoneDevApp _app;
//	protected BleScanService _bleService;
	protected BleScanner _bleScanner;

	protected BleDeviceList _bleDeviceList;

	protected Button _btnScan;
	protected ListView _lvScanList;
	protected BleDeviceFilter _selectedItem;
	protected DeviceListAdapter _adapter;

	@Override
	public void onResume() {
//		// if service is not bound yet, register as service bind listener
//		if (!CrownstoneDevApp.getInstance().isServiceBound()) {
//			CrownstoneDevApp.getInstance().registerServiceBindListener(this);
//		} else {
//			// otherwise get the service
//			_bleService = CrownstoneDevApp.getInstance().getScanService();
//			_bleService.registerEventListener(SelectFragment.this);
//		}
		_app = CrownstoneDevApp.getInstance();
		_bleScanner = CrownstoneDevApp.getInstance().getScanner();

		super.onResume();
	}

	@Override
	public void onPause() {
//		// unregister as service bind listener in any case
//		CrownstoneDevApp.getInstance().unregisterServiceBindListener(this);

		super.onPause();
	}

	protected void stopScan() {
		_scanning = false;
		_btnScan.setText(getString(R.string.main_scan));
		_bleScanner.stopScanning();
		// unregister as scan device listener again
		_bleScanner.unregisterScanDeviceListener(this);
	}

	protected void startScan(final BleDeviceFilter filter) {
		_bleScanner.checkReady(true, false, getActivity(), new IStatusCallback() {
			@Override
			public void onSuccess() {
				_bleScanner.setScanFilter(filter);
				_bleScanner.getIntervalScanner().getBleExt().clearDeviceMap();
				_bleScanner.startScanning(new IStatusCallback() {
					@Override
					public void onSuccess() {
						_scanning = true;
						_btnScan.setText(getString(R.string.main_stop_scan));
						// only register as scan device listener before starting the scan <-- why?
						_bleScanner.registerScanDeviceListener(SelectFragment.this);
						_adapter.clear();
						_adapter.notifyDataSetChanged();
					}

					@Override
					public void onError(int error) {

					}
				});
			}

			@Override
			public void onError(int error) {

			}
		});

	}

	private void updateDeviceList() {
		// update the device list. since we are not keeping up a list of devices ourselves, we
		// get the list of devices from the service

		_bleDeviceList = _bleScanner.getIntervalScanner().getBleExt().getDeviceMap().getRssiSortedList();

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

		if (System.currentTimeMillis() > _lastUpdate + GUI_UPDATE_INTERVAL) {
			_lastUpdate = System.currentTimeMillis();
			updateDeviceList();
		}
	}

//	@Override
//	public void onBind(BleScanService service) {
//		_bleService = CrownstoneDevApp.getInstance().getScanService();
//		_bleService.registerEventListener(SelectFragment.this);
//		_btnScan.setEnabled(true);
//	}

//	@Override
//	public void onEvent(Event event) {
//		switch(event) {
//			case BLE_PERMISSIONS_MISSING: {
//				_btnScan.setText(getString(R.string.main_scan));
//				// unregister as scan device listener again
//				_bleService.unregisterScanDeviceListener(this);
//				_scanning = false;
//			}
//		}
//	}
}
