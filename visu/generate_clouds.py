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
execute_cmd(["rm", "-r", "clouds"])
execute_cmd(["mkdir", "clouds"])

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

def export_csv_data(algo, node_count, violations_in, violations_out):

    folder_name = "clouds/data/%s-%d" % (algo, node_count)

    execute_cmd(["mkdir", "-p", folder_name])

    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": violations_in,  "labels": ["in_time", "in_violation_time"]},   "%s/violations_in.csv" % (folder_name))
    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": violations_out, "labels": ["out_time", "out_violation_time"]}, "%s/violations_out.csv" % (folder_name))

map_algos_size = {}

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
                
                if not map_algos_size.has_key(node_count):
                    map_algos_size[node_count] = []

                map_algos_size[node_count] += [algo]

                violations_in = []
                violations_out = []

                for line in f.readlines():
                    try:
                        data = json.loads(line)

                        if float(data["time"]) > 3601:
                            continue
                        # print(data)

                        if data["event"] == "trace_event" and data["value"] == "violation-det":
                            violations_in += [(data["time"], data["duration"])]

                        if data["event"] == "trace_event" and data["value"] == "violation-out":
                            violations_out += [(data["time"], data["duration"])]

                    except:
                        pass

                export_csv_data(algo, node_count, violations_in, violations_out)



################################################################################
# Find simulation matching and prepare R scripts
################################################################################

def export_clouds_data(algo1, algo2, node_count):
    print("%s and %s with %s" % (algo1, algo2, node_count))

    folder_name = "clouds/scripts/%d-%s-%s" % (node_count, algo1, algo2)

    execute_cmd(["mkdir", "-p", folder_name])

    render_template("template/cloud_script.jinja2", {"algo1": algo1, "algo2": algo2, "node_count": node_count},   "%s/compare.r" % (folder_name))

    pass

for key in map_algos_size:
    algos = map_algos_size[key]
    node_count = key

    if len(algos) < 1:
        continue
    elif len(algos) == 1:
        algos += [algos[0]]

    for element in itertools.combinations(algos, min(2, len(algos))):
        export_clouds_data(element[0], element[1], node_count)

################################################################################
# Clean results folder
################################################################################
execute_cmd(["rm", "-r", "clouds/results"])
execute_cmd(["mkdir", "-p", "clouds/results"])

################################################################################
# Generate clouds figures
################################################################################


for key in map_algos_size:
    algos = map_algos_size[key]
    node_count = key

    for element in itertools.combinations(algos, min(2, len(algos))):

        script_folder_name = "clouds/scripts/%d-%s-%s" % (node_count, element[0], element[1])
        out_file_path = "clouds/results/%d-%s-%s.pdf" % (node_count, element[0], element[1])

        execute_cmd(["/usr/bin/Rscript", "%s/compare.r" % (script_folder_name)])

        execute_cmd(["mv", "Rplots.pdf", out_file_path])

