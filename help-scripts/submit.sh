#!/bin/bash

#
# submit a plan under the name of the experiment
#
JOBNAME="$1"
if [[ "$1" = "-d" ]]; then
    JOBNAME="$3"
fi
qsub -cwd -S /bin/bash -N "$JOBNAME" bin/experiment.sh "$@"
