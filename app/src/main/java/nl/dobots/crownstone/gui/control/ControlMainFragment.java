package nl.dobots.crownstone.gui.control;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.achartengine.tools.PanListener;
import org.achartengine.tools.ZoomEvent;
import org.achartengine.tools.ZoomListener;

import java.util.Date;

import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.CrownstoneServiceData;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.crownstone.R;

/**
 * This example activity shows the use of the bluenet library. The library is first initialized,
 * which enables the bluetooth adapter. It shows the following steps:
 *
 * 1. Connect to a device and discover the available services / characteristics
 * 2. Read a characteristic (PWM characteristic)
 * 3. Write a characteristic (PWM characteristic)
 * 4. Disconnect and close the device
 * 5. And how to do the 3 steps (connectDiscover, execute and disconnectClose) with one
 *    function call
 *
 * For an example of how to scan for devices see MainActivity.java or MainActivity.java
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class ControlMainFragment extends Fragment implements ZoomListener, PanListener {

	private static final String TAG = ControlMainFragment.class.getCanonicalName();

	private static final int TEMP_UPDATE_TIME = 2000;
	public static final int STATISTICS_X_TIME = 5;

	private GraphicalView _graphView;

	private ImageView _lightBulb;
	private CheckBox _cbPwmEnable;
	private Button _btnRelayOff;
	private Button _btnRelayOn;
	private Button _btnPwmOff;
	private Button _btnPwmOn;
	private SeekBar _sbPwm;
	private RelativeLayout _layGraph;
	private ImageButton _btnZoomIn;
	private ImageButton _btnZoomOut;
	private ImageButton _btnZoomReset;

	private RelativeLayout _layStatistics;
	private RelativeLayout _layControl;

	private boolean _zoomApplied = false;
	private boolean _panApplied = false;

	private Handler _handler;

	private long _maxPowerUsage;
	private long _minPowerUsage;
	private long _maxAccumulatedEnergy;
	private long _minAccumulatedEnergy;

	private boolean _lightOn;

	private BleExt _ble;
	private String _address;
	private int _resetCounterSeries;

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
			BleLog.LOGv(TAG, "notify");
		}

		@Override
		public void run() {
				synchronized (this) {
			if (execute()) {
					try {
						BleLog.LOGv(TAG, "wait");
						if (!noWait) {
							wait();
						}
						BleLog.LOGv(TAG, "wait done");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	private int resetCounter = -1;

	private SequentialRunner _advStateChecker = new SequentialRunner("_advStateChecker") {
		@Override
		public boolean execute() {
			if (!_ble.isConnected(null)) {
				BleLog.LOGi(TAG, "starting scan");
				_ble.startScan(new IBleDeviceCallback() {
					@Override
					public void onDeviceScanned(BleDevice device) {
						BleLog.LOGd(TAG, "onDeviceScanned %s", device.getName());
						if (device.getAddress().equals(_address)) {
							CrownstoneServiceData serviceData = device.getServiceData();
							if (serviceData != null) {
//								onSwitchState(serviceData.getSwitchState());
								onPwm(serviceData.getPwm());
								onRelayState(serviceData.getRelayState());
								onTemperature(serviceData.getTemperature());
								onPowerUsage(serviceData.getPowerUsage());
								onAccumulatedEnergy(serviceData.getAccumulatedEnergy());

								updateLightBulb(serviceData.getPwm() > 0 || serviceData.getRelayState());

								String[] split = device.getName().split("_");
								if (split.length > 1) {
									int counter = Integer.valueOf(split[1]);
									if (resetCounter == -1) {
										resetCounter = counter;
									} else if (counter != resetCounter) {
										onResetCounterChange();
										resetCounter = counter;
									}
								}
							}
							_ble.stopScan(null);
							done();
						}
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "scan error: %d", error);
						done();
					}
				});
				_handler.postDelayed(this, TEMP_UPDATE_TIME);
				return true;
			} else {
				_handler.postDelayed(this, 100);
				return false;
			}
		}
	};

	private XYMultipleSeriesRenderer _multipleSeriesRenderer;
	private XYMultipleSeriesDataset _dataSet;

	private PointStyle[] listOfPointStyles = new PointStyle[] { PointStyle.CIRCLE, PointStyle.POINT, PointStyle.DIAMOND,
			PointStyle.SQUARE, PointStyle.TRIANGLE, PointStyle.X };

	private int[] listOfSeriesColors = new int[] { 0xFF00BFFF, Color.DKGRAY, Color.GREEN, Color.YELLOW,
			Color.MAGENTA, Color.CYAN, Color.WHITE };

	private long _liveMinTime;
	private long _maxTime;
	private long _minTemp;
	private long _maxTemp;
	private int _currentSeries = 0;
	private int _temperatureSeries;
	private int _switchStateSeries;
	private int _powerUsageSeries;
	private int _accumulatedEnergySeries;
	private int _relayStateSeries;
	private int _pwmSeries;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		HandlerThread ht = new HandlerThread("BleHandler");
		ht.start();
		_handler = new Handler(ht.getLooper());


		_ble = ControlActivity.getInstance().getBle();
		_address = ControlActivity.getInstance().getAddress();

		final ProgressDialog dlg = ProgressDialog.show(getActivity(), "Connecting", "Please wait...", true, true);
		dlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				getActivity().finish();
			}
		});

		// first we have to connect to the device and discover the available characteristics.
		_ble.connectAndDiscover(_address, new IDiscoveryCallback() {
			@Override
			public void onDiscovery(String serviceUuid, String characteristicUuid) {
				// this function is called for every detected characteristic with the
				// characteristic's UUID and the UUID of the service it belongs.
				// you can keep track of what functions are available on the device,
				// but you don't have to, the library does that for you.
			}

			@Override
			public void onSuccess() {
				// once discovery is completed, this function will be called. we can now execute
				// the functions on the device. in this case, we want to know what the current
				// PWM state is

				// first we try and read the PWM value from the device. this call will make sure
				// that the PWM or State characteristic is available, otherwise an error is created
				_ble.readPwm(new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						// if reading was successful, we get the value in the onSuccess as
						// the parameter

						// now we can update the image of the light bulb to on (if PWM value is
						// greater than 0) or off if it is 0
						updateLightBulb(result > 0);
						_sbPwm.setProgress(result);

						_handler.postDelayed(_advStateChecker, 2000);

						// at the end we disconnect and close the device again. you could also
						// stay connected if you want. but it's preferable to only connect,
						// execute and disconnect, so that the device can continue advertising
						// again.
						_ble.disconnectAndClose(false, new IStatusCallback() {
							@Override
							public void onSuccess() {
								// at this point we successfully disconnected and closed
								// the device again
								dlg.dismiss();
							}

							@Override
							public void onError(int error) {
								// an error occurred while disconnecting
								dlg.dismiss();
							}
						});
					}

					@Override
					public void onError(int error) {
						// an error occurred while trying to read the PWM state
						Log.e(TAG, "Failed to get Pwm: " + error);

						if (error == BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND) {

							// return an error and exit if the PWM characteristic is not available
							getActivity().runOnUiThread(new Runnable() {
								@Override
								public void run() {
									Toast.makeText(getActivity(), "No PWM Characteristic found for this device!", Toast.LENGTH_LONG).show();
								}
							});
							getActivity().finish();
						} else {

							// disconnect and close the device again
							_ble.disconnectAndClose(false, new IStatusCallback() {
								@Override
								public void onSuccess() {
									// at this point we successfully disconnected and closed
									// the device again.
									dlg.dismiss();
								}

								@Override
								public void onError(int error) {
									// an error occurred while disconnecting
									dlg.dismiss();
								}
							});
						}
					}
				});
			}

			@Override
			public void onError(int error) {
				// an error occurred during connect/discover
				Log.e(TAG, "failed to connect/discover: " + error);
				dlg.dismiss();
				getActivity().finish();
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		_handler.removeCallbacksAndMessages(null);
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
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.frag_control_main, container, false);

		_lightBulb = (ImageView) v.findViewById(R.id.imgLightBulb);
		_lightBulb.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				togglePWM();
			}
		});

		_btnPwmOn = (Button) v.findViewById(R.id.btnPwmOn);
		_btnPwmOn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				pwmOn();
			}
		});

		_btnPwmOff = (Button) v.findViewById(R.id.btnPwmOff);
		_btnPwmOff.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				pwmOff();
			}
		});

		_btnRelayOn = (Button) v.findViewById(R.id.btnRelayOn);
		_btnRelayOn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				relayOn();
			}
		});

		_btnRelayOff = (Button) v.findViewById(R.id.btnRelayOff);
		_btnRelayOff.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				relayOff();
			}
		});

		_cbPwmEnable = (CheckBox) v.findViewById(R.id.cbPwmEnable);
		_cbPwmEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				_sbPwm.setEnabled(isChecked);
			}
		});

		_sbPwm = (SeekBar) v.findViewById(R.id.sbPwm);
		_sbPwm.setEnabled(_cbPwmEnable.isChecked());
		_sbPwm.setMax(100);
		_sbPwm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				setPwm(seekBar.getProgress());
			}
		});

		_layControl = (RelativeLayout) v.findViewById(R.id.layControl);
		_layStatistics = (RelativeLayout) v.findViewById(R.id.layStatistics);
		_layGraph = (RelativeLayout) v.findViewById(R.id.graph);

		_btnZoomIn = (ImageButton) v.findViewById(R.id.zoomIn);
		_btnZoomIn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_layStatistics.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
				_layControl.setVisibility(View.INVISIBLE);
			}
		});
		_btnZoomOut = (ImageButton) v.findViewById(R.id.zoomOut);
		_btnZoomOut.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int pixels = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, getResources().getDisplayMetrics());
				_layStatistics.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, pixels));
				_layControl.setVisibility(View.VISIBLE);
			}
		});
		_btnZoomReset = (ImageButton) v.findViewById(R.id.zoomReset);
//		_btnZoomReset.setVisibility(View.GONE);
		_btnZoomReset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graphView.zoomReset();
				_zoomApplied = false;
				_panApplied = false;
			}
		});

		createGraph();
//		_layStatistics.setVisibility(View.GONE);

		return v;
	}

	private void pwmOff() {
		_handler.post(new SequentialRunner("pwmOff") {
			@Override
			public boolean execute() {
				// switch the device off. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device off, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.powerOff(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "power off success");
						// power was switch off successfully, update the light bulb
//						updateLightBulb(false);
						_sbPwm.setProgress(0);
						done();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "power off failed: " + error);
						done();
					}
				});
				return true;
			}
		});
	}

	private void pwmOn() {
		_handler.post(new SequentialRunner("pwmOn") {
			@Override
			public boolean execute() {
				// switch the device on. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device on, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.powerOn(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "power on success");
						// power was switch on successfully, update the light bulb
//						updateLightBulb(true);
						_sbPwm.setProgress(100);
						done();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "power on failed: " + error);
						done();
					}
				});
				return true;
			}
		});
	}

	private void togglePWM() {
		_handler.post(new SequentialRunner("togglePWM") {
			@Override
			public boolean execute() {
				// toggle the device switch, without needing to know the current state. this function will
				// check first if the device is connected (and connect if it is not), then it reads the
				// current PWM state, and depending on the state, decides if it needs to switch it on or
				// off. in the end it disconnects again (once the disconnect timeout expires)
				_ble.togglePower(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "toggle success");
						// power was toggled successfully, update the light bulb
//						updateLightBulb(!_lightOn);
						if (_lightOn) {
							_sbPwm.setProgress(100);
						} else {
							_sbPwm.setProgress(0);
						}
						done();
					}

					@Override
					public void onError(int error) {
						Log.e(TAG, "toggle failed: " + error);
						done();
					}
				});
				return true;
			}
		});
	}

	private void updateLightBulb(final boolean on) {
		if (getActivity() != null) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_lightOn = on;
					if (on) {
						_lightBulb.setImageResource(getResources().getIdentifier("light_bulb_on", "drawable", getActivity().getPackageName()));
					} else {
						_lightBulb.setImageResource(getResources().getIdentifier("light_bulb_off", "drawable", getActivity().getPackageName()));
					}
				}
			});
		}
	}

	private void relayOff() {
		_handler.post(new SequentialRunner("relayOff") {
			@Override
			public boolean execute() {
				// switch the device off. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device off, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.relayOff(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "relay off success");
						// power was switch off successfully, update the light bulb
//						updateLightBulb(false);
						done();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "power off failed: " + error);
						done();
					}
				});
				return true;
			}
		});
	}

	private void relayOn() {
		_handler.post(new SequentialRunner("relayOn") {
			@Override
			public boolean execute() {
				// switch the device on. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device on, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.relayOn(_address, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "relay on success");
						// power was switch on successfully, update the light bulb
//						updateLightBulb(true);
						done();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "power on failed: " + error);
						done();
					}
				});
				return true;
			}
		});
	}

	private void setPwm(final int pwm) {
		_handler.post(new SequentialRunner("setPwm") {
			@Override
			public boolean execute() {
				// switch the device on. this function will check first if the device is connected
				// (and connect if it is not), then it switches the device on, and disconnects again
				// afterwards (once the disconnect timeout expires)
				_ble.writePwm(_address, pwm, new IStatusCallback() {
					@Override
					public void onSuccess() {
						Log.i(TAG, "set pwm success");
						// power was switch on successfully, update the light bulb
//						if (pwm > 0) {
//							updateLightBulb(true);
//						} else {
//							updateLightBulb(false);
//						}
						done();
					}

					@Override
					public void onError(int error) {
						Log.i(TAG, "set pwm failed: " + error);
						done();
					}
				});
				return true;
			}
		});
	}

	void onSwitchState(int switchState) {

		if (switchState > 100) {
			switchState = 100;
		}

		// add new point
//		TimeSeries series = (TimeSeries)_dataSet.getSeriesAt(_switchStateSeries);
//		series.add(new Date(), switchState);
		XYSeries series = _dataSet.getSeriesAt(_switchStateSeries);
		series.add(new Date().getTime(), switchState);

		// update x-axis range
		_maxTime = new Date().getTime() + 1 * 60 * 1000;
		_liveMinTime = _maxTime - STATISTICS_X_TIME * 60 * 1000;

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, 0, 100}, _switchStateSeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, 0, 100}, _switchStateSeries);
		}

		// redraw
		if (getActivity() != null) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_graphView.repaint();
				}
			});
		}
	}

	void onPwm(int pwm) {

		if (pwm > 100) {
			pwm = 100;
		}

		// add new point
//		TimeSeries series = (TimeSeries)_dataSet.getSeriesAt(_switchStateSeries);
//		series.add(new Date(), pwm);
		XYSeries series = _dataSet.getSeriesAt(_pwmSeries);
		series.add(new Date().getTime(), pwm);

		// update x-axis range
		_maxTime = new Date().getTime() + 1 * 60 * 1000;
		_liveMinTime = _maxTime - STATISTICS_X_TIME * 60 * 1000;

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, 0, 100}, _pwmSeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, 0, 100}, _pwmSeries);
		}

		// redraw
		if (getActivity() != null) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_graphView.repaint();
				}
			});
		}
	}

	void onRelayState(boolean relayState) {

		// add new point
//		TimeSeries series = (TimeSeries)_dataSet.getSeriesAt(_switchStateSeries);
//		series.add(new Date(), relayState);
		XYSeries series = _dataSet.getSeriesAt(_relayStateSeries);
		series.add(new Date().getTime(), relayState ? 1 : 0);

		// update x-axis range
		_maxTime = new Date().getTime() + 1 * 60 * 1000;
		_liveMinTime = _maxTime - STATISTICS_X_TIME * 60 * 1000;

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, 0, 1}, _relayStateSeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, 0, 1}, _relayStateSeries);
		}

		// redraw
		if (getActivity() != null) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_graphView.repaint();
				}
			});
		}
	}

	void onResetCounterChange() {

		// add new point
//		TimeSeries series = (TimeSeries)_dataSet.getSeriesAt(_switchStateSeries);
//		series.add(new Date(), relayState);
		XYSeries series = _dataSet.getSeriesAt(_resetCounterSeries);
		series.add(new Date().getTime(), 0);
		series.add(new Date().getTime(), 1);
		series.add(new Date().getTime(), 0);

		// update x-axis range
		_maxTime = new Date().getTime() + 1 * 60 * 1000;
		_liveMinTime = _maxTime - STATISTICS_X_TIME * 60 * 1000;

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, 0, 1}, _resetCounterSeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, 0, 1}, _resetCounterSeries);
		}

		// redraw
		if (getActivity() != null) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_graphView.repaint();
				}
			});
		}
	}

	void onTemperature(int temperature) {

		// add new point
//		TimeSeries series = (TimeSeries)_dataSet.getSeriesAt(_temperatureSeries);
//		series.add(new Date(), temperature);
		XYSeries series = _dataSet.getSeriesAt(_temperatureSeries);
		series.add(new Date().getTime(), temperature);

		// update y-axis range
		if (temperature > _maxTemp) {
			_maxTemp = (long)(temperature + (temperature - _minTemp) * 0.2);
		}
		if (temperature < _minTemp) {
			_minTemp = Math.min(0, (long)(temperature - (_maxTemp - temperature) * 0.2));
		}

		// update x-axis range
		_maxTime = new Date().getTime() + 1 * 60 * 1000;
		_liveMinTime = _maxTime - STATISTICS_X_TIME * 60 * 1000;

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, _minTemp, _maxTemp}, _temperatureSeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, _minTemp, _maxTemp}, _temperatureSeries);
		}

		// redraw
		if (getActivity() != null) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_graphView.repaint();
				}
			});
		}
	}

	void onPowerUsage(int powerUsage) {

		// add new point
		XYSeries series = _dataSet.getSeriesAt(_powerUsageSeries);
		series.add(new Date().getTime(), powerUsage);

		// update y-axis range
		if (powerUsage > _maxPowerUsage) {
			_maxPowerUsage = (long)(powerUsage + (powerUsage - _minPowerUsage) * 0.2);
		}
		if (powerUsage < _minPowerUsage) {
			_minPowerUsage = Math.min(0, (long)(powerUsage - (_maxPowerUsage - powerUsage) * 0.2));
		}

		// update x-axis range
		_maxTime = new Date().getTime() + 1 * 60 * 1000;
		_liveMinTime = _maxTime - STATISTICS_X_TIME * 60 * 1000;

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, _minPowerUsage, _maxPowerUsage}, _powerUsageSeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, _minPowerUsage, _maxPowerUsage}, _powerUsageSeries);
		}

		// redraw
		if (getActivity() != null) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_graphView.repaint();
				}
			});
		}
	}

	void onAccumulatedEnergy(int accumulatedEnergy) {

		// add new point
		XYSeries series = _dataSet.getSeriesAt(_accumulatedEnergySeries);
		series.add(new Date().getTime(), accumulatedEnergy);

		// update y-axis range
		if (accumulatedEnergy > _maxAccumulatedEnergy) {
			_maxAccumulatedEnergy = (long)(accumulatedEnergy + (accumulatedEnergy - _minAccumulatedEnergy) * 0.2);
		}
		if (accumulatedEnergy < _minAccumulatedEnergy) {
			_minAccumulatedEnergy = Math.min(0, (long)(accumulatedEnergy - (_maxAccumulatedEnergy - accumulatedEnergy) * 0.2));
		}

		// update x-axis range
		_maxTime = new Date().getTime() + 1 * 60 * 1000;
		_liveMinTime = _maxTime - STATISTICS_X_TIME * 60 * 1000;

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, _minAccumulatedEnergy, _maxAccumulatedEnergy}, _accumulatedEnergySeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, _minAccumulatedEnergy, _maxAccumulatedEnergy}, _accumulatedEnergySeries);
		}

		// redraw
		if (getActivity() != null) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_graphView.repaint();
				}
			});
		}
	}

	private void createResetCounterSeries() {

		_resetCounterSeries = _currentSeries++;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("SwitchState");
		XYSeries series = new XYSeries("Resets", _resetCounterSeries);

		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(PointStyle.POINT);
		renderer.setColor(Color.RED);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

		renderer.setLineWidth(5);

//		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.LEFT, _resetCounterSeries);
//		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.LEFT, _resetCounterSeries);
//		_multipleSeriesRenderer.setAxisTitleTextSize(30f);
		_multipleSeriesRenderer.setYLabelsColor(_resetCounterSeries, Color.TRANSPARENT);
//		_multipleSeriesRenderer.setYTitle("Relay State", 1);
	}

	private void createRelayStateSeries() {

		_relayStateSeries = _currentSeries++;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("SwitchState");
		XYSeries series = new XYSeries("RelayState", _relayStateSeries);

		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

//		renderer.setPointStyle(listOfPointStyles[_relayStateSeries]);
		renderer.setPointStyle(PointStyle.POINT);
		renderer.setColor(listOfSeriesColors[_relayStateSeries]);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

//		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.RIGHT, _relayStateSeries);
//		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.LEFT, _relayStateSeries);
//		_multipleSeriesRenderer.setAxisTitleTextSize(0);
		_multipleSeriesRenderer.setYLabelsColor(_relayStateSeries, Color.TRANSPARENT);
//		_multipleSeriesRenderer.setYTitle("Relay State", 1);
	}

	private void createPowerUsageSeries() {

		_powerUsageSeries = _currentSeries++;

		_minPowerUsage = 0;
		_maxPowerUsage = 100;

//		_minTime = Long.MAX_VALUE;
//		_maxTime = Long.MIN_VALUE;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("Temperature");
		XYSeries series = new XYSeries("PowerUsage", _powerUsageSeries);
		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(listOfPointStyles[_powerUsageSeries % listOfPointStyles.length]);
		renderer.setColor(listOfSeriesColors[_powerUsageSeries]);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.RIGHT, _powerUsageSeries);
		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.LEFT, _powerUsageSeries);
		_multipleSeriesRenderer.setAxisTitleTextSize(30f);
		_multipleSeriesRenderer.setYLabelsColor(_powerUsageSeries, listOfSeriesColors[_powerUsageSeries]);
//		_multipleSeriesRenderer.setYTitle("PowerUsage", 0);

//		_currentPointStyle++;
//		_currentSeriesColor++
	}

	private void createAccumulatedEnergySeries() {

		_accumulatedEnergySeries = _currentSeries++;

		_minAccumulatedEnergy = 0;
		_maxAccumulatedEnergy = 100;

//		_minTime = Long.MAX_VALUE;
//		_maxTime = Long.MIN_VALUE;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("Temperature");
		XYSeries series = new XYSeries("AccumulatedEnergy", _accumulatedEnergySeries);
		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(listOfPointStyles[_accumulatedEnergySeries]);
		renderer.setColor(listOfSeriesColors[_accumulatedEnergySeries]);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.RIGHT, _accumulatedEnergySeries);
		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.RIGHT, _accumulatedEnergySeries);
		_multipleSeriesRenderer.setAxisTitleTextSize(30f);
		_multipleSeriesRenderer.setYLabelsColor(_accumulatedEnergySeries, listOfSeriesColors[_accumulatedEnergySeries]);
//		_multipleSeriesRenderer.setYTitle("PowerUsage", 0);

//		_currentPointStyle++;
//		_currentSeriesColor++
	}

	private void createTemperatureSeries() {

		_temperatureSeries = _currentSeries++;

		_minTemp = 20;
		_maxTemp = 50;
//
//		_minTime = Long.MAX_VALUE;
//		_maxTime = Long.MIN_VALUE;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("Temperature");
		XYSeries series = new XYSeries("Temperature", _temperatureSeries);

		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(listOfPointStyles[_temperatureSeries]);
		renderer.setColor(listOfSeriesColors[_temperatureSeries]);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.LEFT, _temperatureSeries);
		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.RIGHT, _temperatureSeries);
		_multipleSeriesRenderer.setAxisTitleTextSize(30f);
		_multipleSeriesRenderer.setYLabelsColor(_temperatureSeries, listOfSeriesColors[_temperatureSeries]);
//		_multipleSeriesRenderer.setYTitle("Temperature [°C]", 0);
	}

	private void createSwitchStateSeries() {

		_switchStateSeries = _currentSeries++;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("SwitchState");
		XYSeries series = new XYSeries("SwitchState", _switchStateSeries);

		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(listOfPointStyles[_switchStateSeries]);
		renderer.setColor(listOfSeriesColors[_switchStateSeries]);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.LEFT, _switchStateSeries);
		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.LEFT, _switchStateSeries);
		_multipleSeriesRenderer.setAxisTitleTextSize(30f);
		_multipleSeriesRenderer.setYLabelsColor(_switchStateSeries, listOfSeriesColors[_switchStateSeries]);
//		_multipleSeriesRenderer.setYTitle("Switch State", 1);
	}

	private void createPwmSeries() {

		_pwmSeries = _currentSeries++;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("SwitchState");
		XYSeries series = new XYSeries("Pwm", _pwmSeries);

		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(listOfPointStyles[_pwmSeries]);
		renderer.setColor(listOfSeriesColors[_pwmSeries]);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.LEFT, _pwmSeries);
		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.LEFT, _pwmSeries);
		_multipleSeriesRenderer.setAxisTitleTextSize(30f);
		_multipleSeriesRenderer.setYLabelsColor(_pwmSeries, listOfSeriesColors[_pwmSeries]);
//		_multipleSeriesRenderer.setYTitle("Switch State", 1);
	}

	void createGraph() {

		// get graph renderer
		_multipleSeriesRenderer = getRenderer(6);
		_dataSet = new XYMultipleSeriesDataset();

		createTemperatureSeries();
		createResetCounterSeries();
//		createSwitchStateSeries();
		createPwmSeries();
		createRelayStateSeries();
		createPowerUsageSeries();
		createAccumulatedEnergySeries();

//		_maxTime = new Date().getTime();
//		_liveMinTime = new Date().getTime() - STATISTICS_X_TIME * 60 * 1000;

//		_multipleSeriesRenderer.setInitialRange(new double[] {_liveMinTime, _maxTime, _minTemp, _maxTemp}, _temperatureSeries);

		// create graph
		_graphView = ChartFactory.getTimeChartView(getActivity(), _dataSet, _multipleSeriesRenderer, null);
		_graphView.addZoomListener(this, false, true);
		_graphView.addPanListener(this);

		// add to screen
		_layGraph.addView(_graphView);
	}

	/**
	 * Create graph renderer
	 *
	 * @return renderer object
	 */
	public XYMultipleSeriesRenderer getRenderer(int series) {
		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer(series);

		// set minimum for y axis to 0
//		renderer.setYAxisMin(0);
//		renderer.setYAxisMax(100);

		// scrolling enabled
//		renderer.setPanEnabled(true, false);
		renderer.setPanEnabled(true, true);
		// limits for scrolling (minx, maxx, miny, maxy)
		// zoom buttons (in, out, 1:1)
		renderer.setZoomButtonsVisible(true);
		// enable zoom
//		renderer.setZoomEnabled(true, false);
		renderer.setZoomEnabled(true, true);

		// set labels text size
		renderer.setLabelsTextSize(30f);

		// hide legend
//		renderer.setShowLegend(false);
		renderer.setShowLegend(true);
		renderer.setLegendTextSize(30f);
		renderer.setLegendHeight(130);

		// set margins
//		renderer.setMargins(new int[] {20, 30, 15, 0});
		renderer.setMargins(new int[] {30, 80, 50, 70});

//		renderer.setApplyBackgroundColor(true);
//		renderer.setBackgroundColor(Color.WHITE);
//		renderer.setMarginsColor(Color.WHITE);

		renderer.setMarginsColor(Color.argb(0x00, 0x01, 0x01, 0x01));
		// todo: need to get background colour of activity, transparent is not good enough
//		renderer.setMarginsColor(((ColorDrawable) _layGraph.getBackground()).getColor());

		renderer.setXAxisMin(new Date().getTime() - STATISTICS_X_TIME * 60 * 1000);

		renderer.setZoomButtonsVisible(false);
		renderer.setExternalZoomEnabled(true);

//		XYSeriesRenderer r = new XYSeriesRenderer();

		// set color
//		r.setColor(Color.GREEN);

		// set fill below line
//		r.setFillBelowLine(true);

//		renderer.addSeriesRenderer(r);
		return renderer;
	}

	@Override
	public void zoomApplied(ZoomEvent zoomEvent) {
		_zoomApplied = true;
	}

	@Override
	public void zoomReset() {
		_zoomApplied = false;
	}

	@Override
	public void panApplied() {
		_panApplied = true;
	}
}
