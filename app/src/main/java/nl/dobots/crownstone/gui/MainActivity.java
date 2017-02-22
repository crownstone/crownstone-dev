package nl.dobots.crownstone.gui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.strongloop.android.loopback.callbacks.VoidCallback;

import java.util.List;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.callbacks.EventListener;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.FileLogger;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.cfg.Config;
import nl.dobots.crownstone.cfg.Settings;
import nl.dobots.crownstone.gui.utils.ServiceBindListener;
import nl.dobots.loopback.CrownstoneRestAPI;
import nl.dobots.loopback.gui.LoginActivity;
import nl.dobots.loopback.loopback.models.Sphere;
import nl.dobots.loopback.loopback.models.User;
import nl.dobots.loopback.loopback.repositories.UserRepository;

/**
 * Activity (container) for SelectControlFragment and SelectMonitorFragment. Provides
 * swiping and tabs to change between one and the other
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class MainActivity  extends AppCompatActivity implements ServiceBindListener, EventListener {

	private static final String TAG = MainActivity.class.getCanonicalName();

	private FragmentPagerAdapter _pagerAdapter;

	private ViewPager _pager;

	private SelectControlFragment _fragMain;
	private SelectMonitorFragment _fragSelect;

	private UserRepository _userRepository;
	private Settings _settings;
	private User _currentUser;
	private List<Sphere> _spheres;
	private CrownstoneDevApp _app;

	private BleScanService _bleService;

	private boolean _logToFile = true;
	private String _logDir ="crownstone/dev-app";
	private FileLogger _fileLogger;

//	private BleExt _bleExt;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_viewpager);

		_app = CrownstoneDevApp.getInstance();
		_settings = _app.getSettings();

//		_fileLogger = new FileLogger(this);
//		if (_fileLogger.checkPermissions(this)) {
//			BleLog.addFileLogger(_fileLogger);
//		} else {
//			_fileLogger.requestPermissions(this);
//		}

		// initialize user interface
		initUI();

		// if service is not yet bound, register as service bind listener
		if (!_app.isServiceBound()) {
			_app.registerServiceBindListener(this);
		} else {
			// otherwise get the service
			onBind(_app.getScanService());
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		// if app is resumed, make sure a user is logged in (or app is in offline mode)
		if (!Config.OFFLINE) {
			_userRepository = CrownstoneRestAPI.getUserRepository();

			if (!_settings.isOfflineMode()) {

				// no user is logged in, show login activity
				if (!_userRepository.isLoggedIn()) {
					startActivityForResult(new Intent(this, LoginActivity.class), LoginActivity.LOGIN_RESULT);
					return;
				}

				// retrieve user data from cloud if none is cached
				if (_app.getCurrentUser() == null) {
					_app.retrieveCurrentUserData();
				}
			}
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// if user cancels the login, continue in offline mode
		if (requestCode == LoginActivity.LOGIN_RESULT) {
			_settings.setOfflineMode(resultCode != RESULT_OK);
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private void initUI() {
		_fragMain = new SelectControlFragment();
		_fragSelect = new SelectMonitorFragment();

		_pagerAdapter = new FragmentPagerAdapter(getSupportFragmentManager()) {
			@Override
			public Fragment getItem(int position) {
				if (position == 0) {
					return _fragMain;
				} else {
					return _fragSelect;
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
						.setText("Monitor")
						.setTabListener(tabListener));

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {

		if (!Config.OFFLINE) {
			if (_userRepository.isLoggedIn()) {
				menu.findItem(R.id.action_login).setTitle("Log out");
			} else {
				menu.findItem(R.id.action_login).setTitle("Log in");
			}
		} else {
			menu.findItem(R.id.action_login).setVisible(false);
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		switch(id) {
			case R.id.action_login: {
				if (!Config.OFFLINE) {
					if (_userRepository.isLoggedIn()) {
						_userRepository.logout(new VoidCallback() {
							@Override
							public void onSuccess() {
								Log.d(TAG, "Logout success");
								_settings.setOfflineMode(true);
								_app.setCurrentUser(null);
							}

							@Override
							public void onError(Throwable t) {
								Log.e(TAG, "Logout failed");
							}
						});
					} else {
						startActivityForResult(new Intent(this, LoginActivity.class), LoginActivity.LOGIN_RESULT);
					}
				}
				break;
			}
			case R.id.resetBle: {
				_bleService.getBleExt().getBleBase().resetBle();
				break;
			}
//			case R.id.spheres: {
//				if (!Config.OFFLINE && !_app.getSettings().isOfflineMode()) {
//					if (_app.getSpheres() != null) {
//						_app.showSpheres(this, null);
//					}
//				}
//				break;
//
//			}
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBind(BleScanService service) {
		// get the service object
		_bleService = service;
		// register activity as event listener
		_bleService.registerEventListener(this);
	}

	@Override
	public void onEvent(EventListener.Event event) {
		switch(event) {
			case BLE_PERMISSIONS_MISSING: {
				// try to request permissions for BLE
				_bleService.requestPermissions(this);
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

		// handle permission results
		if (!_bleService.getBleExt().handlePermissionResult(requestCode, permissions, grantResults,
				new IStatusCallback() {

					@Override
					public void onError(int error) {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
								builder.setTitle("Fatal Error")
										.setMessage("Cannot scan for devices without permissions. Please " +
												"grant permissions or uninstall the app again!")
										.setNeutralButton("OK", new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface dialog, int which) {
//												dismiss();
											}
										});
								builder.create().show();
							}
						});
					}

					@Override
					public void onSuccess() {
					}
				}))
		{
			if (!_fileLogger.handlePermissionResult(requestCode, permissions, grantResults,
					new IStatusCallback() {
						@Override
						public void onSuccess() {
							BleLog.addFileLogger(_fileLogger);
						}

						@Override
						public void onError(int error) {
							Toast.makeText(MainActivity.this, "can't write to file without write permissions", Toast.LENGTH_LONG).show();
							BleLog.getInstance().LOGe(TAG, "can't write to file without write permissions");
						}
					}))
			{
				super.onRequestPermissionsResult(requestCode, permissions, grantResults);
			}
		}
	}

}

