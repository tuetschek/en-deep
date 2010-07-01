#!/bin/bash

#
# pouziti: qsub -cwd -S /bin/bash bin/experiment.sh plan params
# 
# plan se uvadi jen nazev souboru v podadresari plan/ bez pripony!


ROOT=/home/odusek/work/dipl
BINNAME=ml-process.jar
BIN=$ROOT/bin/$BINNAME
DATA=$ROOT/data
TRAIN=$DATA/train.txt
DEVEL=$DATA/devel.txt
EVAL="$DATA/eval.txt"
CONF="$DATA/st-en.conf"
CLUSTERS="$DATA/clusters-*.txt"
PLAN=$ROOT/plan
EXT=".plan"

if [[ $1 = "-d" ]]; then # compute in a different directory
    DIR=$ROOT/"$2";
    shift; shift;
fi
if [[ $# -lt 1 ]] || [[ ! -f $PLAN/"$1"$EXT ]]; then
    echo "Usage: ./experiment.sh [work-dir-name] plan-name params";
    exit 1;
fi

if [[ $DIR = "" ]]; then    
    DIR=$ROOT/"$1"
fi

if [[ ! -d $DIR ]]; then
    mkdir -p $DIR
fi
cd $DIR
ln -s $PLAN/"$1"$EXT . 2> /dev/null
ln -s $TRAIN . 2> /dev/null
ln -s $DEVEL . 2> /dev/null
ln -s $EVAL . 2> /dev/null
ln -s $CLUSTERS . 2> /dev/null
if [ '!' -e $BINNAME ]; then
    ln -s $BIN . 2> /dev/null
fi
ln -s $CONF . 2> /dev/null

FILE="$1"$EXT
shift  #run the process
./$BINNAME "$@" "$FILE"
