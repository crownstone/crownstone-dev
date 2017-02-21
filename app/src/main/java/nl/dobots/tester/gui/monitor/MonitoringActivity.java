package nl.dobots.tester.gui.monitor;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;

import com.strongloop.android.loopback.callbacks.ObjectCallback;

import java.io.Serializable;
import java.util.HashMap;
import java.util.UUID;

import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.crownstone.cfg.Config;
import nl.dobots.loopback.CrownstoneRestAPI;
import nl.dobots.loopback.loopback.models.Sphere;
import nl.dobots.loopback.loopback.models.Stone;
import nl.dobots.tester.CrownstoneDevApp;
import nl.dobots.tester.R;
import nl.dobots.tester.gui.utils.ViewPagerActivity;

public class MonitoringActivity extends AppCompatActivity implements ViewPagerActivity {

	private static final String TAG = MonitoringActivity.class.getCanonicalName();

	public static final int LOW_SCAN_INTERVAL = 1000;
	public static final int LOW_SCAN_PAUSE = 1000;

	private FragmentPagerAdapter _pagerAdapter;

	private ViewPager _pager;

	private static MonitoringActivity INSTANCE;
	private String[] _addresses;
	private UUID _proximityUuid;

	public static MonitoringActivity getInstance() {
		return INSTANCE;
	}

	private HashMap<Integer, AdvertisementFragment> _fragments = new HashMap<>();

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_viewpager);

		INSTANCE = this;

		_addresses = getIntent().getStringArrayExtra("addresses");
		_proximityUuid = (UUID)getIntent().getSerializableExtra("proximityUuid");

		initUI();

		CrownstoneDevApp app = CrownstoneDevApp.getInstance();
		BleScanService scanService = app.getScanService();

		if (!Config.OFFLINE && !app.getSettings().isOfflineMode()) {
			Sphere sphere = app.getSphere(_proximityUuid.toString());
			if (sphere != null) {
				EncryptionKeys keys = app.getKeys(sphere.getId());
				scanService.getBleExt().enableEncryption(true);
				scanService.getBleExt().getBleBase().setEncryptionKeys(keys);
			}
		}

		scanService.clearDeviceMap();
		scanService.startIntervalScan(LOW_SCAN_INTERVAL, LOW_SCAN_PAUSE, BleDeviceFilter.anyStone);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		CrownstoneDevApp.getInstance().getScanService().stopIntervalScan();
	}

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
						getSupportActionBar().setSelectedNavigationItem(position);
					}
				});

		final ActionBar actionBar = getSupportActionBar();

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

	@Override
	public void disableTouch(boolean disable) {
		_pager.requestDisallowInterceptTouchEvent(disable);
	}
}
