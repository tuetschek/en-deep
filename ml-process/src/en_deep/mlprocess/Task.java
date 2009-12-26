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

import java.io.Serializable;

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

    /** The possible progress statuses of a {@link Task} */
    public enum TaskStatus {
        PENDING, IN_PROGRESS, DONE
    }



    /* DATA */

    /** The task's status */ 
    private TaskStatus status = TaskStatus.PENDING;

    /* METHODS */

    /** 
     * Checks the task's status.
     * 
     * @return  true if the task is done
     */
    public TaskStatus getStatus(){
        return this.status;
    }

    /**
     * Performs the given task.
     */
    public abstract void perform();

    // TODO melo by mit moznost oznacit task za hotovy primo v plan souboru, ale jak? nejakym unique id?
    // jinak nebude mozne zajistit dependence ~ prip. si delat serializaci sam, beztak jde jen o metadata :-P.
    // reprezentace DAGu pravdepodobne pomoci pointru na dalsi ??
}
