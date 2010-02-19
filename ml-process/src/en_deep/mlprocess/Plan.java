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

import en_deep.mlprocess.Task.TaskStatus;
import en_deep.mlprocess.exception.DataException;
import en_deep.mlprocess.exception.PlanException;
import en_deep.mlprocess.exception.SchedulingException;
import en_deep.mlprocess.exception.TaskException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayDeque;
import java.util.Vector;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;


/**
 * This component is responsible for building the planFile of the whole computation,
 * according to the input scenario.
 *
 * @author Ondrej Dusek
 */
public class Plan {


    /* DATA */

    /** The planFile file */
    private File planFile;

    /** The only instance of {@link Plan}. */
    private static Plan instance = null;

    /** The tasks and their features and pending statuses */
    private Vector<TaskSection> tasks;

    /* METHODS */

    /**
     * Creates a new instance of {@link Plan}. All objects should call
     * {@link Plan.getInstance()} to acquire an instance of {@link Plan}.
     */
    private Plan(){
        
        // open the planFile file (and create it if necessary)
        this.planFile = new File(Process.getInstance().getInputFile() + ".todo");

        try {
            // this ensures we never have an exception, but the file may be empty
            planFile.createNewFile();
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
    public static Plan getInstance(){

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
     * @return the next pending task to be done, or null if there are no tasks to be done
     * @throws PlanException if an exception occurs when working with the scenario or plan file
     * @throws SchedulingException if there are no tasks to process and we have to wait for them
     */
    public synchronized Task getNextPendingTask() throws PlanException, SchedulingException {

        FileLock lock = null;
        Task nextPending = null;
        
        // try to acquire lock on the to-do file and get a planned task
        try {
            RandomAccessFile planFileIO = new RandomAccessFile(this.planFile, "rw");
            lock = planFileIO.getChannel().lock();

            if (planFileIO.length() == 0){ // the planFile file - the planFile has not yet been created
                this.createPlan(planFileIO);
            }

            nextPending = this.getNextPendingTask(planFileIO);
        }
        catch(IOException ex){
            Logger.getInstance().message("I/O error - " + ex.getMessage(), Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_IO_ERROR);
        }
        catch(SAXException ex){
            Logger.getInstance().message("XML error - " + ex.getMessage(), Logger.V_IMPORTANT);
            throw new PlanException(PlanException.ERR_INVALID_SCENARIO);
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

        // always release the lock on the to-do file
        finally {
            if (lock != null && lock.isValid()){
                try {
                    lock.release();
                }
                catch(IOException ex){
                    Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                    throw new PlanException(PlanException.ERR_IO_ERROR);
                }
            }
        }

        return nextPending;
    }

    /**
     * Creates the process planFile, so that {@link Worker}s may retrieve pending {@link Task}s
     * later.
     * Tries to read the process description XML file and create the to-do file according to
     * it, using DAG and parallelizations (up to the specified number of {@Worker}s for all
     * instances of the {@link Process}.
     *
     * TODO possibly check conformity of Task classes upon plan creation ?
     *
     * @param planFileIO the to-do file, locked and opened for writing
     * @throws SAXException if the input XML file is invalid
     * @throws IOException if there are some I/O problems with the file
     * @throws DataException if there are some illogical event dependencies
     */
    private void createPlan(RandomAccessFile planFileIO) throws SAXException, IOException, DataException {

        Process process = Process.getInstance();
        XMLReader parser;
        ScenarioParser dataCollector = new ScenarioParser(process.getInputFile());
        Vector<TaskDescription> plan;

        // parse the input file
        parser = XMLReaderFactory.createXMLReader();
        parser.setContentHandler(dataCollector);
        parser.parse(process.getInputFile());
        
        // set up the dependencies according to the inputs and outputs of data sets/features
        // which are stored in dataCollector
        this.setDependencies(dataCollector);

        // topologically sort the plan
        plan = dataCollector.tasks;
        this.sortPlan(plan);

        // write the plan into the plan file
        this.writePlan(plan, new FileOutputStream(planFileIO.getFD()));
    }

    /**
     * Reads the to-do file structure and retrieves the next pending {@link Task}, updating its
     * progress status in the plan file.
     *
     * @param planFileIO the to-do file, locked and opened for writing
     * @return the next pending task from the .todo file
     * @throws IOException if there are I/O problems with the plan file access
     * @throws ClassNotFoundException if there are problems with the plan file contents
     * @throws TaskException if there are problems with the task classes' descriptions
     * @throws SchedulingException if there are tasks waiting or in progress, but no pending ones
     */
    private Task getNextPendingTask(RandomAccessFile planFileIO) 
            throws IOException, ClassNotFoundException, TaskException, PlanException, SchedulingException {

        Vector<TaskDescription> plan = this.readPlan(new FileInputStream(planFileIO.getFD()));
        TaskDescription pendingDesc = null;
        boolean inProgress = false, waiting = false; // are there waiting tasks & tasks in progress ?

        // obtaining the task to be done: we are operating in the topological order
        for (TaskDescription task : plan){
            if (task.getStatus() == TaskStatus.IN_PROGRESS){
                waiting = true;
            }
            else if (task.getStatus() == TaskStatus.IN_PROGRESS){
                inProgress = true;
            }
            else if (task.getStatus() == TaskStatus.PENDING){
                pendingDesc = task;
                break;
            }
        }
        
        if (pendingDesc == null){
            // some tasks are in progress and some are waiting -> we have wait
            if (inProgress && waiting){
                throw new SchedulingException(SchedulingException.ERR_ALL_IN_PROGRESS);
            }
            // there are no pending tasks & no in progress or waiting - nothing to be done -> return
            return null;
        }
        // mark the task as "in progress"
        pendingDesc.setStatus(TaskStatus.IN_PROGRESS);
        // update the plan file
        this.writePlan(plan, new FileOutputStream(planFileIO.getFD()));

        return Task.createTask(pendingDesc);
    }

    /**
     * Set the dependencies according to features and data sets in the data itself and check them.
     * <p>
     * Check if all the data sets are correctly loaded or created in a {@link Manipulation} task.
     * All the features that are not computed are assumed to be contained in the input data sets.
     * All the files that are not written as output are assumed to exist before the {@link Process}
     * begins.
     * </p>
     *
     * @param plan the {@link TaskSection} as given by the {@link ScenarioParser}
     * @param parserOutput the {@link ScenarioParser} object <i>after</i> the parsing is finished
     */
    private void setDependencies(ScenarioParser parserOutput) throws DataException {

        // set the data set dependencies (check for non-created data sets)
        for (Occurrences oc : parserOutput.dataSetOccurrences.values()){
            if (oc.asOutput == null){
                throw new DataException(DataException.ERR_DATA_SET_NEVER_PRODUCED);
            }
            for (TaskDescription dep : oc.asInput){
                dep.setDependency(oc.asOutput);
            }
        }
        // set-up the file dependencies (no checks)
        for (Occurrences oc : parserOutput.fileOccurrences.values()){
            if (oc.asOutput == null){
                continue;
            }
            for (TaskDescription dep : oc.asInput){
                dep.setDependency(oc.asOutput);
            }
        }
        // set-up the feature-level dependencies (no checks)
        for (Occurrences oc : parserOutput.featureOccurrences.values()){
            if (oc.asInput == null){
                continue;
            }
            for (TaskDescription dep : oc.asInput){
                dep.setDependency(oc.asOutput);
            }
        }
    }

    /**
     * Writes the current plan status into the plan file, using serialization. Closes the output stream.
     * @param plan the current plan status
     * @param planFile the file to write to (an open output stream)
     */
    private void writePlan(Vector<TaskDescription> plan, FileOutputStream planFile) throws IOException {

        ObjectOutputStream oos = new ObjectOutputStream(planFile);

        oos.writeObject(plan);
        oos.flush();
        oos.close();
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
            task.setOrder(sorted.size());
            sorted.add(task);

            for (TaskDescription depTask : task.getDependent()){

                if (depTask.allPrerequisitiesSorted()){
                    independent.add(depTask);
                }
            }
        }

        // check if we found all tasks - if not, the plan has loops, which is prohibited
        for (TaskDescription task : sorted){
            if (task.getOrder() < 0){
                throw new DataException(DataException.ERR_LOOP_DEPENDENCY);
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
    private Vector<TaskDescription> readPlan(FileInputStream planFile) throws IOException, ClassNotFoundException {

        ObjectInputStream ois = new ObjectInputStream(planFile);
        Vector<TaskDescription> plan = (Vector<TaskDescription>) ois.readObject();
        ois.close();

        return plan;
    }

    /**
     * Updates the status of this task (all the dependent tasks, accordingly).
     * @param id the id of the updated task
     * @param status the new task status
     */
    public synchronized void updateTaskStatus(String id, TaskStatus status) throws PlanException {

        FileLock lock = null;

        try {
            // lock the plan file
            RandomAccessFile planFileIO = new RandomAccessFile(this.planFile, "rw");
            lock = planFileIO.getChannel().lock();

            // obtain the plan
            Vector<TaskDescription> plan = this.readPlan(new FileInputStream(planFileIO.getFD()));

            // update the statuses
            this.updateTaskStatus(plan, id, status);
            
            // write the plan back
            this.writePlan(plan, new FileOutputStream(planFileIO.getFD()));
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
            // release lock
            if (lock != null && lock.isValid()){
                try {
                    lock.release();
                }
                catch(IOException ex){
                    Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                    throw new PlanException(PlanException.ERR_IO_ERROR);
                }
            }
        }

    }

    /**
     * This finds the given task and updates its status and the statuses of all depending tasks. Throws an exception
     * if the Task cannot be found in the plan.
     *
     * @param plan the process plan
     * @param id the id of the task to be updated
     * @param taskStatus the new task status
     * @throws PlanException if the task of the given id cannot be found
     */
    private void updateTaskStatus(Vector<TaskDescription> plan, String id, TaskStatus taskStatus) throws PlanException {
        
        TaskDescription updatedTask = null;

        // find the task
        for (TaskDescription td : plan){

            if (td.getId().equals(id)){
                updatedTask = td;
                break;
            }
        }
        if (updatedTask == null){
            throw new PlanException(PlanException.ERR_INVALID_PLAN);
        }

        // update the task
        updatedTask.setStatus(taskStatus);
    }

}