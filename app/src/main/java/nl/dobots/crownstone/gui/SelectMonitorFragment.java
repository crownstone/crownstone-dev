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
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.DeviceListAdapter;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.gui.monitor.MonitoringActivity;

/**
 * Select crownstones to monitor their advertisement data. multiple crownstones can be selected.
 * only crownstones are shown
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class SelectMonitorFragment extends Fragment implements IBleDeviceCallback, IStatusCallback {

	private static final String TAG = SelectControlFragment.class.getCanonicalName();

	private Button _btnScan;
	private ListView _lvScanList;

	private BleDeviceList _bleDeviceList;

	private static final int GUI_UPDATE_INTERVAL = 500;
	private long _lastUpdate;
	private DeviceListAdapter _adapter;

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
		View v = inflater.inflate(R.layout.frag_select_monitor, container, false);

		_btnScan = (Button) v.findViewById(R.id.btnScan);
		_btnScan.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!_scanning) {
					// start a scan with the given filter
					_adapter.clear();
					startScan();
				} else {
					stopScan();
				}
			}
		});

		Button btnShow = (Button) v.findViewById(R.id.btnShow);
		btnShow.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String[] selection;
				if ((selection = _adapter.getSelection()).length > 0) {
					stopScan();

					Intent intent = new Intent(getActivity(), MonitoringActivity.class);
					intent.putExtra("addresses", selection);
					startActivity(intent);
				} else {
					Toast.makeText(getActivity(), "No device(s) selected", Toast.LENGTH_LONG).show();
				}
			}
		});

		// create an empty list to assign to the list view. this will be updated whenever a
		// device is scanned
		_bleDeviceList = new BleDeviceList();
		_adapter = new DeviceListAdapter(getActivity(), _bleDeviceList);

		_lvScanList = (ListView) v.findViewById(R.id.lvScanList);
		_lvScanList.setAdapter(_adapter);
		_lvScanList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				BleDevice device = _bleDeviceList.get(position);
				String address = device.getAddress();
				_adapter.toggleSelection(address);
			}
		});

		return v;
	}

	private void stopScan() {
		_btnScan.setText(getString(R.string.main_scan));
		_bleExt.stopScan(this);
	}

	private void startScan() {
		_btnScan.setText(getString(R.string.main_stop_scan));
		_bleExt.setScanFilter(BleDeviceFilter.crownstone);
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
				startScan();
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
