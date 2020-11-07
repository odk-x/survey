# Survey

This project is __*actively maintained*__

It is part of the ODK-X Android tools suite.

ODK-X Survey is a data collection application based upon HTML, CSS and Javascript.

Instructions on how to use Survey can be found [here](https://docs.odk-x.org/survey-using/).

The developer [wiki](https://github.com/odk-x/tool-suite-X/wiki) (including release notes) and
[issues tracker](https://github.com/odk-x/tool-suite-X/issues) are located under
the [**ODK-X Tool Suite**](https://github.com/odk-x) project.

Engage with the community and get technical support on [the ODK-X forum](https://forum.odk-x.org)

## Setting up your environment

General instructions for setting up an ODK-X environment can be found at our [Developer Environment Setup wiki page](https://github.com/odk-x/tool-suite-X/wiki/Developer-Environment-Setup)

Install [Android Studio](http://developer.android.com/tools/studio/index.html) and the [SDK](http://developer.android.com/sdk/index.html#Other).

This project depends on ODK-X's [androidlibrary](https://github.com/odk-x/androidlibrary) and [androidcommon](https://github.com/odk-x/androidcommon) projects; their binaries will be downloaded automatically fom our maven repository during the build phase. If you wish to modify them yourself, you must clone them into the same parent directory as survey. You directory stucture should resemble the following:

        |-- odk-x

            |-- androidcommon

            |-- androidlibrary

            |-- survey


  * Note that this only applies if you are modifying the library projects. If you use the maven dependencies (the default option), the projects will not show up in your directory. 
    
ODK-X [Core](https://github.com/opendatakit/core) __MUST__ be installed on your device, whether by installing the APK or by cloning the project and deploying it. ODK-X [Tables](https://github.com/odk-x/tables) and ODK-X [Sync](https://github.com/opendatakit/sync) also integrate well with ODK-X Survey, but are not required.

Now you should be ready to build.

## Building the project

Open the Survey project in Android Studio. Select `Build->Make Project` to build the app.

## Running

Be sure to install ODK-X Core onto your device before attempting to run Survey.

## Source tree information
Quick description of the content in the root folder:

    |-- survey_app     -- Source tree for Java components

        |-- src

            |-- main

                |-- res     -- Source tree for Android resources

                |-- java

                    |-- org

                        |-- opendatakit

                            |-- survey

                                |-- android     -- The most relevant Java code lives here
                                
            |-- androidTest    -- Source tree for Android implementation tests
