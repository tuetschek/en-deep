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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

/**
 * This component is responsible for building the plan of the whole computation,
 * according to the input scenario.
 *
 * @author Ondrej Dusek
 */
public class Plan {

    /* DATA */

    /** The plan file */
    private File plan;

    /** The only instance of {@link Plan}. */
    private static Plan instance;

    /* METHODS */

    /**
     * Creates a new instance of {@link Plan}. All objects should call
     * {@link Plan.getInstance()} to acquire an instance of {@link Plan}.
     */
    private Plan(){
        
        // open the plan file (do not require
        this.plan = new File(Process.getInstance().getInputFile() + ".todo");

        try {
            // this ensures we never have an exception, but the file may be empty
            plan.createNewFile();
        }
        catch(IOException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
        }
        
    }


    /**
     * Tries to get the next pending task from the to-do file. Locks the to-do file
     * to avoid concurrent access from within several instances. If the to-do file does
     * not exist or is empty, creates it and fills it with a plan.
     *
     * @return the next pending task to be done, or null if there are no tasks to be done (or an
     *         error occurred)
     */
    public synchronized Task getNextPendingTask(){

        FileLock lock = null;
        Task nextPending = null;
        
        // try to acquire lock on the to-do file and get a planned task
        try {
            RandomAccessFile planData = new RandomAccessFile(this.plan, "rw");
            lock = planData.getChannel().lock();

            if (planData.length() == 0){ // the plan file - the plan has not yet been created
                this.createPlan(planData);
            }

            // TODO samotne nacteni DAGu tasku ze souboru (hlavne jmen, spojnic a stavu provadeni)
            // nic jineho by asi nebylo potreba, kdyz se bude XML jen cist ... ale zas uz bude rozparsovane,
            // tak mozna lepsi tam nacpat vsechna metadata.
            nextPending = this.getNextPendingTask(planData);
        }
        catch(IOException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
            return null;
        }
        // always releas the lock on the to-do file
        finally {
            if (lock != null && lock.isValid()){
                try {
                    lock.release();
                }
                catch(IOException ex){
                    Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                    return null;
                }
            }
        }

        return nextPending;
    }

    /**
     * Creates the process plan, so that {@link Worker}s may retrieve pending {@link Task}s
     * later.
     * Tries to read the process description XML file and create the to-do file according to
     * it, using DAG and parallelizations (up to the specified number of {@Worker}s for all
     * instances of the {@link Process}.
     *
     * @param planData the to-do file, locked and opened for writing
     */
    private void createPlan(RandomAccessFile planData) {
        // TODO createPlan
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Reads the to-do file structure and retrieves the next pending {@link Task}, updating its
     * progress status.
     *
     * @param planData the to-do file, locked and opened for writing
     * @return the next pending task from the TODO file
     */
    private Task getNextPendingTask(RandomAccessFile planData) {
        // TODO
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
