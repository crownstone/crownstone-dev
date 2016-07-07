package nl.dobots.crownstone;

import android.app.Application;
import android.util.Log;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleExt;

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

	private static CrownstoneDevApp instance = null;

	public static CrownstoneDevApp getInstance() {
		return instance;
	}

	private BleExt _ble;

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

	}


	public BleExt getBle() {
		return _ble;
	}

}
