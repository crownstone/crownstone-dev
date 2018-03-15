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

import java.text.SimpleDateFormat;

import nl.dobots.bluenet.ble.base.BleConfiguration;
import nl.dobots.bluenet.ble.base.callbacks.IConfigurationCallback;
import nl.dobots.bluenet.ble.base.callbacks.IExecStatusCallback;
import nl.dobots.bluenet.ble.base.callbacks.IFloatCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.ILongCallback;
import nl.dobots.bluenet.ble.base.callbacks.SimpleExecStatusCallback;
import nl.dobots.bluenet.ble.base.structs.ConfigurationMsg;
import nl.dobots.bluenet.ble.base.structs.ControlMsg;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.BleExtState;
import nl.dobots.bluenet.ble.extended.callbacks.IExecuteCallback;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;
import nl.dobots.crownstone.CrownstoneDevApp;
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

	private Button   _btnConfigDimmingAllowedEnable;
	private Button   _btnConfigDimmingAllowedDisable;
	private Button   _btnConfigSwitchLockedEnable;
	private Button   _btnConfigSwitchLockedDisable;
	private EditText _txtConfigRelayHigh;
	private Button   _btnConfigRelayHighGet;
	private Button   _btnConfigRelayHighSet;
	private EditText _txtConfigPwmPeriod;
	private Button   _btnConfigPwmPeriodGet;
	private Button   _btnConfigPwmPeriodSet;
	private EditText _txtConfigBootDelay;
	private Button   _btnConfigBootDelayGet;
	private Button   _btnConfigBootDelaySet;
	private EditText _txtConfigTxPower;
	private Button   _btnConfigTxPowerGet;
	private Button   _btnConfigTxPowerSet;
	private EditText _txtConfigTime;
	private Button   _btnConfigTimeGet;
	private Button   _btnConfigTimeSet;
	private EditText _txtConfigCurrentThreshold;
	private Button   _btnConfigCurrentThresholdGet;
	private Button   _btnConfigCurrentThresholdSet;
	private EditText _txtConfigCurrentThresholdDimmer;
	private Button   _btnConfigCurrentThresholdDimmerGet;
	private Button   _btnConfigCurrentThresholdDimmerSet;
	private EditText _txtConfigMaxChipTemp;
	private Button   _btnConfigMaxChipTempGet;
	private Button   _btnConfigMaxChipTempSet;
	private EditText _txtConfigDimmerTempThresholdUp;
	private Button   _btnConfigDimmerTempThresholdUpGet;
	private Button   _btnConfigDimmerTempThresholdUpSet;
	private EditText _txtConfigDimmerTempThresholdDown;
	private Button   _btnConfigDimmerTempThresholdDownGet;
	private Button   _btnConfigDimmerTempThresholdDownSet;
	private EditText _txtConfigVoltageMultiplier;
	private Button   _btnConfigVoltageMultiplierGet;
	private Button   _btnConfigVoltageMultiplierSet;
	private EditText _txtConfigCurrentMultiplier;
	private Button   _btnConfigCurrentMultiplierGet;
	private Button   _btnConfigCurrentMultiplierSet;
	private EditText _txtConfigVoltageZero;
	private Button   _btnConfigVoltageZeroGet;
	private Button   _btnConfigVoltageZeroSet;
	private EditText _txtConfigCurrentZero;
	private Button   _btnConfigCurrentZeroGet;
	private Button   _btnConfigCurrentZeroSet;
	private EditText _txtConfigPowerZero;
	private Button   _btnConfigPowerZeroGet;
	private Button   _btnConfigPowerZeroSet;


	// Other stuff
//	private BleExt _ble;
	private CrownstoneDevApp _app;
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

		_app = CrownstoneDevApp.getInstance();
//		_ble = ControlActivity.getInstance().getBle();
		_address = ControlActivity.getInstance().getAddress();
		_bleConfiguration = new BleConfiguration(_app.getBle().getBleBase());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		_closing = true;
		_handler.removeCallbacksAndMessages(null);
		_app.getScanner().stopScanning();

		// finish has to be called on the library to release the objects if the library
		// is not used anymore
		if (_app.getBle().isConnected(null)) {
			_app.getBle().disconnectAndClose(false, new IStatusCallback() {
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

		_btnConfigDimmingAllowedEnable  = (Button)   v.findViewById(R.id.btnConfigDimmingAllowedEnable);
		_btnConfigDimmingAllowedDisable = (Button)   v.findViewById(R.id.btnConfigDimmingAllowedDisable);
		_btnConfigDimmingAllowedEnable.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dimmingAllowedEnable();
			}
		});
		_btnConfigDimmingAllowedDisable.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dimmingAllowedDisable();
			}
		});

		_btnConfigSwitchLockedEnable  = (Button)   v.findViewById(R.id.btnConfigSwitchLockedEnable);
		_btnConfigSwitchLockedDisable = (Button)   v.findViewById(R.id.btnConfigSwitchLockedDisable);
		_btnConfigSwitchLockedEnable.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switchLockedEnable();
			}
		});
		_btnConfigSwitchLockedDisable.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switchLockedDisable();
			}
		});

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

		_txtConfigBootDelay    = (EditText) v.findViewById(R.id.txtConfigBootDelay);
		_btnConfigBootDelayGet = (Button)   v.findViewById(R.id.btnConfigBootDelayGet);
		_btnConfigBootDelaySet = (Button)   v.findViewById(R.id.btnConfigBootDelaySet);
		_btnConfigBootDelayGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getBootDelay();
			}
		});
		_btnConfigBootDelaySet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setBootDelay();
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

		_txtConfigTxPower         = (EditText) v.findViewById(R.id.txtConfigTxPower);
		_btnConfigTxPowerGet      = (Button)   v.findViewById(R.id.btnConfigTxPowerGet);
		_btnConfigTxPowerSet      = (Button)   v.findViewById(R.id.btnConfigTxPowerSet);
		_btnConfigTxPowerGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getTxPower();
			}
		});
		_btnConfigTxPowerSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setTxPower();
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
		v.findViewById(R.id.btnConfigTimeSetNow).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setTimeNow();
			}
		});
//		v.findViewById(R.id.btnConfigTimeSetPlusMinute).setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				setTimeNow(60);
//			}
//		});
//		v.findViewById(R.id.btnConfigTimeSetMinMinute).setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				setTimeNow(-60);
//			}
//		});
//		v.findViewById(R.id.btnConfigTimeSetPlusHour).setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				setTimeNow(60*60);
//			}
//		});
//		v.findViewById(R.id.btnConfigTimeSetMinHour).setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				setTimeNow(-60*60);
//			}
//		});

		_txtConfigCurrentThreshold    = (EditText) v.findViewById(R.id.txtConfigCurrentThreshold);
		_btnConfigCurrentThresholdGet = (Button)   v.findViewById(R.id.btnConfigCurrentThresholdGet);
		_btnConfigCurrentThresholdSet = (Button)   v.findViewById(R.id.btnConfigCurrentThresholdSet);
		_btnConfigCurrentThresholdGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getCurrentThreshold();
			}
		});
		_btnConfigCurrentThresholdSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setCurrentThreshold();
			}
		});

		_txtConfigCurrentThresholdDimmer    = (EditText) v.findViewById(R.id.txtConfigCurrentThresholdDimmer);
		_btnConfigCurrentThresholdDimmerGet = (Button)   v.findViewById(R.id.btnConfigCurrentThresholdDimmerGet);
		_btnConfigCurrentThresholdDimmerSet = (Button)   v.findViewById(R.id.btnConfigCurrentThresholdDimmerSet);
		_btnConfigCurrentThresholdDimmerGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getCurrentThresholdDimmer();
			}
		});
		_btnConfigCurrentThresholdDimmerSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setCurrentThresholdDimmer();
			}
		});

		_txtConfigMaxChipTemp    = (EditText) v.findViewById(R.id.txtConfigMaxChipTemp);
		_btnConfigMaxChipTempGet = (Button)   v.findViewById(R.id.btnConfigMaxChipTempGet);
		_btnConfigMaxChipTempSet = (Button)   v.findViewById(R.id.btnConfigMaxChipTempSet);
		_btnConfigMaxChipTempGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getMaxChipTemp();
			}
		});
		_btnConfigMaxChipTempSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setMaxChipTemp();
			}
		});

		_txtConfigDimmerTempThresholdUp    = (EditText) v.findViewById(R.id.txtConfigDimmerTempThresholdUp);
		_btnConfigDimmerTempThresholdUpGet = (Button)   v.findViewById(R.id.btnConfigDimmerTempThresholdUpGet);
		_btnConfigDimmerTempThresholdUpSet = (Button)   v.findViewById(R.id.btnConfigDimmerTempThresholdUpSet);
		_btnConfigDimmerTempThresholdUpGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getDimmerTempThresholdUp();
			}
		});
		_btnConfigDimmerTempThresholdUpSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setDimmerTempThresholdUp();
			}
		});

		_txtConfigDimmerTempThresholdDown    = (EditText) v.findViewById(R.id.txtConfigDimmerTempThresholdDown);
		_btnConfigDimmerTempThresholdDownGet = (Button)   v.findViewById(R.id.btnConfigDimmerTempThresholdDownGet);
		_btnConfigDimmerTempThresholdDownSet = (Button)   v.findViewById(R.id.btnConfigDimmerTempThresholdDownSet);
		_btnConfigDimmerTempThresholdDownGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getDimmerTempThresholdDown();
			}
		});
		_btnConfigDimmerTempThresholdDownSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setDimmerTempThresholdDown();
			}
		});

		_txtConfigVoltageMultiplier    = (EditText) v.findViewById(R.id.txtConfigVoltageMultiplier);
		_btnConfigVoltageMultiplierGet = (Button)   v.findViewById(R.id.btnConfigVoltageMultiplierGet);
		_btnConfigVoltageMultiplierSet = (Button)   v.findViewById(R.id.btnConfigVoltageMultiplierSet);
		_btnConfigVoltageMultiplierGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getVoltageMultiplier();
			}
		});
		_btnConfigVoltageMultiplierSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setVoltageMultiplier();
			}
		});

		_txtConfigCurrentMultiplier    = (EditText) v.findViewById(R.id.txtConfigCurrentMultiplier);
		_btnConfigCurrentMultiplierGet = (Button)   v.findViewById(R.id.btnConfigCurrentMultiplierGet);
		_btnConfigCurrentMultiplierSet = (Button)   v.findViewById(R.id.btnConfigCurrentMultiplierSet);
		_btnConfigCurrentMultiplierGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getCurrentMultiplier();
			}
		});
		_btnConfigCurrentMultiplierSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setCurrentMultiplier();
			}
		});

		_txtConfigVoltageZero    = (EditText) v.findViewById(R.id.txtConfigVoltageZero);
		_btnConfigVoltageZeroGet = (Button)   v.findViewById(R.id.btnConfigVoltageZeroGet);
		_btnConfigVoltageZeroSet = (Button)   v.findViewById(R.id.btnConfigVoltageZeroSet);
		_btnConfigVoltageZeroGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getVoltageZero();
			}
		});
		_btnConfigVoltageZeroSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setVoltageZero();
			}
		});

		_txtConfigCurrentZero    = (EditText) v.findViewById(R.id.txtConfigCurrentZero);
		_btnConfigCurrentZeroGet = (Button)   v.findViewById(R.id.btnConfigCurrentZeroGet);
		_btnConfigCurrentZeroSet = (Button)   v.findViewById(R.id.btnConfigCurrentZeroSet);
		_btnConfigCurrentZeroGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getCurrentZero();
			}
		});
		_btnConfigCurrentZeroSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setCurrentZero();
			}
		});

		_txtConfigPowerZero    = (EditText) v.findViewById(R.id.txtConfigPowerZero);
		_btnConfigPowerZeroGet = (Button)   v.findViewById(R.id.btnConfigPowerZeroGet);
		_btnConfigPowerZeroSet = (Button)   v.findViewById(R.id.btnConfigPowerZeroSet);
		_btnConfigPowerZeroGet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getPowerZero();
			}
		});
		_btnConfigPowerZeroSet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setPowerZero();
			}
		});

//		return super.onCreateView(inflater, container, savedInstanceState);
		return v;
	}



	/////////////////////
	// DIMMING ALLOWED //
	/////////////////////

	private void dimmingAllowedEnable() {
		writeDimmingAllowed(true);
	}

	private void dimmingAllowedDisable() {
		writeDimmingAllowed(false);
	}

	private void writeDimmingAllowed(boolean enable) {
		optionEnable("allow dimming", enable, BluenetConfig.CMD_ALLOW_DIMMING);
	}



	///////////////////
	// SWITCH LOCKED //
	///////////////////

	private void switchLockedEnable() {
		writeSwitchLocked(true);
	}

	private void switchLockedDisable() {
		writeSwitchLocked(false);
	}

	private void writeSwitchLocked(boolean enable) {
		optionEnable("switch locked", enable, BluenetConfig.CMD_LOCK_SWITCH);
	}



	/////////////////////////
	// RELAY HIGH DURATION //
	/////////////////////////

	private void getRelayHigh() {
		getUint16("RelayHigh", _txtConfigRelayHigh, BluenetConfig.CONFIG_RELAY_HIGH_DURATION);
	}

	private void setRelayHigh() {
		setUint16("RelayHigh", _txtConfigRelayHigh, BluenetConfig.CONFIG_RELAY_HIGH_DURATION);
	}



	////////////////
	// PWM PERIOD //
	////////////////

	private void getPwmPeriod() {
		getUint32("PwmPeriod", _txtConfigPwmPeriod, BluenetConfig.CONFIG_PWM_PERIOD);
	}

	private void setPwmPeriod() {
		setUint32("PwmPeriod", _txtConfigPwmPeriod, BluenetConfig.CONFIG_PWM_PERIOD);
	}



	////////////////
	// BOOT DELAY //
	////////////////

	private void getBootDelay() {
		getUint16("BootDelay", _txtConfigBootDelay, BluenetConfig.CONFIG_BOOT_DELAY);
	}

	private void setBootDelay() {
		setUint16("BootDelay", _txtConfigBootDelay, BluenetConfig.CONFIG_BOOT_DELAY);
	}



	//////////////////
	//   TX POWER   //
	//////////////////

	private void getTxPower() {
		getInt8("TxPower", _txtConfigTxPower, BluenetConfig.CONFIG_TX_POWER);
	}

	private void setTxPower() {
		setInt8("TxPower", _txtConfigTxPower, BluenetConfig.CONFIG_TX_POWER);
	}



	//////////////
	//   TIME   //
	//////////////

	private void getTime() {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner("getTime") {
			@Override
			public boolean execute() {

				BleExtState bleState = new BleExtState(_app.getBle());
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
		// Get time from edit text and set it
		SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd kk:mm:ss z yyyy");
		java.util.Date time = null;
		try {
			time = dateFormat.parse(_txtConfigTime.getText().toString());
		} catch (java.text.ParseException e) {
			showToast("Invalid timestamp");
			return;
		}
		setTime(time.getTime() / 1000);
	}

	private void setTimeNow() {
		setTimeNow(0L);
	}

	private void setTimeNow(long diff) {
		setTime(diff, true);
	}

	private void setTime(long unixTime) {
		setTime(unixTime, false);
	}

	// Set time to given time, or if isDifference = true, to current time + given time
	private void setTime(final long time, final boolean isDifference) {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner("setTime") {
			@Override
			public boolean execute() {

				long unixTime = time;
				if (isDifference) {
					unixTime += System.currentTimeMillis() / 1000;
				}
				final java.util.Date date = new java.util.Date(unixTime*1000);
				_app.getBle().writeSetTime(_address, unixTime, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set time success");
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								_txtConfigTime.setText(date.toString());
							}
						});
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

	///////////////////////////
	//   CURRENT THRESHOLD   //
	///////////////////////////

	private void getCurrentThreshold() {
		getUint16("CurrentThreshold", _txtConfigCurrentThreshold, BluenetConfig.CONFIG_CURRENT_THRESHOLD);
	}

	private void setCurrentThreshold() {
		setUint16("CurrentThreshold", _txtConfigCurrentThreshold, BluenetConfig.CONFIG_CURRENT_THRESHOLD);
	}



	//////////////////////////////////
	//   CURRENT THRESHOLD DIMMER   //
	//////////////////////////////////

	private void getCurrentThresholdDimmer() {
		getUint16("CurrentThresholdDimmer", _txtConfigCurrentThresholdDimmer, BluenetConfig.CONFIG_CURRENT_THRESHOLD_DIMMER);
	}

	private void setCurrentThresholdDimmer() {
		setUint16("CurrentThresholdDimmer", _txtConfigCurrentThresholdDimmer, BluenetConfig.CONFIG_CURRENT_THRESHOLD_DIMMER);
	}



	///////////////////////
	//   MAX CHIP TEMP   //
	///////////////////////

	private void getMaxChipTemp() {
		getInt8("MaxChipTemp", _txtConfigMaxChipTemp, BluenetConfig.CONFIG_MAX_CHIP_TEMP);
	}

	private void setMaxChipTemp() {
		setInt8("MaxChipTemp", _txtConfigMaxChipTemp, BluenetConfig.CONFIG_MAX_CHIP_TEMP);
	}



	//////////////////////////////////
	//   DIMMER TEMP THRESHOLD UP   //
	//////////////////////////////////

	private void getDimmerTempThresholdUp() {
		getFloat("DimmerTempThresholdUp", _txtConfigDimmerTempThresholdUp, BluenetConfig.CONFIG_DIMMER_TEMP_UP);
	}

	private void setDimmerTempThresholdUp() {
		setFloat("DimmerTempThresholdUp", _txtConfigDimmerTempThresholdUp, BluenetConfig.CONFIG_DIMMER_TEMP_UP);
	}



	////////////////////////////////////
	//   DIMMER TEMP THRESHOLD DOWN   //
	////////////////////////////////////

	private void getDimmerTempThresholdDown() {
		getFloat("DimmerTempThresholdDown", _txtConfigDimmerTempThresholdDown, BluenetConfig.CONFIG_DIMMER_TEMP_DOWN);
	}

	private void setDimmerTempThresholdDown() {
		setFloat("DimmerTempThresholdDown", _txtConfigDimmerTempThresholdDown, BluenetConfig.CONFIG_DIMMER_TEMP_DOWN);
	}



	////////////////////////////
	//   VOLTAGE MULTIPLIER   //
	////////////////////////////

	private void getVoltageMultiplier() {
		getFloat("VoltageMultiplier", _txtConfigVoltageMultiplier, BluenetConfig.CONFIG_VOLTAGE_MULTIPLIER);
	}

	private void setVoltageMultiplier() {
		setFloat("VoltageMultiplier", _txtConfigVoltageMultiplier, BluenetConfig.CONFIG_VOLTAGE_MULTIPLIER);
	}



	////////////////////////////
	//   CURRENT MULTIPLIER   //
	////////////////////////////

	private void getCurrentMultiplier() {
		getFloat("CurrentMultiplier", _txtConfigCurrentMultiplier, BluenetConfig.CONFIG_CURRENT_MULTIPLIER);
	}

	private void setCurrentMultiplier() {
		setFloat("CurrentMultiplier", _txtConfigCurrentMultiplier, BluenetConfig.CONFIG_CURRENT_MULTIPLIER);
	}



	/////////////////////
	//   VOTAGE ZERO   //
	/////////////////////

	private void getVoltageZero() {
		getInt32("VoltageZero", _txtConfigVoltageZero, BluenetConfig.CONFIG_VOLTAGE_ZERO);
	}

	private void setVoltageZero() {
		setInt32("VoltageZero", _txtConfigVoltageZero, BluenetConfig.CONFIG_VOLTAGE_ZERO);
	}



	//////////////////////
	//   CURRENT ZERO   //
	//////////////////////

	private void getCurrentZero() {
		getInt32("CurrentZero", _txtConfigCurrentZero, BluenetConfig.CONFIG_CURRENT_ZERO);
	}

	private void setCurrentZero() {
		setInt32("CurrentZero", _txtConfigCurrentZero, BluenetConfig.CONFIG_CURRENT_ZERO);
	}



	//////////////////////
	//   POWER ZERO   //
	//////////////////////

	private void getPowerZero() {
		getInt32("PowerZero", _txtConfigPowerZero, BluenetConfig.CONFIG_POWER_ZERO);
	}

	private void setPowerZero() {
		setInt32("PowerZero", _txtConfigPowerZero, BluenetConfig.CONFIG_POWER_ZERO);
	}







	////////////////////////
	// TEMPLATE FUNCTIONS //
	////////////////////////

	private void getUint16(final String name, final EditText outputEditText, final int configurationType) {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {

				_app.getBle().connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						_app.getBle().getBleBase().getConfiguration(_address, configurationType, new IConfigurationCallback() {
							@Override
							public void onSuccess(ConfigurationMsg configuration) {
								if (configuration.getLength() != 2) {
									Log.e(TAG, "Wrong length parameter: " + configuration.getLength());
									onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
								} else {
									int value = configuration.getShortValue();
									Log.i(TAG, name + ": " + value);
									execCallback.onSuccess(value);
								}
							}

							@Override
							public void onError(int error) {
								execCallback.onError(error);
							}
						});
					}
				}, new SimpleExecStatusCallback(new IIntegerCallback() {
					@Override
					public void onSuccess(final int result) {
						Log.i(TAG, "get " + name + " success");
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								outputEditText.setText(Integer.toString(result));
							}
						});
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "get " + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void setUint16(final String name, final EditText outputEditText, final int configurationType) {
		showProgressSpinner();

		final int value;
		String configStr = outputEditText.getText().toString();
		try {
			value = Integer.parseInt(configStr);
		} catch (NumberFormatException e) {
			showToast("Invalid number");
			return;
		}
		BleLog.getInstance().LOGi(TAG, "set " + name + ": " + configStr + " = " + value);

		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {
				_app.getBle().connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						byte[] valArr = BleUtils.shortToByteArray(value);
						ConfigurationMsg configuration = new ConfigurationMsg(configurationType, valArr.length, valArr);
						_app.getBle().getBleBase().writeConfiguration(_address, configuration, true, execCallback);
					}
				}, new SimpleExecStatusCallback(new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set " + name + " success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set " + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}


	private void getUint32(final String name, final EditText outputEditText, final int configurationType) {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {

				_app.getBle().connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						_app.getBle().getBleBase().getConfiguration(_address, configurationType, new IConfigurationCallback() {
							@Override
							public void onSuccess(ConfigurationMsg configuration) {
								if (configuration.getLength() != 4) {
									Log.e(TAG, "Wrong length parameter: " + configuration.getLength());
									onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
								} else {
									long value = BleUtils.toUint32(configuration.getIntValue());
									Log.i(TAG, name + ": " + value);
									execCallback.onSuccess(value);
								}
							}

							@Override
							public void onError(int error) {
								execCallback.onError(error);
							}
						});
					}
				}, new SimpleExecStatusCallback(new ILongCallback() {
					@Override
					public void onSuccess(final long result) {
						Log.i(TAG, "get " + name + " success");
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								outputEditText.setText(Long.toString(result));
							}
						});
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "get " + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void setUint32(final String name, final EditText outputEditText, final int configurationType) {
		showProgressSpinner();

		final long value;
		String configStr = outputEditText.getText().toString();
		try {
			value = Long.parseLong(configStr);
		} catch (NumberFormatException e) {
			showToast("Invalid number");
			return;
		}
		BleLog.getInstance().LOGi(TAG, "set " + name + ": " + configStr + " = " + value);

		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {
				_app.getBle().connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						byte[] valArr = BleUtils.uint32ToByteArray(value);
						ConfigurationMsg configuration = new ConfigurationMsg(configurationType, valArr.length, valArr);
						_app.getBle().getBleBase().writeConfiguration(_address, configuration, true, execCallback);
					}
				}, new SimpleExecStatusCallback(new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set " + name + " success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set " + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}


	private void getInt8(final String name, final EditText outputEditText, final int configurationType) {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {

				_app.getBle().connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						_app.getBle().getBleBase().getConfiguration(_address, configurationType, new IConfigurationCallback() {
							@Override
							public void onSuccess(ConfigurationMsg configuration) {
								if (configuration.getLength() != 1) {
									Log.e(TAG, "Wrong length parameter: " + configuration.getLength());
									onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
								} else {
									int value = configuration.getByteValue();
									Log.i(TAG, name + ": " + value);
									execCallback.onSuccess(value);
								}
							}

							@Override
							public void onError(int error) {
								execCallback.onError(error);
							}
						});
					}
				}, new SimpleExecStatusCallback(new IIntegerCallback() {
					@Override
					public void onSuccess(final int result) {
						Log.i(TAG, "get " + name + " success");
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								outputEditText.setText(Integer.toString(result));
							}
						});
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "get " + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void setInt8(final String name, final EditText outputEditText, final int configurationType) {
		showProgressSpinner();

		final int value;
		String configStr = outputEditText.getText().toString();
		try {
			value = Integer.parseInt(configStr);
		} catch (NumberFormatException e) {
			showToast("Invalid number");
			return;
		}
		BleLog.getInstance().LOGi(TAG, "set " + name + ": " + configStr + " = " + value);

		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {
				_app.getBle().connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						byte[] valArr = new byte[]{(byte)value};
						ConfigurationMsg configuration = new ConfigurationMsg(configurationType, valArr.length, valArr);
						_app.getBle().getBleBase().writeConfiguration(_address, configuration, true, execCallback);
					}
				}, new SimpleExecStatusCallback(new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set " + name + " success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set " + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}




	private void getInt32(final String name, final EditText outputEditText, final int configurationType) {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {

				_app.getBle().connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						_app.getBle().getBleBase().getConfiguration(_address, configurationType, new IConfigurationCallback() {
							@Override
							public void onSuccess(ConfigurationMsg configuration) {
								if (configuration.getLength() != 4) {
									Log.e(TAG, "Wrong length parameter: " + configuration.getLength());
									onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
								} else {
									int value = configuration.getIntValue();
									Log.i(TAG, name + ": " + value);
									execCallback.onSuccess(value);
								}
							}

							@Override
							public void onError(int error) {
								execCallback.onError(error);
							}
						});
					}
				}, new SimpleExecStatusCallback(new IIntegerCallback() {
					@Override
					public void onSuccess(final int result) {
						Log.i(TAG, "get " + name + " success");
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								outputEditText.setText(Integer.toString(result));
							}
						});
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "get " + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void setInt32(final String name, final EditText outputEditText, final int configurationType) {
		showProgressSpinner();

		final int value;
		String configStr = outputEditText.getText().toString();
		try {
			value = Integer.parseInt(configStr);
		} catch (NumberFormatException e) {
			showToast("Invalid number");
			return;
		}
		BleLog.getInstance().LOGi(TAG, "set " + name + ": " + configStr + " = " + value);

		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {
				_app.getBle().connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						byte[] valArr = BleUtils.intToByteArray(value);
						ConfigurationMsg configuration = new ConfigurationMsg(configurationType, valArr.length, valArr);
						_app.getBle().getBleBase().writeConfiguration(_address, configuration, true, execCallback);
					}
				}, new SimpleExecStatusCallback(new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set " + name + " success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set " + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}


	private void getFloat(final String name, final EditText outputEditText, final int configurationType) {
		showProgressSpinner();
		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {

				_app.getBle().connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						_app.getBle().getBleBase().getConfiguration(_address, configurationType, new IConfigurationCallback() {
							@Override
							public void onSuccess(ConfigurationMsg configuration) {
								if (configuration.getLength() != 4) {
									Log.e(TAG, "Wrong length parameter: " + configuration.getLength());
									onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
								} else {
									float value = configuration.getFloatValue();
									Log.i(TAG, name + ": " + value);
									execCallback.onSuccess(value);
								}
							}

							@Override
							public void onError(int error) {
								execCallback.onError(error);
							}
						});
					}
				}, new SimpleExecStatusCallback(new IFloatCallback() {
					@Override
					public void onSuccess(final float result) {
						Log.i(TAG, "get " + name + " success");
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								outputEditText.setText(Float.toString(result));
							}
						});
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "get " + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void setFloat(final String name, final EditText outputEditText, final int configurationType) {
		showProgressSpinner();

		final float value;
		String configStr = outputEditText.getText().toString();
		try {
			value = Float.parseFloat(configStr);
		} catch (NumberFormatException e) {
			showToast("Invalid number");
			return;
		}
		BleLog.getInstance().LOGi(TAG, "set " + name + ": " + configStr + " = " + value);

		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {
				_app.getBle().connectAndExecute(_address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						byte[] valArr = BleUtils.floatToByteArray(value);
						ConfigurationMsg configuration = new ConfigurationMsg(configurationType, valArr.length, valArr);
						_app.getBle().getBleBase().writeConfiguration(_address, configuration, true, execCallback);
					}
				}, new SimpleExecStatusCallback(new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set " + name + " success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set " + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				}));
				return true;
			}
		});
	}

	private void optionEnable(final String name, final boolean enable, final int controlType) {
		showProgressSpinner();
		final String enableStr = enable ? "Enable " : "Disable ";
		BleLog.getInstance().LOGi(TAG, enableStr + name);
		_handler.post(new ControlConfigFragment.SequentialRunner(name) {
			@Override
			public boolean execute() {
				byte[] valArr = new byte[1];
				valArr[0] = (byte)(enable ? 1 : 0);
				ControlMsg msg = new ControlMsg(controlType, valArr.length, valArr);
				_app.getBle().writeControl(_address, msg, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, enableStr + name + " success");
						showToast("Success");
						done();
						dismissProgressSpinner();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, enableStr + name + " failed: " + error);
						displayError(error);
						done();
						dismissProgressSpinner();
					}
				});
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
