#!/bin/bash

#
# submit a plan under the name of the experiment
#
JOBNAME="$1"
if [[ $1 = "-r" && $2 = "-d" ]]; then
    JOBNAME="$4"
elif [[ $1 = "-d" && $3 = "-r" ]]; then
    JOBNAME="$4"
elif [[ $1 = "-d" ]]; then
    JOBNAME="$3"
elif [[ $1 = "-r" ]]; then
    JOBNAME="$2"
fi

qsub -cwd -S /bin/bash -N "$JOBNAME" bin/experiment.sh "$@"
