#!/bin/bash

#
# submit a plan under the name of the experiment
#
# Usage: ./submit.sh [-q machine] [-i iterations] experiment
# Where:
#   - iterations is the number of times the experiment is run
#   - machine is a parameter passed to qsub -q
#   - experiment is the experiment name (directory)
ITER=1
EXPMEM=12G
LIMMEM=13G

while [[ "$1" = "-q" || "$1" = "-i" ]]; do 
    case "$1" in
        "-q")
        MACHINE="$2";
        shift; shift;
        ;;
        
        "-i")
        ITER="$2";
        shift; shift;
        ;;

        "-m")
        LIMMEM=$(( ${2%?} + 1 ))G;
        EXPMEM="$2";
        shift; shift;
        ;;

    esac
done

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

for I in `seq -w 1 "$ITER"`; do
    if [[ -n "$MACHINE" ]]; then
        qsub -q "$MACHINE" -hard -l h_vmem=$LIMMEM -l mem_free=$LIMMEM -l act_mem_free=$LIMMEM -cwd -S /bin/bash -N "$JOBNAME.$I" bin/experiment.sh -m $EXPMEM "$@"
    else
        qsub -hard -l h_vmem=$LIMMEM -l mem_free=$LIMMEM -l act_mem_free=$LIMMEM -cwd -S /bin/bash -N "$JOBNAME.$I" bin/experiment.sh -m $EXPMEM "$@"
    fi
done
