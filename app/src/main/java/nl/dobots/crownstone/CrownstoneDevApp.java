package nl.dobots.crownstone;

import android.app.Application;
import android.bluetooth.le.ScanSettings;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.crownstone.gui.utils.ServiceBindListener;

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

	public static CrownstoneDevApp getInstance() {
		return instance;
	}

	private BleExt _ble;

	private boolean _bound = false;

	private ArrayList<ServiceBindListener> _listeners = new ArrayList<>();

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;

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

}
