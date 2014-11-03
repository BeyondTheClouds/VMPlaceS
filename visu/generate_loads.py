#!/usr/bin/python
from __future__ import division
from pkg_resources import WorkingSet , DistributionNotFound
working_set = WorkingSet()

import itertools

# Printing all installed modules
#print tuple(working_set)

# Detecting if module is installed
dependency_found = True
try:
    dep = working_set.require('Jinja2')
except DistributionNotFound:
    dependency_found  = False
    pass

if not dependency_found:
    try:
        # Installing it (anyone knows a better way?)
        from setuptools.command.easy_install import main as install
        install(['Jinja2'])
        print("run again as normal user to process results")
    except DistributionNotFound:
        print("run this script as sudo to install a missing template engine")
        pass
    sys.exit(0)


import csv
import subprocess
import time
import os
import json
import jinja2

################################################################################
# Functions of the script
################################################################################
def execute_cmd(args):
	print "%s" % args
	# return "%s" % args
	out, err = subprocess.Popen(args,
                   shell=False,
                   stdout=subprocess.PIPE,
                   stderr=subprocess.PIPE).communicate()
	if not err == "":
		print err
	return out

def render_template(template_file_path, vars, output_file_path):
    templateLoader = jinja2.FileSystemLoader( searchpath="." )
    templateEnv = jinja2.Environment( loader=templateLoader )

    TEMPLATE_FILE = template_file_path
    template = templateEnv.get_template( TEMPLATE_FILE )

    templateVars = vars

    outputText = template.render( templateVars )
    with open(output_file_path, "w") as text_file:
        text_file.write(outputText)
        

################################################################################
# Clean data and scripts folders
################################################################################
execute_cmd(["rm", "-r", "loads"])
execute_cmd(["mkdir", "loads"])

################################################################################
# Detect algorithms used in experiments
################################################################################
algos = []
for dirname, dirnames, filenames in os.walk('./events'):
    # print path to all subdirectories first.
    for filename in filenames:
        if filename.endswith(".json"):
            with open("%s/%s" % (dirname, filename), 'r') as f:
                header_line = f.readline()
                header_data = json.loads(header_line)
                data = header_data["data"]
                algo = data["algorithm"]
                if not algo in algos:
                    algos += [algo]
print algos

################################################################################
# Detect (server_count, vm_count) combination used in experiments
################################################################################
nodes_tuples     = []
vms_tuples       = []
nodes_vms_tuples = []
for dirname, dirnames, filenames in os.walk('./events'):
    # print path to all subdirectories first.
    for filename in filenames:
        if filename.endswith(".json"):
            with open("%s/%s" % (dirname, filename), 'r') as f:
                header_line = f.readline()
                header_data = json.loads(header_line)
                data = header_data["data"]

                node_count = data["server_count"] + data["service_node_count"]

                if not node_count in nodes_tuples:
                    nodes_tuples += [node_count]
                if not data["vm_count"] in vms_tuples:
                    vms_tuples += [data["vm_count"]]
                # nodes_vms_tuple = "%s-%s" % (data["server_count"], data["vm_count"])
                # if not nodes_vms_tuple in nodes_vms_tuples:
                #     nodes_vms_tuples += [nodes_vms_tuple]

# Order the tuples
nodes_tuples = sorted(nodes_tuples)
vms_tuples = sorted(vms_tuples)
nodes_vms_tuples = [str(tuple2[0])+"-"+str(tuple2[1]) for tuple2 in zip(nodes_tuples, vms_tuples)]
# nodes_vms_tuples = sorted(nodes_vms_tuples)

print nodes_tuples
print vms_tuples
print nodes_vms_tuples

################################################################################
# Fill data maps with computed metrics
################################################################################

def export_csv_data(algo, node_count, loads):

    folder_name = "loads/data/%s-%d" % (algo, node_count)

    execute_cmd(["mkdir", "-p", folder_name])

    render_template("template/load_data.jinja2", {"algo": algo, "loads": loads, "labels": ["time", "load"]},   "%s/load.csv" % (folder_name))

simulations = []

for dirname, dirnames, filenames in os.walk('./events'):
    # print path to all subdirectories first.
    for filename in filenames:
        if filename.endswith(".json"):
            with open("%s/%s" % (dirname, filename), 'r') as f:
                header_line = f.readline()
                header_data = json.loads(header_line)
                data = header_data["data"]
                algo = data["algorithm"]
                node_count = data["server_count"] + data["service_node_count"]
                nodes_vms_tuple = "%s-%s" % (data["algorithm"], node_count)
                
                service_node_name = "node%d" % (node_count)

                simulations += [(algo, node_count)]

                loads = []

                for line in f.readlines():
                    try:
                        data = json.loads(line)

                        if float(data["time"]) > 3601:
                            continue
                        # print(data)

                        if data["event"] == "trace_event" and data["state_name"] == "VARIABLE" and data["value"] == "LOAD" and data["origin"] == service_node_name:
                            loads += [(data["time"], data["data"]["value"])]

                    except:
                        pass

                export_csv_data(algo, node_count, loads)



################################################################################
# Find simulation matching and prepare R scripts
################################################################################

def export_loads_data(algo, node_count):

    folder_name = "loads/scripts/%d-%s" % (node_count, algo)

    execute_cmd(["mkdir", "-p", folder_name])

    render_template("template/load_script.jinja2", {"algo": algo, "node_count": node_count},   "%s/compare.r" % (folder_name))

    pass

for simulation in simulations:
    algo = simulation[0]
    node_count = simulation[1]

    export_loads_data(algo, node_count)

################################################################################
# Clean results folder
################################################################################
execute_cmd(["rm", "-r", "loads/results"])
execute_cmd(["mkdir", "-p", "loads/results"])

################################################################################
# Generate loads figures
################################################################################

for simulation in simulations:
    algo = simulation[0]
    node_count = simulation[1]

    export_loads_data(algo, node_count)

    script_folder_name = "loads/scripts/%d-%s" % (node_count, algo)
    out_file_path = "loads/results/%d-%s-.pdf" % (node_count, algo)

    execute_cmd(["/usr/bin/Rscript", "%s/compare.r" % (script_folder_name)])

    execute_cmd(["mv", "Rplots.pdf", out_file_path])

