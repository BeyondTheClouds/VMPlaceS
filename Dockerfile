FROM ubuntu:20.04
MAINTAINER Badock

ARG DEBIAN_FRONTEND=noninteractive

# Enable the APT via HTTP
RUN apt update
RUN apt install -y apt-transport-https

# Download dependencies
# RUN echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
# RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
RUN apt update
RUN apt install -y simgrid wget

# Install openjdk
RUN apt install -y openjdk-8-jdk

# # Install scala
# RUN apt install -y scala

# Install sbt
#RUN curl -L -o sbt.deb http://dl.bintray.com/sbt/debian/sbt-0.13.15.deb
RUN wget http://dl.bintray.com/sbt/debian/sbt-0.13.15.deb --no-check-certificate -O sbt.deb
RUN dpkg -i sbt.deb
RUN apt update
RUN apt install -y sbt

# Clone projects
#   VMPlaceS
RUN apt install -y git
RUN git clone -b master https://github.com/BeyondTheClouds/VMPlaceS.git

# Change the working directory
WORKDIR /VMPlaceS

# Download the jar provided by the Simgrid project
RUN wget http://gforge.inria.fr/frs/download.php/file/37149/simgrid-3_17.jar --no-check-certificate -O lib/simgrid.jar

# Compile the project and create a "fatjar"
RUN sbt clean
RUN sbt update
RUN sbt assembly

# Run the example
RUN java -Xmx4G -d64 -cp target/simulation.jar simulation.SimpleMain --algo=example --duration 1800 --nb_hosts=10 --nb_vms=93 --load_mean=60.0
