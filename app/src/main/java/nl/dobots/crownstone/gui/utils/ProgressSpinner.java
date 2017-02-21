package nl.dobots.crownstone.gui.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.ProgressBar;

import nl.dobots.crownstone.R;

/**
 * Copyright (c) 2017 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p>
 * Created on 17-2-17
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class ProgressSpinner extends AppCompatActivity {

	public interface OnCancelListener {
		void onCancel();
	}

	private static ProgressSpinner instance;
	private static OnCancelListener _cancelListener = null;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		instance = this;
		setContentView(R.layout.activity_progress);
		ProgressBar pg = (ProgressBar) findViewById(R.id.progressBar);
		pg.setIndeterminate(true);
	}

	public static void show(Context context) {
		context.startActivity(new Intent(context, ProgressSpinner.class));
		_cancelListener = null;
	}

	public static void show(Context context, OnCancelListener listener) {
		context.startActivity(new Intent(context, ProgressSpinner.class));
		_cancelListener = listener;
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		if (_cancelListener != null) {
			_cancelListener.onCancel();
		}
	}

	public static void dismiss() {
		if (instance != null) {
			instance.finish();
			instance = null;
		}
	}

}
