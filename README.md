# VMPlaceS

This repository contains the source of a dedicated framework to evaluate and compare VM placement algorithms.

For more details, a deeper scientific description of the project can be found here: [http://beyondtheclouds.github.io/VMPlaceS](http://beyondtheclouds.github.io/VMPlaceS)

## Requirements
* sbt
* java (openjdk-8)
* python
* r language (optional: visualisation)

## Installation

### - To Do
Clone the VMPlaceS project 

```
git clone https://github.com/BeyondTheClouds/VMPlaceS.git
```
Switch to the right branch (group C)

```
git checkout groupeC
```
### 1- Simgrid

#### 1.1- Get Simgrid

```
git clone git://scm.gforge.inria.fr/simgrid/simgrid.git
```

#### 1.2- Compile and install Simgrid
In the simgrid folder, run the following:

```
cmake -Denable_tracing=ON -Denable_documentation=OFF -Denable_java=ON -Denable_smpi=OFF .
```
and then

```
make 
```
please note that you can compile the src code faster by using -j argument of make command (man make for further information)

```
make install
```
file named **simgrid.jar**, containing java bindings to simgrid should be located in the simgrid folder:


```
jonathan@artoo ~/simgrid (master)> ls -lh *.jar
-rw-r--r--  1 jonathan  staff    43K Nov  4 17:28 simgrid.jar
```

This file will be used during step **2.2**.

### 2- SBT

#### 2.1- Installation of sbt:

Please follow the instructions corresponding to your operating system: [http://www.scala-sbt.org/release/tutorial/Setup.html](http://www.scala-sbt.org/release/tutorial/Setup.html) .

#### 2.2- Installation of dependencies

Inside the project source folder, run the following:

```
$ sbt clean
```

```
$ sbt update
```

and then copy the **simgrid.jar** from **step 1.2** in the lib folder



### Configuring the simulation environement

We developed some scripts that ease the conduction of experiments on grid'5000.

```
simulation.Main
```

run this class with the following arguments:

Option Type        | Value
-------------------|-------------
VM_OPTIONS         | -Xmx4G -d64 -Dlogback.configurationFile=config/logback.xml
PROGRAM_ARGUMENTS  | ./config/cluster_platform.xml ./config/generated_deploy.xml  --cfg=cpu/optim:Full --cfg=tracing:1  --cfg=tracing/filename:simu.trace --cfg=tracing/platform:1
LOGS (if exists)   | ./logs/console.log

### A- Running with the development environment (IntelliJ)

Inside the project source folder, run the following:

```
$ sbt gen-idea
```

Open, then chose the project VMPlaceS

Ignore the warning "Maven Project need to be imported"

Do not convert to a SBT project

Do not import the SBT project
and open the folder in intelliJ: a fully configured project has been generated.

### B- Running with command line

Inside the project source folder, run the following command:

```
sbt assembly
```

it results in the creation of a **fat-jar** named **simulation.jar** in the target folder:

```
jonathan@artoo ~/D/w/VMPlaceS (master)> ls -lh target/*.jar
-rw-r--r--  1 jonathan  staff    12M Dec 18 14:21 target/simulation.jar
```

This jar contains all dependencies and can be run with the java command. Please not that the jar must be located in a folder that contains the **config** folder.

Thus it is possible to run the jar with the following command:

```
java -jar $VM_OPTIONS simulation.Main $PROGRAM_ARGUMENTS
```

## Run experiments on grid'5000

We developed some scripts to ease the conduction of experiments on grid'5000. These scripts are located on the Rennes site, in the folder /home/jpastor.

Further documentation will arrive later: in case you plan to use it now, do not hesitate to contact us!
