package nl.dobots.crownstone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.ProgressDialog;
import android.bluetooth.le.ScanSettings;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.strongloop.android.loopback.RestAdapter;
import com.strongloop.android.loopback.callbacks.JsonObjectParser;
import com.strongloop.android.loopback.callbacks.ListCallback;
import com.strongloop.android.loopback.callbacks.ObjectCallback;
import com.strongloop.android.loopback.callbacks.VoidCallback;
import com.strongloop.android.remoting.adapters.Adapter;

import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.dobots.bluenet.ble.base.callbacks.IProgressCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.CrownstoneSetup;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.crownstone.cfg.Config;
import nl.dobots.crownstone.cfg.Settings;
import nl.dobots.crownstone.gui.utils.ServiceBindListener;
import nl.dobots.loopback.CrownstoneRestAPI;
import nl.dobots.loopback.gui.LoginActivity;
import nl.dobots.loopback.gui.adapter.SphereListAdapter;
import nl.dobots.loopback.loopback.callbacks.SimpleObjectCallback;
import nl.dobots.loopback.loopback.models.Sphere;
import nl.dobots.loopback.loopback.models.Stone;
import nl.dobots.loopback.loopback.models.User;
import nl.dobots.loopback.loopback.repositories.StoneRepository;
import nl.dobots.loopback.loopback.repositories.UserRepository;

/**
 * Copyright (c) 2016 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 7-7-16
 *
 * @author Dominik Egger
 */
public class CrownstoneDevApp extends Application {

	private static final String TAG = CrownstoneDevApp.class.getCanonicalName();

	// scan for 1 second every 3 seconds
	public static final int LOW_SCAN_INTERVAL = 10000; // 1 second scanning
	public static final int LOW_SCAN_PAUSE = 2000; // 2 seconds pause


	private BleScanService _service;

	private static CrownstoneDevApp instance = null;
	private JSONArray _keys;
	private StoneRepository _stoneRepository;
	private boolean _bleInitialized;

	public static CrownstoneDevApp getInstance() {
		return instance;
	}

	private BleExt _ble;

	private boolean _bound = false;

	private ArrayList<ServiceBindListener> _listeners = new ArrayList<>();

	private Settings _settings;
	private RestAdapter _restAdapter;

	private UserRepository _userRepository;
	private User _currentUser;

	private List<Sphere> _spheres;

	public Settings getSettings() {
		return _settings;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;

		// create our access point to the library, and make sure it is initialized (if it
		// wasn't already)
		_ble = new BleExt();
//		_ble.init(this, new IStatusCallback() {
//			@Override
//			public void onSuccess() {
//				Log.v(TAG, "onSuccess");
//			}
//
//			@Override
//			public void onError(int error) {
//				Log.e(TAG, "onError: " + error);
//			}
//		});

		_settings = Settings.getInstance(getApplicationContext());

		if (!Config.OFFLINE) {
			_restAdapter = CrownstoneRestAPI.initializeApi(this);
			_userRepository = CrownstoneRestAPI.getUserRepository();
			_stoneRepository = CrownstoneRestAPI.getStoneRepository();
		}

		// create and bind to the BleScanService
		Intent intent = new Intent(this, BleScanService.class);
		bindService(intent, _connection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		_ble.destroy();
		unbindService(_connection);
	}

	public BleExt getBle() {
		if (!_bleInitialized) {
			_ble.init(this, new IStatusCallback() {
				@Override
				public void onSuccess() {
					Log.v(TAG, "onSuccess");
					_bleInitialized = true;
				}

				@Override
				public void onError(int error) {
					Log.e(TAG, "onError: " + error);
					_bleInitialized = false;
				}
			});
		}
		return _ble;
	}

	public BleScanService getScanService() {
		return _service;
	}

	public boolean isServiceBound() {
		return _bound;
	}

	// if the service was connected successfully, the service connection gives us access to the service
	private ServiceConnection _connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "connected to ble scan service ...");
			// get the service from the binder
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_service = binder.getService();

			// set the scan interval (for how many ms should the service scan for devices)
			_service.setScanInterval(LOW_SCAN_INTERVAL);
			// set the scan pause (how many ms should the service wait before starting the next scan)
			_service.setScanPause(LOW_SCAN_PAUSE);

			if (Build.VERSION.SDK_INT >= 21) {
				_service.getBleExt().getBleBase().setScanMode(ScanSettings.SCAN_MODE_BALANCED);
			}

			_bound = true;
			onServiceBind();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "disconnected from service");
			_bound = false;
		}
	};

	/**
	 * Register as a ScanDeviceListener. Whenever a device is detected, an onDeviceScanned event
	 * is triggered with the detected device as a parameter
	 * @param listener the listener to register
	 */
	public synchronized void registerServiceBindListener(ServiceBindListener listener) {
		if (!_listeners.contains(listener)) {
			_listeners.add(listener);
		}
	}

	/**
	 * Unregister from the service
	 * @param listener the listener to unregister
	 */
	public synchronized void unregisterServiceBindListener(ServiceBindListener listener) {
		if (_listeners.contains(listener)) {
			_listeners.remove(listener);
		}
	}

	/**
	 * Helper function to notify IntervalScanListeners when a scan interval starts
	 */
	private synchronized void onServiceBind() {
		for (ServiceBindListener listener : _listeners) {
			listener.onBind();
		}
	}

	public void retrieveCurrentUserData() {
		_userRepository.findCurrentUser(new ObjectCallback<User>() {
			@Override
			public void onSuccess(User object) {
				_currentUser = object;
				loadSpheres(_currentUser, new VoidCallback() {
					@Override
					public void onSuccess() {
						loadKeys(_currentUser);
					}

					@Override
					public void onError(Throwable t) {

					}
				});
			}

			@Override
			public void onError(Throwable t) {
				Log.i(TAG, "failed to get user");
				if (t instanceof HttpResponseException) {
					if (((HttpResponseException)t).getStatusCode() == 401) {
						LoginActivity.attemptReLogin(getApplicationContext(), new SimpleObjectCallback<User>() {
							@Override
							public void onSuccess(User object) {
								_currentUser = object;

								loadSpheres(_currentUser, new VoidCallback() {
									@Override
									public void onSuccess() {
										loadKeys(_currentUser);
									}

									@Override
									public void onError(Throwable t) {

									}
								});
							}

							@Override
							public void onError(Throwable t) {
								Log.i(TAG, "failed to get user");
								t.printStackTrace();
								Toast.makeText(getApplicationContext(), "Failed to login, please try again", Toast.LENGTH_LONG).show();
							}
						});
						return;
					}
				}

				t.printStackTrace();
			}
		});
	}

	Set<Object> _isAdmin = new HashSet<>();

	List<Sphere> _adminSpheres = new ArrayList<>();

	private void loadKeys(User currentUser) {
		currentUser.keys(new Adapter.JsonArrayCallback() {
			@Override
			public void onSuccess(JSONArray response) {
				_keys = response;
				try {
					for (int i = 0; i < response.length(); i++) {
						JSONObject object = response.getJSONObject(i);
						if (object.getJSONObject("keys").has("admin")) {
							Object sphereId = object.get("sphereId");
							_isAdmin.add(sphereId);
							for (int j = 0; j < _spheres.size(); j++) {
								Sphere sphere = _spheres.get(j);
								if (sphere.getId().equals(sphereId)) {
									_adminSpheres.add(sphere);
								}
							}
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onError(Throwable t) {
				Log.i(TAG, "failed to get keys of user");
				t.printStackTrace();
			}
		});
	}

	private void loadSpheres(User currentUser, final VoidCallback callback) {
		currentUser.spheres(new ListCallback<Sphere>() {
			@Override
			public void onSuccess(List<Sphere> objects) {
				_spheres = objects;
				callback.onSuccess();
			}

			@Override
			public void onError(Throwable t) {
				Log.i(TAG, "failed to get spheres of user");
				t.printStackTrace();
				callback.onError(t);
			}
		});
	}

	public List<Sphere> getSpheres() {
		return _spheres;
	}

	private JSONObject findKeys(Object sphereId) {
		for (int i = 0; i < _keys.length(); i++) {
			try {
				JSONObject object = _keys.getJSONObject(i);
				if (object.get("sphereId").equals(sphereId)) {
					return object.getJSONObject("keys");
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public void prepareSetup(final BleDevice device, IStatusCallback callback) {

	}

	public void executeSetup(final Activity activity, final BleDevice device, final IStatusCallback callback) {

		showSpheres(activity, new ObjectCallback<Sphere>() {

			@Override
			public void onSuccess(final Sphere sphere) {

				sphere.findStone(device.getAddress(), new ObjectCallback<Stone>() {
//				_stoneRepository.findByAddress(device.getAddress(), new ObjectCallback<Stone>() {
					@Override
					public void onSuccess(Stone stone) {
						if (stone != null && sphere.getId().equals(stone.getSphereId())) {
							runSetup(sphere, stone, activity, device, callback);
						} else {
							sphere.createStone(device.getAddress(), device.getName(), new ObjectCallback<Stone>() {
								@Override
								public void onSuccess(Stone object) {
									runSetup(sphere, object, activity, device, callback);
								}

								@Override
								public void onError(Throwable t) {
									Log.e(TAG, "error");
									t.printStackTrace();
								}
							});
						}
					}

					@Override
					public void onError(Throwable t) {
						sphere.createStone(device.getAddress(), device.getName(), new ObjectCallback<Stone>() {
							@Override
							public void onSuccess(Stone object) {
								runSetup(sphere, object, activity, device, callback);
							}

							@Override
							public void onError(Throwable t) {
								Log.e(TAG, "error");
								t.printStackTrace();
							}
						});
					}
				});


			}

			@Override
			public void onError(Throwable t) {
			}
		});
	}

	private void runSetup(Sphere object, Stone stone, final Activity activity, BleDevice device, final IStatusCallback callback) {
		final ProgressDialog dlg = new ProgressDialog(activity);
		dlg.setTitle("Executing Setup");
		dlg.setMessage("Please wait ...");
		dlg.setIndeterminate(false);
		dlg.setMax(13);
		dlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dlg.show();

		EncryptionKeys keys = getKeys(object.getId());
		CrownstoneSetup setup = new CrownstoneSetup(getBle());
		getBle().enableEncryption(true);
		setup.executeSetup(device.getAddress(),
				stone.getUid(),
				keys.getAdminKeyString(),
				keys.getMemberKeyString(),
				keys.getGuestKeyString(),
				Long.valueOf(object.getMeshAccessAddress(), 16).intValue(),
				object.getUuid(),
				stone.getMajor(),
				stone.getMinor(),
				new IProgressCallback() {

					@Override
					public void onError(final int error) {
						BleLog.getInstance().LOGe(TAG, "failed with error: %d", error);
						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(activity, "failed with error: " + error, Toast.LENGTH_LONG).show();
							}
						});
					}

					@Override
					public void onProgress(final double progress, @Nullable JSONObject statusJson) {
						BleLog.getInstance().LOGi(TAG, "progress: %f", progress);

						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								dlg.setProgress((int) progress);
								//							Toast.makeText(activity, "progress: " + progress, Toast.LENGTH_SHORT).show();
							}
						});
					}
				}, new IStatusCallback() {

					@Override
					public void onError(final int error) {
						BleLog.getInstance().LOGe(TAG, "status error: %d", error);

						callback.onError(error);
						dlg.dismiss();

						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(activity, "status error: " + error, Toast.LENGTH_LONG).show();
							}
						});
					}

					@Override
					public void onSuccess() {
						BleLog.getInstance().LOGd(TAG, "success");

						callback.onSuccess();
						dlg.dismiss();

						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(activity, "success", Toast.LENGTH_LONG).show();
							}
						});
					}
				}
		);
	}

	public void showSpheres(Activity activity, final ObjectCallback<Sphere> selectionCallback) {

		AlertDialog.Builder b = new AlertDialog.Builder(activity);
		b.setTitle("Select a sphere");

		SphereListAdapter sphereListAdapter = new SphereListAdapter(activity, _adminSpheres);
		b.setAdapter(sphereListAdapter, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (selectionCallback != null) {
					selectionCallback.onSuccess(_adminSpheres.get(which));
				}
			}
		});
		b.show();

	}

	public User getCurrentUser() {
		return _currentUser;
	}

	public Sphere getSphere(String proximityUuid) {
		for (int i = 0; i < _spheres.size(); i++) {
			Sphere sphere = _spheres.get(i);
			if (sphere.getUuid().equals(proximityUuid)) {
				return sphere;
			}
		}
		return null;
	}

	public EncryptionKeys getKeys(Object sphereId) {
		JSONObject keys = findKeys(sphereId);

		String adminKey = null;
		String memberKey = null;
		String guestKey = null;
		try {
			adminKey = keys.getString("admin");
			memberKey = keys.getString("member");
			guestKey = keys.getString("guest");
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return new EncryptionKeys(adminKey, memberKey, guestKey);
	}

}
