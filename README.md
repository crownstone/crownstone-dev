#Crownstone DEV

This app is designed to be used for developement of crownstone. 

##Installation

To install the project follow these steps:

1. Clone this project to your disk

        git clone https://github.com/crownstone/crownstone-dev

2. Clone the following two libraries into the project location

        cd crownstone-dev
        git clone https://github.com/crownstone/bluenet-lib-android.git bluenet
        git clone https://github.com/crownstone/crownstone-cloud-lib-android.git crownstone-loopback-sdk

   Make sure the folder of the library will be called bluenet and crownstone-loopback-sdk
   
3. Go to the crownstone-loopback-sdk and clone the loopback-sdk

        cd crownstone-loopback-sdk
        git clone https://github.com/eggerdo/loopback-sdk-android.git loopback-sdk-android
        
   Make sure the folder is called loopback-sdk-android

3. Import the project in Android Studio

        File > New > Import Project ...
Choose the crownstone-dev dir

4. Build and run

##Copyrights

The copyrights (2015) for this code belongs to [DoBots](http://dobots.nl) and are provided under an noncontagious open-source license:

* Author: Dominik Egger
* Date: 05.07.2016
* License: LGPL v3
* Distributed Organisms B.V. (DoBots), http://www.dobots.nl
* Rotterdam, The Netherlands
