#!/bin/bash
#
# This creates a directory for experiments, linking to the input data files,
# and runs ML-Process with the specified parameters and scenario. The name
# of the created directory is by default identical to the name of the scenario.
# If the directory exists, it is not recreated.
#
# The ROOT directory (and possibly other directories) must be configured before 
# use!
#
# All scenario files should have the extension .plan and be situated in a
# plan/ subdirectory.
# All data files needed should be situated in the data/ subdirectory.
#
#
# Usage: experiment.sh [-r] [-d directory] scenario-name params
# -d = run in a directory named differently
# -r = restart the plan (delete the to-do file first)


ROOT=/home/odusek/work/dipl # set main working directory here
BINNAME=ml-process.jar
BINDIR=$ROOT/bin
BIN=$BINDIR/$BINNAME
DATA=$ROOT/data
LANG=cs # set-up language here
TRAIN=$DATA/$LANG/train.txt
DEVEL=$DATA/$LANG/devel.txt
EVAL="$DATA/$LANG/eval.txt"
CONF="$DATA/$LANG/st-$LANG.conf"
CLUSTERS="$DATA/$LANG/clusters-*.txt"
PREDINFO="$DATA/$LANG/predinfo.txt"
PLAN=$ROOT/plan
EXT=".plan"
MEM=-Xmx12g

# LP_Solve libraries for the right architecture
ARCH=`uname --all | sed 's/.* \([^ ]*\) [^ ]*$/\1/'` 
if [[ $ARCH = 'i686' ]]; then
    DLL_PATH=$BINDIR/dll_32
elif [[ $ARCH == *64* ]]; then
    DLL_PATH=$BINDIR/dll_64
else 
    echo "Unknown ARCH: $ARCH";
    exit 1;
fi

# compute in a different directory / reset
while [[ "$1" = "-d" || "$1" = "-r" || "$1" = "-m" ]]; do 
    case "$1" in
        "-d")
            DIR=$ROOT/"$2"
            shift
            shift 
            ;;
        "-r")
            RESET=1
            shift
            ;;
        "-m")
            MEM=-Xmx$2
            shift
            shift
            ;;
    esac
done

if [[ $# -lt 1 ]] || [[ ! -f $PLAN/"$1"$EXT ]]; then
    echo "Usage: ./experiment.sh [-d work-dir-name][-r][-m heapsize] plan-name params";
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

mkdir /mnt/h/tmp/mlprocess-temp

shift  #run the process
export LC_ALL="en_US.UTF-8"
export LD_LIBRARY_PATH="$DLL_PATH:$LD_LIBRARY_PATH"
java  -Djava.library.path=$DLL_PATH -Xms128m $MEM -jar $BINNAME "$@" "$FILE"

rm -rf /mnt/h/tmp/mlprocess-temp
