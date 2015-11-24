#!/usr/bin/python
from __future__ import division
from pkg_resources import WorkingSet , DistributionNotFound
working_set = WorkingSet()

from numpy import array
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

duration = 3600

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
execute_cmd(["rm", "-r", "detailed"])
execute_cmd(["mkdir", "detailed"])

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
# print algos

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

# print nodes_tuples
# print vms_tuples
# print nodes_vms_tuples

################################################################################
# Fill data maps with computed metrics
################################################################################

def export_csv_data(algo, node_count, computes, migrations, migrations_count, violations, reconfigurations):

    folder_name = "detailed/data/%s-%d" % (algo, node_count)

    execute_cmd(["mkdir", "-p", folder_name])

    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": computes,         "labels": ["type", "value"]}, "%s/detailed_computations.csv" % (folder_name))
    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": migrations_count, "labels": ["type", "value"]}, "%s/detailed_migrations_count.csv" % (folder_name))
    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": migrations,       "labels": ["type", "value"]}, "%s/detailed_migrations.csv" % (folder_name))
    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": violations_count, "labels": ["type", "value"]}, "%s/detailed_violations_count.csv" % (folder_name))
    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": violations,       "labels": ["type", "value"]}, "%s/detailed_violations.csv" % (folder_name))
    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": reconfigurations, "labels": ["type", "value"]}, "%s/detailed_reconfigurations.csv" % (folder_name))

map_algos_size = {}

metrics = ["migrations_count", "migrations", "computations", "violations_count", "violations", "reconfigurations"]

# variable that is used to detect "violation-out", "violation-normal" and "violation-sched":
# it will store the last line about "violations-out" or "violation-det", to detect if the next
# "violation" has been already processed!
last_line = None

algos_map = {}
for algo in algos:
    algos_map[algo] = {}
metrics_map = {}
for metric in metrics:
    metrics_map[metric] = {}

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
                migrations_count = 0
                migrations = []
                violations_count = 0
                violations = []
                reconfigurations = []

                for line in f.readlines():
                    try:
                        data = json.loads(line)

                        if float(data["time"]) > duration:
                            continue

                        if data["event"] == "trace_event" and data["value"] == "migrate":
                            migration_duration = data["duration"]
                            migrations += [migration_duration]
                            migrations_count += 1

                        if data["event"] == "trace_event" and data["value"] == "compute":
                            compute_duration = data["duration"]
                            compute_result = data["data"]["state"]
                            computes += [compute_duration]

                        if data["event"] == "trace_event" and data["value"] == "violation":
                            violation_duration = data["duration"]
                            violations += [violation_duration]
                            violations_count += 1

                        if data["event"] == "trace_event" and data["value"] == "reconfigure":
                            reconfiguration_duration = data["duration"]
                            reconfigurations += [reconfiguration_duration]

                    except Exception as e:
                        # print traceback.format_exc()
                        pass

                algos_map[algo][compute_node_count] = {
                    "migrations_count": [migrations_count],
                    "migrations": migrations,
                    "reconfigurations": reconfigurations,
                    "computations": computes,
                    "violations_count": [violations_count],
                    "violations": violations
                }

                for metric in metrics:
                    if not metrics_map[metric].has_key(compute_node_count):
                        metrics_map[metric][compute_node_count] = {}

                metrics_map["migrations_count"][compute_node_count][algo] = [migrations_count]
                metrics_map["migrations"][compute_node_count][algo] = migrations
                metrics_map["computations"][compute_node_count][algo] = computes
                metrics_map["violations_count"][compute_node_count][algo] = [violations_count]
                metrics_map["violations"][compute_node_count][algo] = violations
                metrics_map["reconfigurations"][compute_node_count][algo] = reconfigurations
    
                f.seek(0)

                # export_csv_data(algo, compute_node_count, computes, migrations, violations, reconfigurations)

################################################################################
# Clean results folder
################################################################################
execute_cmd(["rm", "-r", "detailed/results"])
execute_cmd(["mkdir", "-p", "detailed/results"])

################################################################################
# Generate mean and std for each metric and algorith
################################################################################

node_numbers = nodes_tuples

legends = {
    "migrations_count": "migrations count", 
    "migrations": "migration time (s)", 
    "computations": "computation time (s)",
    "violations_count": "violations count", 
    "violations": "violation time (s)",
    "reconfigurations": "reconfigurations time (s)"
}

metrics_data = {}
for metric in metrics:
    metrics_data[metric] = {}
    for node_number in node_numbers:
        metrics_data[metric][node_number] = {}
        for algo in algos:
            metrics_data[metric][node_number][algo] = [0.0, 0.0]

for metric in metrics:
    for algo in algos:
        for node_number in node_numbers:
            nums = array(metrics_map[metric][node_number][algo])
            std = nums.std()
            mean = nums.mean()
            metrics_data[metric][node_number][algo] = ["%6s"  % "{0:0.2f}".format(mean), "%6s"  % "{0:0.2f}".format(std)]

algos_data = {}
for algo in algos:
    algos_data[algo] = {}
    for node_number in node_numbers:
        algos_data[algo][node_number] = {}
        for metric in metrics:
            algos_data[algo][node_number][metric] = [0.0, 0.0]

for algo in algos:
    for node_number in node_numbers:
        for metric in metrics:
            nums = array(algos_map[algo][node_number][metric])
            std = nums.std()
            mean = nums.mean()
            algos_data[algo][node_number][metric] = ["%6s"  % "{0:0.2f}".format(mean), "%6s"  % "{0:0.2f}".format(std)]

# print(metrics_data)
# print(algos_data)

################################################################################
# Prepare R scripts for each simulation
################################################################################

def export_detailed_single_metric(metrics_data, algos, node_numbers, metric, legend):
    print("%s with %s" % (metric, node_count))
    folder_name = "detailed/latex/%s" % (metric)
    execute_cmd(["mkdir", "-p", folder_name])
    render_template("template/detailed_metric_script.jinja2", {"algos": algos, "node_numbers": sorted(node_numbers), "x_axis": zip(nodes_tuples, vms_tuples), "data": metrics_data, "metric": metric, "algos": algos, "legend": legend},   "%s/detailed_%s.r" % (folder_name, metric))

def export_detailed_single_algo(algos_data, metrics, node_numbers, algo, legend):
    print("%s with %s" % (algo, node_count))
    folder_name = "detailed/latex/%s" % (algo)
    execute_cmd(["mkdir", "-p", folder_name])
    render_template("template/detailed_algo_script.jinja2", {"algo": algo, "node_numbers": sorted(node_numbers), "x_axis": zip(nodes_tuples, vms_tuples), "data": algos_data, "metrics": metrics, "metrics": metrics, "legend": legend},   "%s/detailed_%s.r" % (folder_name, algo))

legend = "TOTO"

for metric in metrics:
    export_detailed_single_metric(metrics_data, algos, node_numbers, metric, legend)

for algo in algos:
    export_detailed_single_algo(algos_data, metrics, node_numbers, algo, legend)

