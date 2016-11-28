package nl.dobots.crownstone.gui.utils;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.widget.Button;
import android.widget.ListView;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.bluenet.service.callbacks.ScanDeviceListener;
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
public abstract class SelectFragment extends Fragment implements ScanDeviceListener, ServiceBindListener {

	private static final String TAG = SelectControlFragment.class.getCanonicalName();

	private static final int GUI_UPDATE_INTERVAL = 500;
	private long _lastUpdate;

	protected boolean _scanning;

	protected BleScanService _bleService;

	protected BleDeviceList _bleDeviceList;

	protected Button _btnScan;
	protected ListView _lvScanList;
	protected BleDeviceFilter _selectedItem;

	@Override
	public void onResume() {
		// if service is not bound yet, register as service bind listener
		if (!CrownstoneDevApp.getInstance().isServiceBound()) {
			CrownstoneDevApp.getInstance().registerServiceBindListener(this);
		} else {
			// otherwise get the service
			_bleService = CrownstoneDevApp.getInstance().getScanService();
		}

		super.onResume();
	}

	@Override
	public void onPause() {
		// unregister as service bind listener in any case
		CrownstoneDevApp.getInstance().unregisterServiceBindListener(this);

		super.onPause();
	}

	protected void stopScan() {
		_btnScan.setText(getString(R.string.main_scan));
		_bleService.stopIntervalScan();
		// unregister as scan device listener again
		_bleService.unregisterScanDeviceListener(this);
		_scanning = false;
	}

	protected void startScan(BleDeviceFilter filter) {
		_btnScan.setText(getString(R.string.main_stop_scan));
		// only register as scan device listener before starting the scan
		_bleService.registerScanDeviceListener(this);
		_bleService.startIntervalScan(2000, 500, filter);
		_bleService.clearDeviceMap();
		_scanning = true;
	}

	private void updateDeviceList() {
		// update the device list. since we are not keeping up a list of devices ourselves, we
		// get the list of devices from the service

		_bleDeviceList = _bleService.getDeviceMap().getRssiSortedList();
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

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == 123) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				startScan(_selectedItem);
			} else {
				Log.e(TAG, "Can't write fingerprints without access to storage!");
			}
		} else if (!_bleService.getBleExt().handlePermissionResult(requestCode, permissions, grantResults,
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
	public void onBind() {
		_bleService = CrownstoneDevApp.getInstance().getScanService();
		_btnScan.setEnabled(true);
	}
}
