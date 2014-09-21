#!/usr/bin/python
from __future__ import division
from pkg_resources import WorkingSet , DistributionNotFound
working_set = WorkingSet()

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
execute_cmd(["rm", "-r", "data"])
execute_cmd(["mkdir", "data"])

execute_cmd(["rm", "-r", "scripts"])
execute_cmd(["mkdir", "scripts"])


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
                if not data["server_count"] in nodes_tuples:
                    nodes_tuples += [data["server_count"]]
                if not data["vm_count"] in vms_tuples:
                    vms_tuples += [data["vm_count"]]
                nodes_vms_tuple = "%s-%s" % (data["server_count"], data["vm_count"])
                if not nodes_vms_tuple in nodes_vms_tuples:
                    nodes_vms_tuples += [nodes_vms_tuple]
print nodes_tuples
print vms_tuples
print nodes_vms_tuples

################################################################################
# Fill data maps with computed metrics
################################################################################
map_compute_time   = {}
map_migration_time = {}
map_total_time     = {}

map_reconfigure_failure_count   = {}
map_reconfigure_success_count   = {}
map_migration_count             = {}

map_avg_psize                   = {}
map_avg_migration_duration      = {}

for dirname, dirnames, filenames in os.walk('./events'):
    # print path to all subdirectories first.
    for filename in filenames:
        if filename.endswith(".json"):
            with open("%s/%s" % (dirname, filename), 'r') as f:
                header_line = f.readline()
                header_data = json.loads(header_line)
                data = header_data["data"]
                algo = data["algorithm"]
                nodes_vms_tuple = "%s-%s" % (data["algorithm"], data["server_count"])
                
                compute_time     = 0
                migrate_time     = 0
                reconfigure_time = 0

                reconfigure_failure_count = 0
                reconfigure_success_count = 0
                migration_count           = 0

                servers_involved = 0

                for line in f.readlines():
                    data = json.loads(line)

                    # print(data)

                    if data["event"] == "trace_event" and data["value"] == "compute":
                        compute_time += data["data"]["duration"]

                        if data["data"]["result"] == False:
                            reconfigure_failure_count += 1
                        else:
                            reconfigure_success_count += 1
                            servers_involved += data["data"]["psize"]

                    if data["event"] == "trace_event" and data["state_name"] == "migration" and data["value"] == "finished":
                        migrate_time += data["data"]["duration"]
                        migration_count += 1

                reconfigure_time = compute_time + migrate_time

                try:
                    avg_psize = servers_involved / reconfigure_success_count
                except:
                    avg_psize = 0

                try: 
                    avg_migration_duration = migrate_time / migration_count
                except:
                    avg_migration_duration = 0

                map_compute_time[nodes_vms_tuple]   = compute_time
                map_migration_time[nodes_vms_tuple] = migrate_time
                map_total_time[nodes_vms_tuple]     = reconfigure_time

                map_reconfigure_failure_count[nodes_vms_tuple] = reconfigure_failure_count
                map_reconfigure_success_count[nodes_vms_tuple] = reconfigure_success_count
                map_migration_count[nodes_vms_tuple] = migration_count       

                map_avg_psize[nodes_vms_tuple] = avg_psize
                map_avg_migration_duration[nodes_vms_tuple] = avg_migration_duration


################################################################################
# Generate CSV files from data maps
################################################################################
print map_compute_time
print map_migration_time
print map_total_time 

print map_reconfigure_failure_count
print map_reconfigure_success_count
print map_migration_count

print map_avg_psize 
print map_avg_migration_duration 

render_template("template/matrix_data.jinja2", {"algos": algos, "server_counts": nodes_tuples, "data": map_compute_time  },            "data/compute_time.csv")
render_template("template/matrix_data.jinja2", {"algos": algos, "server_counts": nodes_tuples, "data": map_migration_time},            "data/migration_time.csv")
render_template("template/matrix_data.jinja2", {"algos": algos, "server_counts": nodes_tuples, "data": map_total_time},                "data/total_time.csv")
render_template("template/matrix_data.jinja2", {"algos": algos, "server_counts": nodes_tuples, "data": map_reconfigure_failure_count}, "data/reconfigure_failure_count.csv")
render_template("template/matrix_data.jinja2", {"algos": algos, "server_counts": nodes_tuples, "data": map_reconfigure_success_count}, "data/reconfigure_success_count.csv")
render_template("template/matrix_data.jinja2", {"algos": algos, "server_counts": nodes_tuples, "data": map_migration_count},           "data/migration_count.csv")

group_by_nodes     = ["distributed"]
not_group_by_nodes = list(set(algos) - set(group_by_nodes))

print("group_by_nodes -> %s" %(group_by_nodes))
print("not_group_by_nodes -> %s" %(not_group_by_nodes))

render_template("template/matrix_script.jinja2", {"source": "data/compute_time.csv",              "x_label": "Configuration", "y_label": "Time (s)", "algos": algos, "x_axis": zip(nodes_tuples, vms_tuples), "group_by_nodes": group_by_nodes, "not_group_by_nodes": not_group_by_nodes, "title": "computation time"},                 "scripts/compute_time.r")
render_template("template/matrix_script.jinja2", {"source": "data/migration_time.csv",            "x_label": "Configuration", "y_label": "Time (s)", "algos": algos, "x_axis": zip(nodes_tuples, vms_tuples), "group_by_nodes": group_by_nodes, "not_group_by_nodes": not_group_by_nodes, "title": "migration time"},                   "scripts/migration_time.r")
render_template("template/matrix_script.jinja2", {"source": "data/total_time.csv",                "x_label": "Configuration", "y_label": "Time (s)", "algos": algos, "x_axis": zip(nodes_tuples, vms_tuples), "group_by_nodes": group_by_nodes, "not_group_by_nodes": not_group_by_nodes, "title": "reconfiguration time"},             "scripts/total_time.r")
render_template("template/matrix_script.jinja2", {"source": "data/reconfigure_failure_count.csv", "x_label": "Configuration", "y_label": "Count",    "algos": algos, "x_axis": zip(nodes_tuples, vms_tuples), "group_by_nodes": [],              "not_group_by_nodes": [],                "title": "Failed reconfiguration count"},     "scripts/reconfigure_failure_count.r")
render_template("template/matrix_script.jinja2", {"source": "data/reconfigure_success_count.csv", "x_label": "Configuration", "y_label": "Count",    "algos": algos, "x_axis": zip(nodes_tuples, vms_tuples), "group_by_nodes": [],              "not_group_by_nodes": [],                "title": "Successful reconfiguration count"}, "scripts/reconfigure_success_count.r")
render_template("template/matrix_script.jinja2", {"source": "data/migration_count.csv",           "x_label": "Configuration", "y_label": "Count",    "algos": algos, "x_axis": zip(nodes_tuples, vms_tuples), "group_by_nodes": [],              "not_group_by_nodes": [],                "title": "Migration count"},                  "scripts/migration_count.r")


