package nl.dobots.crownstone.gui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
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
import android.widget.Toast;

import java.util.UUID;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.cfg.Config;
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
	private CrownstoneDevApp _app;
	private BleExt _bleExt;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.frag_select_control, container, false);

		_app = CrownstoneDevApp.getInstance();
		_bleExt = _app.getBle();

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
		_spFilter.setSelection(2);

		// create an empty list to assign to the list view. this will be updated whenever a
		// device is scanned
		_bleDeviceList = new BleDeviceList();
		_adapter = new DeviceListAdapter(getActivity(), _bleDeviceList);

		_lvScanList = (ListView) v.findViewById(R.id.lvScanList);
		_lvScanList.setAdapter(_adapter);
		_lvScanList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// stop scanning for devices. We can't scan and connect to a device at the same time.
				if (_scanning) {
					stopScan();
				}

				final BleDevice device = _bleDeviceList.get(position);

				final String address = device.getAddress();

				if (device.isSetupMode() &&
					!Config.OFFLINE && !_app.getSettings().isOfflineMode())  {

					final AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
					builder.setTitle("Crownstone in Setup Mode");
					builder.setMessage("Do you want to setup the stone " + device.getName() + "?");
					builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							setupStone(device);
						}
					});
					builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							UUID proximityUuid = device.getProximityUuid();
							// start the control activity to switch the device
							Intent intent = new Intent(getActivity(), ControlActivity.class);
							intent.putExtra("address", address);
							intent.putExtra("proximityUuid", proximityUuid);
							startActivity(intent);
						}
					});
					builder.show();

				} else {
					UUID proximityUuid = device.getProximityUuid();
					// start the control activity to switch the device
					Intent intent = new Intent(getActivity(), ControlActivity.class);
					intent.putExtra("address", address);
					intent.putExtra("proximityUuid", proximityUuid);
					startActivity(intent);
				}

			}
		});
		_lvScanList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, long id) {

				final BleDevice device = _bleDeviceList.get(position);
				if (device.isSetupMode()) {

					final AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
					builder.setTitle("Dfu Stone");
					builder.setMessage("Do you want to set the stone " + device.getName() + " into DFU?");
					builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							_bleExt.resetToBootloader(device.getAddress(), new IStatusCallback() {
								@Override
								public void onSuccess() {
									getActivity().runOnUiThread(new Runnable() {
										@Override
										public void run() {
											_bleDeviceList.remove(device);
											_adapter.notifyDataSetChanged();
										}
									});
								}

								@Override
								public void onError(final int error) {
									getActivity().runOnUiThread(new Runnable() {
										@Override
										public void run() {
											Toast.makeText(getActivity(), "failed with error: " + error, Toast.LENGTH_LONG).show();
										}
									});
								}
							});
						}
					});

					builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							/* nothing to do */
						}
					});
					builder.show();

					return true;
				} else if (device.isStone()) {

					final AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
					builder.setTitle("Recover Stone");
					builder.setMessage("Do you want to recover the stone " + device.getName() + "?");
					builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							recoverStone(device);
						}
					});

					builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							/* nothing to do */
						}
					});
					builder.show();

					return true;
				} else {
					return false;
				}
			}
		});

		return v;
	}

	private void recoverStone(final BleDevice device) {
		final ProgressDialog dlg = ProgressDialog.show(getActivity(), "Recovering Stone " + device.getName(), "Please wait ...", true);
		_bleExt.recover(device.getAddress(), new IStatusCallback() {
			@Override
			public void onSuccess() {
				dlg.dismiss();

				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
//						_bleDeviceList.clear();
						_bleDeviceList.remove(device);
						_adapter.notifyDataSetChanged();
//						_lvScanList.invalidate();
						Toast.makeText(getActivity(), "Stone successfully recovered", Toast.LENGTH_LONG).show();
					}
				});
			}

			@Override
			public void onError(final int error) {
				dlg.dismiss();

				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getActivity(), "failed with error: " + error, Toast.LENGTH_LONG).show();
					}
				});
			}
		});
	}

	private void setupStone(final BleDevice device) {
		_app.executeSetup(getActivity(), device, new IStatusCallback() {
			@Override
			public void onSuccess() {
				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						_bleDeviceList.remove(device);
						_adapter.notifyDataSetChanged();
//						_lvScanList.invalidate();
					}
				});
			}

			@Override
			public void onError(int error) {

			}
		});
	}

}
