package nl.dobots.crownstone.gui.control;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.base.callbacks.IExecStatusCallback;
import nl.dobots.bluenet.ble.base.callbacks.SimpleExecStatusCallback;
import nl.dobots.bluenet.ble.base.structs.ControlMsg;
import nl.dobots.bluenet.ble.base.structs.ScheduleCommandPacket;
import nl.dobots.bluenet.ble.base.structs.ScheduleEntryPacket;
import nl.dobots.bluenet.ble.base.structs.ScheduleListPacket;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.callbacks.IExecuteCallback;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;
import nl.dobots.crownstone.R;
import nl.dobots.crownstone.gui.utils.ProgressSpinner;

/**
 * Copyright (c) 2015 Bart van Vliet <bart@dobots.nl>. All rights reserved.
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
 * Created on 1-5-17
 *
 * @author Bart van Vliet
 */
public class ControlScheduleFragment extends Fragment {
	private static final String TAG = ControlScheduleFragment.class.getCanonicalName();

	// Keep alive
	private EditText _txtSchedulePacket;
	private EditText _txtScheduleId;
	private EditText _txtScheduleOverrideMask;
	private EditText _txtScheduleTimestamp;
	private EditText _txtScheduleRepeatMinute;
	private EditText _txtScheduleRepeatDay;
	private EditText _txtScheduleActionSwitch;
	private EditText _txtScheduleActionFadeSwitch;
	private EditText _txtScheduleActionFadeDuration;
	private TextView _textScheduleList;
	private ScheduleCommandPacket _packet;

	// Other stuff
	private BleExt _ble;
	private String _address;
	private boolean _closing;
	private Handler _handler;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		HandlerThread ht = new HandlerThread("BleHandler");
		ht.start();
		_handler = new Handler(ht.getLooper());

		_ble = ControlActivity.getInstance().getBle();
		_address = ControlActivity.getInstance().getAddress();
		_packet = new ScheduleCommandPacket();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		_closing = true;
		_handler.removeCallbacksAndMessages(null);
		if (_ble.isScanning()) {
			_ble.stopScan(null);
		}
		// finish has to be called on the library to release the objects if the library
		// is not used anymore
		if (_ble.isConnected(null)) {
			_ble.disconnectAndClose(false, new IStatusCallback() {
				@Override
				public void onSuccess() {

				}

				@Override
				public void onError(int error) {

				}
			});
		}
	}


	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.frag_control_schedule, container, false);

		_txtSchedulePacket = (EditText) v.findViewById(R.id.txtSchedulePacket);
		v.findViewById(R.id.btnSchedulePacketSet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setEntry();
			}
		});
		v.findViewById(R.id.btnSchedulePacketClear).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				clearEntry();
			}
		});

		_textScheduleList = (TextView) v.findViewById(R.id.textScheduleList);
		v.findViewById(R.id.btnScheduleListGet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getList();
			}
		});

		_txtScheduleId = (EditText) v.findViewById(R.id.txtScheduleId);
		v.findViewById(R.id.btnScheduleIdSet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setId();
			}
		});

		_txtScheduleOverrideMask = (EditText) v.findViewById(R.id.txtScheduleOverrideMask);
		v.findViewById(R.id.btnScheduleOverrideMaskSet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setOverrideMask();
			}
		});

		_txtScheduleTimestamp = (EditText) v.findViewById(R.id.txtScheduleTimestamp);
		java.util.Date date = new java.util.Date();
		_txtScheduleTimestamp.setText(date.toString());
		v.findViewById(R.id.btnScheduleTimestampSet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setTime();
			}
		});

		_txtScheduleRepeatMinute = (EditText) v.findViewById(R.id.txtScheduleRepeatMinute);
		v.findViewById(R.id.btnScheduleRepeatMinuteSet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setRepeatMinute();
			}
		});

		_txtScheduleRepeatDay = (EditText) v.findViewById(R.id.txtScheduleRepeatDay);
		v.findViewById(R.id.btnScheduleRepeatDaySet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setRepeatDay();
			}
		});

		v.findViewById(R.id.btnScheduleRepeatOnceSet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setRepeatOnce();
			}
		});

		_txtScheduleActionSwitch = (EditText) v.findViewById(R.id.txtScheduleActionSwitch);
		v.findViewById(R.id.btnScheduleActionSwitchSet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setActionSwitch();
			}
		});

		_txtScheduleActionFadeSwitch = (EditText) v.findViewById(R.id.txtScheduleActionFadeSwitch);
		v.findViewById(R.id.btnScheduleActionFadeSwitchSet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
//				setActionFadeSwitch();
			}
		});
		v.findViewById(R.id.btnScheduleActionFadeSwitchSet).setVisibility(View.INVISIBLE);

		_txtScheduleActionFadeDuration = (EditText) v.findViewById(R.id.txtScheduleActionFadeDuration);
		v.findViewById(R.id.btnScheduleActionFadeDurationSet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setActionFadeDuration();
			}
		});

		v.findViewById(R.id.btnScheduleActionToggleSet).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setActionToggle();
			}
		});

		updatePacket();
//		return super.onCreateView(inflater, container, savedInstanceState);
		return v;
	}

	private void updatePacket() {
		byte[] array = _packet.toArray();
		_txtSchedulePacket.setText(BleUtils.bytesToString(array));
		BleLog.getInstance().LOGd(TAG, "packet:\n" + _packet.toString());
	}

	private void setEntry() {
		showProgressSpinner();
		BleLog.getInstance().LOGi(TAG, "setEntry");

		String text = _txtSchedulePacket.getText().toString();
		text = text.replace("[", ""); // Remove all [
		text = text.replace("]", ""); // Remove all ]
		String[] textArr = text.split("[ ,]+"); // Split by space and/or comma
		byte[] msgBytes = new byte[textArr.length];
		for (int i=0; i<textArr.length; i++) {
			msgBytes[i] = (byte)Integer.parseInt(textArr[i]);
		}
		BleLog.getInstance().LOGd(TAG, "txt=" + text);
		BleLog.getInstance().LOGd(TAG, "msg=" + BleUtils.bytesToString(msgBytes));
		final byte[] array = msgBytes;


		_handler.post(new ControlScheduleFragment.SequentialRunner("setEntry") {
			@Override
			public boolean execute() {
				_ble.connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						ControlMsg msg = new ControlMsg(BluenetConfig.CMD_SCHEDULE_ENTRY_SET, array.length, array);
						_ble.writeControl(_address, msg, execCallback);
					}
				}, new SimpleExecStatusCallback(new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "setEntry success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "setEntry failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void clearEntry() {
		showProgressSpinner();
		BleLog.getInstance().LOGi(TAG, "clearEntry");

		String text = _txtSchedulePacket.getText().toString();
		text = text.replace("[", ""); // Remove all [
		text = text.replace("]", ""); // Remove all ]
		String[] textArr = text.split("[ ,]+"); // Split by space and/or comma
		byte[] msgBytes = new byte[textArr.length];
		for (int i=0; i<textArr.length; i++) {
			msgBytes[i] = (byte)Integer.parseInt(textArr[i]);
		}
		BleLog.getInstance().LOGd(TAG, "txt=" + text);
		BleLog.getInstance().LOGd(TAG, "msg=" + BleUtils.bytesToString(msgBytes));
		final byte[] array = new byte[] {msgBytes[0]};

		_handler.post(new ControlScheduleFragment.SequentialRunner("setEntry") {
			@Override
			public boolean execute() {
				_ble.connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						ControlMsg msg = new ControlMsg(BluenetConfig.CMD_SCHEDULE_ENTRY_CLEAR, array.length, array);
						_ble.writeControl(_address, msg, execCallback);
					}
				}, new SimpleExecStatusCallback(new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "clearEntry success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "clearEntry failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void getList() {
		showProgressSpinner();
		BleLog.getInstance().LOGi(TAG, "getList");

		_handler.post(new ControlScheduleFragment.SequentialRunner("getList") {
			@Override
			public boolean execute() {
				_ble.connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						_ble.getBleExtState().getSchedule(_address, execCallback);
					}
				}, new SimpleExecStatusCallback(new IByteArrayCallback() {
					@Override
					public void onSuccess(byte[] bytes) {
						Log.i(TAG, "getList success");
						ScheduleListPacket schedule = new ScheduleListPacket();
						if (!schedule.fromArray(bytes)) {
							displayError(BleErrors.ERROR_MSG_PARSING);
							BleLog.getInstance().LOGw(TAG, "Invalid schedule: " + BleUtils.bytesToString(bytes));
							done();
							dismissProgressSpinner();
							return;
						}
						showToast("Success");
						final String scheduleStr = schedule.toString();
						Log.i(TAG, scheduleStr);
						Activity activity = getActivity();
						if (activity == null) {
							return;
						}
						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								_textScheduleList.setText(scheduleStr);
							}
						});
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "getList failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void setId() {
		Integer value = getInt(_txtScheduleId);
		if (value == null) return;
		_packet._index = value;
		updatePacket();
	}

	private void setOverrideMask() {
		Long value = getBitmask(_txtScheduleOverrideMask);
		if (value == null) return;
		if (value < 0 || value > 255) {
			showToast("Invalid number");
			return;
		}
		_packet._entry._overrideMask = (byte)((long)value);
		updatePacket();
	}

	private void setTime() {
		String timeString = _txtScheduleTimestamp.getText().toString();
		if (android.text.TextUtils.isDigitsOnly(timeString)) {
			long value;
			try {
				value = Long.parseLong(timeString);
			} catch (NumberFormatException e) {
				showToast("Invalid number");
				return;
			}
			_packet._entry._timestamp = value;
		}
		else {
			SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd kk:mm:ss z yyyy");
			java.util.Date time = null;
			try {
				time = dateFormat.parse(timeString);
			} catch (java.text.ParseException e) {
				showToast("Invalid timestamp");
				return;
			}
			_packet._entry._timestamp = time.getTime() / 1000;
		}
		updatePacket();
	}

	private void setRepeatMinute() {
		Integer value = getInt(_txtScheduleRepeatMinute);
		if (value == null) return;
		_packet._entry._repeatType = ScheduleEntryPacket.REPEAT_MINUTES;
		_packet._entry._minutes = value;
		updatePacket();
	}

	private void setRepeatDay() {
		Long value = getBitmask(_txtScheduleRepeatDay);
		if (value == null) return;
		if (value < 0 || value > 255) {
			showToast("Invalid number");
			return;
		}
		_packet._entry._repeatType = ScheduleEntryPacket.REPEAT_DAY;
		_packet._entry._dayOfWeekMask = (byte)((long)value);

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.setFirstDayOfWeek(Calendar.SUNDAY); // Make the week start at sunday
		calendar.add(Calendar.DATE, 1); // Add a day
		int dayTomorrow = calendar.get(Calendar.DAY_OF_WEEK) - 1; // Calendar days are 1-7, we need 0-6
		_packet._entry._dayOfWeekNext = (byte)dayTomorrow;
		updatePacket();
	}

	private void setRepeatOnce() {
		_packet._entry._repeatType = ScheduleEntryPacket.REPEAT_ONCE;
		updatePacket();
	}

	private void setActionSwitch() {
		Integer value = getInt(_txtScheduleActionSwitch);
		if (value == null) return;
		_packet._entry._switchVal = value;
		_packet._entry._actionType = ScheduleEntryPacket.ACTION_SWITCH;
		updatePacket();
	}

	private void setActionFadeSwitch() {
		setActionFadeSwitch(true);
	}

	private void setActionFadeSwitch(boolean setDuration) {
		Integer value = getInt(_txtScheduleActionFadeSwitch);
		if (value == null) return;
		_packet._entry._switchVal = value;
		if (setDuration) {
			setActionFadeDuration(false);
		}
		else {
			_packet._entry._actionType = ScheduleEntryPacket.ACTION_FADE;
			updatePacket();
		}
	}

	private void setActionFadeDuration() {
		setActionFadeDuration(true);
	}

	private void setActionFadeDuration(boolean setSwitch) {
		Integer value = getInt(_txtScheduleActionFadeDuration);
		if (value == null) return;
		_packet._entry._fadeDuration = value;
		if (setSwitch) {
			setActionFadeSwitch(false);
		}
		else {
			_packet._entry._actionType = ScheduleEntryPacket.ACTION_FADE;
			updatePacket();
		}
	}

	private void setActionToggle() {
		_packet._entry._actionType = ScheduleEntryPacket.ACTION_TOGGLE;
		updatePacket();
	}



	//////////////////////
	// HELPER FUNCTIONS //
	//////////////////////

	private Integer getInt(EditText editText) {
		Integer value;
		String str = editText.getText().toString();
		try {
			value = Integer.parseInt(str);
		} catch (NumberFormatException e) {
			showToast("Invalid number");
			return null;
		}
		return value;
	}

	private Long getBitmask(EditText editText) {
		Long value;
		String str = editText.getText().toString();
		try {
			value = Long.parseLong(str, 2);
		} catch (NumberFormatException e) {
			showToast("Invalid number");
			return null;
		}
		return value;
	}

	private abstract class SequentialRunner implements Runnable {

		private final String _name;
		private boolean noWait = false;

		public SequentialRunner(String name) {
			_name = name;
		}

		public abstract boolean execute();


		protected synchronized void done() {
			this.notify();
			noWait = true;
			BleLog.getInstance().LOGv(TAG, "notify");
		}

		@Override
		public void run() {
			synchronized (this) {
				if (execute()) {
					try {
						BleLog.getInstance().LOGv(TAG, "wait");
						if (!noWait) {
							wait();
						}
						BleLog.getInstance().LOGv(TAG, "wait done");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	private void showProgressSpinner() {
		ProgressSpinner.show(getActivity());
	}

	private void dismissProgressSpinner() {
		ProgressSpinner.dismiss();
	}

	private void displayError(final int error) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				String errorMsg;
				switch (error) {
					case 19: {
						errorMsg = "Failed: Disconnected by device!";
						break;
					}
					default:
						errorMsg = "Failed with error " + error;
				}
				Toast.makeText(getActivity(), errorMsg, Toast.LENGTH_LONG).show();
			}
		});
	}

	private void showToast(final String str) {
		Activity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getActivity(), str, Toast.LENGTH_LONG).show();
			}
		});
	}
}
