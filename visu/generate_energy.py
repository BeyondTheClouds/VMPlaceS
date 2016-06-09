#!/usr/bin/python
from __future__ import division, print_function
from pkg_resources import WorkingSet , DistributionNotFound
import sys
import re
import pprint
pp = pprint.PrettyPrinter(indent=4).pprint

working_set = WorkingSet()

# Detecting if module is installed
dependency_found = True
try:
	dep = working_set.require('Jinja2')
except DistributionNotFound:
	dependency_found  = False

if not dependency_found:
	try:
		# Installing it (anyone knows a better way?)
		from setuptools.command.easy_install import main as install
		install(['Jinja2'])
		print("run again as normal user to process results")
	except DistributionNotFound:
		print("run this script as sudo to install a missing template engine")

	sys.exit(0)

import csv
import subprocess
import time
import os
import json
import jinja2

################################################################################
# Constant and parameters
################################################################################

max_duration = 86400


################################################################################
# Functions of the script
################################################################################
def execute_cmd(args):
	print("Running '%s'" % " ".join(args))
	out, err = subprocess.Popen(args,
				   shell=False,
				   stdout=subprocess.PIPE,
				   stderr=subprocess.PIPE).communicate()
	if not err == "":
		print(err)
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
execute_cmd(["rm", "-rf", "energy"])
execute_cmd(["mkdir", "energy"])

execute_cmd(["rm", "-rf", "energy/scripts"])
execute_cmd(["mkdir", "energy/scripts"])

execute_cmd(["mkdir", "-p", "data"])

################################################################################
# Fill data maps with computed metrics
################################################################################
algos = []

for dir in os.listdir('./events'):
	algos.append(dir)
print('Algos: ', end='')
pp(algos)

nodes_tuples = []
vms_tuples   = []
map_energy				  = {}
map_energy_per_second  = {}
map_energy_per_service_node = {}

for dirname, dirnames, filenames in os.walk('./events'):
	# print(path to all subdirectories first.)
	for filename in filenames:
		if filename.endswith(".json"):
			with open("%s/%s" % (dirname, filename), 'r') as f:
				print("Reading " + os.path.join(dirname, filename))
				header_line = f.readline()
				header_data = json.loads(header_line)
				data = header_data["data"]
				
				algo = os.path.basename(dirname)
				compute_node_count = data["server_count"]
				nodes_vms_tuple = "%s-%s" % (algo, compute_node_count)

				if not compute_node_count in nodes_tuples:
					nodes_tuples += [compute_node_count]
				if not data['vm_count'] in vms_tuples:
					vms_tuples += [data['vm_count']]
				
				energy = {}

				l = 1
				for line in f.readlines():
					try:
						data = json.loads(line)

						if float(data["time"]) > max_duration:
							continue

						event_time = int(float(data["time"]))

						if data["event"] == "trace_event" and data["value"] == "ENERGY":
							if not energy.has_key(data["origin"]):
								energy[data["origin"]] = 0

							energy[data["origin"]] += data["data"]["value"]

							if not map_energy_per_second.has_key(event_time):
								map_energy_per_second[event_time] = {}
								for a in algos:
									map_energy_per_second[event_time][a] = 0

							map_energy_per_second[event_time][algo] += data["data"]["value"]

						l += 1
					except Exception as ex:
						print(ex)
						print(str(l) + ' ' + line)

				map_energy[nodes_vms_tuple]						  = energy
				#map_avg_energy_per_service_node[nodes_vms_tuple]	 = reduce(lambda x, y: x + y, map_energy[nodes_vms_tuple]) / len(map_energy)

################################################################################
# Generate CSV files from data maps
################################################################################
#pp(map_energy_per_second)

# Make the column names comply with R, also make pretty names
regex = re.compile(r'(\w+)\-(\w+)')
r_algos = map(lambda n: regex.sub(r'\1\2', n), algos)
names = map(lambda n: regex.sub(r'\1 (\2)', n), algos)

render_template("template/energy_data.jinja2", {"algos": r_algos, "data": map_energy_per_second}, "data/energy.csv")

group_by_nodes = []
not_group_by_nodes = []
render_template("template/energy_script.jinja2",
		{
			"source": "data/energy.csv",
			"x_label": "Time",
			"y_label": "Joules",
			"algos": r_algos,
			"names": names,
			"x_axis": zip(nodes_tuples, vms_tuples),
			"group_by_nodes": group_by_nodes,
			"not_group_by_nodes": not_group_by_nodes, 
			"title": "cumulated computation time"
		},
		"scripts/energy.r")


