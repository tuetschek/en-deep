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
import java.util.Vector;



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
        RandomAccessFile planFileIO = null;
        
        // try to acquire lock on the to-do file and get a planned task
        try {
            planFileIO = new RandomAccessFile(this.planFile, "rw");
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
            // close the to-do file
            try {
                planFileIO.close();
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

        Process process = Process.getInstance();
        ScenarioParser parser = new ScenarioParser(process.getInputFile());
        Vector<TaskDescription> plan;

        // parse the input file
        parser.parse();
        
        // topologically sort the plan
        plan = parser.getTasks();
        this.sortPlan(plan);

        // write the plan into the plan file
        this.writePlan(plan, planFileIO);
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
    private synchronized Task getNextPendingTask(RandomAccessFile planFileIO)
            throws IOException, ClassNotFoundException, TaskException, PlanException, SchedulingException {

        Vector<TaskDescription> plan = this.readPlan(planFileIO);
        TaskDescription pendingDesc = null;
        boolean inProgress = false, waiting = false; // are there waiting tasks & tasks in progress ?
        int pos;

        // obtaining the task to be done: we are operating in the topological order
        for (TaskDescription task : plan){
            if (task.getStatus() == TaskStatus.WAITING){
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

        // expand the task (and possibly dependent tasks) accoring to "*"'s in input / output file names
        TaskExpander te = new TaskExpander(pendingDesc);
        te.expand();
        plan.addAll(pos = plan.indexOf(pendingDesc), te.getTasksToAdd()); // these well may be empty
        plan.removeAll(te.getTasksToRemove());

        pendingDesc = plan.get(pos); // the first expanded task
        
        // mark the task as "in progress"
        pendingDesc.setStatus(TaskStatus.IN_PROGRESS);
        // update the plan file
        this.writePlan(plan, planFileIO);

        return Task.createTask(pendingDesc);
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

        FileOutputStream debugOs = new FileOutputStream(Process.getInstance().getInputFile() + ".status", false);
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
     * Updates the status of this task (all the dependent tasks, accordingly).
     * @param id the id of the updated task
     * @param status the new task status
     */
    public synchronized void updateTaskStatus(String id, TaskStatus status) throws PlanException {

        FileLock lock = null;
        RandomAccessFile planFileIO = null;

        try {
            // lock the plan file
            planFileIO = new RandomAccessFile(this.planFile, "rw");
            lock = planFileIO.getChannel().lock();

            // obtain the plan
            Vector<TaskDescription> plan = this.readPlan(planFileIO);

            // update the statuses
            this.updateTaskStatus(plan, id, status);
            
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
