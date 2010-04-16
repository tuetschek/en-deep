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
 * This feature indicates several following relations:
 *  SiblChild - 0/1 - is the word a sibling or child of the predicate ?
 *  SyntDep - 0/1 - is the word syntactically dependent on the predicate ?
 * @author Ondrej Dusek
 */
public class SyntRel extends Feature {

    public SyntRel(StToArffConfig config) {
        super(config);
    }

    @Override
    public String getHeader() {
        return StToArff.ATTRIBUTE + " SiblChild " + StToArff.INTEGER + LF
                + StToArff.ATTRIBUTE + " SyntDep " + StToArff.INTEGER;
    }

    @Override
    public String generate(Vector<String[]> sentence, int wordNo, int predNo) {

        boolean siblChild = false;
        boolean syntDep = false;
        int curPos = wordNo + 1;

        while (curPos != 0){ // find out the syntactical dependency (a predicate doesn't depend on itself)
            curPos = Integer.parseInt(sentence.get(curPos - 1)[this.config.IDXI_HEAD]);
            if (curPos == predNo + 1){
                syntDep = true;
                break;
            }
        }

        // siblings (a predicate is it's own sibling)
        if (sentence.get(wordNo)[this.config.IDXI_HEAD].equals(sentence.get(predNo)[this.config.IDXI_HEAD])){
            siblChild = true;
        }
        // child
        else if (Integer.parseInt(sentence.get(wordNo)[this.config.IDXI_HEAD]) == predNo + 1) {
            siblChild = true;
        }

        return (siblChild ? "1" : "0") + "," + (syntDep ? "1" : "0");
    }



}
