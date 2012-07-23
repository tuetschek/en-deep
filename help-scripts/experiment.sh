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
# Usage: experiment.sh [-r] [-l] [-d directory] [-m mem_required ] scenario-name params
# -d = run in a directory named differently
# -r = restart the plan (delete the to-do file first)
# -m = request custom memory size (default: 2g for 32-bit, 12g for 64-bit)
# -l = create links to standard data sets in the data/ subdirectory

ROOT=/home/odusek/work/mlprocess # set main working directory here
BINNAME=ml-process.jar
BINDIR=$ROOT/bin
LIBDIR=$BINDIR/lib
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
MEM=-Xmx2g

# LP_Solve libraries for the right architecture
# Make more memory available
ARCH=`uname --all | sed 's/.* \([^ ]*\) [^ ]*$/\1/'` 
if [[ $ARCH = 'i686' ]]; then
    DLL_PATH=$BINDIR/dll_32
elif [[ $ARCH == *64* ]]; then
    DLL_PATH=$BINDIR/dll_64
    MAXMEM=$(( `cat /proc/meminfo | head -n 1 | sed s/[^0-9]//g` / 1000000 - 1 ))
    if [[ $MAXMEM -ge 12 ]]; then
        MEM=-Xmx12g
    else
        MEM=-Xmx${MAXMEM}g
    fi
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

        "-l")
            LINK_DATA=1
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

if [[ $LINK_DATA = 1 ]]; then # link to standard train, devel, test data etc.
    ln -s $TRAIN . 2> /dev/null
    ln -s $DEVEL . 2> /dev/null
    ln -s $EVAL . 2> /dev/null
    ln -s $CLUSTERS . 2> /dev/null
    ln -s $CONF . 2> /dev/null
fi

# Copy the ml-process binary for newly running projects
if [ "$RESET" = 1  -o '!' -e "$BINNAME" ]; then
    cp $BIN .
    ln -s $LIBDIR . 2> /dev/null
fi

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
