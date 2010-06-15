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
import en_deep.mlprocess.manipulation.StReader;
import en_deep.mlprocess.utils.StringUtils;
import java.util.Arrays;

/**
 * This feature contains the complete path in DEPREL and POS values from the argument candidate
 * to the predicate.
 * It is composed of the DEPREL/POS values + / and \ as "up" and "down", or a single "+" for
 * the argument candidate being the predicate itself.
 * @author Ondrej Dusek
 */
public class DepPath extends Feature {

    public DepPath(StReader reader) {
        super(reader);
    }

    @Override
    public String getHeader() {
        return StToArff.ATTRIBUTE + " DepPathRel " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " DepPathPOS " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " DepPathLength " + StToArff.INTEGER;
    }

    @Override
    public String generate(int wordNo, int predNo) {

        int [] pathBack = new int [this.reader.length()];
        int curPos = predNo + 1;
        StringBuilder pathRel = new StringBuilder(), pathPos = new StringBuilder();
        int pathLength = 0;
        int predRoot = -1;

        Arrays.fill(pathBack, -1);

        if (wordNo == predNo){ // special case -- argument == predicate
            return "\"+\",\"+\",0";
        }

        while(curPos > 0){ // find the way from the root to the predicate and store it in pathBack
            int head = Integer.parseInt(this.reader.getWordInfo(curPos - 1, this.reader.IDXI_HEAD));

            if (head > 0){
                pathBack[head-1] = curPos;
            }
            else { // store the root for the predicate
                predRoot = curPos;
            }
            curPos = head;
        }

        // find the way up from the argument to the predicate-root path
        curPos = wordNo + 1;
        while (curPos != 0 && curPos != predNo + 1 && pathBack[curPos-1] == -1){
            int head = Integer.parseInt(this.reader.getWordInfo(curPos - 1,this.reader.IDXI_HEAD));

            if (curPos != wordNo + 1){
                pathRel.append("/" + this.reader.getWordInfo(curPos - 1,this.reader.IDXI_DEPREL));
                pathPos.append("/" + this.reader.getWordInfo(curPos - 1, this.reader.IDXI_POS));
            }
            curPos = head;
            pathLength++;
        }

        if (curPos != predNo + 1){

            if (curPos == 0){ // the sentence is not a tree and predicate and the given word are in separate trees
                pathRel.append("/+++");
                pathPos.append("/+++");
                curPos = predRoot;
            }
            else {
                if (curPos != wordNo + 1){ // end the way up
                pathRel.append("/" + this.reader.getWordInfo(curPos - 1, this.reader.IDXI_DEPREL));
                pathPos.append("/" + this.reader.getWordInfo(curPos - 1, this.reader.IDXI_POS));
                pathLength++;
                }
                curPos = pathBack[curPos-1];
            }
            // follow the predicate-root path down to the predicate
            while (curPos != 0 && curPos != predNo + 1){
                pathRel.append("\\" + this.reader.getWordInfo(curPos - 1, this.reader.IDXI_DEPREL));
                pathPos.append("\\" + this.reader.getWordInfo(curPos - 1, this.reader.IDXI_POS));
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

        return "\"" + StringUtils.escape(pathRel.toString()) + "\",\""
                + StringUtils.escape(pathPos.toString()) + "\"," + Integer.toString(pathLength);
    }

}
