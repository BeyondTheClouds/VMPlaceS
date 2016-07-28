#! /usr/bin/env python

from __future__ import print_function

import os, sys, re, json, math
import traceback
import operator
import pprint
pp = pprint.PrettyPrinter(indent=4).pprint

import numpy as np

import matplotlib.pyplot as plt
import matplotlib.patches as patches
import matplotlib.path as path
import matplotlib.animation as animation
import matplotlib.ticker as ticker
import locale
locale.setlocale(locale.LC_ALL, 'en_US')

def eprint(*args, **kwargs):
	print(*args, file=sys.stderr, **kwargs)

# Determines the order of the bars in the plots
#ORDER = ['Entropy', 'BtrPlace', 'Lazy FFD', 'Optimistic FFD']
ORDER = ['Lazy FFD', 'Optimistic FFD']

# Check arguments
if len(sys.argv) != 3:
	eprint('Usage: ./energy_plot.py <log file> <energy file>')
	sys.exit(1)

# Some functions
def to_bool(string):
	if string in ['true', 'True']:
		return True
	
	if string in ['false', 'False']:
		return False

	eprint("%s is not a boolean" % string)
	sys.exit(3)

def correct_name(name):
	names = {
		'LazyFirstFitDecreased': 'Lazy FFD',
		'OptimisticFirstFitDecreased': 'Optimistic FFD',
		'BtrPlaceRP': 'BtrPlace',
		'Entropy2RP': 'Entropy'}

	return names[name]

# time_on['Entropy']['node56'] = 17546.57
time_on = {}
last_on = None
def new_experiment(alg):
	global last_on
	time_on[alg] = {}
	last_on = {}

def end_experiment(time, alg):
	for node in last_on.keys():
		if last_on[node] is not None:
			node_off(node, time, alg)

def node_on(name, time, alg):
	if name in last_on and last_on[name] is not None:
		eprint("Node %s was already on since %.2f" % (name, time))
		sys.exit(1)
	
	last_on[name] = time

def node_off(name, time, alg):
	if last_on[name] is None:
		eprint("None %s was not on" % name)
		sys.exit(1)

	if name not in time_on[alg]:
		time_on[alg][name] = 0
	
	time_on[alg][name] += time - last_on[name]
	last_on[name] = None

########################################
# Get the number of turned off hosts
# and of migrations
########################################
n_turn_off = {True: {}, False: {}}
n_migrations = {True: {}, False: {}}
algos = []
n_off = {}
scheduler_ticks = {True: {}, False: {}}

# load and standard deviation must be the same
# for all the experiments in the log file
load = None
std = None
simulation_time = None
n_hosts = None

with open(sys.argv[1], 'r') as f:
	turn_off = None

	curr = None

	# Compile 3 patterns and read the logs
	start_pattern = re.compile(r'Running (\w+)(\s-D[\w\.]+\=([\w\.]+))? with (\d+) compute and (\d+) service nodes turning off hosts: (\w+), load.mean=(\d+), load.std=(\d+)')
	end_pattern = re.compile(r'\[.*\s(\d+\.\d+)\] \[.*\] End of Injection')
	off_pattern = re.compile(r'\[(.*\s)?(\d+\.\d+)\] \[.*\] Turn off (node\d+)')
	on_pattern = re.compile(r'\[(.* )?(\d+\.\d+)\] \[.*\] Turn on (node\d+)')
	migration_pattern = re.compile(r'End of migration of VM vm-\d+ from node\d+ to node\d+')

	scheduler_pattern = re.compile(r'\[(.*)\s(\d+\.\d+)\] \[.*\] Launching scheduler \(id = \d+\) - start to compute')

	for line in f:
		# This is a new experiment
		m = re.search(start_pattern, line)
		if m:
			turn_off = to_bool(m.group(6))

			algo = correct_name(m.group(3).split('.')[-1])
			if algo not in algos:
				algos.append(algo)
				scheduler_ticks[turn_off][algo] = []

			if turn_off:
				n_off[algo] = {}
				n_off[algo][0.0] = 0

			n_turn_off[turn_off][algo] = 0
			n_migrations[turn_off][algo] = 0

			curr = turn_off

			load = int(m.group(7))
			std = int(m.group(8))

			new_experiment(algo)

			n_hosts = int(m.group(4))

			continue

		# An experiment is over
		m = re.search(end_pattern, line)
		if m:
			time = float(m.group(1))
			end_experiment(time, algo)
			simulation_time = int(time)
			continue
		
		# The scheduler is running
		m = re.search(scheduler_pattern, line)
		if m:
			if algo not in scheduler_ticks[turn_off]:
				scheduler_ticks[turn_off][algo] = []
			scheduler_ticks[turn_off][algo].append(float(m.group(2)))
			continue

		# A node has been turned off
		m = re.search(off_pattern, line)
		if m:
			n_turn_off[curr][algo] += 1

			if turn_off:
				n_off[algo][float(m.group(2))] = n_off[algo][n_off[algo].keys()[-1]] - 1

			node_off(m.group(3), float(m.group(2)), algo)
			continue

		# A node has been turned on
		m = re.search(on_pattern, line)
		if m:
			if turn_off:
				n_off[algo][float(m.group(2))] = n_off[algo][n_off[algo].keys()[-1]] + 1

			node_on(m.group(3), float(m.group(2)), algo)
			continue

		# A VM has been migrated
		m = re.search(migration_pattern, line)
		if m:
			n_migrations[curr][algo] += 1

########################################
# Count the number of on VMs
########################################
n_vms = {
	True: {},
	False: {}
}

n_vms_true = {
	True: {},
	False: {}
}

dir_pattern = re.compile(r'(\w+)-([\w\d]+)-(\d+)-(true|false)')
events = os.path.join('visu', 'events')

# list dir in 'visu/events'
for item in os.listdir(events):
		m = re.search(dir_pattern, item)

		# look for dirs like 'centralized-algo-64'
		if m.group(1) == 'centralized' and int(m.group(3)) == n_hosts:
			algo = correct_name(m.group(2))
			turn_off = to_bool(m.group(4))

			event_file = os.path.join(events, item, 'events.json')
			print('Reading ' + event_file)

			with open(event_file, 'r') as f:
				n_vms[turn_off][algo] = {}
				n_vms_true[turn_off][algo] = {}

				# each line in this file is a JSON document
				for line in f:
					try:
						event = json.loads(line)

						if event['value'] == "NB_VM":
							time = float(event['time'])
							value = int(event['data']['value'])
							n_vms[turn_off][algo][time] = value

						if event['value'] == "NB_VM_TRUE":
							time = float(event['time'])
							value = int(event['data']['value'])
							n_vms_true[turn_off][algo][time] = value
					except:
						t, value, tb = sys.exc_info()
						print(str(t) + " " + str(value))
						print(line)

					if event['value'] != 'NB_VNS_ON':
						continue

					n_vms[turn_off][algo][float(event['time'])] = int(event['data']['value'])
					n_vms_true[turn_off][algo][float(event['time'])] = int(event['data']['value'])

migration_ordered = []
########################################
# Get the energy metrics
########################################
energy = {True: {}, False: {}}
#for alg in range(len(algos)):
#	energy[True].append(-1000)
#	energy[False].append(-1000)

with open(sys.argv[2], 'r') as f:
	p = re.compile(r'\d+ \w+ (\w+) (\w+) ([\d\.]+)')
	for line in f:
		m = re.match(p, line)
		implem = correct_name(m.group(1))
		turn_off = to_bool(m.group(2))
		joules = float(m.group(3))

		energy[turn_off][implem] = joules / simulation_time / 1000

########################################
# Make the bar plot
########################################
ind = np.arange(len(ORDER)) # the x locations for the groups
width = 0.35

ordered_energy = {True: [], False: []}
for alg in ORDER:
	ordered_energy[True].append(energy[True][alg])
	ordered_energy[False].append(energy[False][alg])
	print("Inserting True %s %.2f" % (alg, energy[True][alg]))
	print("Inserting False %s %.2f" % (alg, energy[False][alg]))

print("erergy:")
pp(energy)
print("ordered_energy:")
pp(ordered_energy)

fig, ax1 = plt.subplots()

color1 = '#888888'
color2 = '#FFFFFF'
linewidth = 1
rects1 = ax1.bar(ind, ordered_energy[False], width, color=color1, linewidth=linewidth)
rects2 = ax1.bar(ind + width, ordered_energy[True], width, color=color2, linewidth=linewidth)

ax1.set_ylabel('Energy (Megawatts)')
ax1.set_xticks(ind + width)
lim = ax1.get_ylim()
ax1.set_ylim(lim[0], lim[1])

ax1.set_xticklabels(ORDER)

########################################
# Make the line plots
########################################

# Make sure the values here are in the same order as the energy values
off_ordered = []
migation_ordered = []
for alg in ORDER:
	off_ordered.append(n_turn_off[True][alg])
	migration_ordered.append(n_migrations[True][alg])

print("off_ordered:")
pp(off_ordered)

ax2 = ax1.twinx()
off_plot, = ax2.plot(ind + width, off_ordered, 'k-o', linewidth=linewidth)
migration_plot, = ax2.plot(ind + width, migration_ordered, 'k--^', linewidth=linewidth)

lim = ax2.get_ylim()
ax2.set_ylim(lim[0], lim[1])
ax2.set_yticks(range(0, int(math.ceil(max(migration_ordered))), 50))

for i,j in zip(ind + width, off_ordered):
	ax2.annotate(str(j), xy=(i,j + .5), va='bottom', weight='bold', size='large')

for i,j in zip(ind + width, migration_ordered):
	ax2.annotate(str(j), xy=(i,j + .5), va='bottom', weight='bold', size='large')

lgd = ax1.legend((rects1[0], rects2[0], off_plot, migration_plot),
		('Not turning off hosts', 'Turning off hosts', 'No. machines turned off', 'No. VM migrations'),
		loc='upper left', bbox_to_anchor=(1.08, 1.02),
		handlelength=2, framealpha=1.0, markerscale=.8)

def find_filename(format):
	i = 0
	path = format % i
	while os.path.isfile(path):
		i += 1
		path = format % i
	
	return path

save_path = find_filename('energy_%d_%d_%%d.pdf' % (load, std))
plt.savefig(save_path, transparent=True, bbox_extra_artists=(lgd,), bbox_inches='tight')
print('Saved plot as ' + save_path)
if os.system('which imgcat > /dev/null 2>&1') == 0:
	os.system('imgcat ' + save_path)


########################################
# Make n_on plot
########################################
fig, ax1 = plt.subplots()

ordered_n_on = {}
plots = {}
styles = ['k-o', 'k-^', 'k-v', 'k-*']
i = 0
for alg in ORDER:
	ordered_n_on[alg] = sorted(n_off[alg].items())
	plots[alg], = ax1.plot(map(lambda t: t[0], ordered_n_on[alg]),
			map(lambda t: t[1], ordered_n_on[alg]), styles[i], linewidth=linewidth, ms=8)
	i += 1

lgd = ax1.legend(plots.values(),
		n_off.keys(),
		loc='lower right')

ax1.set_ylim(0, n_hosts + 5)

save_path = find_filename('n_on_%d_%d_%%d.pdf' % (load, std))
plt.savefig(save_path, transparent=True, bbox_extra_artists=(lgd,), bbox_inches='tight')
print('Saved plot as ' + save_path)
if os.system('which imgcat > /dev/null 2>&1') == 0:
	os.system('imgcat ' + save_path)

print("n_on:")
pp(ordered_n_on)
print("Scheduler:")
pp(scheduler_ticks)

########################################
# Make time_on plot
########################################
time_on = {k: reduce(lambda a, b: a+b, v.values()) for k, v in time_on.items()}
ordered_time_on = []

for alg in ORDER:
	ordered_time_on.append(time_on[alg])

print("time_on:")
pp(time_on)

fig, ax1 = plt.subplots()

color1 = '#888888'
color2 = '#FFFFFF'
linewidth = 1
rects1 = ax1.bar(ind, ordered_time_on, width, color=color1, linewidth=linewidth)

ax1.set_ylabel('Cumulative uptime of all servers (in seconds)')
ax1.set_xticks(ind + width / 2)
lim = ax1.get_ylim()
ax1.set_ylim(lim[0], lim[1])

ax1.set_xticklabels(ORDER)

save_path = find_filename('time_on_%d_%d_%%d.pdf' % (load, std))
plt.savefig(save_path, transparent=True, bbox_extra_artists=(lgd,), bbox_inches='tight')
print('Saved plot as ' + save_path)
if os.system('which imgcat > /dev/null 2>&1') == 0:
	os.system('imgcat ' + save_path)

########################################
# Make vm_on plot
########################################
n_vms_ordered = {}

fig, ax1 = plt.subplots()

linewidth = 1

print("n_vms:")
pp(n_vms[True].keys())

i = 0
for alg in ORDER:
	n_vms_ordered[alg] = sorted(n_vms[True][alg].items())
	plots[alg], = ax1.plot(map(lambda t: t[0], n_vms_ordered[alg]),
			map(lambda t: t[1], n_vms_ordered[alg]), '-', linewidth=linewidth, ms=8)
	i += 1

#for scheduler_ticks[True][

lgd = ax1.legend(plots.values(),
		n_off.keys(),
		loc='upper right')

save_path = find_filename('vms_on_%d_%d_%%d.pdf' % (load, std))
plt.savefig(save_path, transparent=True, bbox_extra_artists=(lgd,), bbox_inches='tight')
print('Saved plot as ' + save_path)
if os.system('which imgcat > /dev/null 2>&1') == 0:
	os.system('imgcat ' + save_path)


###############################



n_vms_ordered_true = {}

fig, ax1 = plt.subplots()

linewidth = 1

i = 0
for alg in ORDER:
	n_vms_ordered_true[alg] = sorted(n_vms_true[True][alg].items())
	plots[alg], = ax1.plot(map(lambda t: t[0], n_vms_ordered_true[alg]),
			map(lambda t: t[1], n_vms_ordered_true[alg]), '-', linewidth=linewidth, ms=8)
	i += 1

#for scheduler_ticks[True][

lgd = ax1.legend(plots.values(),
		n_off.keys(),
		loc='upper right')

save_path = find_filename('vms_on_true_%d_%d_%%d.pdf' % (load, std))
plt.savefig(save_path, transparent=True, bbox_extra_artists=(lgd,), bbox_inches='tight')
print('Saved plot as ' + save_path)
if os.system('which imgcat > /dev/null 2>&1') == 0:
	os.system('imgcat ' + save_path)
