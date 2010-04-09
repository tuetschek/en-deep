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

package en_deep.mlprocess.manipulation.genfeat;

import en_deep.mlprocess.manipulation.StToArff;
import en_deep.mlprocess.manipulation.StToArff.StToArffConfig;
import java.util.Vector;

/**
 * This feature contains the complete path in DEPREL and POS values from the argument to the predicate.
 * It is composed of the DEPREL/POS values + / and \ as "up" and "down", or a single "+" for
 * the argument being the predicate itself.
 * @author Ondrej Dusek
 */
public class DepPath extends Feature {

    public DepPath(StToArffConfig config) {
        super(config);
    }

    @Override
    public String getHeader() {
        return StToArff.ATTRIBUTE + " DepPathRel " + StToArff.STRING + "\n"
                + StToArff.ATTRIBUTE + " DepPathPOS " + StToArff.STRING + "\n"
                + StToArff.ATTRIBUTE + " DepPathLength " + StToArff.INTEGER;
    }

    @Override
    public String generate(Vector<String[]> sentence, int wordNo, int predNo) {

        int [] pathBack = new int [sentence.size()];
        int curPos = predNo + 1;
        StringBuilder pathRel = new StringBuilder(), pathPos = new StringBuilder();
        int pathLength = 0;

        while(curPos > 0){ // find the way from the predicate to the root
            int head = Integer.parseInt(sentence.get(curPos - 1)[this.config.IDXI_HEAD]);
            pathBack[head-1] = curPos;

            curPos = head;
        }

        // find the way up from the argument to the predicate-root path
        curPos = wordNo + 1;
        while (curPos != 0 && pathBack[curPos-1] == 0){
            int head = Integer.parseInt(sentence.get(curPos - 1)[this.config.IDXI_HEAD]);

            pathRel.append("/" + sentence.get(curPos - 1)[this.config.IDXI_DEPREL]);
            pathPos.append("/" + sentence.get(curPos - 1)[this.config.IDXI_POS]);
            curPos = head;
            pathLength++;
        }

        if (curPos != predNo + 1){
            if (curPos != wordNo + 1){ // end the way up
                pathRel.append("/" + sentence.get(curPos - 1)[this.config.IDXI_DEPREL]);
                pathPos.append("/" + sentence.get(curPos - 1)[this.config.IDXI_POS]);
                pathLength++;
            }
            curPos = pathBack[curPos-1];
            // follow the predicate-root path down to the predicate
            while (curPos != 0 && curPos != predNo + 1){
                pathRel.append("\\" + sentence.get(curPos - 1)[this.config.IDXI_DEPREL]);
                pathPos.append("\\" + sentence.get(curPos - 1)[this.config.IDXI_POS]);
                curPos = pathBack[curPos-1];
                pathLength++;
            }
            pathRel.append("\\");
            pathPos.append("\\");
        }
        else { // the way is up only - end it
            pathRel.append("/");
            pathPos.append("/");
        }

        return "\"" + pathRel.toString() + "\",\"" + pathPos.toString() + "\"," + Integer.toString(pathLength);
    }

}
