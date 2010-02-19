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
 * An exception that is thrown for invalid data in the input scenario file.
 * @author Ondrej Dusek
 */
public class DataException extends GeneralException {

    /* CONSTANTS */

    /** Error code: "Task end expected" */
    public static final int ERR_END_EXPECTED = 1;
    /** Error code: "Beginning of a task description expected" */
    public static final int ERR_TASK_EXPECTED = 2;
    /** Error code: "Invalid clause in a task description" */
    public static final int ERR_UNKNOWN_CLAUSE = 3;
    /** Error code: "Duplicate clause in a task description" */
    public static final int ERR_DUPLICATE_CLAUSE = 4;
    /** Error code: "Missing clause in a task description" */
    public static final int ERR_MISSING_CLAUSE = 5;
    /** Error code: "Duplicate usage of a file as an output" */
    public static final int ERR_DUPLICATE_OUTPUT = 6;
    /** Error code: "Invalid character in a file name" */
    public static final int ERR_INVALID_CHAR_IN_FILE_NAME = 7;
    /** Error code: "Invalid character in parameters description" */
    public static final int ERR_INVALID_CHAR_IN_PARAMETER = 8;

    /* DATA */

    /** The file in which the error occured */
    private String fileName;
    /** Line at which the error occurred */
    private int line;

    /* METHODS */

    /**
     * Creates a new {@link DataException}, given the exception code.
     * @param code
     */
    public DataException(int code, String fileName, int line){
        super(code);
        this.fileName = fileName;
        this.line = line;
    }


    @Override
    public String getMessage() {

        String errMsg = null;

        switch(this.code){
            case ERR_OK:
                errMsg = "No error";
                break;
            case ERR_END_EXPECTED:
                errMsg = "Task end expected";
                break;
            case ERR_TASK_EXPECTED:
                errMsg = "Beginning of a task description expected";
                break;
            case ERR_UNKNOWN_CLAUSE:
                errMsg = "Invalid clause in a task description";
                break;
            case ERR_DUPLICATE_CLAUSE:
                errMsg = "Duplicate clause in a task description";
                break;
            case ERR_MISSING_CLAUSE:
                errMsg = "Missing clause in a task description";
                break;
            case ERR_DUPLICATE_OUTPUT:
                errMsg = "Duplicate usage of a file as an output";
                break;
            case ERR_INVALID_CHAR_IN_FILE_NAME:
                errMsg = "Invalid character in a file name";
                break;
            case ERR_INVALID_CHAR_IN_PARAMETER:
                errMsg = "Invalid character in parameters description";
                break;
            default:
                errMsg = "Unknown error";
                break;
        }
        return errMsg + " in " + this.fileName + ", line " + this.line + ".";
    }

}
