package nl.dobots.crownstone.gui.monitor;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

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

import nl.dobots.bluenet.ble.base.structs.CrownstoneServiceData;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceMap;
import nl.dobots.bluenet.service.callbacks.IntervalScanListener;
import nl.dobots.crownstone.CrownstoneDevApp;
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
public class AdvertisementFragment extends Fragment implements ZoomListener, IntervalScanListener, PanListener {

	private static final String TAG = AdvertisementFragment.class.getCanonicalName();

	public static final int STATISTICS_X_TIME = 5;

	private GraphicalView _graphView;

	private boolean _lightOn;

	private TextView _txtTemperature;
	private RelativeLayout _layGraph;
	private ImageButton _btnZoomIn;
	private ImageButton _btnZoomOut;
	private ImageButton _btnZoomReset;

	private boolean _zoomApplied = false;
	private boolean _panApplied = false;

//	private Handler _handler;

	private long _maxPowerUsage;
	private long _minPowerUsage;
	private long _maxAccumulatedEnergy;
	private long _minAccumulatedEnergy;
	private int _powerUsageSeries;
	private int _accumulatedEnergySeries;
	private BleExt _ble;
	private String _address;
	private int _maxSwitchState;

	public static AdvertisementFragment newInstance(String address) {
		AdvertisementFragment f = new AdvertisementFragment();
		Bundle args = new Bundle();
		args.putString("address", address);
		f.setArguments(args);
		return f;
	}

	private long _liveMinTime;
	private long _maxTime;
	private long _minTemp;
	private long _maxTemp;
	private int _minSwitchState;
	private int _currentSeries = 0;
	private int _temperatureSeries;
	private int _currentPointStyle = 0;
	private int _currentSeriesColor = 0;
	private int _switchStateSeries;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		createGraph();

		_address = getArguments().getString("address");

		_ble = CrownstoneDevApp.getInstance().getBle();

	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.activity_advertisements, container, false);

		_layGraph = (RelativeLayout) v.findViewById(R.id.graph);

		_btnZoomIn = (ImageButton) v.findViewById(R.id.zoomIn);
		_btnZoomIn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graphView.zoomIn();
				_zoomApplied = true;
			}
		});
		_btnZoomOut = (ImageButton) v.findViewById(R.id.zoomOut);
		_btnZoomOut.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graphView.zoomOut();
				_zoomApplied = true;
			}
		});
		_btnZoomReset = (ImageButton) v.findViewById(R.id.zoomReset);
		_btnZoomReset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
//				int pixels = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, getResources().getDisplayMetrics());
//				_layStatistics.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, pixels));
//				_layControl.setVisibility(View.VISIBLE);
				_graphView.zoomReset();
				_zoomApplied = false;
				_panApplied = false;
			}
		});

		_layGraph.addView(_graphView);

//		createGraph();
//		_layStatistics.setVisibility(View.GONE);

		return v;
	}

	@Override
	public void onDestroyView() {
		_layGraph.removeView(_graphView);

		super.onDestroyView();
	}

	void onSwitchState(int switchState) {

		if (switchState > 100) {
			switchState = 100;
		}

		_minSwitchState = 0;
		_maxSwitchState = 100;

		// add new point
//		TimeSeries series = (TimeSeries)_dataSet.getSeriesAt(_switchStateSeries);
//		series.add(new Date(), switchState);
		XYSeries series = _dataSet.getSeriesAt(_switchStateSeries);
		series.add(new Date().getTime(), switchState);
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
	}

	private XYMultipleSeriesRenderer _multipleSeriesRenderer;
	private XYMultipleSeriesDataset _dataSet;

	private PointStyle[] listOfPointStyles = new PointStyle[] { PointStyle.CIRCLE, PointStyle.DIAMOND,
			PointStyle.POINT, PointStyle.SQUARE, PointStyle.TRIANGLE, PointStyle.X };

	private int[] listOfSeriesColors = new int[] { 0xFF00BFFF, Color.GREEN, Color.RED, Color.YELLOW,
			Color.MAGENTA, Color.CYAN, Color.WHITE };

	private void updateRange() {

		// update x-axis range
		_maxTime = new Date().getTime() + 1 * 60 * 1000;
		_liveMinTime = _maxTime - STATISTICS_X_TIME * 60 * 1000;

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, _minSwitchState, _maxSwitchState}, _switchStateSeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, _minSwitchState, _maxSwitchState}, _switchStateSeries);
		}

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, _minTemp, _maxTemp}, _temperatureSeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, _minTemp, _maxTemp}, _temperatureSeries);
		}

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, _minPowerUsage, _maxPowerUsage}, _powerUsageSeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, _minPowerUsage, _maxPowerUsage}, _powerUsageSeries);
		}

		// update range
		if (!(_zoomApplied || _panApplied)) {
			_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, _minAccumulatedEnergy, _maxAccumulatedEnergy}, _accumulatedEnergySeries);
			_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, _minAccumulatedEnergy, _maxAccumulatedEnergy}, _accumulatedEnergySeries);
		}

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

		renderer.setPointStyle(listOfPointStyles[_powerUsageSeries]);
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

	void createGraph() {

		// get graph renderer
		_multipleSeriesRenderer = getRenderer(4);
		_dataSet = new XYMultipleSeriesDataset();

		createTemperatureSeries();
		createSwitchStateSeries();
		createPowerUsageSeries();
		createAccumulatedEnergySeries();

		_maxTime = new Date().getTime();
		_liveMinTime = new Date().getTime() - STATISTICS_X_TIME * 60 * 1000;

		_multipleSeriesRenderer.setInitialRange(new double[] {_liveMinTime, _maxTime, _minTemp, _maxTemp}, _temperatureSeries);

		// create graph
		_graphView = ChartFactory.getTimeChartView(getActivity(), _dataSet, _multipleSeriesRenderer, null);
		_graphView.addZoomListener(this, false, true);
		_graphView.addPanListener(this);

		// add to screen
//		_layGraph.addView(_graphView);
	}

	/**
	 * Create graph renderer
	 *
	 * @return renderer object
	 */
	public XYMultipleSeriesRenderer getRenderer(int series) {
		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer(series);

		// set minimum for y axis to 0
		renderer.setYAxisMin(0);
		renderer.setYAxisMax(100);

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
	public void onScanStart() {
		MonitoringActivity.getInstance().getService().clearDeviceMap();
	}

	@Override
	public void onScanEnd() {
		if (getActivity() == null) return;

		BleDeviceMap deviceMap = MonitoringActivity.getInstance().getService().getDeviceMap();
		BleDevice device = deviceMap.getDevice(_address);
		if (device != null) {
			CrownstoneServiceData serviceData = device.getServiceData();
			if (serviceData != null) {
				onSwitchState(serviceData.getSwitchState());
				onTemperature(serviceData.getTemperature());
				onPowerUsage(serviceData.getPowerUsage());
				onAccumulatedEnergy(serviceData.getAccumulatedEnergy());
			}
		}
		updateRange();

		// redraw
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				_graphView.repaint();
			}
		});
	}

	@Override
	public void panApplied() {
		_panApplied = true;
	}
}
