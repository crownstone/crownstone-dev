package nl.dobots.crownstone;

import android.app.Activity;
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
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import com.strongloop.android.loopback.RestAdapter;
import com.strongloop.android.loopback.callbacks.ListCallback;
import com.strongloop.android.loopback.callbacks.ObjectCallback;
import com.strongloop.android.remoting.adapters.Adapter;

import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import nl.dobots.bluenet.ble.base.callbacks.IProgressCallback;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
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
 * The Crownstone DEV application. Binds to the service and provides access to the cloud.
 * Service can be retrieved from here instead of each activity binding to the service separately.
 *
 * Keeps a cache of the user data, sphere and keys (from the cloud)
 *
 * Created on 7-7-16
 *
 * @author Dominik Egger
 */
public class CrownstoneDevApp extends Application {

	private static final String TAG = CrownstoneDevApp.class.getCanonicalName();

	// the scan interval
	public static final int LOW_SCAN_INTERVAL = 10000;
	// the scan pause
	public static final int LOW_SCAN_PAUSE = 2000;
	// rssi value expiration after ...
	public static final int DEVICE_EXPIRATION_TIME = 5000;

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
	private User _currentUser = null;

	private List<Sphere> _spheres;

	public Settings getSettings() {
		return _settings;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;

		// create our access point to the library
		_ble = new BleExt();

		// set device expiration time (after which rssi values are discarded)
		BleDevice.setExpirationTime(DEVICE_EXPIRATION_TIME);

		// get application esttings
		_settings = Settings.getInstance(getApplicationContext());

		// setup cloud access
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
		// destroy ble library
		_ble.destroy();
		// unbind from service
		unbindService(_connection);
	}

	public BleExt getBle() {
		// make sure it is initialized (if it wasn't already)
		if (!_bleInitialized) {
			_ble.init(this, new IStatusCallback() {
				@Override
				public void onSuccess() {
					Log.v(TAG, "onSuccess");
					_bleInitialized = true;
				}

				@Override
				public void onError(int error) {
					BleLog.getInstance().LOGe(TAG, "onError: " + error);
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
			BleLog.getInstance().LOGi(TAG, "connected to ble scan service ...");
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
			onServiceBind(_service);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			BleLog.getInstance().LOGi(TAG, "disconnected from service");
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
	 * @param service
	 */
	private synchronized void onServiceBind(BleScanService service) {
		for (ServiceBindListener listener : _listeners) {
			listener.onBind(service);
		}
	}

	/**
	 * Retrieve user data of currently logged in user
	 */
	public void retrieveCurrentUserData() {
		_userRepository.findCurrentUser(new ObjectCallback<User>() {
			@Override
			public void onSuccess(User object) {
				_currentUser = object;
				retrieveUserSpheres(_currentUser);
			}

			@Override
			public void onError(Throwable t) {
				BleLog.getInstance().LOGi(TAG, "failed to get user");
				// if the error was caused because of an unauthorized error, attempt to login
				// the user with the stored credentials. if that fails, alert the user
				if (t instanceof HttpResponseException) {
					if (((HttpResponseException)t).getStatusCode() == 401) {
						LoginActivity.attemptReLogin(getApplicationContext(), new SimpleObjectCallback<User>() {
							@Override
							public void onSuccess(User object) {
								_currentUser = object;
								retrieveUserSpheres(_currentUser);
							}

							@Override
							public void onError(Throwable t) {
								BleLog.getInstance().LOGi(TAG, "failed to get user");
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

	/**
	 * Retrieve all spheres of the user
	 * @param user the user object
	 */
	private void retrieveUserSpheres(final User user) {
		if (user != null) {
			// get all spheres of user
			user.spheres(new ListCallback<Sphere>() {
				@Override
				public void onSuccess(List<Sphere> objects) {
					_spheres = objects;
					loadKeys(user);
				}

				@Override
				public void onError(Throwable t) {
					BleLog.getInstance().LOGi(TAG, "failed to get spheres of user");
					t.printStackTrace();
				}
			});
		} else {
			BleLog.getInstance().LOGe(TAG, "no user is logged in");
		}
	}

	// list of sphereIds in which the user is an admin
	Set<Object> _isAdmin = new HashSet<>();
	// list of spheres in which the user is an admin
	List<Sphere> _adminSpheres = new ArrayList<>();

	/**
	 * Load the keys for all spheres and populate the list of admin spheres
	 * @param user the user object
	 */
	private void loadKeys(User user) {
		user.keys(new Adapter.JsonArrayCallback() {
			@Override
			public void onSuccess(JSONArray response) {
				_keys = response;
				try {
					_adminSpheres.clear();
					// go through the list of keys and check which spheres have
					// an admin key. those are the spheres for which the user has
					// admin access
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
				BleLog.getInstance().LOGi(TAG, "failed to get keys of user");
				t.printStackTrace();
			}
		});
	}

	/**
	 * Return the list of spheres from cache
	 * @return cached list of spheres
	 */
	public List<Sphere> getSpheres() {
		return _spheres;
	}

	/**
	 * Find the keys for a given sphereId
	 * @param sphereId the id of the sphere
	 * @return the keys of that sphere
	 */
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

	public void executeSetup(final Activity activity, final BleDevice device, final IStatusCallback callback) {

		// show a list of spheres for the user to select one
		showSpheres(activity, new ObjectCallback<Sphere>() {

			@Override
			public void onSuccess(final Sphere sphere) {
				// check if the stone is already added to the sphere
				sphere.findStone(device.getAddress(), new ObjectCallback<Stone>() {
					@Override
					public void onSuccess(Stone stone) {
						// if the stone was already added, use that to setup
						if (stone != null && sphere.getId().equals(stone.getSphereId())) {
							runSetup(sphere, stone, activity, device, callback);
						} else {
							// if a wrong stone object was returned, create a new stone in the cloud
							sphere.createStone(device.getAddress(), device.getName(), new ObjectCallback<Stone>() {
								@Override
								public void onSuccess(Stone object) {
									// and use that stone for the setup
									runSetup(sphere, object, activity, device, callback);
								}

								@Override
								public void onError(Throwable t) {
									BleLog.getInstance().LOGe(TAG, "error");
									t.printStackTrace();
								}
							});
						}
					}

					@Override
					public void onError(Throwable t) {
						// if retrieving the stone fails, i.e. it was not found in the cloud
						// create a new stone and use that for the setup
						sphere.createStone(device.getAddress(), device.getName(), new ObjectCallback<Stone>() {
							@Override
							public void onSuccess(Stone object) {
								runSetup(sphere, object, activity, device, callback);
							}

							@Override
							public void onError(Throwable t) {
								BleLog.getInstance().LOGe(TAG, "error");
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

	/**
	 * Use the stone and sphere objects from the cloud to setup a stone over ble
	 *
	 * @param sphere
	 * @param stone
	 * @param activity
	 * @param device
	 * @param callback
	 */
	private void runSetup(Sphere sphere, Stone stone, final Activity activity, BleDevice device, final IStatusCallback callback) {
		// create the setup helper object
		final CrownstoneSetup setup = new CrownstoneSetup(getBle());

		// create a progress dialog to display the setup progress
		final ProgressDialog dlg = new ProgressDialog(activity);
		dlg.setTitle("Executing Setup");
		dlg.setMessage("Please wait ...");
		dlg.setIndeterminate(false);
		dlg.setMax(13);
		dlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dlg.setCanceledOnTouchOutside(false);
		dlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				setup.cancelSetup();
			}
		});
		dlg.show();

		// set the proximity uuid from the sphere on the device
		device.setProximityUuid(UUID.fromString(sphere.getUuid()));
		// set the major and minor on the device
		device.setMajor(stone.getMajor());
		device.setMinor(stone.getMinor());
		// get the encryption keys and set them on the library
		EncryptionKeys keys = getKeys(sphere.getId());
		getBle().enableEncryption(true);
		// execute the setup
		setup.executeSetup(device.getAddress(),
				stone.getUid(),
				keys.getAdminKeyString(),
				keys.getMemberKeyString(),
				keys.getGuestKeyString(),
				Long.valueOf(sphere.getMeshAccessAddress(), 16).intValue(),
				sphere.getUuid(),
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

	/**
	 * Show the list of spheres to the user
	 *
	 * @param activity the activity to show the list
	 * @param selectionCallback the callback to informed about the selected sphere
	 */
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

	/**
	 * Set the currently logged-in user
	 * @param currentUser user object
	 */
	public void setCurrentUser(User currentUser) {
		_currentUser = currentUser;
	}

	/**
	 * Get the current user object
	 * @return user object
	 */
	public User getCurrentUser() {
		return _currentUser;
	}

	/**
	 * Get the sphere for a given proximity uuid
	 * @param proximityUuid the proximity uuid of the sphere
	 * @return the sphere if found, null otherwise
	 */
	public Sphere getSphere(String proximityUuid) {
		if (_spheres != null) {
			for (int i = 0; i < _spheres.size(); i++) {
				Sphere sphere = _spheres.get(i);
				if (sphere.getUuid().equals(proximityUuid)) {
					return sphere;
				}
			}
		}
		return null;
	}

	/**
	 * Get the encryption keys of a sphere
	 * @param sphereId the id of the sphere
	 * @return the encryption keys object
	 */
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
