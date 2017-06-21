package nl.dobots.crownstone.gui.control;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import nl.dobots.bluenet.ble.base.BleConfiguration;
import nl.dobots.bluenet.ble.base.callbacks.IExecStatusCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.ILongCallback;
import nl.dobots.bluenet.ble.base.callbacks.SimpleExecStatusCallback;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.BleExtState;
import nl.dobots.bluenet.ble.extended.callbacks.IExecuteCallback;
import nl.dobots.bluenet.ble.mesh.structs.MeshControlMsg;
import nl.dobots.bluenet.ble.mesh.structs.MeshKeepAlivePacket;
import nl.dobots.bluenet.ble.mesh.structs.MeshMultiSwitchPacket;
import nl.dobots.bluenet.ble.mesh.structs.cmd.MeshControlPacket;
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
public class ControlConfigFragment extends Fragment {
	private static final String TAG = ControlConfigFragment.class.getCanonicalName();

	private EditText _txtConfigRelayHigh;
	private Button   _btnConfigRelayHighGet;
	private Button   _btnConfigRelayHighSet;
	private EditText _txtConfigPwmPeriod;
	private Button   _btnConfigPwmPeriodGet;
	private Button   _btnConfigPwmPeriodSet;
	private EditText _txtConfigTime;
	private Button   _btnConfigTimeGet;
	private Button   _btnConfigTimeSet;



	// Other stuff
	private BleExt _ble;
	private String _address;
	private boolean _closing;
	private Handler _handler;
	private Context _context;
	private BleConfiguration _bleConfiguration;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		_context = getContext();

		HandlerThread ht = new HandlerThread("BleHandler");
		ht.start();
		_handler = new Handler(ht.getLooper());

		_ble = ControlActivity.getInstance().getBle();
		_address = ControlActivity.getInstance().getAddress();
		_bleConfiguration = new BleConfiguration(_ble.getBleBase());
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
		View v = inflater.inflate(R.layout.frag_control_config, container, false);

		_txtConfigRelayHigh    = (EditText) v.findViewById(R.id.txtConfigRelayHigh);
		_btnConfigRelayHighGet = (Button)   v.findViewById(R.id.btnConfigRelayHighGet);
		_btnConfigRelayHighSet = (Button)   v.findViewById(R.id.btnConfigRelayHighSet);
		_btnConfigRelayHighGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getRelayHigh();
			}
		});
		_btnConfigRelayHighSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setRelayHigh();
			}
		});

		_txtConfigPwmPeriod    = (EditText) v.findViewById(R.id.txtConfigPwmPeriod);
		_btnConfigPwmPeriodGet = (Button)   v.findViewById(R.id.btnConfigPwmPeriodGet);
		_btnConfigPwmPeriodSet = (Button)   v.findViewById(R.id.btnConfigPwmPeriodSet);
		_btnConfigPwmPeriodGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getPwmPeriod();
			}
		});
		_btnConfigPwmPeriodSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setPwmPeriod();
			}
		});

		_txtConfigTime         = (EditText) v.findViewById(R.id.txtConfigTime);
		_btnConfigTimeGet      = (Button)   v.findViewById(R.id.btnConfigTimeGet);
		_btnConfigTimeSet      = (Button)   v.findViewById(R.id.btnConfigTimeSet);
		_btnConfigTimeGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getTime();
			}
		});
		_btnConfigTimeSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setTime();
			}
		});



//		return super.onCreateView(inflater, container, savedInstanceState);
		return v;
	}


	//////////////
	//   TIME   //
	//////////////

	private void getTime() {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner("getTime") {
			@Override
			public boolean execute() {

				BleExtState bleState = new BleExtState(_ble);
				bleState.getTime(_address, new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						Log.i(TAG, "get time success");
						long unixTime = BleUtils.toUint32(result);
						final java.util.Date time = new java.util.Date(unixTime*1000);
//						Calendar calendar = new Calendar();
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								_txtConfigTime.setText(time.toString());
							}
						});

						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "get time failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				});
				return true;
			}
		});
	}

	private void setTime() {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner("setTime") {
			@Override
			public boolean execute() {

				// Or use Calendar.getInstance().getTime() / 1000;
				long unixTime = System.currentTimeMillis() / 1000;
				_ble.writeSetTime(_address, unixTime, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set time success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set time failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				});
				return true;
			}
		});
	}


	/////////////////////////
	// RELAY HIGH DURATION //
	/////////////////////////

	private void getRelayHigh() {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner("getRelayHigh") {
			@Override
			public boolean execute() {

				_ble.connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						_bleConfiguration.getRelayHighDuration(_address, execCallback);
					}
				}, new SimpleExecStatusCallback(new IIntegerCallback() {
					@Override
					public void onSuccess(final int result) {
						Log.i(TAG, "get relay high duration success");
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								_txtConfigRelayHigh.setText(Integer.toString(result));
							}
						});
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "get relay high duration failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void setRelayHigh() {
		showProgressSpinner();

		final int relayHighDurationMs;
		String configStr = _txtConfigRelayHigh.getText().toString();
		try {
			relayHighDurationMs = Integer.parseInt(configStr);
		} catch (NumberFormatException e) {
			showToast("Invalid number");
			return;
		}
		BleLog.getInstance().LOGi(TAG, "setRelayHigh: " + configStr + " = " + relayHighDurationMs);

		_handler.post(new ControlConfigFragment.SequentialRunner("setRelayHigh") {
			@Override
			public boolean execute() {
				_ble.connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						_bleConfiguration.setRelayHighDuration(_address, relayHighDurationMs, execCallback);
					}
				}, new SimpleExecStatusCallback(new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set relay high duration success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set relay high duration failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
			return true;
			}
		});
	}


	////////////////
	// PWM PERIOD //
	////////////////

	private void getPwmPeriod() {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner("getPwmPeriod") {
			@Override
			public boolean execute() {

				_ble.connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						_bleConfiguration.getPwmPeriod(_address, execCallback);
					}
				}, new SimpleExecStatusCallback(new ILongCallback() {
					@Override
					public void onSuccess(final long result) {
						Log.i(TAG, "get pwm period success");
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								_txtConfigPwmPeriod.setText(Long.toString(result));
							}
						});
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "get pwm period failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void setPwmPeriod() {
		showProgressSpinner();

		final long pwmPeriod;
		String configStr = _txtConfigPwmPeriod.getText().toString();
		try {
			pwmPeriod = Long.parseLong(configStr);
		} catch (NumberFormatException e) {
			showToast("Invalid number");
			return;
		}
		BleLog.getInstance().LOGi(TAG, "setPwmPeriod: " + configStr + " = " + pwmPeriod);

		_handler.post(new ControlConfigFragment.SequentialRunner("setPwmPeriod") {
			@Override
			public boolean execute() {
				_ble.connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						_bleConfiguration.setPwmPeriod(_address, pwmPeriod, execCallback);
					}
				}, new SimpleExecStatusCallback(new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set pwm period success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set pwm period failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}



	//////////////////////
	// HELPER FUNCTIONS //
	//////////////////////

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
