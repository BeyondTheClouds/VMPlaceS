#! /usr/bin/env python

from __future__ import print_function

import sys
import re
import math

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
n_turnOff = {'true': {}, 'false': {}}
with open(sys.argv[1], 'r') as f:
	currentAlg = None
	turn_off = None

	curr = None

	# Compile 2 patterns and read the logs
	start_pattern = re.compile(r'Running (\w+)(\s-D[\w\.]+\=([\w\.]+))? with (\d+) compute and (\d+) service nodes turning off hosts: (\w+)')
	off_pattern = re.compile(r'Turn off node\d+')

	for line in f:
		# This is a new experiment
		m = re.search(start_pattern, line)
		if m:
			currentAlg = correct_name(m.group(3).split('.')[-1])
			turn_off = m.group(6)

			n_turnOff[turn_off][currentAlg] = 0

			curr = turn_off
			continue

		# A node has been turned off
		m = re.search(off_pattern, line)
		if m:
			n_turnOff[curr][currentAlg] += 1

for alg in n_turnOff['true']:
	print("\t%s & %d" % (alg, n_turnOff[turn_off][alg]))


########################################
# Get the energy metrics
########################################
energy = {'true': {}, 'false': {}}
with open(sys.argv[2], 'r') as f:
	p = re.compile(r'\d+ \w+ (\w+) (\w+) ([\d\.]+)')
	for line in f:
		m = re.match(p, line)
		implem = correct_name(m.group(1))
		turn_off = m.group(2)
		joules = float(m.group(3))

		energy[turn_off][implem] = joules / 1000 / 1000

########################################
# Make the bar plot
########################################
ind = np.arange(len(energy['true'])) # the x locations for the groups
width = 0.35

fig, ax1 = plt.subplots()
rects1 = ax1.bar(ind, energy['false'].values(), width, color='#FF4040')
rects2 = ax1.bar(ind + width, energy['true'].values(), width, color='#5DD475')

ax1.set_ylabel('Energy (Megajoules)')
ax1.set_xticks(ind + width)
lim = ax1.get_ylim()
ax1.set_ylim(lim[0], lim[1] * 1.15)

ax1.set_xticklabels(energy['false'].keys())
ax1.legend((rects1[0], rects2[0]), ('Not turning off hosts', 'Turning off hosts'))
#ax1.get_yaxis().set_major_formatter(ticker.FuncFormatter(lambda val, pos: locale.format("%.2f", val, grouping=True)))

########################################
# Make the line plot
########################################

# Make sure the values here are in the same order as the energy values
values = []
for alg in energy['true']:
	values.append(n_turnOff['true'][alg])

ax2 = ax1.twinx()
p = ax2.plot(ind + width, values, 'k-o', linewidth=2)
ax2.set_ylabel('# of hosts turned off')
lim = ax2.get_ylim()
ax2.set_ylim(lim[0], lim[1] * 1.30)
ax2.set_yticks(range(0, int(math.ceil(max(values))), 2))

for i,j in zip(ind + width, values):
	ax2.annotate(str(j), xy=(i,j + .5), va='bottom', weight='bold', size='large')

plt.show()

