#Android Bluenet Example

This is a simple project for android studio to showcase the use of the bluenet library for android. The bluenet library for android can be found [here](https://github.com/dobots/bluenet-lib-android).

The project shows the following steps:

1. Initialization of the library
2. Searching for Devices
3. Connecting to a device
4. Reading a characteristic (read the PWM characteristic to check the current state)
5. Writing to a characteristic (switch Power)

The example shows to ways of how to scan for devices using the library:

1. Using the library directly to scan for devices
2. Using the BleScanService to scan for devices

##Installation

To install the project follow these steps:

1. Clone this project to your disk

        git clone https://github.com/dobots/android-bluenet-example

2. Clone the library into the project location

        cd android-bluenet-example
        git clone https://github.com/dobots/bluenet-lib-android.git bluenet

    Make sure the folder of the library will be called bluenet

3. Import the project in Android Studio

        File > New > Import Project ...
Choose the android-bluenet-example dir

4. The project shows by default the example where we scan through the library directly. If you want to see the example with the BleScanService instead, go to the AndroidManifest.xml and

    1. Comment the activity MainActivity
    
    2. Uncomment the activity MainActivityService and the service BleScanService

5. Build and run

##Copyrights

The copyrights (2015) for this code belongs to [DoBots](http://dobots.nl) and are provided under an noncontagious open-source license:

* Author: Dominik Egger
* Date: 02.10.2015
* License: LGPL v3
* Distributed Organisms B.V. (DoBots), http://www.dobots.nl
* Rotterdam, The Netherlands
