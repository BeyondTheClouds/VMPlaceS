#! /usr/bin/env python

from __future__ import print_function

import os
import sys
import re
import math
import operator

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

# Check arguments
if len(sys.argv) != 3:
	eprint("Usage: ./energy_plot.py <log file> <energy file>")
	sys.exit(1)

def correct_name(name):
	names = {
		'LazyFirstFitDecreased': 'Lazy FFD',
		'OptimisticFirstFitDecreased': 'Optimistic FFD',
		'BtrPlaceRP': 'BtrPlace',
		'Entropy2RP': 'Entropy'}

	return names[name]

########################################
# Get the number of turned off hosts 
########################################
n_turn_off = {'true': [], 'false': []}
n_migrations = {'true': [], 'false': []}
algos = []
with open(sys.argv[1], 'r') as f:
	currentAlg = None
	turn_off = None

	curr = None

	# Compile 3 patterns and read the logs
	start_pattern = re.compile(r'Running (\w+)(\s-D[\w\.]+\=([\w\.]+))? with (\d+) compute and (\d+) service nodes turning off hosts: (\w+)')
	off_pattern = re.compile(r'Turn off node\d+')
	migration_pattern = re.compile(r'End of migration of VM vm-\d+ from node\d+ to node\d+')

	for line in f:
		# This is a new experiment
		m = re.search(start_pattern, line)
		if m:
			turn_off = m.group(6)

			if curr != turn_off:
				currentAlg = 0
			else:
				currentAlg += 1 

			algo = correct_name(m.group(3).split('.')[-1])
			if algo not in algos:
				algos.append(algo)

			n_turn_off[turn_off].append(0)
			n_migrations[turn_off].append(0)

			curr = turn_off
			continue

		# A node has been turned off
		m = re.search(off_pattern, line)
		if m:
			n_turn_off[curr][currentAlg] += 1
			continue

		# A VM has been migrated
		m = re.search(migration_pattern, line)
		if m:
			n_migrations[curr][currentAlg] += 1

for alg in range(len(algos)):
	print("\t%s & %d" % (algos[alg], n_turn_off[turn_off][alg]))


########################################
# Get the energy metrics
########################################
energy = {'true': [], 'false': []}
for alg in range(len(algos)):
	energy['true'].append(-1000)
	energy['false'].append(-1000)

with open(sys.argv[2], 'r') as f:
	p = re.compile(r'\d+ \w+ (\w+) (\w+) ([\d\.]+)')
	for line in f:
		m = re.match(p, line)
		implem = algos.index(correct_name(m.group(1)))
		turn_off = m.group(2)
		joules = float(m.group(3))

		energy[turn_off][implem] = joules / 1000 / 1000

########################################
# Make the bar plot
########################################
ind = np.arange(len(energy['true'])) # the x locations for the groups
width = 0.35

fig, ax1 = plt.subplots()

#color1 = '#FF4040'
#color2 = '#5DD475'
color1 = '#888888'
color2 = '#FFFFFF'
linewidth = 1
rects1 = ax1.bar(ind, energy['false'], width, color=color1, linewidth=linewidth)
rects2 = ax1.bar(ind + width, energy['true'], width, color=color2, linewidth=linewidth)

ax1.set_ylabel('Energy (Megajoules)')
ax1.set_xticks(ind + width)
lim = ax1.get_ylim()
ax1.set_ylim(lim[0], lim[1])

ax1.set_xticklabels(algos)
#ax1.get_yaxis().set_major_formatter(ticker.FuncFormatter(lambda val, pos: locale.format("%.2f", val, grouping=True)))

########################################
# Make the line plots
########################################

# Make sure the values here are in the same order as the energy values
off_values = []
migration_values = []
for alg in range(len(algos)):
	off_values.append(n_turn_off['true'][alg])
	migration_values.append(n_migrations['true'][alg])

ax2 = ax1.twinx()
off_plot, = ax2.plot(ind + width, off_values, 'k-o', linewidth=linewidth)
migration_plot, = ax2.plot(ind + width, migration_values, 'k--^', linewidth=linewidth)

lim = ax2.get_ylim()
ax2.set_ylim(lim[0], lim[1])
ax2.set_yticks(range(0, int(math.ceil(max(migration_values))), 50))

for i,j in zip(ind + width, off_values):
	ax2.annotate(str(j), xy=(i,j + .5), va='bottom', weight='bold', size='large')

for i,j in zip(ind + width, migration_values):
	ax2.annotate(str(j), xy=(i,j + .5), va='bottom', weight='bold', size='large')

lgd = ax1.legend((rects1[0], rects2[0], off_plot, migration_plot),
		('Not turning off hosts', 'Turning off hosts', 'No. machines turned off', 'No. VM migrations'),
		loc='upper left', bbox_to_anchor=(1.08, 1.02),
		handlelength=2, framealpha=1.0, markerscale=.8)

plt.savefig('energy.pdf', transparent=True, bbox_extra_artists=(lgd,), bbox_inches='tight')
if os.system('which imgcat') == 0:
	os.system('imgcat energy.pdf')
