# ODK-X Survey

This project is __*actively maintained*__

**ODK-X Survey** is an Android application for performing data collection in the ODK-X framework.
It operates similarly to ODK Collect, but is based on HTML, CSS, and Javascript rather than native Android, and is more flexible in its presentation and execution
It is part of the ODK-X Android tools suite.


## Table of Contents
- [Getting Started](#getting-started)
  - [Instructions on how to use Survey](#instructions-on-how-to-use-survey)
  - [Setting up your environment](#setting-up-your-environment)
  - [Building the project](#building-the-project)
  - [Running](#running)
  - [Android Version Compatibility](#android-version-compatibility)
- [Source Tree Information](#source-tree-information)
- [How to Contribute](#how-to-contribute)
- [Links for Users](#links-for-users)


## Instructions on how to use Survey.

Note: Detailed information on how to use the survey can be found [here](https://docs.odk-x.org/survey-using/).

The developer [wiki](https://github.com/odk-x/tool-suite-X/wiki) (including release notes) and
[issues tracker](https://github.com/odk-x/tool-suite-X/issues) are located under
the [**ODK-X Tool Suite**](https://github.com/odk-x) project.

Engage with the community and get technical support on [the ODK-X forum](https://forum.odk-x.org)

## Setting up your environment

General instructions for setting up an ODK-X environment can be found at our [Developer Environment Setup wiki page](https://github.com/odk-x/tool-suite-X/wiki/Developer-Environment-Setup)

Install [Android Studio](http://developer.android.com/tools/studio/index.html) and the [SDK](http://developer.android.com/sdk/index.html#Other).

This project depends on ODK-X's [androidlibrary](https://github.com/odk-x/androidlibrary) and [androidcommon](https://github.com/odk-x/androidcommon) projects; their binaries will be downloaded automatically fom our maven repository during the build phase. If you wish to modify them yourself, you must clone them into the same parent directory as survey. Your directory structure should resemble the following:

        |-- odk-x

            |-- androidcommon

            |-- androidlibrary

            |-- survey


  * Note that this only applies if you are modifying the library projects. If you use the maven dependencies (the default option), the projects will not show up in your directory.

ODK-X [Service](https://github.com/odk-x/services) __MUST__ be installed on your device, whether by installing the APK or by cloning the project and deploying it. [Tables](https://github.com/odk-x/tables) also integrates well with ODK-X Survey, but is not required.

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

## How to contribute
If you’re new to ODK-X you can check out the documentation:
- [https://docs.odk-x.org](https://docs.odk-x.org)

Once you’re up and running, you can choose an issue to start working on from here: 
- [https://github.com/odk-x/tool-suite-X/issues](https://github.com/odk-x/tool-suite-X/issues)

Issues tagged as [good first issue](https://github.com/odk-x/tool-suite-X/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) should be a good place to start.

Pull requests are welcome, though please submit them against the development branch. We prefer verbose descriptions of the change you are submitting. If you are fixing a bug, please provide steps to reproduce it or a link to an issue that provides that information. If you are submitting a new feature, please provide a description of the need or a link to a forum discussion about it.

## Links for users
This document is aimed at helping developers and technical contributors. For information on how to get started as a user of ODK-X, see our [online documentation](https://docs.odk-x.org), or to learn more about the Open Data Kit project, visit [https://odk-x.org](https://odk-x.org).
