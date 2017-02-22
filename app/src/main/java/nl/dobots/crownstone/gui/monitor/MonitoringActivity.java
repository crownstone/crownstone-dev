package nl.dobots.crownstone.gui.monitor;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;

import java.util.HashMap;
import java.util.UUID;

import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.cfg.Config;
import nl.dobots.crownstone.gui.utils.ViewPagerActivity;
import nl.dobots.loopback.loopback.models.Sphere;

/**
 * The Monitoring activity shows a set of stones in different fragments and displays the
 * service data in separate graphs.
 * Only stones from the same sphere (UUID) can be shown because of the encryption keys
 *
 * Provide parameters EXTRA_ADDRESSES and EXTRA_PROXIMITY_UUID in the intent.
 *
 */
public class MonitoringActivity extends AppCompatActivity implements ViewPagerActivity {

	private static final String TAG = MonitoringActivity.class.getCanonicalName();

	// used for the intent extras to provide the address of the stone to control
	public static final String EXTRA_ADDRESSES = "addresses";
	// used for the intent extras to provide the UUID of the sphere to which the stone belongs
	public static final String EXTRA_PROXIMITY_UUID = "proximityUuid";

	// scan interval
	public static final int LOW_SCAN_INTERVAL = 1000;
	// scan pause
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

	/**
	 * Show monitoring activity
	 * @param context the context which wants to show the activity
	 * @param proximityUuid the proximity UUID of the sphere (to which the stones belong)
	 * @param addresses an array of device MAC addresses
	 */
	public static void show(Context context, UUID proximityUuid, String[] addresses) {
		Intent intent = new Intent(context, MonitoringActivity.class);
		intent.putExtra(EXTRA_ADDRESSES, addresses);
		intent.putExtra(EXTRA_PROXIMITY_UUID, proximityUuid);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_viewpager);

		INSTANCE = this;

		// get the list of addresses and the proximity uuid from the intent
		_addresses = getIntent().getStringArrayExtra(EXTRA_ADDRESSES);
		_proximityUuid = (UUID)getIntent().getSerializableExtra(EXTRA_PROXIMITY_UUID);

		// set up the user interface
		initUI();

		CrownstoneDevApp app = CrownstoneDevApp.getInstance();
		BleScanService scanService = app.getScanService();

		// retrieve the sphere by proximity uuid
		if (!Config.OFFLINE && !app.getSettings().isOfflineMode()) {
			Sphere sphere = app.getSphere(_proximityUuid.toString());
			if (sphere != null) {
				// and set the encryption keys
				EncryptionKeys keys = app.getKeys(sphere.getId());
				scanService.getBleExt().enableEncryption(true);
				scanService.getBleExt().getBleBase().setEncryptionKeys(keys);
			}
		}

		// clear the device map and start scanning
		scanService.clearDeviceMap();
		scanService.startIntervalScan(LOW_SCAN_INTERVAL, LOW_SCAN_PAUSE, BleDeviceFilter.anyStone);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// stop scanning
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
