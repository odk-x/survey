# survey

This project is __*actively maintained*__

It is part of the ODK 2.0 Android tools suite.

ODK Survey is a data collection application based upon HTML, CSS and Javascript.

Instructions on how to use Survey can be found [here](https://opendatakit.org/use/2_0_tools/odk-survey-2-0-rev126/).

The developer [wiki](https://github.com/opendatakit/opendatakit/wiki) (including release notes) and
[issues tracker](https://github.com/opendatakit/opendatakit/issues) are located under
the [**opendatakit**](https://github.com/opendatakit/opendatakit) project.

The Google group for software engineering questions is: [opendatakit-developers@](https://groups.google.com/forum/#!forum/opendatakit-developers)

## Setting up your environment

Install [Android Studio](http://developer.android.com/tools/studio/index.html) and the [SDK](http://developer.android.com/sdk/index.html#Other).

This project depends on ODK's [androidlibrary](https://github.com/opendatakit/androidlibrary) and [androidcommon](https://github.com/opendatakit/androidcommon) projects, so be sure to clone those into the same parent directory as Survey. ODK [Core](https://github.com/opendatakit/core) must also be installed on your device, whether by installing the APK or by cloning the project and deploying it. ODK [Tables](https://github.com/opendatakit/tables) and ODK [Sync](https://github.com/opendatakit/sync) also integrate well with ODK Survey, but are not required.

Now you should be ready to build.

## Building the project

Open the Survey project in Android Studio. As long as androidlibrary and androidcommon are in the same parent directory, you should be able to select `Build->Make` Project to build the app.

## Running

Be sure to install ODK Core onto your device before attempting to run Survey.

## Source tree information
Quick description of the content in the root folder:

    |-- survey\_app     -- Source tree for Java components

        |-- src

            |-- main

                |-- res     -- Source tree for Android resources

                |-- java

                    |-- org

                        |-- opendatakit

                            |-- survey

                                |-- android     -- The most relevant Java code lives here
                                
            |-- androidTest    -- Source tree for Android implementation tests
