/*
 *  Copyright (c) 2010 Ondrej Dusek
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

package en_deep.mlprocess.evaluation;

import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This makes a sum of all the evaluations from various files and computes the overall scores.
 * @author Ondrej Dusek
 */
public class SumEval extends Task {

    /**
     * This creates a new {@link SumEval} object. It just checks the inputs and outputs. There are
     * no parameters. The input must be one or more files, the output must be one file.
     * @param id
     * @param parameters
     * @param input
     * @param output
     * @throws en_deep.mlprocess.exception.TaskException
     */
    public SumEval(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException {
        super(id, parameters, input, output);

        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have some input.");
        }
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "There must be just one output.");
        }
    }

    @Override
    public void perform() throws TaskException {


        try {
            Vector<Stats> labelled = new Vector<Stats>(),
                    unlabelled = new Vector<Stats>();

            for (String file : this.input){ // read all partial statistics

                RandomAccessFile in = new RandomAccessFile(file, "r");

                labelled.add(new Stats(in.readLine()));
                unlabelled.add(new Stats(in.readLine()));

                in.close();

            }

            // sum them all
            Stats totalLabelled = new Stats();
            Stats totalUnlabelled = new Stats();

            for (int i = 0; i < labelled.size(); ++i){
                totalLabelled.add(labelled.get(i));
                totalUnlabelled.add(unlabelled.get(i));
            }

            // print it all to the output
            PrintStream out = new PrintStream(this.output.get(0));

            out.println(totalLabelled.toString());
            out.println(totalUnlabelled.toString());
            out.println("accuracy:" + totalLabelled.getAcc());
            out.println("labeled precision:" + totalLabelled.getPrec());
            out.println("labeled recall:" + totalLabelled.getRecall());
            out.println("labeled f1:" + totalLabelled.getF1());
            out.println("unlabeled precision:" + totalUnlabelled.getPrec());
            out.println("unlabeled recall:" + totalUnlabelled.getRecall());
            out.println("unlabeled f1:" + totalUnlabelled.getF1());

            out.close();

        }
        catch (IOException e){
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

}
