# How to change the placement algorithm to test

_This feature is disponible for centralized algorithms only._

Two centralized algorithms are already implemented:

- BtrPlace
- Entropy

You can find them in the `scheduling.centralized` java package. 

If you want to test another one, start at step 1. Otherwise, go directly to step 2.

## 1- Implement the algorithm

### 1.1- Create the architecture

Create a package named after the algorithm you are to implement in the `centralized` package.

### 1.2- Write the code

Create a class named after the algorithm you are to implement in the package newly created. It must extends the `AbstractScheduler`, which implements the `Scheduler` interface.

## 2- Choose the algorithm to run

Open the `config/simulator.properties` file and find the `simulator.implementation` property. Write the fully qualified name of the class implementing the algorithm you want to test.