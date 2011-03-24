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
 * Exception to be thrown for incorrect program command parameters.
 * @author Ondrej Dusek
 */
public class ParamException extends GeneralException {

    /* CONSTANTS */

    /** Error code: invalid command parameter */
    public static final int ERR_INVPAR = 1;
    /** Error code: argument of a command parameter must be numeric */
    public static final int ERR_NONNUMARG = 2;
    /** Error code: invalid argument of a command parameter */
    public static final int ERR_INVARG = 3;
    /** Error code: missing command parameter */
    public static final int ERR_MISSING = 4;
    /** Error code: too many arguments (argument name not needed) */
    public static final int ERR_TOO_MANY = 5;
    /** Error code: The input file cannot be found. */
    public static final int ERR_FILE_NOT_FOUND = 6;
    /** Error code: The working directory cannot be found */
    public static final int ERR_DIR_NOT_FOUND = 7;

    /* DATA */

    /** Name of the command parameter which triggered the exception */
    private String parName;

    /* METHODS */

    /**
     * Creates a new ParamException, given the command parameter
     * and the error type.
     * @param code the error type
     * @param parName the name of the paramater that triggered the exception
     */
    public ParamException(int code, String parName){
        
        super(code);        
        this.parName = parName;
    }

    /**
     * Creates a new ParamException with empty parameter name. Should not be used
     * with errors that concern a specific argument.
     *
     * @param code the error code of the exception (should be {@link ParamException#ERR_OK} or {@link ParamException#ERR_TOO_MANY}
     */
    public ParamException(int code){
        this(code, "");
    }


    @Override
    public String getMessage() {

        switch(this.code){
            case ERR_OK:
                return "No error.";
            case ERR_INVPAR:
                return "Invalid command parameter:" + this.parName;
            case ERR_NONNUMARG:
                return "Argument of a command parameter must be numeric:" + this.parName;
            case ERR_INVARG:
                return "Invalid argument of a command parameter:" + this.parName;
            case ERR_MISSING:
                return "Missing command parameter:" + this.parName;
            case ERR_TOO_MANY:
                return "Too many arguments.";
            case ERR_FILE_NOT_FOUND:
                return "The input file cannot be found.";
            case ERR_DIR_NOT_FOUND:
                return "The working directory cannot be found";
            default:
                return "Unknown error.";
        }
    }


}
