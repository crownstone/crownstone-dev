package nl.dobots.tester.gui.control;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.strongloop.android.loopback.callbacks.ObjectCallback;

import java.util.UUID;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.tester.CrownstoneDevApp;
import nl.dobots.tester.R;
import nl.dobots.tester.cfg.Config;
import nl.dobots.tester.gui.utils.ViewPagerActivity;
import nl.dobots.loopback.CrownstoneRestAPI;
import nl.dobots.loopback.loopback.models.Sphere;
import nl.dobots.loopback.loopback.models.Stone;
import nl.dobots.loopback.loopback.repositories.StoneRepository;

public class ControlActivity extends FragmentActivity implements ViewPagerActivity {

	private static final String TAG = ControlActivity.class.getCanonicalName();

	private FragmentPagerAdapter _pagerAdapter;

	private ViewPager _pager;

	private ControlMainFragment _fragControlMain;
	private ControlMeasurementsFragment _fragControlMeasurements;

	private static ControlActivity INSTANCE;
	private CrownstoneDevApp _app;
	private UUID _proximityUuid;
	private StoneRepository _stoneRepository;
	private Stone _currentStone;
	private boolean _pwmEnabled;

	public static ControlActivity getInstance() {
		return INSTANCE;
	}

	private String _address;
	private BleExt _ble;
	
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_viewpager);

		INSTANCE = this;

		_address = getIntent().getStringExtra("address");
		_proximityUuid = (UUID)getIntent().getSerializableExtra("proximityUuid");

		_app = CrownstoneDevApp.getInstance();
		_ble = _app.getBle();

		if (!Config.OFFLINE && !_app.getSettings().isOfflineMode()) {
			Sphere sphere = _app.getSphere(_proximityUuid.toString());
			if (sphere != null) {
				EncryptionKeys keys = _app.getKeys(sphere.getId());

				_ble.enableEncryption(true);
				_ble.getBleBase().setEncryptionKeys(keys);

				_stoneRepository = CrownstoneRestAPI.getStoneRepository();
				_stoneRepository.findByAddress(_address, new ObjectCallback<Stone>() {
					@Override
					public void onSuccess(Stone object) {
						_currentStone = object;
					}

					@Override
					public void onError(Throwable t) {

					}
				});
			}
		}

		initUI();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		_ble.enableEncryption(false);
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

	@Override
	public void disableTouch(boolean disable) {
		_pager.requestDisallowInterceptTouchEvent(disable);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_control, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		switch(id) {
//			case R.id.action_pwm: {
//				enablePwm(!_pwmEnabled);
//				break;
//			}
			case R.id.action_dfu: {
				_ble.resetToBootloader(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "success");
						finish();
					}

					@Override
					public void onError(int error) {
						Log.e(TAG, "error" + error);
					}
				});
				break;
			}
			case R.id.action_factoryreset: {
				_ble.writeFactoryReset(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "success");
						Toast.makeText(ControlActivity.this, "success", Toast.LENGTH_SHORT).show();
						_currentStone.destroy(new VoidCallback() {
							@Override
							public void onSuccess() {
								Toast.makeText(ControlActivity.this, "Stone removed from DB", Toast.LENGTH_SHORT).show();
							}

							@Override
							public void onError(Throwable t) {
								Toast.makeText(ControlActivity.this, "Failed to remove Stone from DB", Toast.LENGTH_LONG).show();
							}
						});
						finish();
					}

					@Override
					public void onError(int error) {
						Log.e(TAG, "error" + error);
						Toast.makeText(ControlActivity.this, "failed to factory reset", Toast.LENGTH_LONG).show();
					}
				});
				break;
			}
		}

		return super.onOptionsItemSelected(item);
	}

//	private void enablePwm(boolean enable) {
//		_pwmEnabled = enable;
//	}

}
