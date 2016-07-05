package nl.dobots.crownstone;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleExt;

public class ControlActivity extends FragmentActivity {

	private static final String TAG = ControlActivity.class.getCanonicalName();

	private FragmentPagerAdapter _pagerAdapter;

	private ViewPager _pager;

	private ControlMainFragment _fragControlMain;
	private ControlMeasurementsFragment _fragControlMeasurements;

	private static ControlActivity INSTANCE;

	public static ControlActivity getInstance() {
		return INSTANCE;
	}

	private String _address;
	private BleExt _ble;
	
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_control);

		INSTANCE = this;

		_address = getIntent().getStringExtra("address");

		// create our access point to the library, and make sure it is initialized (if it
		// wasn't already)
		_ble = new BleExt();
		_ble.init(this, new IStatusCallback() {
			@Override
			public void onSuccess() {
				Log.v(TAG, "onSuccess");
			}

			@Override
			public void onError(int error) {
				Log.e(TAG, "onError: " + error);
			}
		});

		initUI();
	}

	private void initUI() {
		_fragControlMain = new ControlMainFragment();
		_fragControlMeasurements = new ControlMeasurementsFragment();

		_pagerAdapter = new FragmentPagerAdapter(getSupportFragmentManager()) {
			@Override
			public Fragment getItem(int position) {
				if (position == 0) {
					return _fragControlMain;
				} else {
					return _fragControlMeasurements;
				}
			}

			@Override
			public int getCount() {
				return 2;
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

		actionBar.addTab(
				actionBar.newTab()
						.setText("Control")
						.setTabListener(tabListener));

		actionBar.addTab(
				actionBar.newTab()
						.setText("Measure")
						.setTabListener(tabListener));

	}

	public BleExt getBle() {
		return _ble;
	}

	public String getAddress() {
		return _address;
	}
}
