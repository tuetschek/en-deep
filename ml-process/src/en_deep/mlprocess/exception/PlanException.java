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

package en_deep.mlprocess.exception;

/**
 * An exception that is thrown if there's something unusual or wrong with the Task plan.
 * @author Ondrej Dusek
 */
public class PlanException extends GeneralException {

    /* CONSTANTS */

    /** Exception code: Something's wrong in the scenario file. */
    public static final int ERR_INVALID_SCENARIO = 1;
    /** Exception code: Cannot access the scenario or plan file. */
    public static final int ERR_IO_ERROR = 2;
    /** Exception code: Something's wrong with the plan file */
    public static final int ERR_INVALID_PLAN = 3;
    /** Exception code: the process has been interrupted, end immediately */
    public static final int INTERRUPT = 4;

    /* METHODS */

    /**
     * Creates a new {@link DataException}, given the exception code.
     * @param code
     */
    public PlanException(int code){
        super(code);
    }


    @Override
    public String getMessage() {
        switch(this.code){
            case ERR_IO_ERROR:
                return "Cannot access the scenario or plan file.";
            case ERR_INVALID_SCENARIO:
                return "Something's wrong in the scenario file.";
            case ERR_INVALID_PLAN:
                return "Something's wrong with the plan file.";
            case INTERRUPT:
                return "The process has been interrupted by user.";
            default:
                return "Unknown error.";
        }
    }

}
