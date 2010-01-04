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

import en_deep.mlprocess.DataSourceDescription;
import en_deep.mlprocess.exception.TaskException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A general task to be computed or performed ~ specialized into {@link computation.Computation},
 * {@link manipulation.Manipulation} and {@link evaluation.Evaluation} classes.
 * 
 * @author Ondrej Dusek
 */
public abstract class Task implements Serializable {

    /* CONSTANTS */

    /** The possible types of {@link Task}s */
    public enum TaskType {
        COMPUTATION, MANIPULATION, EVALUATION
    }

    /**
     * The possible progress statuses of a {@link Task}.
     * <ul>
     * <li>WAITING = waiting for another {@link Task}(s) to finish</li>
     * <li>PENDING = ready to be processed</li>
     * <li>IN_PROGRESS = currently being processed</li>
     * <li>DONE = successfully finished</li>
     * <li>FAILED = finished with an error, this stops the processing of dependant tasks</li>
     * </ul>
     *
     * TODO move TaskStatus to TaskDescription ?
     */
    public enum TaskStatus {
        WAITING, PENDING, IN_PROGRESS, DONE, FAILED
    }


    /* DATA */



    /* METHODS */

    /**
     * Performs the given task.
     */
    public abstract void perform() throws TaskException;


    /**
     * This creates a {@link Task} object of the specified class for the given
     * {@link TaskDescription}.
     *
     * TODO possibly add default package for classes ?
     *
     * @param desc the description of the class, containing all the necessary parameters
     * @return the {@link Task} object that may be processed by the {@link Worker}s
     */
    static Task createTask(TaskDescription desc) throws TaskException {

        Task res = null;
        Class taskClass = null;
        Constructor taskConstructor = null;

        // retrieve the task class
        try {
            taskClass = Class.forName(desc.getAlgorithm().className);
        }
        catch (ClassNotFoundException ex) {
            throw new TaskException(TaskException.ERR_TASK_CLASS_NOT_FOUND, desc.getId());
        }

        // try to call a constructor with the given parameters
        try {
            switch (desc.getType()){
                case COMPUTATION:
                    // TODO constructor creation for Computation tasks
                    break;
                case MANIPULATION:
                    taskConstructor = taskClass.getConstructor(String.class, String.class, Vector.class, Vector.class);
                    res = (Task) taskConstructor.newInstance(desc.getId(), desc.getAlgorithm().parameters,
                            ((ManipulationDescription) desc).getInput(), ((ManipulationDescription) desc).getOutput());
                    break;
                case EVALUATION:
                    // TODO constructor creation for Evaluation tasks
                    break;
            }
        }
        catch(Exception ex){
            throw new TaskException(TaskException.ERR_TASK_CLASS_INCORRECT, desc.getId());
        }

        return res;
    }
}
