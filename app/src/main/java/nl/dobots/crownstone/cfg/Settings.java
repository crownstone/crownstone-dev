package nl.dobots.crownstone.cfg;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 7-12-15
 *
 * @author Dominik Egger
 */
public class Settings {

	// store username and password to provide in Login activity or to try automatic login if
	// access token expires

	private static final String SHARED_PREFS = "settings";

	private static final String LOGIN_USER_NAME = "prefs_username";
	private static final String LOGIN_PASSWORD = "prefs_password";
	private static final String OFFLINE_MODE = "prefs_offline_mode";

	private static Settings ourInstance;

	private final Context _context;
	private final SharedPreferences _sharedPreferences;

	private String _username;
	private String _password;

	private boolean _offlineMode = false;

	public static Settings getInstance(Context context) {

		if (ourInstance == null) {
			ourInstance = new Settings(context);
		}

		return ourInstance;
	}

	private Settings(Context context) {
		this._context = context;

		_sharedPreferences = _context.getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
		readSettings();
	}

	public void saveLogin(String username, String password) {
		SharedPreferences.Editor editor = _sharedPreferences.edit();
		_username = username;
		_password = password;
		editor.putString(LOGIN_USER_NAME, _username);
		editor.putString(LOGIN_PASSWORD, _password);
		editor.commit();
	}

	public void readSettings() {
		_username = _sharedPreferences.getString(LOGIN_USER_NAME, "");
		_password = _sharedPreferences.getString(LOGIN_PASSWORD, "");
		_offlineMode = _sharedPreferences.getBoolean(OFFLINE_MODE, false);
	}

	public void clearSettings() {
		final SharedPreferences.Editor editor = _sharedPreferences.edit();
		editor.clear().commit();
		_username = "";
		_password = "";
		_offlineMode = false;
	}

	public String getUsername() {
		return _username;
	}

	public String getPassword() {
		return _password;
	}

	public boolean isOfflineMode() {
		return _offlineMode;
	}

	public void setOfflineMode(boolean offlineMode) {
		_offlineMode = offlineMode;
		SharedPreferences.Editor editor = _sharedPreferences.edit();
		editor.putBoolean(OFFLINE_MODE, _offlineMode);
		editor.commit();
	}

}
