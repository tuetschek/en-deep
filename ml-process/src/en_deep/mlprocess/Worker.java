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
import en_deep.mlprocess.exception.PlanException;
import en_deep.mlprocess.exception.TaskException;
import java.net.InetAddress;
import java.net.UnknownHostException;


/**
 * One of the parallel threads, which keeps obtaining tasks from the process plan and
 * executing them.
 *
 * If there are no more tasks to be done, the Worker exits.
 * 
 * @author Ondrej Dusek
 */
public class Worker implements Runnable {

    /* DATA */
    
    /** The task currently in progress */
    private Task currentTask;
    
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

        Logger.getInstance().message("Worker thread " + this.id + "started.", Logger.V_INFO);

        try {
            // TODO add PlanException.ERR_ALL_IN_PROGRESS
            while ((this.currentTask = Plan.getInstance().getNextPendingTask()) != null){

                Logger.getInstance().message("Worker thread " + this.id + "working on task " + this.currentTask.getId(),
                        Logger.V_INFO);

                try {
                    this.currentTask.perform();
                }
                catch(TaskException ex){
                    Logger.getInstance().message(ex.getErrorMessage(), Logger.V_IMPORTANT);
                    Plan.getInstance().updateTaskStatus(this.currentTask.getId(), TaskStatus.FAILED);
                    continue;
                }

                Logger.getInstance().message("task " + this.currentTask.getId() + " finished.", Logger.V_INFO);
                Plan.getInstance().updateTaskStatus(this.currentTask.getId(), TaskStatus.DONE);
            }
        }
        catch(PlanException ex){
            // plan file related errors are critical - they result in Worker stopping
            Logger.getInstance().message("Plan access in worker thread #" + this.id + ":" + ex.getErrorMessage(),
                    Logger.V_IMPORTANT);
        }

        Logger.getInstance().message("Worker thread #" + this.id + "finished - nothing else to do.", Logger.V_INFO);
    }

}
