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
import traceback

################################################################################
# Constant and parameters
################################################################################

duration = 1800

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
execute_cmd(["rm", "-r", "repartition"])
execute_cmd(["mkdir", "repartition"])

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

                compute_node_count = data["server_count"]
                service_node_count = data["service_node_count"]
                node_count = compute_node_count + service_node_count

                if not compute_node_count in nodes_tuples:
                    nodes_tuples += [compute_node_count]
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

def export_csv_data(algo, node_count, computes, migrations):

    folder_name = "repartition/data/%s-%d" % (algo, node_count)

    execute_cmd(["mkdir", "-p", folder_name])

    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": computes,   "labels": ["type", "value"]}, "%s/repartition_computations.csv" % (folder_name))
    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": migrations, "labels": ["type", "value"]}, "%s/repartition_migrations.csv" % (folder_name))

map_algos_size = {}


# variable that is used to detect "violation-out", "violation-normal" and "violation-sched":
# it will store the last line about "violations-out" or "violation-det", to detect if the next
# "violation" has been already processed!
last_line = None

for dirname, dirnames, filenames in os.walk('./events'):
    # print path to all subdirectories first.
    for filename in filenames:
        if filename.endswith(".json"):
            with open("%s/%s" % (dirname, filename), 'r') as f:
                header_line = f.readline()
                header_data = json.loads(header_line)
                data = header_data["data"]
                algo = data["algorithm"]

                compute_node_count = data["server_count"]
                service_node_count = data["service_node_count"]
                node_count = compute_node_count + service_node_count
                
                nodes_vms_tuple = "%s-%s" % (data["algorithm"], compute_node_count)
                
                if not map_algos_size.has_key(compute_node_count):
                    map_algos_size[compute_node_count] = []

                map_algos_size[compute_node_count] += [algo]

                computes = []
                migrations = []

                for line in f.readlines():
                    try:
                        data = json.loads(line)

                        if float(data["time"]) > duration:
                            continue

                        if data["event"] == "trace_event" and data["value"] == "migrate":
                            migration_time = data["duration"]
                            migrations += [["MIGRATION", migration_time]]

                        if data["event"] == "trace_event" and data["value"] == "compute":
                            compute_time = data["duration"]
                            compute_result = data["data"]["result"]
                            computes += [[compute_result, compute_time]]

                    except Exception as e:
                        # print traceback.format_exc()
                        pass
                
                f.seek(0)

                export_csv_data(algo, compute_node_count, computes, migrations)

################################################################################
# Clean results folder
################################################################################
execute_cmd(["rm", "-r", "repartition/results"])
execute_cmd(["mkdir", "-p", "repartition/results"])

################################################################################
# Prepare R scripts for each simulation
################################################################################

metrics = ["migrations", "computations"]
legends = {
    "migrations": "migration time (s)", 
    "computations": "computation time (s)"
}

def export_repartition_single_data(algo, node_count, metric, legend):
    print("%s with %s" % (algo, node_count))

    folder_name = "repartition/scripts/%d-%s" % (node_count, algo)

    execute_cmd(["mkdir", "-p", folder_name])

    render_template("template/repartition_script.jinja2", {"algo": algo, "node_count": node_count, "metric": metric, "legend": legend},   "%s/compare_%s.r" % (folder_name, metric))

    pass

for key in map_algos_size:
    algos = map_algos_size[key]
    node_count = key

    for algo in algos:
        for metric in metrics:
            export_repartition_single_data(algo, node_count, metric, legends[metric])

################################################################################
# Generate repartition figures
################################################################################


for key in map_algos_size:
    algos = map_algos_size[key]
    node_count = key

    for algo in algos:
        for metric in metrics:
            script_folder_name = "repartition/scripts/%d-%s" % (node_count, algo)
            out_file_path = "repartition/results/%s-%s-%d.pdf" % (metric, algo, node_count)

            execute_cmd(["/usr/bin/Rscript", "%s/compare_%s.r" % (script_folder_name, metric)])

            execute_cmd(["mv", "Rplots.pdf", out_file_path])

