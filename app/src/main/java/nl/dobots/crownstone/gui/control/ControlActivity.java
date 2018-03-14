package nl.dobots.crownstone.gui.control;

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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.strongloop.android.loopback.callbacks.ObjectCallback;
import com.strongloop.android.loopback.callbacks.VoidCallback;

import java.util.UUID;

import nl.dobots.bluenet.ble.base.BleBaseEncryption;
import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.cfg.Config;
import nl.dobots.crownstone.gui.utils.ProgressSpinner;
import nl.dobots.crownstone.gui.utils.ViewPagerActivity;
import nl.dobots.loopback.CrownstoneRestAPI;
import nl.dobots.loopback.loopback.models.Sphere;
import nl.dobots.loopback.loopback.models.Stone;
import nl.dobots.loopback.loopback.repositories.StoneRepository;

/**
 * Control activity for a stone. Provides switching PWM and relay. Shows service data of
 * received advertisements. Provides also a graph for power measurements.
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class ControlActivity extends AppCompatActivity implements ViewPagerActivity {

	private static final String TAG = ControlActivity.class.getCanonicalName();

	// used for the intent extras to provide the address of the stone to control
	public static final String EXTRA_ADDRESS = "address";
	// used for the intent extras to provide the UUID of the sphere to which the stone belongs
	public static final String EXTRA_PROXIMITY_UUID = "proximityUuid";

	private FragmentPagerAdapter _pagerAdapter;

	private ViewPager _pager;

	private ControlMainFragment _fragControlMain;
	private ControlMeasurementsFragment _fragControlMeasurements;
	private ControlConfigFragment _fragControlConfig;
	private ControlScheduleFragment _fragControlSchedule;
	private ControlMeshFragment _fragControlMesh;

	private static ControlActivity INSTANCE;
	private CrownstoneDevApp _app;
	private UUID _proximityUuid;
	private StoneRepository _stoneRepository;
	private Stone _currentStone;

	public static ControlActivity getInstance() {
		return INSTANCE;
	}

	private String _address;
	private BleExt _ble;

	/**
	 * Show the Control activity
	 * @param context the context which wants to show the activity
	 * @param proximityUuid the proximity uuid of the sphere to which the stone belongs
	 * @param address the MAC address of the stone
	 */
	public static void show(Context context, UUID proximityUuid, String address) {
		Intent intent = new Intent(context, ControlActivity.class);
		intent.putExtra(EXTRA_ADDRESS, address);
		intent.putExtra(EXTRA_PROXIMITY_UUID, proximityUuid);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_viewpager);

		INSTANCE = this;

		// retrieve address and proximity uuid from the intent
		_address = getIntent().getStringExtra(EXTRA_ADDRESS);
		_proximityUuid = (UUID)getIntent().getSerializableExtra(EXTRA_PROXIMITY_UUID);

		_app = CrownstoneDevApp.getInstance();
		_ble = _app.getBle();

		// retrieve the sphere by proximity uuid
		if (!Config.OFFLINE && !_app.getSettings().isOfflineMode() && _proximityUuid != null) {
			Sphere sphere = _app.getSphere(_proximityUuid.toString());
			if (sphere != null) {
				// get the encryption keys of the sphere
				EncryptionKeys keys = _app.getKeys(sphere.getId());

				// set the keys on the library
//				_ble.enableEncryption(true);
				_ble.getBleBase().setEncryptionKeys(keys);

				if (keys.getHighestKey().accessLevel != BleBaseEncryption.ACCESS_LEVEL_ADMIN) {
					showToast("No admin access!");
				}

				// get the stone object from the cloud
				_stoneRepository = CrownstoneRestAPI.getStoneRepository();
				_stoneRepository.findByAddress(_address, new ObjectCallback<Stone>() {
					@Override
					public void onSuccess(Stone object) {
						_currentStone = object;
						String title = _address;
						if (_currentStone != null) {
							title += " (" + _currentStone.getUid() + ")";
						}
						setTitle(title);
					}

					@Override
					public void onError(Throwable t) {

					}
				});
			}
			else {
				showToast("Unknown sphere!");
			}
		}

//		// Enable encryption so we can control crownstones in setup
//		_ble.enableEncryption(true);

		initUI();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
//		// disable encryption again
//		_ble.enableEncryption(false);
	}

	private void initUI() {
		setTitle(_address);
		_fragControlMain = new ControlMainFragment();
		_fragControlMeasurements = new ControlMeasurementsFragment();
		_fragControlConfig = new ControlConfigFragment();
		_fragControlSchedule = new ControlScheduleFragment();
		_fragControlMesh = new ControlMeshFragment();

		_pagerAdapter = new FragmentPagerAdapter(getSupportFragmentManager()) {
			@Override
			public Fragment getItem(int position) {
				switch (position) {
					case 0:
						return _fragControlMain;
					case 1:
						return _fragControlMeasurements;
					case 2:
						return _fragControlConfig;
					case 3:
						return _fragControlSchedule;
					case 4:
						return _fragControlMesh;
					default:
						return null;
				}
			}

			@Override
			public int getCount() {
				return 5;
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

		actionBar.addTab(
				actionBar.newTab()
						.setText("Control")
						.setTabListener(tabListener));

		actionBar.addTab(
				actionBar.newTab()
						.setText("Measure")
						.setTabListener(tabListener));

		actionBar.addTab(
				actionBar.newTab()
						.setText("Config")
						.setTabListener(tabListener));

		actionBar.addTab(
				actionBar.newTab()
						.setText("Schedule")
						.setTabListener(tabListener));

		actionBar.addTab(
				actionBar.newTab()
						.setText("Mesh")
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
			case R.id.action_reset: {
				ProgressSpinner.show(this);
				_ble.resetDevice(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "success");
						showToast("Reset device");
						ProgressSpinner.dismiss();
					}

					@Override
					public void onError(int error) {
						Log.e(TAG, "error" + error);
						showToast("Failed to reset");
						ProgressSpinner.dismiss();
					}
				});
				break;
			}
			case R.id.action_dfu: {
				ProgressSpinner.show(this);
				_ble.resetToBootloader(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "success");
						showToast("Set dfu mode");
						ProgressSpinner.dismiss();
						finish();
					}

					@Override
					public void onError(int error) {
						Log.e(TAG, "error" + error);
						showToast("Failed to set dfu mode");
						ProgressSpinner.dismiss();
					}
				});
				break;
			}
			case R.id.action_factoryreset: {
				ProgressSpinner.show(this);
				_ble.writeFactoryReset(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "success");
						Toast.makeText(ControlActivity.this, "success", Toast.LENGTH_SHORT).show();
						showToast("Factory reset successful");
						if (_currentStone != null) {
							_currentStone.destroy(new VoidCallback() {
								@Override
								public void onSuccess() {
									showToast("Stone removed from DB");
								}

								@Override
								public void onError(Throwable t) {
									showToast("Failed to remove Stone from DB");
								}
							});
						}
						ProgressSpinner.dismiss();
						finish();
					}

					@Override
					public void onError(int error) {
						Log.e(TAG, "error" + error);
						showToast("Failed to factory reset");
						ProgressSpinner.dismiss();
					}
				});
				break;
			}
			case R.id.action_firmwareversion: {
				BleLog.getInstance().LOGd(TAG, "read firmware version");
				ProgressSpinner.show(this);
				_ble.readFirmwareRevision(_address, new IByteArrayCallback() {
					@Override
					public void onSuccess(byte[] result) {
						String firmwareVersionStr = new String(result);
						Log.i(TAG, "firmware version: " + firmwareVersionStr);
						showToast("Firmware version: " + firmwareVersionStr);
						ProgressSpinner.dismiss();
					}

					@Override
					public void onError(int error) {
						showToast("Failed to read firmware version");
						ProgressSpinner.dismiss();
					}
				});
				break;
			}
			case R.id.action_errorstate: {
				BleLog.getInstance().LOGd(TAG, "read error state");
				ProgressSpinner.show(this);
				_ble.getBleExtState().getErrorState(_address, new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						String stateErrorStr = Integer.toBinaryString(result);
						Log.i(TAG, "state errors: " + result + " ("  + stateErrorStr + ")");
						showToast("State errors: " + stateErrorStr);
						ProgressSpinner.dismiss();
					}

					@Override
					public void onError(int error) {
						showToast("Failed to read state errors");
						ProgressSpinner.dismiss();
					}
				});
				break;
			}
			case R.id.action_reseterrorstate: {
				BleLog.getInstance().LOGd(TAG, "reset error state");
				ProgressSpinner.show(this);
				_ble.writeResetStateErrors(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						showToast("State errors reset");
						ProgressSpinner.dismiss();
					}

					@Override
					public void onError(int error) {
						showToast("Failed to reset state errors");
						ProgressSpinner.dismiss();
					}
				});
				break;
			}
		}

		return super.onOptionsItemSelected(item);
	}

	private void showToast(final String str) {
		INSTANCE.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(ControlActivity.this, str, Toast.LENGTH_LONG).show();
			}
		});
	}

}
