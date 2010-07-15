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
import en_deep.mlprocess.utils.StringUtils;
import gnu.getopt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The main executable class, responsible for the whole process.
 *
 * <p>
 * According to the command parameters, the program checks for a process plan and builds
 * one using {@link Plan} if there is no previously built. Then it tries to run all the
 * {@link Task}s defined in the plan using {@link Worker}(s) a if there's nothing left to do,
 * it exits.
 * </p>
 * <p>
 * The program has following command parameters:
 * </p>
 * <ul>
 * <li>the input file with the process description (obligatory, no switches)</li>
 * <li><tt>--threads (-t)</tt> number of {@link Worker} threads for this {@link Process} instance (default: 1)</li>
 * <li><tt>--instances (-i)</tt> number of instances that are going to be run simultaneously (only informative, default: 1)</li>
 * <li><tt>--verbosity (-v)</tt> the desired verbosity level (0-4, default: 0 - i.e. no messages)</li>
 * <li><tt>--reset (-r)</tt> comma-separated list of tasks whose status should be reset to PENDING or WAITING; in any
 * case if this is triggered, all changed tasks are reset. In order to trigger just the reset of changed tasks,
 * a single "#" should be set as an argument. If all tasks should be reset, "!" should be set as argument.</li>
 * <li><tt>--retrieve_count (-c)</tt> the number of tasks that should be retrieved by one worker at one time (default: 10)</li>
 * <li><tt>--parse_only (-p)</tt> if set, the program will just parse the scenario file, report any problems and end.</li>
 * <li><tt>--workdir (-d)</tt> specifies the working directory (if not the same as that of the plan file).</li>
 * </ul>
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
    /** The --workdir option long name */
    private static final String OPTL_WORK_DIR = "workdir";
    /** The --reset option long name */
    private static final String OPTL_RESET_TASKS = "reset";
    /** The --retrieve_count option long name */
    private static final String OPTL_RETRIEVE_COUNT = "retrieve_count";
    /** The --parse_only option long name */
    private static final String OPTL_PARSE_ONLY = "parse_only";

    /** The --threads option short name */
    private static final char OPTS_THREADS = 't';
    /** The --threads option short name */
    private static final char OPTS_INSTANCES = 'i';
    /** The --verbosity option short name */
    private static final char OPTS_VERBOSITY = 'v';
    /** The --workdir option short name */
    private static final char OPTS_WORK_DIR = 'd';
    /** The --reset option short name */
    private static final char OPTS_RESET_TASKS = 'r';
    /** The --retrieve_count option short name */
    private static final char OPTS_RETRIEVE_COUNT = 'c';
    /** The --parse_only option short name */
    private static final char OPTS_PARSE_ONLY = 'p';

    /** Program name as it's passed to getopts */
    private static final String PROGNAME = "ML-Process";
    /** Optstring for getopts, must correspond to the OPTS_ constants */
    private static final String OPTSTRING = "i:t:v:d:r:c:p";

    /* DATA */

    /** The only instance of Process */
    private static Process instance;
    /** The currently set options of the process */
    private ProcessOptions opts;
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

        int verbosity = Logger.DEFAULT_VERBOSITY;
        ProcessOptions opts = new ProcessOptions();
        opts.threads = 1; // default values to parameters
        opts.instances = 1;
        opts.retrieveCount = Plan.DEFAULT_RETRIEVE_COUNT;
        opts.workDir = null;
        opts.inputFile = null;
        opts.resetTasks = null;

        try {
            // parsing the options
            LongOpt[] possibleOpts = new LongOpt[7];
            possibleOpts[0] = new LongOpt(OPTL_THREADS, LongOpt.REQUIRED_ARGUMENT, null, OPTS_THREADS);
            possibleOpts[1] = new LongOpt(OPTL_INSTANCES, LongOpt.REQUIRED_ARGUMENT, null, OPTS_INSTANCES);
            possibleOpts[2] = new LongOpt(OPTL_VERBOSITY, LongOpt.REQUIRED_ARGUMENT, null, OPTS_VERBOSITY);
            possibleOpts[3] = new LongOpt(OPTL_WORK_DIR, LongOpt.REQUIRED_ARGUMENT, null, OPTS_WORK_DIR);
            possibleOpts[4] = new LongOpt(OPTL_RESET_TASKS, LongOpt.REQUIRED_ARGUMENT, null, OPTS_RESET_TASKS);
            possibleOpts[5] = new LongOpt(OPTL_RETRIEVE_COUNT, LongOpt.REQUIRED_ARGUMENT, null, OPTS_RETRIEVE_COUNT);
            possibleOpts[6] = new LongOpt(OPTL_PARSE_ONLY, LongOpt.NO_ARGUMENT, null, OPTS_PARSE_ONLY);

            Getopt getter = new Getopt(PROGNAME, args, OPTSTRING, possibleOpts);
            int c;
            getter.setOpterr(false);

            while ((c = getter.getopt()) != -1) {
                switch (c) {
                    case OPTS_INSTANCES:
                        opts.instances = Process.getNumericArgPar(OPTL_INSTANCES, getter.getOptarg());
                        break;
                    case OPTS_THREADS:
                        opts.threads = Process.getNumericArgPar(OPTL_THREADS, getter.getOptarg());
                        break;
                    case OPTS_VERBOSITY:
                        verbosity = Process.getNumericArgPar(OPTL_VERBOSITY, getter.getOptarg());
                        break;
                    case OPTS_WORK_DIR:
                        opts.workDir = getter.getOptarg();
                        break;
                    case OPTS_RESET_TASKS:
                        opts.resetTasks = getter.getOptarg();
                        break;
                    case OPTS_RETRIEVE_COUNT:
                        opts.retrieveCount = Process.getNumericArgPar(OPTL_RETRIEVE_COUNT, getter.getOptarg());
                        break;
                    case OPTS_PARSE_ONLY:
                        opts.parseOnly = true;
                        break;
                    case ':':
                        throw new ParamException(ParamException.ERR_MISSING, "" + (char) getter.getOptopt());
                    case '?':
                        throw new ParamException(ParamException.ERR_INVPAR, "" + (char) getter.getOptopt());
                }
            }

            // checking the number of parameters for one input scenario file
            if (getter.getOptind() > args.length - 1) {
                throw new ParamException(ParamException.ERR_MISSING, "input scenario file name");
            }
            else if (getter.getOptind() < args.length - 1) {
                throw new ParamException(ParamException.ERR_TOO_MANY);
            }
            opts.inputFile = args[args.length - 1];

            // find out the working directory, if set within the input file specs
            if (opts.workDir == null && opts.inputFile.indexOf(File.separator) != -1){

                opts.workDir = opts.inputFile.substring(0, opts.inputFile.lastIndexOf(File.separator));
                opts.inputFile = StringUtils.truncateFileName(opts.inputFile);
            }
            // otherwise working directory is the current one
            else if (opts.workDir == null){
                opts.workDir = ".";
            }
            // append path separator character to the directory specification
            if (opts.workDir.charAt(opts.workDir.length() - 1) != File.separatorChar){
                opts.workDir += File.separator;
            }

            // check the validity of the input file and working directory (if applicable)
            if (!(new File(opts.workDir)).isDirectory()){ // TODO possibly check access rights for working directory and input file ?
                throw new ParamException(ParamException.ERR_DIR_NOT_FOUND);
            }
            if (!(new File(opts.workDir + opts.inputFile)).exists()){
                throw new ParamException(ParamException.ERR_FILE_NOT_FOUND);
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
        try {
            Process p = new Process(opts);
            p.run();
        }
        catch (Exception e){
            Logger.getInstance().message("Could not create process - " + e.getMessage(), Logger.V_IMPORTANT);
            System.exit(1);
        }        
    }

    /**
     * Converts a numeric value of the argument parameter to its integer representation,
     * throws an exception upon error.
     *
     * @param argName  the argument name (just for exceptions)
     * @param parValue  the value of the parameter
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
     * The creation of the main process. 
     * 
     * Just initializes the values, all the actual work is done in {@link run()}.
     *
     * @param threads the number of threads this instance should launch
     * @param instances the number of instances that are to be run in total
     * @param workDir the working directory (if different from where the input file is)
     * @param inputFile the input process scenario file
     * @param resetTasks the tasks whose status is to be reset befor the running (may be null)
     * @param retrieveCount number of tasks retrieved at one time
     * @throws IOException if the reset task list could not be created
     */
    private Process(ProcessOptions opts)
            throws IOException {

        this.opts = opts;

        Process.instance = this;

        Logger.getInstance().message("Starting process - input:" + this.opts.workDir + File.separator
                + this.opts.inputFile + ", " + this.opts.threads + " thread(s); "
                + this.opts.instances + " instance(s) assumed.", Logger.V_INFO);

        if (this.opts.resetTasks != null){
            this.createResetList(this.opts.resetTasks);
        }
    }

    /**
     * Returns the path to the input process file. The path is already related to the process
     * working directory.
     * @return the path to the input process file
     */
    public String getInputFile(){
        return this.opts.workDir + this.opts.inputFile;
    }

    /**
     * Returns the maximum number of {@link Worker}s that are supposed to be active.
     * This is the number of {@link Process} instances times the number of {@link Worker}s per instance.
     *
     * @return the maximum expected number of {@link Worker}s
     */
    public int getMaxWorkers(){
        return this.opts.threads * this.opts.instances;
    }

    /**
     * Returns the current working directory, with path separator character at the end. This
     * is used in parsing of input and output files' specification for tasks and should be used
     * in all other file operations, since all paths should be relative to the working directory.
     * 
     * @return the current working directory
     */
    public String getWorkDir(){
        return this.opts.workDir;
    }

    /**
     * Returns the maximum number of tasks that should be retrieved at the same time.
     * @return the number of tasks that should be retrieved at one time
     */
    public int getRetrieveCount(){
        return this.opts.retrieveCount;
    }

    /**
     * All the actual work of the {@link Process} is done in here.
     * <p>
     * {@link Worker}(s) is/are launched to perform all the prescribed {@link Task}s. They use
     * the {@link Plan} singleton to obtain the {@link Task}s. The first call  to
     * {@link Plan.getNextPendingTask()} among all instances of the {@link Process} results
     * in creation of the to-do file, other just obtain next {@Task}s and mark their progress.
     * </p>
     * <p>
     * If the {@link ProcessOptions#parseOnly} option is set, this just parses the scenario file
     * and exits.
     */
    private void run() {

        if (this.opts.parseOnly){
            Plan.getInstance().checkScenario();
            return;
        }

        this.workers = new Worker [this.opts.threads];

        // create all the workers and run them
        for (int i = 0; i < this.opts.threads; ++i){
            
            this.workers[i] = new Worker(i);
            Thread t = new Thread(this.workers[i]);

            t.start();
        }
    }

    /**
     * Creates a file that lists all tasks whose statuses should be reset upon plan loading.
     *
     * @param resetTasks comma-separated list of task name prefixes to be reset
     * @throws IOException if the list file cannot be created
     */
    private void createResetList(String resetTasks) throws IOException {

        FileOutputStream out = new FileOutputStream(this.getInputFile() + Plan.RESET_FILE_SUFFIX);

        out.write(resetTasks.getBytes());
        out.close();        
    }

    /**
     * This will hold all the options of a {@link Process}
     */
    private static class ProcessOptions {

        /** Number of running {@link Worker} threads in this instance */
        int threads;
        /** Number of running instances */
        int instances;
        /** The working directory */
        String workDir;
        /** The input scenario file */
        String inputFile;
        /** List of task name prefixes whose status is to be reset */
        String resetTasks;
        /** Number of tasks to be retrieved at once from the {@link Plan} */
        int retrieveCount;
        /** Should we only parse the plan, output any errors and exit ? */
        boolean parseOnly;
    }
}
