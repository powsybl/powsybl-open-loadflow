# PowSyBl Open Load Flow

[![Build Status](https://api.travis-ci.com/powsybl/powsybl-open-loadflow.svg?branch=master)](https://travis-ci.com/powsybl/powsybl-open-loadflow)
[![Build status](https://ci.appveyor.com/api/projects/status/8yntd7it1wwe0pik/branch/master?svg=true)](https://ci.appveyor.com/project/powsybl/powsybl-open-loadflow/branch/master)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-open-loadflow&metric=coverage)](https://sonarcloud.io/component_measures?id=com.powsybl%3Apowsybl-open-loadflow&metric=coverage)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-open-loadflow&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.powsybl%3Apowsybl-open-loadflow)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
[![Join the community on Spectrum](https://withspectrum.github.io/badge/badge.svg)](https://spectrum.chat/powsybl)

PowSyBl (**Pow**er **Sy**stem **Bl**ocks) is an open source framework written in Java, that makes it easy to write complex
software for power systems’ simulations and analysis. Its modular approach allows developers to extend or customize its
features.

PowSyBl is part of the LF Energy Foundation, a project of The Linux Foundation that supports open source innovation projects
within the energy and electricity sectors.

<p align="center">
<img src="https://raw.githubusercontent.com/powsybl/powsybl-gse/master/gse-spi/src/main/resources/images/logo_lfe_powsybl.svg?sanitize=true" alt="PowSyBl Logo" width="50%"/>
</p>

Read more at https://www.powsybl.org !

This project and everyone participating in it is governed by the [PowSyBl Code of Conduct](https://github.com/powsybl/.github/blob/master/CODE_OF_CONDUCT.md).
By participating, you are expected to uphold this code. Please report unacceptable behavior to [powsybl-tsc@lists.lfenergy.org](mailto:powsybl-tsc@lists.lfenergy.org).

## PowSyBl vs PowSyBl Open Load Flow

PowSyBl Open Load Flow is an open source implementation of the load flow API that can be found in PowSyBl Core. It supports 
AC Newtow-Raphson and linear DC calculation methods:
 - Fast and robust convergence, based on [KLU](http://faculty.cse.tamu.edu/davis/suitesparse.html) numerical solver.
 - Distributed slack (generation).
 - Generator active and reactive power limits (reactive capability curve).
 - 3 starting point modes: flat, warm and DC based.

Almost all of the code is written in Java. It only relies on native code for the [KLU](http://faculty.cse.tamu.edu/davis/suitesparse.html)
sparse linear solver. Linux, Windows and MacOS are supported.

## Getting started

Running a load flow with PowSyBl Open Load Flow is easy. First let's start loading a CGMES network. We first add a few Maven 
dependencies to respectively have access to network model, CGMES importer, PowSyBl platform configuration and simple logging 
capabilities:

```xml
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-iidm-impl</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-cgmes-conversion</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-config-classic</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-triple-store-impl-rdf4j</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>1.7.28</version>
</dependency>
```

We are now able to load the CGMES network:
 ```java
Network network = Importers.loadNetwork("<path to the zip>");
 ```

After adding a last Maven dependency on Open Load Flow implementation:
```xml
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-open-loadflow</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

We can run the load flow with default parameters on the network:
```java
LoadFlow.run(network);
```

State variables and power flows computed by the load flow are have been updated inside the network model and we can for instance 
print on standard output buses voltage magnitude and angle:

```java
network.getBusView().getBusStream().forEach(b -> System.out.println(b.getId() + " " + b.getV() + " " + b.getAngle()));
```
