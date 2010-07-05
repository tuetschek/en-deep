#!/bin/bash

#
# pouziti: qsub -cwd -S /bin/bash bin/experiment.sh plan params
# 
# plan se uvadi jen nazev souboru v podadresari plan/ bez pripony!


ROOT=/home/odusek/work/dipl
BINNAME=ml-process.jar
BINDIR=$ROOT/bin
BIN=$BINDIR/$BINNAME
DATA=$ROOT/data
TRAIN=$DATA/train.txt
DEVEL=$DATA/devel.txt
EVAL="$DATA/eval.txt"
CONF="$DATA/st-en.conf"
CLUSTERS="$DATA/clusters-*.txt"
PLAN=$ROOT/plan
EXT=".plan"

ARCH=`uname --all | sed 's/.* \([^ ]*\) [^ ]*$/\1/'`
if [[ $ARCH = 'i686' ]]; then
    DLL_PATH=$BINDIR/dll_32
elif [[ $ARCH == *64* ]]; then
    DLL_PATH=$BINDIR/dll_64
else 
    echo "Unknown ARCH: $ARCH";
    exit 1;
fi

while [[ $1 = "-d" || $1 = "-r" ]]; do # compute in a different directory / reset
    case $1 in
        "-d")
            DIR=$ROOT/"$2"
            shift
            shift 
            ;;
        "-r")
            RESET=1
            shift
            ;;
    esac
done

if [[ $# -lt 1 ]] || [[ ! -f $PLAN/"$1"$EXT ]]; then
    echo "Usage: ./experiment.sh [-d work-dir-name][-r] plan-name params";
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

if [[ $RESET = 1 ]]; then # remove the plan status if necessary
    rm $FILE.* 2> /dev/null
fi

shift  #run the process
export LD_LIBRARY_PATH="$DLL_PATH:$LD_LIBRARY_PATH"
java  -Djava.library.path=$DLL_PATH -Xms128m -Xmx2g -jar $BINNAME "$@" "$FILE"
