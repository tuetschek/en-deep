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

import en_deep.mlprocess.TaskDescription.TaskStatus;
import en_deep.mlprocess.exception.DataException;
import en_deep.mlprocess.exception.PlanException;
import en_deep.mlprocess.exception.SchedulingException;
import en_deep.mlprocess.exception.TaskException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;



/**
 * This component is responsible for building the planFile of the whole computation,
 * according to the input scenario.
 *
 * @author Ondrej Dusek
 */
public class Plan {

    /* CONSTANTS */

    /** File extension for the plan file */
    public static final String PLAN_FILE_SUFFIX =  ".todo";
    /** File extension for the reset file */
    public static final String RESET_FILE_SUFFIX =  ".reset";
    /** File extension for the status file */
    public static final String STATUS_FILE_SUFFIX = ".status";

    /** Number of tasks to retrieve at once @todo make RETRIEVE_TASKS configurable */
    static final int DEFAULT_RETRIEVE_COUNT = 10;

    /* DATA */

    /** The planFile file */
    private File planFile;

    /** The task reset list file */
    private File resetFile;

    /** Current status printout file */
    private File statusFile;
    /** Number of tasks that should be retrieved at the same time */
    private int retrieveCount;

    /** The only instance of {@link Plan}. */
    private static Plan instance = null;



    /* METHODS */

    /**
     * Creates a new instance of {@link Plan}. All objects should call
     * {@link Plan.getInstance()} to acquire an instance of {@link Plan}.
     */
    private Plan(){
        
        this.planFile = new File(Process.getInstance().getInputFile() + PLAN_FILE_SUFFIX);
        this.resetFile = new File(Process.getInstance().getInputFile() + RESET_FILE_SUFFIX);
        this.statusFile = new File(Process.getInstance().getInputFile() + STATUS_FILE_SUFFIX);
        this.retrieveCount = Process.getInstance().getRetrieveCount();

        // create the needed files if necessary
        try {
            // this ensures we never have an exception, but the file may be empty
            planFile.createNewFile();
            resetFile.createNewFile();
        }
        catch(IOException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
        }
    }

    /**
     * Retrieves the only instance of the {@link Plan} singleton. This calls
     * the {@link Plan} constructor upon first call.
     *
     * @return the only instance of {@link Plan}
     */
    public static synchronized Plan getInstance(){

        if (Plan.instance == null){
            Plan.instance = new Plan();
        }
        return Plan.instance;
    }


    /**
     * Tries to get the next pending task from the to-do file.
     * <p>
     * Locks the to-do file
     * to avoid concurrent access from within several instances. If the to-do file does
     * not exist or is empty, creates it and fills it with a planFile.
     * </p><p>
     * Returns null in case of nothing else to do. If an error occurs, it is logged with
     * the highest importance setting and an exception is thrown.
     * <p>
     *
     * @return the next pending task to be done, or an empty vector if there are no tasks to be done
     * @throws PlanException if an exception occurs when working with the scenario or plan file
     * @throws SchedulingException if there are no tasks to process and we have to wait for them
     */
    public synchronized Vector<TaskDescription> getNextPendingTasks() throws PlanException, SchedulingException {

        FileLock planLock = null;
        FileLock resetLock = null;
        Vector<TaskDescription> nextPending = null;
        RandomAccessFile planFileIO = null;
        RandomAccessFile resetFileIO = null;
        
        // try to acquire planLock on the to-do file and get a planned task
        try {
            planFileIO = new RandomAccessFile(this.planFile, "rw");
            planLock = planFileIO.getChannel().lock();

            if (planFileIO.length() == 0){ // the planFile file - the planFile has not yet been created
                this.createPlan(planFileIO);
            }

            resetFileIO = new RandomAccessFile(this.resetFile, "rw");
            resetLock = resetFileIO.getChannel().lock();
            if (resetFileIO.length() != 0){
                this.resetTasks(planFileIO, resetFileIO);
            }
           
            nextPending = this.getNextPendingTasks(planFileIO);
        }
        catch(IOException ex){
            Logger.getInstance().message("I/O error - " + ex.getMessage(), Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_IO_ERROR);
        }
        catch(DataException ex){
            Logger.getInstance().message("Data error - " + ex.getMessage(), Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_INVALID_SCENARIO);
        }
        catch(ClassNotFoundException ex){
            Logger.getInstance().message("Incorrect plan file - " + ex.getMessage(), Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_INVALID_PLAN);
        }
        catch(TaskException ex){
            Logger.getInstance().message("Task error - " + ex.getMessage(), Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_INVALID_SCENARIO);
        }

        // always release the lock on the plan and reset file
        finally {
            if (resetLock != null && resetLock.isValid()){
                try {
                    resetLock.release();
                }
                catch(IOException ex){
                    Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                    throw new PlanException(PlanException.ERR_IO_ERROR);
                }
            }
            if (planLock != null && planLock.isValid()){
                try {
                    planLock.release();
                }
                catch(IOException ex){
                    Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                    throw new PlanException(PlanException.ERR_IO_ERROR);
                }
            }
            // close the to-do file and reset file
            try {
                planFileIO.close();
                if (resetFileIO != null){ // test if we really got that far
                    resetFileIO.close();
                }
            }
            catch(IOException ex){
                Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                throw new PlanException(PlanException.ERR_IO_ERROR);
            }
        }

        return nextPending;
    }

    /**
     * Creates the process planFile, so that {@link Worker}s may retrieve pending {@link Task}s
     * later.
     * Tries to read the process description file and create the to-do file according to
     * it, using DAG and parallelizations (up to the specified number of {@Worker}s for all
     * instances of the {@link Process}.
     *
     * TODO possibly check conformity of Task classes upon plan creation ?
     *
     * @param planFileIO the to-do file, locked and opened for writing
     * @throws IOException if there are some I/O problems with the file
     * @throws DataException if there are some illogical event dependencies
     */
    private synchronized void createPlan(RandomAccessFile planFileIO) throws IOException, DataException {

        ScenarioParser parser = new ScenarioParser(Process.getInstance().getInputFile());
        Vector<TaskDescription> plan;

        Logger.getInstance().message("Parsing the scenario file ...", Logger.V_DEBUG);

        // parse the input file
        parser.parse();
        
        // topologically sort the plan
        plan = parser.getTasks();
        this.sortPlan(plan);

        // write the plan into the plan file
        this.writePlan(plan, planFileIO);

        Logger.getInstance().message("Plan written ...", Logger.V_DEBUG);
    }


    /**
     * Reads the to-do file structure and retrieves at most {@link #RETRIEVE_TASKS} next pending {@link Task}s,
     * updating their progress status to {@link TaskStatus#IN_PROGRESS} in the plan file.
     *
     * @param planFileIO the to-do file, locked and opened for writing
     * @return next pending tasks from the .todo file, or an empty vector
     * @throws IOException if there are I/O problems with the plan file access
     * @throws ClassNotFoundException if there are problems with the plan file contents
     * @throws TaskException if there are problems with the task classes' descriptions
     * @throws SchedulingException if there are tasks waiting or in progress, but no pending ones
     */
    private synchronized Vector<TaskDescription> getNextPendingTasks(RandomAccessFile planFileIO)
            throws IOException, ClassNotFoundException, TaskException, PlanException, SchedulingException {

        Vector<TaskDescription> plan = this.readPlan(planFileIO);
        Vector<TaskDescription> retrieved = new Vector<TaskDescription>(this.retrieveCount);

        Logger.getInstance().message("Retrieving tasks ...", Logger.V_DEBUG);

        for (int i = 0; i < this.retrieveCount; i++) {
            try {
                TaskDescription nextTask = this.retrievePendingTask(plan);
                if (nextTask == null){
                    break;
                }
                retrieved.add(nextTask);
            }
            catch (SchedulingException e) { // if we have to wait, return with less tasks than RETRIEVE_TASKS
                if (i == 0){
                    throw e;
                }
                break;
            }
        }

        Logger.getInstance().message("Got " + retrieved.size() + ". Writing back the status of all "
                + plan.size() + "...", Logger.V_DEBUG);        
        // update the plan file
        this.writePlan(plan, planFileIO);

        return retrieved;
    }


    /**
     * This finds the next pending task and returns it, performing the necessary task expansions along the way.
     * @param plan the opened and active process plan
     * @return the next pending task, or null if there are none
     * @throws SchedulingException if there are only tasks waiting for dependencies
     * @throws TaskException if task expansion fails
     */
    private synchronized TaskDescription retrievePendingTask(Vector<TaskDescription> plan)
            throws SchedulingException, TaskException {

        TaskDescription pendingDesc = null;
        boolean inProgress = false, waiting = false; // are there waiting tasks & tasks in progress ?
        int pos;

        // obtaining the task to be done: we are operating in the topological order
        for (pos = 0; pos < plan.size(); ++pos){
                        
            if (plan.get(pos % plan.size()).getStatus() == TaskStatus.WAITING){
                waiting = true;
            }
            else if (plan.get(pos % plan.size()).getStatus() == TaskStatus.IN_PROGRESS){
                inProgress = true;
            }
            else if (plan.get(pos % plan.size()).getStatus() == TaskStatus.PENDING){
                pendingDesc = plan.get(pos % plan.size());
                break;
            }
        }

        if (pendingDesc == null){
            // some tasks are in progress and some are waiting -> we have to wait
            if (inProgress && waiting){
                throw new SchedulingException(SchedulingException.ERR_DEP_WAIT);
            }
            // no dependencies, but it's still needed to wait if some task will produce others
            else if (inProgress){
                throw new SchedulingException(SchedulingException.ERR_IN_PROGRESS);
            }
            // there are no pending tasks & no in progress or waiting - nothing to be done -> return
            return null;
        }
        // expand the task (and possibly dependent tasks) accoring to "*"'s in input / output file names
        TaskExpander te = new TaskExpander(pendingDesc);
        te.expand();
        this.topologicalAdd(plan, pos, te.getTasksToAdd()); // these well may be empty
        plan.removeAll(te.getTasksToRemove());

        pendingDesc = plan.get(pos); // the first expanded task

        // mark the task as "in progress"
        pendingDesc.setStatus(TaskStatus.IN_PROGRESS);
        return pendingDesc;
    }


    /**
     * Reads all the prefixes of the tasks to be reset from a file. If there is nothing to be
     * reset, returns null. If changed tasks should be reset, returns an empty string; if all
     * tasks should be reset, returns "*"; if the plan should be stopped immediately, returns
     * "-". The reset of changed tasks is triggered by a single "#" in the reset file, the
     * reset of all tasks by a single "!" in the reset file and stopping by a single "-" in the
     * reset file.
     *
     * @param resetFileIO the open reset file
     * @return a string pattern for tasks to be reset (null for none, empty for changed, "*" for all, "-" for stop)
     * @throws IOException in case of an I/O error
     */
    private String getResetPrefixes(RandomAccessFile resetFileIO) throws IOException {

        String line = resetFileIO.readLine();
        StringBuilder pattern = new StringBuilder("(");
        boolean first = true;

        while (line != null) {

            String[] taskPrefixes = line.split(",");

            for (String prefix : taskPrefixes) {
                
                if (prefix.matches("\\s+")){ // skip just whitespace
                    continue;
                }
                prefix = prefix.trim();
                if (first){
                    pattern.append(prefix);
                    first= false;
                }
                else {
                    pattern.append("|");
                    pattern.append(prefix);
                }
            }
            line = resetFileIO.readLine();
        }
        pattern.append(").*");

        if (pattern.toString().equals("().*")){ // reset none
            return null;
        }
        else if (pattern.toString().equals("(#).*")){ // reset changed
            return "";
        }
        else if (pattern.toString().equals("(!).*")){ // reset all
            return "*";
        }
        else if (pattern.toString().equals("(-).*")){ // scrap the plan
            return "-";
        }
        return pattern.toString();
    }

    /**
     * Marks all tasks in the given plan and their expansions, too. Creates a hashtable with unexpanded
     * task names as keys and their (even expanded) tasks as values.
     *
     * @param plan the plan where to collect task names in
     * @return the hashtable with unexpanded task names and the corresponding tasks
     */
    private Hashtable<String, Vector<TaskDescription>> markTaskNames(Vector<TaskDescription> plan) {

        Hashtable<String, Vector<TaskDescription>> oldPlanByName = new Hashtable<String, Vector<TaskDescription>>();

        for (TaskDescription task : plan) {
            String prefix = task.getId().replaceFirst("#.*$", "");
            if (oldPlanByName.get(prefix) == null) {
                oldPlanByName.put(prefix, new Vector<TaskDescription>());
            }
            oldPlanByName.get(prefix).add(task);
        }
        return oldPlanByName;
    }


    /**
     * This removes the tasks from the current plan that need to be reset in any case,
     * so that they are certainly reset in the new one. If the regex is "*", all
     * tasks are removed, if the regex is "", none are removed.
     *
     * @param resetRegex the regexp for tasks to be reset (obtained by{@link Plan.getResetPrefixes(RandomAccessFile)})
     * @param plan the plan to remove the tasks from
     */
    private void removeTasksToReset(String resetRegex, Vector<TaskDescription> plan) {

        if (resetRegex.equals("*")){ // force reset all tasks
            plan.clear();
        }
        else if (!resetRegex.equals("")){ // remove just the tasks with the given pattern (if any)

            Pattern resetPattern = Pattern.compile(resetRegex);
            LinkedList<TaskDescription> toRemove = new LinkedList<TaskDescription>();

            for (TaskDescription task : plan) {

                if (resetPattern.matcher(task.getId()).matches()) {

                    LinkedList<TaskDescription> trans = task.getDependentTransitive();

                    if (trans != null){
                        toRemove.addAll(trans);
                    }
                }
            }
            plan.removeAll(toRemove);
        }
    }

    /**
     * This checks the new plan against the old one, updating all the statuses of unchanged tasks.
     * If there have been some expansions performed in the old plan, they are done in the new one and
     * the task equality is checked on the results of that expansion.
     *
     * @param newPlan the new plan
     * @param oldPlanByName the old plan, in a hashmap by (unexpanded) task names
     * @throws TaskException if an expansion fails
     */
    private void resetUpdateStatuses(Vector<TaskDescription> newPlan,
            Hashtable<String, Vector<TaskDescription>> oldPlanByName) throws TaskException {
        
        int i = 0;
        while (i < newPlan.size()) {

            Vector<TaskDescription> oldTasks;
            TaskDescription task = newPlan.get(i);

            if ((oldTasks = oldPlanByName.get(task.getId())) != null) {

                // we need to check for changes
                // there was already an expansion for the old task, therefore we need to expand the new task
                //
                // "here-expansion" works, too (last condition) - the task is expanded and "i" doesn't move, so
                // the equality of the expansion result is tested in the next iteration
                if (oldTasks.size() > 1 || !oldTasks.get(0).getId().equals(task.getId())
                        || (task.hasInputPattern("**") && !oldTasks.get(0).hasInputPattern("**"))) {

                    TaskExpander expander = new TaskExpander(task);
                    Collection<TaskDescription> newTasks;

                    expander.expand();
                    newPlan.removeAll(expander.getTasksToRemove());
                    newTasks = expander.getTasksToAdd();
                    newPlan.addAll(i, newTasks);

                    // check the expansion results and set the right statuses
                    for (TaskDescription newTask : newTasks) {
                        int index;
                        if ((index = oldTasks.indexOf(newTask)) != -1) {
                            newTask.setStatus(oldTasks.get(index).getStatus());
                        }
                    }
                    i += newTasks.size();
                }
                else {
                    // update an unexpanded task status, if it's identical to the one in the old plan
                    if (oldTasks.get(0).equals(task)) {
                        task.setStatus(oldTasks.get(0).getStatus());
                    }
                    ++i;
                }
            }
            else {
                // task not found in the old plan, just leave is as it is
                ++i;
            }
        }
    }


    /**
     * Writes the current plan status into the plan file, using serialization.
     * @param plan the current plan status
     * @param planFile the file to write to (an open output stream)
     */
    private synchronized void writePlan(Vector<TaskDescription> plan, RandomAccessFile planFile) throws IOException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        byte [] planData;

        oos.writeObject(plan);
        oos.flush();
        oos.close();

        planData = bos.toByteArray();

        planFile.seek(0);
        planFile.setLength(planData.length);
        planFile.write(planData);

        FileOutputStream debugOs = new FileOutputStream(this.statusFile, false);
        for (TaskDescription td : plan){
            debugOs.write(td.toString().getBytes());
        }
        debugOs.close();
    }

    
    /**
     * Topologically sorts the process plan. Sort as Kahn, A. B. (1962), "Topological sorting of large networks",
     * Communications of the ACM 5 (11): 558–562.
     * @param plan the process plan to be sorted
     */
    private void sortPlan(Vector<TaskDescription> plan) throws DataException {

        Vector<TaskDescription> sorted = new Vector<TaskDescription>(plan.size());
        ArrayDeque<TaskDescription> independent = new ArrayDeque<TaskDescription>();

        // first, find all independent tasks and put them into the queue
        for (TaskDescription task : plan){
            if (task.allPrerequisitiesSorted()){ // since no tasks are sorted, this outputs only the independent tasks
                independent.add(task);
            }
        }

        // try to process all the tasks
        while (!independent.isEmpty()){

            TaskDescription task = independent.poll();
            Vector<TaskDescription> dependent;

            task.setOrder(sorted.size());
            sorted.add(task);

            dependent = task.getDependent();
            if (dependent != null){
                for (TaskDescription depTask : dependent){

                    if (depTask.allPrerequisitiesSorted()){
                        independent.add(depTask);
                    }
                }
            }
        }

        // check if we found all tasks - if not, the plan has loops, which is prohibited
        for (TaskDescription task : sorted){
            if (task.getOrder() < 0){
                throw new DataException(DataException.ERR_LOOP_DEPENDENCY, Process.getInstance().getInputFile());
            }
        }

        // add the sorted plan into the original task list
        plan.clear();
        plan.addAll(sorted);
    }

    /**
     * Reads the current plan status from the plan input file, using serialization. Closes the input stream.
     *
     * @param planFile the file to read from
     * @return the current plan with correct task statuses
     * @throws IOException if an I/O error occurs while reading the input file or if the file is incorrect
     */
    private synchronized Vector<TaskDescription> readPlan(RandomAccessFile planFile) throws IOException, ClassNotFoundException {

        byte [] planFileContents = new byte [(int) planFile.length()];
        ByteArrayInputStream bis;
        ObjectInputStream ois;
        Vector<TaskDescription> plan;

        planFile.seek(0); // bufferring needed: file must not be closed
        planFile.read(planFileContents);
        bis = new ByteArrayInputStream(planFileContents);
        ois = new ObjectInputStream(bis);
        plan = (Vector<TaskDescription>) ois.readObject();
        ois.close();

        return plan;
    }

    /**
     * Updates the statuses of the given tasks (and all the dependent tasks, accordingly).
     * @param tasks the task whose statuses are to be updated
     * @param status the new status
     */
    public synchronized void updateStatuses(List<TaskDescription> tasks, TaskStatus status) throws PlanException {

        FileLock lock = null;
        RandomAccessFile planFileIO = null;

        try {
            // planLock the plan file
            planFileIO = new RandomAccessFile(this.planFile, "rw");
            lock = planFileIO.getChannel().lock();

            // obtain the plan
            Vector<TaskDescription> plan = this.readPlan(planFileIO);

            // update the statuses
            for (TaskDescription task : tasks){
                this.updateTaskStatus(plan, task.getId(), status);
            }
            
            // write the plan back
            this.writePlan(plan, planFileIO);
        }
        catch (ClassNotFoundException ex){
            Logger.getInstance().message("Plan file error - " + ex.getMessage(), Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_INVALID_PLAN);
        }
        catch (IOException ex){
            Logger.getInstance().message("I/O error - " + ex.getMessage(), Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_IO_ERROR);
        }
        finally {
            // release planLock
            if (lock != null && lock.isValid()){
                try {
                    lock.release();
                }
                catch(IOException ex){
                    Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                    throw new PlanException(PlanException.ERR_IO_ERROR);
                }
            }

            // close the plan file
            try {
                planFileIO.close();
            }
            catch(IOException ex){
                Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                throw new PlanException(PlanException.ERR_IO_ERROR);
            }
        }

    }

    /**
     * This finds the given task and updates its status and the statuses of all depending tasks; if the
     * updated status is {@link TaskStatus#DONE}, it removes the task from the plan completely.
     *
     * @param plan the process plan
     * @param id the id of the task to be updated
     * @param taskStatus the new task status
     * @throws PlanException if the task of the given id cannot be found in the plan
     */
    private synchronized void updateTaskStatus(Vector<TaskDescription> plan, String id, TaskStatus taskStatus)
            throws PlanException {
        
        int pos = this.findLastUsedTask(plan, id);

        if (pos == -1){
            Logger.getInstance().message("Cannot find task " + id + " to update its status!", Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_INVALID_PLAN);
        }

        // update the task
        plan.get(pos).setStatus(taskStatus);
        if (taskStatus == TaskStatus.DONE){
            plan.get(pos).looseAllDeps();
            plan.remove(pos);
        }
    }

    /**
     * Reset all the tasks that are listed in the resetFile. Writes the updated plan. Resets the plan position
     * to -1, so that the reading starts from 0.
     * 
     * @param planFileIO locked and open plan I/O file
     * @param resetFileIO locked and open reset I/O file
     */
    private void resetTasks(RandomAccessFile planFileIO, RandomAccessFile resetFileIO) 
            throws IOException, ClassNotFoundException, DataException, TaskException, PlanException {

        Vector<TaskDescription> oldPlan = this.readPlan(planFileIO);
        ScenarioParser parser = new ScenarioParser(Process.getInstance().getInputFile());
        Vector<TaskDescription> newPlan;
        String resetRegex = this.getResetPrefixes(resetFileIO);
        Hashtable<String, Vector<TaskDescription>> oldPlanByName;

        if (resetRegex == null){ // nothing to be reset
            return;
        }
        if (resetRegex.equals("-")){
            throw new PlanException(PlanException.INTERRUPT);
        }

        // parse the new version of the input file
        parser.parse();

        // topologically sort the new plan
        newPlan = parser.getTasks();
        this.sortPlan(newPlan);

        // remove the tasks that need to be reset in any case
        this.removeTasksToReset(resetRegex, oldPlan);
        // mark task names
        oldPlanByName = this.markTaskNames(oldPlan);

        // update unchanged tasks' statuses to the current progress in the old plan
        this.resetUpdateStatuses(newPlan, oldPlanByName);

        resetFileIO.setLength(0); // clear the reset file
        this.writePlan(newPlan, planFileIO); // write down the new plan
    }


    /**
     * This takes an existing task (found by id) and appends it with the given groups of new tasks.
     * The other tasks are all set as depending on the original task and all the forward dependencies
     * of the original task are forwarded to the LAST of the new tasks. The internal dependencies
     * within the group of new tasks MUST be already properly set-up. The last task
     * in the vector MUST (in any way) depend on all the other new tasks, so that the topological
     * order is not affected.
     * 
     * @param id the id of the task to be expanded
     * @param expansion the new tasks to be added -- last one of them MUST depend on all the other
     */
    public synchronized void appendToTask(String id, Vector<TaskDescription> expansion) throws PlanException {
        
        RandomAccessFile planFileIO = null;
        FileLock planLock = null;

        try {
            planFileIO = new RandomAccessFile(this.planFile, "rw");
            planLock = planFileIO.getChannel().lock();

            Vector<TaskDescription> plan = this.readPlan(planFileIO);

            this.appendToTask(plan, id, expansion);

            this.writePlan(plan, planFileIO);
        }
        catch (ClassNotFoundException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_INVALID_PLAN);
        }
        catch (IOException ex) {
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_IO_ERROR);
        }
        finally {
            // release the plan file lock
            if (planLock != null && planLock.isValid()){
                try {
                    planLock.release();
                }
                catch(IOException ex){
                    Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                    throw new PlanException(PlanException.ERR_IO_ERROR);
                }
            }
            // close the plan file
            try {
                planFileIO.close();
            }
            catch(IOException ex){
                Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                throw new PlanException(PlanException.ERR_IO_ERROR);
            }
        }
    }

    /**
     * This does the internal plan operations for the {@link Plan.appendToTask(String, Vector)} function --
     * it puts the new tasks just after the old task, if the given id is found and arranges the dependencies
     * accordingly.
     *
     * @param plan the plan to be modified
     * @param id the old task to be found in the plan
     * @param expansion the new tasks to be added to the plan
     */
    private synchronized void appendToTask(Vector<TaskDescription> plan, String id, Vector<TaskDescription> expansion)
            throws PlanException {

        TaskDescription old = null;
        Vector<TaskDescription> deps;
        int pos = this.findLastUsedTask(plan, id);

        if (pos == -1){
            Logger.getInstance().message("Cannot find " + id + "where the new tasks should be added!",
                    Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_INVALID_PLAN);
        }
        old = plan.get(pos);

        // loosen the dependencies for the original task
        deps = old.getDependent();
        old.looseDeps(null, false);

        // connect it to all the new tasks
        for (TaskDescription t : expansion){
            t.setDependency(old);
        }

        // set the dependencies right
        if (deps != null){
            TaskDescription last = expansion.get(expansion.size() - 1);

            for (TaskDescription d : deps){
                d.setDependency(last);
            }
        }

        // insert the complex into the plan AFTER the original task
        plan.addAll(plan.indexOf(old) + 1, expansion);
    }

    public synchronized void checkScenario(){

        try {
            ScenarioParser parser = new ScenarioParser(Process.getInstance().getInputFile());
            Vector<TaskDescription> plan;

            Logger.getInstance().message("Parsing the scenario file ...", Logger.V_DEBUG);

            // parse the input file
            parser.parse();

            // topologically sort the plan
            plan = parser.getTasks();
            this.sortPlan(plan);
        }
        catch (DataException e){
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            Logger.getInstance().message("Data error: " + e.getMessage(), Logger.V_IMPORTANT);
            return;
        }
        catch (IOException e){
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            Logger.getInstance().message("I/O error: " + e.getMessage(), Logger.V_IMPORTANT);
            return;
        }
        Logger.getInstance().message("The scenario file seems to be OK.", Logger.V_INFO);
    }

    /**
     * This tries the task with given id, starting off the {@link #lastFirstRetrieved} position.
     * @param plan the plan to search in
     * @param id the id of the desired task
     * @return the position of the desired task, or -1
     */
    private synchronized int findLastUsedTask(Vector<TaskDescription> plan, String id){

        int pos;

        for (pos = 0; pos < plan.size(); ++pos){

            if (plan.get(pos % plan.size()).getId().equals(id)){
                return pos % plan.size();
            }
        }
        return -1;
    }

    /**
     * Add the given tasks to the plan while preserving the topological ordering of the plan.
     *
     * @param plan the plan to be changed
     * @param pos the starting position
     * @param tasksToAdd the tasks to be added
     */
    private synchronized void topologicalAdd(Vector<TaskDescription> plan, int pos, Collection<TaskDescription> tasksToAdd) {
        
        Iterator<TaskDescription> it = tasksToAdd.iterator();

        while (it.hasNext()){
            TaskDescription addCur = it.next();
            while (pos < plan.size() && plan.get(pos).getOrder() < addCur.getOrder()){
                pos++;
            }
            plan.add(pos, addCur);
        }
    }
}
