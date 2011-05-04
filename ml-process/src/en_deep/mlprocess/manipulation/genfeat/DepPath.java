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

import en_deep.mlprocess.manipulation.DataReader;
import en_deep.mlprocess.manipulation.DataReader.FeatType;
import en_deep.mlprocess.utils.StringUtils;
import java.util.Arrays;

/**
 * This feature contains the complete path in SYNT_REL POS, Coarse POS and direction values from the
 * argument candidate to the predicate, as well as the length of the path. It is composed of the SYNT_REL/POS
 * values + / and \ as "up" and "down", or a single "+" for the argument candidate being the predicate itself.
 * If the predicate and the argument are not in the same tree, "+++" is added to the path.
 *
 * @author Ondrej Dusek
 */
public class DepPath extends ParametrizedFeature {

    /* METHODS */

    public DepPath(DataReader reader) {
        
        super(reader, FeatType.SYNT);
    }

    @Override
    public String getHeader() {
        return this.getParametrizedHeader("DepPath", DataReader.STRING) + LF
                + DataReader.ATTRIBUTE + " DepPathDir " + DataReader.STRING + LF
                + DataReader.ATTRIBUTE + " DepPathLength " + DataReader.INTEGER;
    }

    @Override
    public String generate(int wordNo, int predNo) {

        int [] pathBack = new int [this.reader.getSentenceLength()];
        int curPos = predNo + 1; // current position
        String pos; // last used POS value, for producing the Coarse POSes
        StringBuilder [] paths = new StringBuilder [this.attrPos.length];
        StringBuilder pathDir = new StringBuilder();
        int pathLength = 0;
        int predRoot = -1;

        for (int i = 0; i < paths.length; ++i){
            paths[i] = new StringBuilder();
        }

        Arrays.fill(pathBack, -1);

        if (wordNo == predNo){ // special case -- argument == predicate
            return "\"+\",\"+\",\"+\",\"+\",0";
        }

        while(curPos > 0){ // find the way from the root to the predicate and store it in pathBack
            int head = this.reader.getHead(curPos - 1) + 1;

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
            int head = this.reader.getHead(curPos - 1) + 1;

            if (curPos != wordNo + 1){
                for (int i = 0; i < paths.length; ++i){
                    paths[i].append("/").append(this.reader.getWordInfo(curPos - 1, this.attrPos[i]));
                }
                pathDir.append("/");
            }
            curPos = head;
            pathLength++;
        }

        if (curPos != predNo + 1){

            if (curPos == 0){ // the sentence is not a tree and predicate and the given word are in separate trees
                for (int i = 0; i < paths.length; ++i){
                    paths[i].append("/+++");
                }
                pathDir.append("/+++");
                curPos = predRoot;
                pathLength += 100;
            }
            else {
                if (curPos != wordNo + 1){ // end the way up
                    for (int i = 0; i < paths.length; ++i){
                        paths[i].append("/").append(this.reader.getWordInfo(curPos - 1, this.attrPos[i]));
                    }
                    pathDir.append("/");
                    pathLength++;
                }
                curPos = pathBack[curPos-1];
            }
            // follow the predicate-root path down to the predicate
            while (curPos != 0 && curPos != predNo + 1){
                for (int i = 0; i < paths.length; ++i){
                    paths[i].append("\\").append(this.reader.getWordInfo(curPos - 1, this.attrPos[i]));
                }
                pathDir.append("\\");
                curPos = pathBack[curPos-1];
                pathLength++;
            }
            for (int i = 0; i < paths.length; ++i){
                paths[i].append("\\");
            }
            pathDir.append("\\");
        }
        else { // the way is up only - end it
            for (int i = 0; i < paths.length; ++i){
                paths[i].append("/");
            }
            pathDir.append("/");
        }

        StringBuilder res = new StringBuilder();
        for (int i = 0; i < paths.length; ++i){
            res.append("\"").append(StringUtils.escape(paths[i].toString())).append("\",\"");
        }
        res.append(StringUtils.escape(pathDir.toString())).append("\",").append(Integer.toString(pathLength));
        return res.toString();
    }

}
