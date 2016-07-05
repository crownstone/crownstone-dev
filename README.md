#Crownstone DEV

This app is designed to be used for developement of crownstone. 

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
* Date: 05.07.2016
* License: LGPL v3
* Distributed Organisms B.V. (DoBots), http://www.dobots.nl
* Rotterdam, The Netherlands
