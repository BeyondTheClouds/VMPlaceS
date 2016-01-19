#!/usr/bin/python

import csv
import subprocess
import time
import os


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

################################################################################
# Clean results folder
################################################################################
execute_cmd(["rm", "-r", "results"])
execute_cmd(["mkdir", "results"])

################################################################################
# Regenerate allfigure.r
################################################################################
execute_cmd(["rm", "scripts/allfigure.r"])

open("scripts/allfigure.r", 'a').close()
all_figure_script = open("scripts/allfigure.r", 'a')
with all_figure_script as f:
    f.write("#!/usr/bin/env Rscript")

    execute_cmd(["chmod", "+x", "scripts/allfigure.r"])


    ############################################################################
    # Append all scripts to allfigure.r
    ############################################################################
    for dirname, dirnames, filenames in os.walk('./scripts'):
        # print path to all subdirectories first.
        for filename in filenames:
            if not filename == "allfigure.r":
                # print filename
                # ins = open("%s/%s" % (dirname, filename), "r" )
                # for line in ins:
                #     if not line.startswith("#!"):
                #     	popen("ls")
                #     	# execute_cmd(["echo", "toto"])
                execute_cmd(["/usr/bin/env", "Rscript", "%s/%s" % (dirname, filename)])
                basename_script = filename.split(".")[0]
                execute_cmd(["mv", "Rplots.pdf", "results/%s.pdf" % (basename_script)])
