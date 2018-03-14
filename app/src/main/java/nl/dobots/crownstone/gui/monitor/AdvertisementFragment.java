package nl.dobots.crownstone.gui.monitor;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import java.util.UUID;

import nl.dobots.bluenet.ble.base.structs.CrownstoneServiceData;
import nl.dobots.bluenet.ble.extended.callbacks.EventListener;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceMap;
import nl.dobots.bluenet.scanner.BleScanner;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.gui.utils.AdvertisementGraph;

/**
 * show advertisement of a crownstone in a graph
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class AdvertisementFragment extends Fragment implements EventListener {

	private static final String TAG = AdvertisementFragment.class.getCanonicalName();

	private RelativeLayout _layGraph;
	private ImageButton _btnZoomIn;
	private ImageButton _btnZoomOut;
	private ImageButton _btnZoomReset;

	private String _address;

//	private BleScanService _scanService;
	private BleScanner _scanner;
	private AdvertisementGraph _graph;
	private UUID _proximityUuid;

	public static AdvertisementFragment newInstance(String address) {
		AdvertisementFragment f = new AdvertisementFragment();
		Bundle args = new Bundle();
		args.putString("address", address);
		f.setArguments(args);
		return f;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		_graph = new AdvertisementGraph(getActivity());

		_address = getArguments().getString("address");

		_scanner = CrownstoneDevApp.getInstance().getScanner();
		_scanner.registerEventListener(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		_scanner.unregisterEventListener(this);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.activity_advertisements, container, false);

		_layGraph = (RelativeLayout) v.findViewById(R.id.graph);
		_btnZoomIn = (ImageButton) v.findViewById(R.id.zoomIn);
		_btnZoomIn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graph.zoomIn();
			}
		});
		_btnZoomOut = (ImageButton) v.findViewById(R.id.zoomOut);
		_btnZoomOut.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graph.zoomOut();
			}
		});
		_btnZoomReset = (ImageButton) v.findViewById(R.id.zoomReset);
		_btnZoomReset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graph.resetZoom();
			}
		});

		_graph.setView(_layGraph);

		return v;
	}

	@Override
	public void onDestroyView() {
		_graph.removeView(_layGraph);

		super.onDestroyView();
	}

	@Override
	public void onEvent(Event event) {
		switch (event) {
			case SCAN_INTERVAL_START: {
				_scanner.getIntervalScanner().getBleExt().clearDeviceMap();
				break;
			}
			case SCAN_INTERVAL_END: {
				if (getActivity() == null) return;

				BleDeviceMap deviceMap = _scanner.getIntervalScanner().getBleExt().getDeviceMap();
				BleDevice device = deviceMap.getDevice(_address);
				if (device != null) {
					CrownstoneServiceData serviceData = device.getServiceData();
					if (serviceData != null) {
						_graph.onServiceData(device.getName(), serviceData);
					}
				}
				_graph.updateRange();
				break;
			}
		}
	}
}
