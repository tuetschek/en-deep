/*
 *  Copyright (c) 2009 Ondrej Dusek
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list 
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this 
 *  list of conditions and the following disclaimer in the documentation and/or other 
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific 
 *  prior written permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package en_deep.mlprocess;

import en_deep.mlprocess.exception.ParamException;
import gnu.getopt.*;

/**
 * The main executable class, responsible for the whole process.
 *
 * <p>
 * According to the command parameters, the program checks for a process plan and builds
 * one using {@link plan.PlanBuilder} if there is no previously built. Then it tries to run all the
 * {@link Task}s defined in the plan using {@link Worker}(s) a if there's nothing left to do,
 * it exits.
 * </p>
 * <p>
 * The program has following command parameters:
 * </p>
 * <ul>
 * <li>the input file with the XML process description (obligatory, no switches)</li>
 * <li><tt>--threads</tt> number of {@link Worker} threads for this {@link Process} instance (default: 1)</li>
 * <li><tt>--instances</tt> number of instances that are going to be run simultaneously (default: 1)</li>
 * <li><tt>--verbosity</tt> the desired verbosity level (0-4, default: 0 - i.e. no messages)</li>
 * </ul>
 * <p>
 * The <tt>--instances</tt> parameter determines the level of parallelization for the process plan
 * creation, i.e. states how many parts the data should be split into for parallelizable tasks. If the
 * <tt>--instances</tt> parameter is too low (i.e. lower than <tt>--threads</tt> or lower than the
 * actual number of instances run), many threads may end up idle.
 * </p>
 * <p>
 * The verbosity setting looks as follows:
 * </p>
 * <ul>
 * <li>4 - debug</li>
 * <li>3 - information</li>
 * <li>2 - warning</li>
 * <li>1 - important</li>
 * <li>0 - nothing</li>
 * </ul>
 *
 * TODO set working dir ?
 * TODO second run - set a special switch: "overwrite, overwrite-newer + date, no-overwrite,
 *          & overwrite-which, leave-which
 *
 * @author Ondrej Dusek
 */
public class Process {

    /* CONSTANTS */
    /** The --threads option long name */
    private static final String OPTL_THREADS = "threads";
    /** The --instances option long name */
    private static final String OPTL_INSTANCES = "instances";
    /** The --verbosity option long name */
    private static final String OPTL_VERBOSITY = "verbosity";
    /** The --threads option short name */
    private static final char OPTS_THREADS = 't';
    /** The --threads option short name */
    private static final char OPTS_INSTANCES = 'i';
    /** The --verbosity option short name */
    private static final char OPTS_VERBOSITY = 'v';
    /** Program name as it's passed to getopts */
    private static final String PROGNAME = "ML-Process";
    /** Optstring for getopts, must correspond to the OPTS_ constants */
    private static final String OPTSTRING = "i:t:v:";

    /* DATA */
    /** The only instance of Process */
    private static Process instance;
    /** The input process XML scenario file */
    private String inputFile;
    /** The number of threads this instance should launch */
    private int instances;
    /** Instances the number of instances that are to be run in total */
    private int threads;

    /** All the working threads of this process */
    private Worker [] workers;

    /* METHODS */
    /**
     * Returns the only instance of the process, but never creates one.
     */
    public static Process getInstance() {
        return Process.instance;
    }

    /**
     * Parse the command arguments and create the Process singleton.
     * @param args the command line arguments (see the @{link Process class description} for details)
     */
    public static void main(String[] args) {

        int threads = 1; // default values to parameters
        int instances = 1;
        int verbosity = Logger.V_NOTHING;

        try {
            // parsing the options
            LongOpt[] opts = new LongOpt[3];
            opts[0] = new LongOpt(OPTL_THREADS, LongOpt.REQUIRED_ARGUMENT, null, OPTS_THREADS);
            opts[1] = new LongOpt(OPTL_INSTANCES, LongOpt.REQUIRED_ARGUMENT, null, OPTS_INSTANCES);
            opts[2] = new LongOpt(OPTL_VERBOSITY, LongOpt.OPTIONAL_ARGUMENT, null, OPTS_VERBOSITY);

            Getopt getter = new Getopt(PROGNAME, args, OPTSTRING, opts);
            int c;
            getter.setOpterr(false);

            while ((c = getter.getopt()) != -1) {
                switch (c) {
                    case OPTS_INSTANCES:
                        instances = Process.getNumericArgPar(OPTL_INSTANCES, getter.getOptarg());
                        break;
                    case OPTS_THREADS:
                        instances = Process.getNumericArgPar(OPTL_INSTANCES, getter.getOptarg());
                        break;
                    case OPTS_VERBOSITY:
                        verbosity = Process.getNumericArgPar(OPTL_VERBOSITY, getter.getOptarg());
                        break;

                    case '?':
                        throw new ParamException(ParamException.ERR_INVPAR, "" + getter.getOptopt());
                }
            }

            // checking the number of parameters for one input scenario file
            if (getter.getOptind() > args.length - 1) {
                throw new ParamException(ParamException.ERR_MISSING, "input XML scenario file name");
            }
            else if (getter.getOptind() < args.length - 1) {
                throw new ParamException(ParamException.ERR_TOO_MANY);
            }
        }
        catch (ParamException e) {
            Logger.getInstance().message(e.getMessage(), Logger.V_IMPORTANT);
            System.exit(1);
        }

        // set logging verbosity
        Logger.getInstance().setVerbosity(verbosity);

        // if the parameters are correct and everything is set up, create the actual process
        // and launch it
        Process p = new Process(args[args.length - 1], threads, instances);
        p.run();
    }

    /**
     * Converts a numeric value of the argument parameter to its integer representation,
     * throws an exception upon error.
     * @return the numeric value of the argument parameter
     * @throws ParamException if the value is not numeric
     */
    private static int getNumericArgPar(String argName, String parValue) throws ParamException {

        try {
            return Integer.parseInt(parValue);
        } catch (NumberFormatException e) {
            throw new ParamException(ParamException.ERR_NONNUMARG, argName);
        } catch (NullPointerException e) {
            throw new ParamException(ParamException.ERR_INVPAR, argName);
        }
    }

    /**
     * The creation of the main process. Just initializes the values, all the actual work
     * is done in {@link run()}.
     *
     * @param inputFile the input process XML scenario file
     * @param threads the number of threads this instance should launch
     * @param instances the number of instances that are to be run in total
     */
    private Process(String inputFile, int threads, int instances) {

        this.inputFile = inputFile;
        this.threads = threads;
        this.instances = instances;

        Logger.getInstance().message("Starting process - input:" + inputFile + ", " + threads + "threads, "
                + instances + "instances assumed.", Logger.V_INFO);
    }

    /**
     * Returns the name of the input process file.
     * @return the name of the input process file
     */
    public String getInputFile(){
        return this.inputFile;
    }

    /**
     * Returns the maximum number of {@link Worker}s that are supposed to be active.
     * This is the number of {@link Process} instances times the number of {@link Worker}s per instance.
     *
     * @return the maximum expected number of {@link Worker}s
     */
    public int getMaxWorkers(){
        return this.threads * this.instances;
    }

    /**
     * All the actual work of the {@link Process} is done in here. 
     * {@link Worker}(s) is/are launched to perform all the prescribed {@link Task}s. They use
     * the {@link Plan} singleton to obtain the {@link Task}s. The first call  to
     * {@link Plan.getNextPendingTask()} among all instances of the {@link Process} results
     * in creation of the to-do file, other just obtain next {@Task}s and mark their progress.
     */
    private void run() {

        this.workers = new Worker [this.instances];

        // create all the
        for (int i = 0; i < this.instances; ++i){
            
            this.workers[i] = new Worker(i);
            this.workers[i].run();
        }

        // TODO wait for them to finish ?
    }


}
