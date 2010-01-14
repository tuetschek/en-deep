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
 * An exception that is thrown for invalid data in the input XML file.
 * @author Ondrej Dusek
 */
public class DataException extends GeneralException {

    /* CONSTANTS */

    /** Exception code: Invalid ID of a {@link Task} in the scenario XML file */
    public static final int ERR_INVALID_ID = 1;
    /** Exception code: {@link Mainpulation} {@link Task}s cannot be parallelized */
    public static final int ERR_CANNOT_PARALELIZE_MANIPULATION = 2;
    /** Exception code: Duplicate algorithm setting */
    public static final int ERR_ALGORITHM_ALREADY_SET = 3;
    /** Exception code: "There are no training data set for Computation task" */
    public static final int ERR_NO_TRAIN_SET = 4;
    /** Exception code: "Input or output has not been set for Task" */
    public static final int ERR_NO_IN_OR_OUT = 5;
    /** Exception code: "Working Data Set has not been set for Evaluation task" */
    public static final int ERR_NO_DATA_SET = 6;
    /** Exception code: "There is no output file and no eval data set for Computation task" */
    public static final int ERR_NO_EVAL_SET = 7;
    /** Exception code: "The numbers of training, evaluation and development data sets don't match" */
    public static final int ERR_NO_MATCHING_DATA_NUMBERS = 8;
    /** Exception code: "Cannot open a data sources section within an another one" */
    public static final int ERR_NESTED_DATA_SECTIONS = 9;
    /** Exception code: "An invalid section or data type has been encountered" */
    public static final int ERR_INVALID_DATA_TYPE = 10;
    /** Exception code: "Cannot end Task within an open data sources section" */
    public static final int ERR_OPEN_DATA_SECTION = 11;
    /** Exception code: "Cannot close this type of data sources section here" */
    public static final int ERR_INVALID_DATA_SECTION_CLOSE = 12;
    /** Exception code: "The algorithm type does not match the task type" */
    public static final int ERR_INVALID_ALGORITHM_TYPE = 13;
    /** Exception code: "A data source is marked as being produced by two or more Tasks" */
    public static final int ERR_DUPLICATE_OUTPUT = 14;
    /** Exception code: "A used data set is never produced by a Task" - TODO add parameter to certain types of DataException ? */
    public static final int ERR_DATA_SET_NEVER_PRODUCED = 15;
    /** Exception code: "Input and output data sets overlap" */
    public static final int ERR_OVERLAPPING_INPUT_OUTPUT = 16;
    /** Exception code: "Tasks that operate on files cannot be parallelized" */
    public static final int ERR_CANNOT_PARALELIZE_ON_FILES = 17;
    /** Exception code: "There are loops in tasks dependencies - cannot sort" */
    public static final int ERR_LOOP_DEPENDENCY = 18;

    /* METHODS */

    /**
     * Creates a new {@link DataException}, given the exception code.
     * @param code
     */
    public DataException(int code){
        super(code);
    }


    @Override
    public String getMessage() {
        switch(this.code){
            case ERR_INVALID_ID:
                return "Invalid ID of a task in the scenario XML file";
            case ERR_OK:
                return "No error";
            case ERR_ALGORITHM_ALREADY_SET:
                return "Duplicate algorithm specification";
            case ERR_CANNOT_PARALELIZE_MANIPULATION:
                return "Manipulation tasks cannot be parallelized";
            case ERR_NO_TRAIN_SET:
                return "There are no training data set for Computation task";
            case ERR_NO_IN_OR_OUT:
                return "Input or output has not been set for Task";
            case ERR_NO_DATA_SET:
                return "Working Data Set has not been set for Evaluation task";
            case ERR_NO_EVAL_SET:
                return "There are no eval data set for Computation task";
            case ERR_NO_MATCHING_DATA_NUMBERS:
                return "The numbers of training, evaluation and development data sets don't match";
            case ERR_NESTED_DATA_SECTIONS:
                return "Cannot open a data sources section within an another one";
            case ERR_INVALID_DATA_TYPE:
                return "An invalid section or data type has been encountered";
            case ERR_OPEN_DATA_SECTION:
                return "Cannot end Task within an open data sources section";
            case ERR_INVALID_DATA_SECTION_CLOSE:
                return "Cannot close this type of data sources section here";
            case ERR_INVALID_ALGORITHM_TYPE:
                return "The algorithm type does not match the task type";
            case ERR_DUPLICATE_OUTPUT:
                return "A data source is marked as being produced by two or more Tasks";
            case ERR_DATA_SET_NEVER_PRODUCED:
                return "A used data set is never produced by a Task";
            case ERR_OVERLAPPING_INPUT_OUTPUT:
                return "Input and output data sets overlap";
            case ERR_CANNOT_PARALELIZE_ON_FILES:
                return "Tasks that operate on files cannot be parallelized";
            case ERR_LOOP_DEPENDENCY:
                return "There are loops in tasks dependencies - cannot sort";
            default:
                return "Unknown error";
        }
    }

}
