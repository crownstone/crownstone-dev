package nl.dobots.crownstone.gui.monitor;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;

import java.util.HashMap;

import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.crownstone.R;

public class MonitoringActivity extends FragmentActivity {

	private static final String TAG = MonitoringActivity.class.getCanonicalName();

	public static final int LOW_SCAN_INTERVAL = 1000;
	public static final int LOW_SCAN_PAUSE = 1000;

	private FragmentPagerAdapter _pagerAdapter;

	private ViewPager _pager;

	private static MonitoringActivity INSTANCE;
	private String[] _addresses;

	public static MonitoringActivity getInstance() {
		return INSTANCE;
	}

	private HashMap<Integer, AdvertisementFragment> _fragments = new HashMap<>();

	private BleScanService _service;

	public BleScanService getService() {
		return _service;
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_viewpager);

		INSTANCE = this;

		_addresses = getIntent().getStringArrayExtra("addresses");

		initUI();

		// create and bind to the BleScanService
		Intent intent = new Intent(this, BleScanService.class);
		bindService(intent, _connection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		for (int i = 0; i < _pagerAdapter.getCount(); ++i) {
			_service.unregisterIntervalScanListener((AdvertisementFragment)_pagerAdapter.getItem(i));
		}

		_service.stopIntervalScan();
		unbindService(_connection);
	}

	// if the service was connected successfully, the service connection gives us access to the service
	private ServiceConnection _connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "connected to ble scan service ...");
			// get the service from the binder
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_service = binder.getService();

//			// register as event listener. Events, like bluetooth initialized, and bluetooth turned
//			// off events will be triggered by the service, so we know if the user turned bluetooth
//			// on or off
//			_service.registerEventListener(MonitoringActivity.this);
//
//			// register as a scan device listener. If you want to get an event every time a device
//			// is scanned, then this is the choice for you.
//			_service.registerScanDeviceListener(MonitoringActivity.this);
//			// register as an interval scan listener. If you only need to know the list of scanned
//			// devices at every end of an interval, then this is better. additionally it also informs
//			// about the start of an interval.
//			_service.registerIntervalScanListener(MonitoringActivity.this);

			for (int i = 0; i < _pagerAdapter.getCount(); ++i) {
				_service.registerIntervalScanListener((AdvertisementFragment)_pagerAdapter.getItem(i));
			}

			_service.startIntervalScan(LOW_SCAN_INTERVAL, LOW_SCAN_PAUSE, BleDeviceFilter.crownstone);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "disconnected from service");
		}
	};

	private void initUI() {

		_pagerAdapter = new FragmentPagerAdapter(getSupportFragmentManager()) {
			@Override
			public Fragment getItem(int position) {
				AdvertisementFragment fragment;
				if ((fragment = _fragments.get(position)) == null) {
					fragment = AdvertisementFragment.newInstance(_addresses[position]);
					_fragments.put(position, fragment);
				}
				return fragment;
			}

			@Override
			public int getCount() {
				return _addresses.length;
			}
		};

		_pager = (ViewPager) findViewById(R.id.pager);
		_pager.setAdapter(_pagerAdapter);
		_pager.setOnPageChangeListener(
				new ViewPager.SimpleOnPageChangeListener() {
					@Override
					public void onPageSelected(int position) {
						// When swiping between pages, select the
						// corresponding tab.
						getActionBar().setSelectedNavigationItem(position);
					}
				});

		final ActionBar actionBar = getActionBar();

		// Specify that tabs should be displayed in the action bar.
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// Create a tab listener that is called when the user changes tabs.
		ActionBar.TabListener tabListener = new ActionBar.TabListener() {
			public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
				// show the given tab
				_pager.setCurrentItem(tab.getPosition());
			}

			public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
				// hide the given tab
			}

			public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
				// probably ignore this event
			}
		};

		for (String address: _addresses) {
			actionBar.addTab(
					actionBar.newTab()
							.setText(String.format("%s", address))
							.setTabListener(tabListener));
		}

	}

}
