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

def export_csv_data(algo, node_count, violations_smp_detected, violations_smp_hidden, violations_out_detected, violations_out_hidden):

    folder_name = "clouds/data/%s-%d" % (algo, node_count)

    execute_cmd(["mkdir", "-p", folder_name])

    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": violations_smp_detected, "labels": ["smp_det_time", "smp_det_duration", "node", "type"]}, "%s/violations_smp_det.csv" % (folder_name))
    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": violations_smp_hidden,   "labels": ["smp_hid_time", "smp_hid_duration", "node", "type"]}, "%s/violations_smp_hid.csv" % (folder_name))
    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": violations_out_detected, "labels": ["out_det_time", "out_det_duration", "node", "type"]}, "%s/violations_out_det.csv" % (folder_name))
    render_template("template/cloud_data.jinja2", {"algo": algo, "node_count": node_count, "violations": violations_out_hidden,   "labels": ["out_hid_time", "out_hid_duration", "node", "type"]}, "%s/violations_out_hid.csv" % (folder_name))

map_algos_size = {}

map_hidden_violation_count   = {}
map_detected_violation_count = {}
map_detected_violation_ratio = {}


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

                _violations_det_per_node = {}
                _violations_out_per_node = {}
                _violations_smp_per_node = {}

                for line in f.readlines():
                    try:
                        data = json.loads(line)

                        if float(data["time"]) > duration:
                            continue

                        if data["event"] == "trace_event" and data["value"] == "violation-det":
                            current_violation_det = (float(data["time"]), float(data["duration"]), data["origin"], "det")
                            if not _violations_det_per_node.has_key(data["origin"]):
                                _violations_det_per_node[data["origin"]] = []
                            _violations_det_per_node[data["origin"]] += [current_violation_det]

                        if data["event"] == "trace_event" and data["value"] == "violation-out":
                            current_violation_out = (float(data["time"]), float(data["duration"]), data["origin"], "out")
                            if not _violations_out_per_node.has_key(data["origin"]):
                                _violations_out_per_node[data["origin"]] = []
                            _violations_out_per_node[data["origin"]] += [current_violation_out]

                        if data["event"] == "trace_event" and data["value"] == "violation":
                            current_violation_smp = (float(data["time"]), float(data["duration"]), data["origin"], "smp")
                            if not _violations_smp_per_node.has_key(data["origin"]):
                                _violations_smp_per_node[data["origin"]] = []
                            _violations_smp_per_node[data["origin"]] += [current_violation_smp]

                    except Exception as e:
                        # print traceback.format_exc()
                        pass
                
                f.seek(0)

                nodes = set(_violations_smp_per_node.keys() + _violations_out_per_node.keys())

                violations_smp_detected = []
                violations_smp_hidden   = []
                violations_out_detected = []
                violations_out_hidden   = []

                for node in nodes:
                    try:

                        current_violation_det = _violations_det_per_node[node] if _violations_det_per_node.has_key(node) else []
                        current_violation_out = _violations_out_per_node[node] if _violations_out_per_node.has_key(node) else []
                        current_violation_smp = _violations_smp_per_node[node] if _violations_smp_per_node.has_key(node) else []

                        product = itertools.product(current_violation_smp, current_violation_det)
                        product_filtered = [element for element in product if abs(element[0][0] + element[0][1] - element[1][0] - element[1][1]) < 0.01]

                        violations_smp_per_node_detected = set([element[0] for element in product_filtered])
                        violations_smp_per_node_hidden   = set([element    for element in current_violation_smp if element not in violations_smp_per_node_detected])

                        if len(violations_smp_per_node_detected) + len(violations_smp_per_node_hidden) != len(current_violation_smp):
                            print("%s + %s = %s" % (violations_smp_per_node_detected, violations_smp_per_node_hidden, current_violation_smp))

                        product = itertools.product(current_violation_out, current_violation_det)
                        product_filtered = [element for element in product if abs(element[0][0] + element[0][1] - element[1][0] - element[1][1]) < 0.01]

                        violations_out_per_node_detected = set([element[0] for element in product_filtered])
                        violations_out_per_node_hidden   = set([element    for element in current_violation_out if element not in violations_out_per_node_detected])
                        if len(violations_out_per_node_detected) + len(violations_out_per_node_hidden) != len(current_violation_out):
                            print("%s + %s = %s" % (violations_out_per_node_detected, violations_out_per_node_hidden, current_violation_out))

                        

                        violations_smp_detected += violations_smp_per_node_detected
                        violations_smp_hidden   += violations_smp_per_node_hidden
                        violations_out_detected += violations_out_per_node_detected
                        violations_out_hidden   += violations_out_per_node_hidden
                        
                    except:
                        pass

                hidden_violation_count   = len(violations_smp_hidden) + len(violations_out_hidden)
                detected_violation_count = len(violations_smp_detected) + len(violations_out_detected)

                map_hidden_violation_count[nodes_vms_tuple]     = hidden_violation_count  
                map_detected_violation_count[nodes_vms_tuple]   = detected_violation_count

                map_detected_violation_ratio[nodes_vms_tuple] = detected_violation_count / (detected_violation_count + hidden_violation_count)

                # print("%s@%d => %d" % (algo, compute_node_count, violation_total_time))

                # export_csv_data(algo, compute_node_count, violations_smp_detected, violations_smp_hidden, violations_out_detected, violations_out_hidden)


################################################################################
# Generate CSV files from data maps
################################################################################
print map_hidden_violation_count
print map_detected_violation_count
print map_detected_violation_ratio

render_template("template/matrix_data.jinja2", {"algos": algos, "server_counts": nodes_tuples, "data": map_hidden_violation_count  },             "data/hidden_violation_count.csv")
render_template("template/matrix_data.jinja2", {"algos": algos, "server_counts": nodes_tuples, "data": map_detected_violation_count  },           "data/detected_violation_count.csv")
render_template("template/matrix_data.jinja2", {"algos": algos, "server_counts": nodes_tuples, "data": map_detected_violation_ratio  },           "data/detected_violation_ratio.csv")

group_by_nodes     = ["distributed", "hierarchical"]
not_group_by_nodes = list(set(algos) - set(group_by_nodes))

print("group_by_nodes -> %s" %(group_by_nodes))
print("not_group_by_nodes -> %s" %(not_group_by_nodes))

render_template("template/matrix_script.jinja2", {"source": "data/hidden_violation_count.csv",   "x_label": "Configuration", "y_label": "count",          "algos": algos, "x_axis": zip(nodes_tuples, vms_tuples), "group_by_nodes": [], "not_group_by_nodes": [],  "title": "hidden_violation_count"},   "scripts/hidden_violation_count.r")
render_template("template/matrix_script.jinja2", {"source": "data/detected_violation_count.csv", "x_label": "Configuration", "y_label": "count",          "algos": algos, "x_axis": zip(nodes_tuples, vms_tuples), "group_by_nodes": [], "not_group_by_nodes": [],  "title": "detected_violation_count"}, "scripts/detected_violation_count.r")
render_template("template/matrix_script.jinja2", {"source": "data/detected_violation_ratio.csv", "x_label": "Configuration", "y_label": "percentage",     "algos": algos, "x_axis": zip(nodes_tuples, vms_tuples), "group_by_nodes": [], "not_group_by_nodes": [],  "title": "detected_violation_ratio"}, "scripts/detected_violation_ratio.r")
