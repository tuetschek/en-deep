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
import en_deep.mlprocess.exception.PlanException;
import en_deep.mlprocess.exception.SchedulingException;
import en_deep.mlprocess.exception.TaskException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Vector;


/**
 * One of the parallel threads, which keeps obtaining tasks from the process plan and
 * executing them.
 *
 * If there are no more tasks to be done, the Worker exits.
 * 
 * @author Ondrej Dusek
 */
public class Worker implements Runnable {

    /* CONSTANTS */

    /** Base time to suspend the {@link Worker} for when there are no pending {@link Task}s */
    private static final int SUSPEND_TIME = 30000;

    /** Random time that is added to base suspend time */
    private static final int SUSPEND_RANDOM = 10000;


    /* DATA */
    
    /** The task currently in progress */
    private Vector<Task> currentTasks;
    
    /** {@link Worker} thread identification string */
    private String id;


    /* METHODS */

    /**
     * Creating a new {@link Worker} - just set the thread identification.
     * @param number the number of the worker on the current machine
     */
    public Worker(int number){

        try {
            this.id = number + "@" + InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException ex) {
            this.id = number + "@unknown";
        }
    }


    /**
     * The main {@link Worker} method - tries to get next pending tasks as long as there is something
     * to be done (and no critical error occurs), processing them and updating their status in
     * the {@link Plan}.
     */
    public void run() {

        Logger.getInstance().message("Worker thread " + this.id + " started.", Logger.V_INFO);

        try {
            while (this.waitForNextTasks()){

                long time = System.currentTimeMillis();
                Task task = null;
                int current = 0;

                try {
                    // perform all the retrieved tasks
                    for (; current < this.currentTasks.size(); ++current){
                        task = this.currentTasks.get(current);
                        Logger.getInstance().message("Worker thread " + this.id + " working on task " + task.getId(),
                                Logger.V_INFO);
                        task.perform();
                        time = System.currentTimeMillis() - time;
                        Logger.getInstance().message("task " + task.getId() + " finished in " + time/1000.0 + " secs.", Logger.V_INFO);
                    }

                    Plan.getInstance().updateStatuses(this.currentTasks, TaskStatus.DONE);
                }
                catch(TaskException ex){
                    Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                    // If there's an exception, set the done tasks to DONE, the bad task to FAILED and those not done back to PENDING
                    if (current > 0){
                        Plan.getInstance().updateStatuses(this.currentTasks.subList(0, current), TaskStatus.DONE);
                    }
                    Plan.getInstance().updateStatuses(this.currentTasks.subList(current, current + 1),
                            TaskStatus.FAILED);
                    if (current < this.currentTasks.size() - 1){
                        Plan.getInstance().updateStatuses(this.currentTasks.subList(current + 1, this.currentTasks.size()),
                                TaskStatus.PENDING);
                    }
                    continue;
                }
            }
        }
        catch(PlanException ex){
            // plan file related errors are critical - they result in Worker stopping
            Logger.getInstance().message("Plan access in worker thread #" + this.id + ": " + ex.getMessage(),
                    Logger.V_IMPORTANT);
        }

        Logger.getInstance().message("Worker thread #" + this.id + " finished - nothing else to do.", Logger.V_INFO);
    }

    /**
     * Tries to get the next pending task from the {@link Plan}, waits if there are dependent
     * {@link Task}s waiting to be done and all {@link Workers} are busy.
     *
     * If {@link Plan.getNextPendingTasks()} ends with a {@link SchedulingException}, tries to wait
     * and repeat the call. Stores the next task to be processed in the {@link currentTasks} member.
     * @return true if there is a task to process
     * @throws PlanException if there's something wrong with the plan
     */
    private boolean waitForNextTasks() throws PlanException {

        try {
            // try to get the next pending task to process
            this.currentTasks = Plan.getInstance().getNextPendingTasks();
        }
        catch(SchedulingException ex){
            
            int suspendTime = SUSPEND_TIME + (int) (Math.random() * SUSPEND_RANDOM);
            Logger.getInstance().message("Worker thread #" + this.id + "suspending for " + suspendTime + " msecs.",
                    Logger.V_DEBUG);

            // wait, if there's nothing to be processed
            try {
                Thread.sleep(suspendTime);
            }
            catch (InterruptedException ex1) {
            }
            // and try again after a while
            return waitForNextTasks();
        }
        return !currentTasks.isEmpty();
    }

}
