# Visualisation tools

This folder contains a set of tools that helps to generate beautiful diagrams, in order to compare differents scheduling algorithms. Several metrics are reported by the SimgridInjector, scripts contained in this folder generates files from the reported metrics.

## Requirements
* Python
* easy_install
* Jinja (template engine for python)

## Installation

When ***generate_data.py*** is run, it will try to find *Jinja* by leveraging the *easy_install* library. To install easy_install, run the following:

```
$ apt-get install python-setuptools # Debian
$ brew install easy_install # MacOS
```

Then,


```
$ sudo ./generate_data.py
```

## Generate the diagrams

When a simulation is performed, an ***events.json*** file is generated, containing information about the experiments and the events that occured.

Just put this file in a folder (with the simulation name) inside the ***events*** folder, as illustrated in the following screenshot:

![image](https://raw.githubusercontent.com/BeyondTheClouds/VMPlaceS/master/docs/assets/img/screenshot_events.png)

Once it is done, just run the following command:

```
./generate_data.py ; ./generate_figures.py
```

The diagrams will auto-magically appear in the results folder!
