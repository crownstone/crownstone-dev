package nl.dobots.crownstone.gui.control;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.base.callbacks.IPowerSamplesCallback;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.PowerSamples;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.crownstone.CrownstoneDevApp;
import nl.dobots.crownstone.R;

/**
 * This fragment is part of the ControlActivity to request the power samples from device and
 * display in graph
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class ControlMeasurementsFragment extends Fragment {

	private static final String TAG = ControlMeasurementsFragment.class.getCanonicalName();

	// the mac address of the stone
	private String _address;
	private BleExt _ble;

	// the view to display the graph
	private GraphicalView _graphView;
	// the layout to hold the graph + controls
	private RelativeLayout _layContainer;
	// the layout to hold the graph view
	private RelativeLayout _layGraph;
	// the layout to hold the controls
	private RelativeLayout _layControl;
	// the buttons to control zoom level
	private ImageButton _btnZoomIn;
	private ImageButton _btnZoomOut;
	private ImageButton _btnZoomReset;
	// the sample and subscribe power buttons
	private Button _btnSamplePower;
	private Button _btnSubscribePower;

	// the index of the next series to be added
	private int _nextSeries = 0;
	// index of the current series
	private int _currentSampleSeries;
	// index of the voltage series
	private int _voltageSampleSeries;

	// the four following values are used to scale the graph at runtime based on the received values
	// minimum current value
	private int _minCurrentSample;
	// maximum current value
	private int _maxCurrentSample;
	// minimum voltage value
	private int _minVoltageSample;
	// maximum voltage value
	private int _maxVoltageSample;

	// is subscribed to power samples
	private boolean _subscribedPowerSamples;

	// graph objects
	private XYMultipleSeriesRenderer _multipleSeriesRenderer;
	private XYMultipleSeriesDataset _dataSet;

	private PointStyle[] listOfPointStyles = new PointStyle[] { PointStyle.CIRCLE, PointStyle.DIAMOND,
			PointStyle.POINT, PointStyle.SQUARE, PointStyle.TRIANGLE, PointStyle.X };

	private int[] listOfSeriesColors = new int[] { 0xFF00BFFF, Color.GREEN, Color.RED, Color.YELLOW,
			Color.MAGENTA, Color.CYAN, Color.WHITE };


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		_ble = CrownstoneDevApp.getInstance().getBle();
		// get the address of the stone
		_address = ControlActivity.getInstance().getAddress();

	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.frag_control_measurements, container, false);

		_layControl = (RelativeLayout) v.findViewById(R.id.layControl);
		_layContainer = (RelativeLayout) v.findViewById(R.id.layContainer);
		_layGraph = (RelativeLayout) v.findViewById(R.id.graph);

		_btnZoomIn = (ImageButton) v.findViewById(R.id.zoomIn);
		_btnZoomIn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_layContainer.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
				_layControl.setVisibility(View.INVISIBLE);
//				_graphView.zoomIn();
//				_zoomLevel++;
			}
		});
		_btnZoomOut = (ImageButton) v.findViewById(R.id.zoomOut);
		_btnZoomOut.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int pixels = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, getResources().getDisplayMetrics());
				_layContainer.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, pixels));
				_layControl.setVisibility(View.VISIBLE);
//				_graphView.zoomOut();
//				_zoomLevel--;
			}
		});
		_btnZoomReset = (ImageButton) v.findViewById(R.id.zoomReset);
		_btnZoomReset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graphView.zoomReset();

				updateVoltageSamplesRange();
				updateCurrentSamplesRange();
			}
		});

		_btnSamplePower = (Button) v.findViewById(R.id.btnSamplePower);
		_btnSamplePower.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				samplePower();
			}
		});

		_btnSubscribePower = (Button) v.findViewById(R.id.btnSubscribePower);
		_btnSubscribePower.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (_subscribedPowerSamples) {
					_btnSubscribePower.setText("Subscribe");
					unsubscribePowerSamples();
				} else {
					_btnSubscribePower.setText("Unsubscribe");
					subscribePowerSamples();
				}
			}
		});

		createGraph();

		return v;
	}

	/**
	 * Request the power samples (current + voltage) from the stone and display them in the graph
	 */
	void samplePower() {

		final ProgressDialog dlg = ProgressDialog.show(getActivity(), "Retrieving samples", "Please wait...", true, true);
		dlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				getActivity().finish();
			}
		});

		_ble.readPowerSamples(_address, new IPowerSamplesCallback() {
			@Override
			public void onData(PowerSamples powerSamples) {
				dlg.dismiss();
				onPowerSamples(powerSamples);
			}

			@Override
			public void onError(int error) {
				dlg.dismiss();
				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getActivity(), "Failed to get samples", Toast.LENGTH_LONG).show();
					}
				});
			}
		});
	}

	/**
	 * Connect to the stone and subscribe for power samples. every time a new list of samples is
	 * received, the graph is updated
	 */
	private void subscribePowerSamples() {

		_ble.connectAndDiscover(_address, new IDiscoveryCallback() {
			@Override
			public void onDiscovery(String serviceUuid, String characteristicUuid) {

			}

			@Override
			public void onSuccess() {
				_ble.subscribePowerSamples(
						new IPowerSamplesCallback() {
							@Override
							public void onData(PowerSamples powerSamples) {
								_subscribedPowerSamples = true;
								onPowerSamples(powerSamples);
							}

							@Override
							public void onError(int error) {
								getActivity().runOnUiThread(new Runnable() {
									@Override
									public void run() {
										Toast.makeText(getActivity(), "Failed to get samples", Toast.LENGTH_LONG).show();
									}
								});
							}
						});
			}

			@Override
			public void onError(int error) {

			}
		});
	}

	/**
	 * unsubscribe from the power samples and disconnect
	 */
	private void unsubscribePowerSamples() {
		_ble.unsubscribePowerSamples(new IStatusCallback() {
			@Override
			public void onSuccess() {
				_subscribedPowerSamples = false;
				_ble.disconnect(new IStatusCallback() {
					@Override
					public void onSuccess() {

					}

					@Override
					public void onError(int error) {

					}
				});
			}

			@Override
			public void onError(int error) {

			}
		});
	}

	/**
	 * Display the received power samples
	 * @param powerSamples list of (current + voltage) samples
	 */
	private void onPowerSamples(PowerSamples powerSamples) {
		onCurrentSamples(powerSamples.getCurrentSamples(), powerSamples.getCurrentTimestamps());
		onVoltageSamples(powerSamples.getVoltageSamples(), powerSamples.getVoltageTimestamps());
	}

	/**
	 * Update the Y axis for the current based on the max and min received current values
	 */
	private void updateCurrentSamplesRange() {
		_multipleSeriesRenderer.setYAxisMin(_minCurrentSample - Math.max((double)(_maxCurrentSample - _minCurrentSample) / 10, 10), _currentSampleSeries);
		_multipleSeriesRenderer.setYAxisMax(_maxCurrentSample + Math.max((double)(_maxCurrentSample - _minCurrentSample) / 10, 10), _currentSampleSeries);
	}

	/**
	 * Update the Y axis for the voltage based on the max and min received current voltage
	 */
	private void updateVoltageSamplesRange() {
		_multipleSeriesRenderer.setYAxisMin(_minVoltageSample - Math.max((double)(_maxVoltageSample - _minVoltageSample) / 10, 10), _voltageSampleSeries);
		_multipleSeriesRenderer.setYAxisMax(_maxVoltageSample + Math.max((double)(_maxVoltageSample - _minVoltageSample) / 10, 10), _voltageSampleSeries);
	}

	/**
	 * add the current samples to the graph and rescale
	 * @param currentSamples list of current samples
	 * @param currentTimestamps list of timestamps
	 */
	private void onCurrentSamples(PowerSamples.Samples currentSamples, PowerSamples.Timestamps currentTimestamps) {

		XYSeries series = _dataSet.getSeriesAt(_currentSampleSeries);
		series.clear();

		_minCurrentSample = Integer.MAX_VALUE;
		_maxCurrentSample = Integer.MIN_VALUE;

		// go through the list and add point by point. keep track of min/max values for adjusting
		// the y axis (rescale)
		for (int i = 0; i < currentSamples.getCount(); ++i) {
			int sample = currentSamples.getSample(i);
			series.add(currentTimestamps.getTimestamp(i) - currentTimestamps.getFirst(), sample);

			if (sample < _minCurrentSample) {
				_minCurrentSample = sample;
			}
			if (sample > _maxCurrentSample) {
				_maxCurrentSample = sample;
			}
		}

		updateCurrentSamplesRange();

		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				_graphView.repaint();
			}
		});

	}

	/**
	 * add the voltage samples to the graph and rescale
	 * @param voltageSamples list of voltage samples
	 * @param voltageTimestamps list of timestamps
	 */
	private void onVoltageSamples(PowerSamples.Samples voltageSamples, PowerSamples.Timestamps voltageTimestamps) {

		XYSeries series = _dataSet.getSeriesAt(_voltageSampleSeries);
		series.clear();

		_minVoltageSample = Integer.MAX_VALUE;
		_maxVoltageSample = Integer.MIN_VALUE;

		// go through the list and add point by point. keep track of min/max values for adjusting
		// the y axis (rescale)
		for (int i = 0; i < voltageSamples.getCount(); ++i) {
			int sample = voltageSamples.getSample(i);

			series.add(voltageTimestamps.getTimestamp(i) - voltageTimestamps.getFirst(), sample);

			if (sample < _minVoltageSample) {
				_minVoltageSample = sample;
			}
			if (sample > _maxVoltageSample) {
				_maxVoltageSample = sample;
			}
		}

		updateVoltageSamplesRange();

		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				_graphView.repaint();
			}
		});

	}

	/**
	 * Create the series (current + voltage)
	 */
	private void createPowerSamplesSeries() {
		createCurrentSamplesSeries();
		createVoltageSamplesSeries();
	}

	/**
	 * Create the current series
	 */
	private void createCurrentSamplesSeries() {

		_currentSampleSeries = _nextSeries++;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("SwitchState");
		XYSeries series = new XYSeries("Current", _currentSampleSeries);

		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(listOfPointStyles[_currentSampleSeries]);
		renderer.setColor(listOfSeriesColors[_currentSampleSeries]);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.LEFT, _currentSampleSeries);
		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.RIGHT, _currentSampleSeries);
		_multipleSeriesRenderer.setAxisTitleTextSize(30f);
		_multipleSeriesRenderer.setYLabelsColor(_currentSampleSeries, listOfSeriesColors[_currentSampleSeries]);
		_multipleSeriesRenderer.setYTitle("Current", _currentSampleSeries);
		_multipleSeriesRenderer.setYAxisMin(0, _currentSampleSeries);
		_multipleSeriesRenderer.setYAxisMax(300, _currentSampleSeries);
	}

	/**
	 * Create the voltage series
	 */
	private void createVoltageSamplesSeries() {

		_voltageSampleSeries = _nextSeries++;

		// create time series (series with x = timestamp, y = temperature)
//		TimeSeries series = new TimeSeries("SwitchState");
		XYSeries series = new XYSeries("Voltage", _voltageSampleSeries);

		_dataSet.addSeries(series);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(listOfPointStyles[_voltageSampleSeries]);
		renderer.setColor(listOfSeriesColors[_voltageSampleSeries]);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(true);
//		renderer.setDisplayChartValuesDistance(50);
		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(true);

		_multipleSeriesRenderer.setYAxisAlign(Paint.Align.RIGHT, _voltageSampleSeries);
		_multipleSeriesRenderer.setYLabelsAlign(Paint.Align.LEFT, _voltageSampleSeries);
		_multipleSeriesRenderer.setAxisTitleTextSize(30f);
		_multipleSeriesRenderer.setYLabelsColor(_voltageSampleSeries, listOfSeriesColors[_voltageSampleSeries]);
		_multipleSeriesRenderer.setYTitle("Voltage", _voltageSampleSeries);
		_multipleSeriesRenderer.setYAxisMin(0, _voltageSampleSeries);
		_multipleSeriesRenderer.setYAxisMax(300, _voltageSampleSeries);

	}

	/**
	 * Create the graph
	 */
	void createGraph() {

		// get graph renderer
		_multipleSeriesRenderer = getRenderer(2);
		_dataSet = new XYMultipleSeriesDataset();

		createPowerSamplesSeries();

//		_maxTime = new Date().getTime();
//		_liveMinTime = new Date().getTime() - STATISTICS_X_TIME * 60 * 1000;

//		_multipleSeriesRenderer.setInitialRange(new double[] {_liveMinTime, _maxTime, _minTemp, _maxTemp}, _temperatureSeries);

		// create graph
		_graphView = ChartFactory.getLineChartView(getActivity(), _dataSet, _multipleSeriesRenderer);
//		_graphView.addZoomListener(this, false, true);

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

//		renderer.setXAxisMin(new Date().getTime() - STATISTICS_X_TIME * 60 * 1000);

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

}
