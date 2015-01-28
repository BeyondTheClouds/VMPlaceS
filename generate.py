#!/usr/bin/python

# This script generates a specific deployment file for the injection simulator.
# It assumes that the platform will be a cluster.
# Usage: python generate.py scheduling policies nb_nodes
# Example: python generate.py centralized 100000 32 1000

import sys, random

## centralized scheduling
largv=len(sys.argv)
nb_nodes = int(sys.argv[2])

if (sys.argv[1] == 'centralized') or (sys.argv[1] == 'without'):
    sys.stderr.write("generate deployment file for entropy");
    sys.stdout.write("<?xml version='1.0'?>\n"
    "<!DOCTYPE platform SYSTEM \"http://simgrid.gforge.inria.fr/simgrid.dtd\">\n"
    "<platform version=\"3\">\n"
    "  <process host=\"node%d\" function=\"injector.Injector\"> </process>\n"
    "  <process host=\"node%d\" function=\"simulation.CentralizedResolver\"> </process>\n"
    "</platform>" % (nb_nodes +1, nb_nodes));

elif (sys.argv[1] == 'hierarchical'):
        nb_servicenodes = int(sys.argv[3])
        sys.stderr.write("generate deployment file for snooze");
        sys.stdout.write("<?xml version='1.0'?>\n"
        "<!DOCTYPE platform SYSTEM \"http://simgrid.gforge.inria.fr/simgrid.dtd\">\n"
        "<platform version=\"3\">\n"
        "  <process host=\"node%d\" function=\"injector.Injector\"> </process>\n"
        "  <process host=\"node%d\" function=\"simulation.HierarchicalResolver\"> </process>\n"
        % (nb_nodes + nb_servicenodes, nb_nodes + nb_servicenodes))

#        for i in range(0, nb_nodes):
#                line = "  <process host=\"node%d\" function=\"scheduling.snooze.LocalController\">\
#<argument value=\"node%d\" /><argument value=\"localController-%d\" />\
#</process>\n" % (i, i, i)
#                sys.stdout.write(line)

        for i in range(nb_nodes, nb_nodes+nb_servicenodes):

            line = "  <process host=\"node%d\" function=\"scheduling.snooze.GroupManager\">\
<argument value=\"node%d\" /><argument value=\"groupManager-%d\" />\
</process>\n" % (i, i, i)
            sys.stdout.write(line)

        sys.stdout.write("</platform>")

elif (sys.argv[1] == 'distributed'):
        nb_nodes = int(sys.argv[2])
        nb_cpu = int(sys.argv[3])
        total_cpu_cap = int(sys.argv[4])
        ram = int(sys.argv[5])
        port_orig = int(sys.argv[6])
        port = port_orig
        sys.stdout.write("<?xml version='1.0'?>\n"
        "<!DOCTYPE platform SYSTEM \"http://simgrid.gforge.inria.fr/simgrid.dtd\">\n"
        "<platform version=\"3\">\n"
        "  <process host=\"node%d\" function=\"injector.Injector\"> </process>\n" % (nb_nodes))

        for i in range(0, nb_nodes - 1):

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
        </process>\n" % (nb_nodes-1, nb_nodes-1, nb_cpu, total_cpu_cap, ram, port, 0, port_orig)

        sys.stdout.write(line)
        sys.stdout.write("</platform>")

else:
        print("Usage: python generate.py scheduling_policy nb_nodes or python generate.py distributed nb_nodes nb_cpu total_cpu_cap ram port > dvms_deploy.xml")
        sys.exit(1)
