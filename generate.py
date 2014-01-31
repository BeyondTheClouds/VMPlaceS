#!/usr/bin/python

# This script generates a specific deployment file for the Chord example.
# It assumes that the platform will be a cluster.
# Usage: python generate.py nb_nodes nb_bits end_date
# Example: python generate.py 100000 32 1000

import sys, random

## centralized scheduling
largv=len(sys.argv)
if largv == 2:
		nb_nodes = int(sys.argv[1])
		sys.stderr.write("generate deployment file for entropy"); 
		sys.stdout.write("<?xml version='1.0'?>\n"
		"<!DOCTYPE platform SYSTEM \"http://simgrid.gforge.inria.fr/simgrid.dtd\">\n"
		"<platform version=\"3\">\n"
		"  <process host=\"node0\" function=\"Injector.Injector\"> </process>\n"
		"  <process host=\"node0\" function=\"simulation.CentralizedResolver\"> </process>\n"
		"</platform>")

elif largv == 6:
	nb_nodes = int(sys.argv[1])
	nb_cpu = int(sys.argv[2])
	total_cpu_cap = int(sys.argv[3])
	ram = int(sys.argv[4])
	port_orig = int(sys.argv[5])
	port = port_orig
	sys.stdout.write("<?xml version='1.0'?>\n"
	"<!DOCTYPE platform SYSTEM \"http://simgrid.gforge.inria.fr/simgrid.dtd\">\n"
	"<platform version=\"3\">\n"
	"  <process host=\"node0\" function=\"Injector.Injector\"> </process>\n")

	for i in range(1, nb_nodes):

		line = "  <process host=\"node%d\" function=\"simulation.DistributedResolver\">\n \
		<argument value=\"node%d\" /><argument value=\"%d\" /><argument value=\"%d\" /><argument value=\"%d\" /><argument value=\"%d\" />\n \
		<argument value=\"node%d\" /><argument value=\"%d\" />\n \
		</process>\n" % (i, i, nb_cpu, total_cpu_cap, ram, port, i+1, port+1) 
	
		port+=1

		sys.stdout.write(line)

	# link the last agent to the first
	line = "  <process host=\"node%d\" function=\"simulation.DistributedResolver\">\n \
	<argument value=\"node%d\" /><argument value=\"%d\" /><argument value=\"%d\" /><argument value=\"%d\" /><argument value=\"%d\" />\n \
	<argument value=\"node%d\" /><argument value=\"%d\" />\n \
	</process>\n" % (nb_nodes, nb_nodes, nb_cpu, total_cpu_cap, ram, port, 1, port_orig) 
	
	sys.stdout.write(line)
	sys.stdout.write("</platform>")

else:
	print("Usage: python generate.py nb_nodes or python generate.py nb_nodes nb_cpu total_cpu_cap ram port > dvms_deploy.xml")
	sys.exit(1)
