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
ORDER = ['Entropy', 'Lazy FFD', 'Optimistic FFD']
#ORDER = ['Lazy FFD', 'Optimistic FFD']

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
n_turn_off = {}
n_migrations = {}
algos = []
n_on = {}
scheduler_ticks = {}

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
            n_hosts = int(m.group(4))
            
            if n_hosts not in n_turn_off:
                n_turn_off[n_hosts] = {True: {}, False: {}}
               
            if n_hosts not in n_migrations:
                n_migrations[n_hosts] = {True: {}, False: {}}
               
            if n_hosts not in scheduler_ticks:
                scheduler_ticks[n_hosts] = {True: {}, False: {}}
               
            if n_hosts not in n_on:
                n_on[n_hosts] = {}

            algo = correct_name(m.group(3).split('.')[-1])
            if algo not in algos:
                algos.append(algo)
                scheduler_ticks[n_hosts][turn_off][algo] = []

            if turn_off:
                n_on[n_hosts][algo] = {}
                n_on[n_hosts][algo][0.0] = 0

            n_turn_off[n_hosts][turn_off][algo] = 0
            n_migrations[n_hosts][turn_off][algo] = 0

            curr = turn_off

            load = int(m.group(7))
            std = int(m.group(8))

            new_experiment(algo)


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
            if algo not in scheduler_ticks[n_hosts][turn_off]:
                scheduler_ticks[n_hosts][turn_off][algo] = []
            scheduler_ticks[n_hosts][turn_off][algo].append(float(m.group(2)))
            continue

        # A node has been turned off
        m = re.search(off_pattern, line)
        if m:
            n_turn_off[n_hosts][curr][algo] += 1

            if turn_off:
                n_on[n_hosts][algo][float(m.group(2))] = n_on[n_hosts][algo][n_on[n_hosts][algo].keys()[-1]] - 1

            node_off(m.group(3), float(m.group(2)), algo)
            continue

        # A node has been turned on
        m = re.search(on_pattern, line)
        if m:
            if turn_off:
                n_on[n_hosts][algo][float(m.group(2))] = n_on[n_hosts][algo][n_on[n_hosts][algo].keys()[-1]] + 1

            node_on(m.group(3), float(m.group(2)), algo)
            continue

        # A VM has been migrated
        m = re.search(migration_pattern, line)
        if m:
            n_migrations[n_hosts][curr][algo] += 1

########################################
# Count the number of on VMs
########################################
n_vms = {}

dir_pattern = re.compile(r'(\w+)-([\w\d]+)-(\d+)-(true|false)')
events = os.path.join('visu', 'events')

# list dir in 'visu/events'
for item in os.listdir(events):
    m = re.search(dir_pattern, item)

    if m is None:
        continue

    # look for dirs like 'centralized-algo-64'
    if m.group(1) == 'centralized':
        algo = correct_name(m.group(2))
        turn_off = to_bool(m.group(4))
        n_hosts = int(m.group(3))
        
        if n_hosts not in n_vms:
            n_vms[n_hosts] = { True: {}, False: {} }

        event_file = os.path.join(events, item, 'events.json')
        print('Reading ' + event_file)

        with open(event_file, 'r') as f:
            n_vms[n_hosts][turn_off][algo] = {}

            # each line in this file is a JSON document
            for line in f:
                try:
                    event = json.loads(line)

                    if event['value'] == "NB_VM":
                        time = float(event['time'])
                        value = int(event['data']['value'])
                        n_vms[n_hosts][turn_off][algo][time] = value
                except:
                    t, value, tb = sys.exc_info()
                    print(str(t) + " " + str(value))
                    print(line)
                    traceback.print_tb(tb)
                    sys.exit(1)

                if event['value'] != 'NB_VNS_ON':
                    continue

                n_vms[n_hosts][turn_off][algo][float(event['time'])] = int(event['data']['value'])

migration_ordered = []
########################################
# Get the energy metrics
########################################
energy = {}

with open(sys.argv[2], 'r') as f:
    p = re.compile(r'(\d+) \w+ (\w+) (\w+) ([\d\.]+)')
    for line in f:
        m = re.match(p, line)
        n_hosts = int(m.group(1))
        implem = correct_name(m.group(2))
        turn_off = to_bool(m.group(3))
        joules = float(m.group(4))
        
        if n_hosts not in energy:
            energy[n_hosts] = { True: {}, False: {} }

        energy[n_hosts][turn_off][implem] = joules / simulation_time / 1000

########################################
# Make the bar plot
########################################
ind = np.arange(len(algos)) # the x locations for the groups
width = 0.35

ordered_energy = {}
off_ordered = {}
migration_ordered = {}

for n_hosts in energy.keys():
    if n_hosts not in ordered_energy:
        ordered_energy[n_hosts] = { True: [], False: [] }
        
    for alg in ORDER:
        if alg not in energy[n_hosts][True]:
            continue
    
        ordered_energy[n_hosts][True].append(energy[n_hosts][True][alg])
        ordered_energy[n_hosts][False].append(energy[n_hosts][False][alg])

    print("ordered_energy %d:" % n_hosts)
    pp(ordered_energy[n_hosts])

    fig, ax1 = plt.subplots()

    color1 = '#888888'
    color2 = '#FFFFFF'
    linewidth = 1
    rects1 = ax1.bar(ind, ordered_energy[n_hosts][False], width, color=color1, linewidth=linewidth)
    rects2 = ax1.bar(ind + width, ordered_energy[n_hosts][True], width, color=color2, linewidth=linewidth)

    ax1.set_ylabel('Energy (Megawatts)')
    ax1.set_xticks(ind + width)
    lim = ax1.get_ylim()
    ax1.set_ylim(lim[0], lim[1])

    ax1.set_xticklabels(ORDER)

    ########################################
    # Make the line plots
    ########################################
    # Make sure the values here are in the same order as the energy values
    off_ordered[n_hosts] = []
    migration_ordered[n_hosts] = []
    for alg in ORDER:
        if alg not in n_turn_off[n_hosts][True]:
            continue
        
        off_ordered[n_hosts].append(n_turn_off[n_hosts][True][alg])
        migration_ordered[n_hosts].append(n_migrations[n_hosts][True][alg])

    print("off_ordered[%d]:" % n_hosts)
    pp(off_ordered[n_hosts])
    
    print("migration_ordered[%d]:" % n_hosts)
    print(migration_ordered[n_hosts])

    ax2 = ax1.twinx()
    migration_plot, = ax2.plot(ind + width, migration_ordered[n_hosts], 'k--^', linewidth=linewidth)

    lim = ax2.get_ylim()
    ax2.set_ylim(lim[0], lim[1])
    ax2.set_yticks(range(0, int(math.ceil(max(migration_ordered[n_hosts]))), 500))

    for i,j in zip(ind + width, migration_ordered[n_hosts]):
        ax2.annotate(str(j), xy=(i,j + .5), va='bottom', weight='bold', size='large')

    lgd = ax1.legend((rects1[0], rects2[0], migration_plot),
            ('Not turning off hosts', 'Turning off hosts', 'No. VM migrations'),
            loc='lower right')

    def find_filename(format):
        i = 0
        path = format % i
        while os.path.isfile(path):
            i += 1
            path = format % i
        
        return path

    save_path = find_filename('energy_%d_%d_%d_%%d.pdf' % (n_hosts, load, std))
    plt.savefig(save_path, transparent=True, bbox_extra_artists=(lgd,), bbox_inches='tight')
    print('Saved plot as ' + save_path)
    if os.system('which imgcat > /dev/null 2>&1') == 0:
        os.system('imgcat ' + save_path)


########################################
# Make n_on plot
########################################
ordered_n_on = {}
plots = {}

styles = ['k-o', 'k-^', 'k-v', 'k-*']

for n_hosts in n_on:
    fig, ax1 = plt.subplots()

    if n_hosts not in ordered_n_on:
        ordered_n_on[n_hosts] = {}

    if n_hosts not in plots:
        plots[n_hosts] = {}

    i = 0
    for alg in ORDER:
        if alg not in n_on[n_hosts]:
            continue
        
        ordered_n_on[n_hosts][alg] = sorted(n_on[n_hosts][alg].items())
        plots[n_hosts][alg], = ax1.plot(map(lambda t: t[0], ordered_n_on[n_hosts][alg]),
                map(lambda t: t[1], ordered_n_on[n_hosts][alg]), styles[i], linewidth=linewidth, ms=8)
        i += 1
     
    print("ordered_n_on[%d]" % n_hosts)
    pp(ordered_n_on[n_hosts])

    lgd = ax1.legend(plots[n_hosts].values(),
            n_on[n_hosts].keys(),
            loc='upper right')

    ax1.set_xlim(0, simulation_time)
    ax1.set_ylim(20, n_hosts)

    save_path = find_filename('n_on_%d_%d_%d_%%d.pdf' % (n_hosts, load, std))
    plt.savefig(save_path, transparent=True, bbox_extra_artists=(lgd,), bbox_inches='tight')
    print('Saved plot as ' + save_path)
    if os.system('which imgcat > /dev/null 2>&1') == 0:
        os.system('imgcat ' + save_path)

########################################
# Make vm_on plot
########################################
n_vms_ordered = {}

linewidth = 1

for n_hosts in n_vms:
    fig, ax1 = plt.subplots()

    if n_hosts not in n_vms_ordered:
        n_vms_ordered[n_hosts] = {}

    i = 0
    colors = ['g', 'b', 'm', 'y']
    for alg in ORDER:
        if alg not in n_vms[n_hosts][True]:
            continue
        
        n_vms_ordered[n_hosts][alg] = sorted(n_vms[n_hosts][True][alg].items())
        plots[n_hosts][alg], = ax1.plot(map(lambda t: t[0], n_vms_ordered[n_hosts][alg]),
                map(lambda t: t[1], n_vms_ordered[n_hosts][alg]), colors[i] + '.-', linewidth=linewidth, ms=8)
        
        #for tick in scheduler_ticks[n_hosts][True][alg]:
        #   ax1.plot((tick, tick), (450, 512), colors[i] + '-')

        i += 1

    ax1.set_xlim(0, simulation_time)

    lgd = ax1.legend(plots[n_hosts].values(),
            n_on[n_hosts].keys(),
            loc='lower right')

    save_path = find_filename('vms_on_%d_%d_%d_%%d.pdf' % (n_hosts, load, std))
    plt.savefig(save_path, transparent=True, bbox_extra_artists=(lgd,), bbox_inches='tight')
    print('Saved plot as ' + save_path)
    if os.system('which imgcat > /dev/null 2>&1') == 0:
        os.system('imgcat ' + save_path)

