package nl.dobots.crownstone.gui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.UUID;

import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.gui.monitor.MonitoringActivity;
import nl.dobots.crownstone.gui.utils.DeviceListAdapter;
import nl.dobots.crownstone.gui.utils.SelectFragment;

/**
 * Select crownstones to monitor their advertisement data. multiple crownstones can be selected.
 * but only if they belong to the same sphere (because of the encryption keys)
 * only crownstones are shown
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class SelectMonitorFragment extends SelectFragment {

	private static final String TAG = SelectControlFragment.class.getCanonicalName();

	// the currently selected proximity UUID. only stones with the same UUID can be selected
	private UUID _selectedProximityUuid;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.frag_select_monitor, container, false);

		_btnScan = (Button) v.findViewById(R.id.btnScan);
		_btnScan.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!_scanning) {
					// clear
					_selectedProximityUuid = null;
					_adapter.clear();

					// start a scan with the given filter
					_selectedItem = BleDeviceFilter.anyStone;
					startScan(_selectedItem);
				} else {
					stopScan();
				}
			}
		});
		if (!CrownstoneDevApp.getInstance().isServiceBound()) {
			_btnScan.setEnabled(false);
		}

		Button btnShow = (Button) v.findViewById(R.id.btnShow);
		btnShow.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String[] selection;
				if ((selection = _adapter.getSelection()).length > 0) {
					stopScan();
					MonitoringActivity.show(getActivity(), _selectedProximityUuid, selection);
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
				UUID proximityUuid = device.getProximityUuid();

				String address = device.getAddress();
				_adapter.toggleSelection(address);

				if (_adapter.getSelectedCount() == 0) {
					// if last selected was cleared, also clear uuid
					_selectedProximityUuid = null;
				} else {
					if (_selectedProximityUuid == null) {
						// if no uuid set so far, use uuid of selected stone
						_selectedProximityUuid = proximityUuid;
					} else {
						// otherwise, only allow selection of stones from same sphere (same uuid)
						if (!_selectedProximityUuid.equals(proximityUuid)) {
							Toast.makeText(getActivity(), "Can only select stones from same sphere", Toast.LENGTH_LONG).show();
							_adapter.toggleSelection(address);
						}
					}
				}
			}
		});

		return v;
	}

}
