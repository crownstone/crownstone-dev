package nl.dobots.crownstone.gui.utils;

import nl.dobots.bluenet.service.BleScanService;

/**
 * Register and wait for the application to bind to the service. will be informed
 * with onBind once the service is bound, and provides the service object as parameter
 *
 * Created on 26-7-16
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public interface ServiceBindListener {

	void onBind(BleScanService service);

}
