/*
 *  Copyright (c) 2011 Ondrej Dusek
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

package en_deep.mlprocess.manipulation.wekaclassifier;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.genfeat.Feature;
import java.util.ArrayList;
import java.util.Enumeration;
import weka.core.Attribute;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Reorder;

public class TreeReader {

    /* CONSTANT */

    /** Binary mode name */
    private static final String BINARY = "bin";

    /** Index of the sentence id parameter in the {@link #idxData} member */
    private static final int SENTID_IDX = 0;
    /** Index of the word id parameter in the {@link #idxData} member */
    private static final int WORDID_IDX = 1;
    /** Index of the syntactic head parameter in the {@link #idxData} member */
    private static final int HEAD_IDX = 2;

    /** Possible fillers for missing nominal values */
    private static final String [] FILLERS = { "[OTHER]", "" };

    /** Multiple valued features: separator for the individual values */
    private static final String SEP = Feature.SEP;

    /* DATA */

    /** The current task id */
    private String taskId;

    /** The data set (original) */
    private Instances origData;
    /** The index data set (just word id, sentence id, head) - in case they get ignored */
    private Instances idxData;

    /** Are we working with binary attributes ? */
    private boolean binMode;

    /** The current instance node */
    private Node curNode;
    /** The current root */
    private Node curRoot;

    /** Start of the current sentence in the data file */
    private int curSentBase;
    /** Length of the current sentence in the data file */
    private int curSentLen;

    /** Name of the attribute with the head of the current node's class value */
    private String headClass;
    /** Name of the attribute with the left sibling of the current node's class value */
    private String leftClass;
    /** Name of the attribute with class values of all the current node left siblings */
    private String leftClasses;

    /* METHODS */

    /**
     * Given the ARFF data file and a parameter string from the {@link Task} parameters,
     * this creates a new {@link TreeReader}.
     * <p>
     * The format of the class parameters is as follows:
     * </p>
     * <pre>
     * mode wordId sentId head headClass leftClass leftClasses
     * </pre>
     * Where:
     * <ul>
     * <li><tt>mode</tt> is <tt>bin</tt> or <tt>nom</tt>, i.e. working with binary or nominal attributes</li>
     * <li><tt>wordId</tt> is the word id attribute name</li>
     * <li><tt>sentId</tt> is the sentence id attribute name</li>
     * <li><tt>headClass</tt> is the attribute name for the class value of the head of the current node</li>
     * <li><tt>leftClass</tt> is the attribute name for the class value of the nearest left sibling of the current node</li>
     * <li><tt>leftClasses</tt> is the attribute name for the class values all left siblings of the current node</li>
     * </ul>
     *
     * @param taskId the current task id
     * @param data the input ARFF data file
     * @param params parameters: format -- mode wordId sentId head headClass leftClass leftClasses
     */
    TreeReader(String taskId, Instances data, String params) throws TaskException, Exception {

        this.taskId = taskId;
        String [] paramArr = params.split("\\s+");

        if (paramArr.length != 6){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.taskId, "Invalid parameters for a TreeReader class!");
        }

        this.binMode = paramArr[0].equals(BINARY);

        this.origData = data;
        this.idxData = new Instances(data);
        this.filterIdxData(paramArr[1], paramArr[2], paramArr[3]);

        this.headClass = paramArr[4];
        this.leftClass = paramArr[5];
        this.leftClasses = paramArr[6];
    }

    /**
     * Returns the instance number of the root node for the next tree. Creates a tree-like structure
     * in {@link #curRoot} to be browsed by all the further calls to {@link #getNextNode()}.
     * 
     * @return the instance number of the root node for the next tree
     */
    int getNextTree(){
        
        this.curSentBase += this.curSentLen; // first call: 0 + 0 = 0 and the further search begins

        if (this.curSentBase > this.idxData.numInstances()){
            return -1;
        }

        this.curSentLen = 0;
        int curSentId = (int) this.idxData.get(this.curSentBase).value(SENTID_IDX);
        int root = -1;

        while ((int) this.idxData.get(this.curSentBase + this.curSentLen).value(SENTID_IDX) == curSentId){
            if (this.idxData.get(this.curSentBase + this.curSentLen).value(HEAD_IDX) == 0){
                root = this.curSentBase + this.curSentLen;
            }
            this.curSentLen++;
        }

        this.curRoot = this.exploreSubtree(root, null);
        this.curNode = null;
        return root;
    }

    /**
     * This creates a new node and explores its whole subtree recursively.
     * @param inst the input data instance whose subtree is to be explored
     * @param head the syntactic head of the current node
     * @return the whole subtree of the newly created node
     */
    Node exploreSubtree(int inst, Node head){

        Node n = new Node(inst);

        n.head = head;
        n.subtreeLast = n;
        
        int [] childrenInst = this.getChildren(n.instance);
        if (childrenInst != null){

            n.children = new Node [childrenInst.length];

            for (int i = 0; i < childrenInst.length; ++i){
                n.children[i] = exploreSubtree(childrenInst[i], n);
                if (i > 0){
                    n.children[i-1].subtreeLast.next = n.children[i];
                    n.children[i-1].rightSibling = n.children[i];
                    n.children[i].leftSibling = n.children[i-1];
                }
            }
            n.next = n.children[0];
            n.subtreeLast = n.children[childrenInst.length-1].subtreeLast;
        }

        return n;
    }

    /**
     * Returns the instance numbers of all syntactical children of the given node.
     * @param instance the instance number of the desired node
     * @return the instance numbers of all its children
     */
    private int[] getChildren(int instance) {
        
        ArrayList<Integer> children = new ArrayList<Integer>();
        int [] childrenArr;

        for (int i = 0; i < this.curSentLen; ++i){
            if (this.idxData.get(this.curSentBase + i).value(HEAD_IDX) == this.idxData.get(instance).value(WORDID_IDX)){
                children.add(this.curSentBase + i);
            }
        }
        childrenArr = new int[children.size()];
        for (int i = 0; i < children.size(); ++i){
            childrenArr[i] = children.get(i);
        }
        return childrenArr;
    }

    /**
     * Returns the instance number of the next node (in DFS).
     * @return the instance number of the next node (in DFS).
     */
    int getNextNode(){

        // the very first call
        if (this.curNode == null){
            this.curNode = this.curRoot;
        }
        // usual case
        else {
            this.curNode = this.curNode.next;
        }

        // end of a tree -- move to next one if possible
        if (this.curNode == null){
            if (this.getNextTree() >= 0){
                this.curNode = this.curRoot;
            }
            else {
                return -1;
            }
        }
        return this.curNode.instance;
    }

    /**
     * Propagates class value to all children and right siblings of the current node.
     * @param value the class value to be propagated
     */
    void classToCurrentNeighborhood(String value){

        if (this.binMode){
            for (Node child : this.curNode.children){
                this.setBinaryValue(child.instance, this.headClass, value);
            }
            if (this.curNode.rightSibling != null){
                
                Node right = this.curNode.rightSibling;
                this.setBinaryValue(right.instance, this.leftClass, value);

                while (right != null){
                    this.addBinaryValue(right.instance, this.leftClasses, value);
                    right = right.rightSibling;
                }
            }
        }
        else {
            for (Node child : this.curNode.children){
                this.setNominalValue(child.instance, this.origData.attribute(this.headClass), value);
            }
            if (this.curNode.rightSibling != null){
                Node right = this.curNode.rightSibling;
                this.setNominalValue(this.curNode.rightSibling.instance, this.origData.attribute(this.leftClass), value);

                while (right != null){
                    this.addNominalValue(right.instance, this.origData.attribute(this.leftClasses), value);
                    right = right.rightSibling;
                }
            }
        }
    }

    /**
     * This takes the {@link #idxData} and filters out all unnecessary attributes, leaving only
     * those specified in parameters.
     * @param wordId name of the word id parameter
     * @param sentId name of the sentence id parameter
     * @param head name of the syntactic head parameter
     */
    private void filterIdxData(String wordId, String sentId, String head) throws TaskException, Exception {

        if (this.idxData.attribute(wordId) == null || this.idxData.attribute(sentId) == null
                || this.idxData.attribute(head) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.taskId, "TreeReader:"
                    + "wordId, sentId or head not found in the ARFF data");
        }

        int wordIdOrd = this.idxData.attribute(wordId).index();
        int sentIdOrd = this.idxData.attribute(sentId).index();
        int headOrd = this.idxData.attribute(head).index();

        Reorder filter = new Reorder();
        filter.setAttributeIndices((wordIdOrd + 1) + "," + (sentIdOrd + 1) + "," + (headOrd + 1));
        this.idxData = Filter.useFilter(this.idxData, filter);
    }

    /**
     * Sets a nominal value of a given attribute for the given instance. If the nominal value is not found in
     * the list of possible values and a current value is missing, it is replaced by {@link #FILLERS}.
     *
     * @param instanceNo number of the instance in question
     * @param attr the attribute to be set
     * @param value the value to be set
     */
    private void setNominalValue(int instanceNo, Attribute attr, String value) {

        try {
            this.origData.get(instanceNo).setValue(attr, value);
        }
        catch (IllegalArgumentException e){

            if (this.origData.get(instanceNo).isMissing(attr)){
                Logger.getInstance().message("Instance " + instanceNo + ": value " + value + " missing in attribute "
                        + attr.name() + ", replacing with a filler.", Logger.V_INFO);

                int filler = -1;
                int i = 0;

                while (filler == -1 && i < FILLERS.length){
                    filler = attr.indexOfValue(FILLERS[i++]);
                }
                if (filler != -1){
                    this.origData.get(instanceNo).setValue(attr, filler);
                }
            }
        }
    }

    /**
     * Sets a binary value for a given original nominal attribute name. Sets all other possible values
     * of this original attribute to zero. If the given nominal value is not found, it is replaced by {@link #FILLERS}.
     *
     * @param instanceNo number of the instance in question
     * @param attrName the name of the original nominal attribute (now expected to be binarized)
     * @param value the value of the original nominal attribute (now expected to be one of the attributes with
     *  the name <tt>attrName=value</tt>)
     */
    private void setBinaryValue(int instanceNo, String attrName, String value) {

        Attribute attr;

        this.makeZero(instanceNo, attrName + "=");

        if ((attr = this.origData.attribute(attrName + "=" + value)) != null){
            this.origData.get(instanceNo).setValue(attr, 1);
        }
        else {
            Logger.getInstance().message("Instance " + instanceNo + ": value " + value + " missing in attribute "
                    + attrName + ", replacing with a filler.", Logger.V_INFO);

            int i = 0;
            while (attr == null && i < FILLERS.length){
                attr = this.origData.attribute(attrName + "=" + FILLERS[i++]);
            }
            if (attr != null){
                this.origData.get(instanceNo).setValue(attr, 1);
            }
        }
    }

    /**
     * Sets a binary value for the given nominal attribute set. All possible values of this set will be zeroed.
     * If the given nominal value is not found, nothing is set.
     * 
     * @param instanceNo number of the instance in question
     * @param attrName the name of the original nominal set attribute (now expected to be binarized)
     * @param value a member of the original nominal set attribute (now expected to be one of the attributes with
     *  the name <tt>attrName&gt;value</tt>)
     */
    private void addBinaryValue(int instanceNo, String attrName, String value) {

        Attribute attr;

        this.makeZero(instanceNo, attrName + ">");

        if ((attr = this.origData.attribute(attrName + ">" + value)) != null){
            this.origData.get(instanceNo).setValue(attr, 1);
        }
    }

    /**
     * Adds a value to the set for a nominal set attribute. If the combination of the current value + the
     * new value is not in the list of possible values, nothing is done.
     *
     * @param instanceNo number of the instance in question
     * @param attr the nominal set attribute to be modified
     * @param addedValue the new value to be added to the set
     */
    private void addNominalValue(int instanceNo, Attribute attr, String addedValue) {

        String origValue = this.origData.get(instanceNo).stringValue(attr);

        // value is just a filler -> keep it out
        if (origValue != null){
            for (String filler : FILLERS){
                if (origValue.equals(filler)){
                    this.origData.get(instanceNo).setMissing(attr);
                    origValue = null;
                    break;
                }
            }
        }

        // first value in the set
        if (origValue == null){
            this.setNominalValue(instanceNo, attr, addedValue);
        }
        // further values
        else {
            this.setNominalValue(instanceNo, attr, origValue + SEP + addedValue);
        }
    }

    /**
     * Set all attributes with the given prefix to zero, if their values are missing for the
     * given instance.
     * @param instanceNo the number of the instance in question
     * @param prefix the prefix for attribute names
     */
    private void makeZero(int instanceNo, String prefix) {

        Enumeration<Attribute> attribs = this.origData.enumerateAttributes();
        while (attribs.hasMoreElements()){
            Attribute attrib = attribs.nextElement();
            
            if (attrib.name().startsWith(prefix) && this.origData.get(instanceNo).isMissing(attrib)){
                this.origData.get(instanceNo).setValue(attrib, 0);
            }
        }
    }


    /**
     * Tree representation of sentence, used in DFS searches.
     */
    private class Node {

        /** Syntactic head of the current node */
        Node head;
        /** Syntactic children of the current node */
        Node [] children;
        /** The last node in DFS ordering in the subtree of the current node */
        Node subtreeLast;
        /** The next node in DFS ordering for the current tree */
        Node next;
        /** The nearest left sibling of the current node */
        Node leftSibling;
        /** The nearest right sibling of the current node */
        Node rightSibling;

        /** Instance number corresponding to the current node */
        int instance;

        /**
         * Create a node for the given instance
         * @param instance the instance number for the current node
         */
        private Node(int instance) {
            this.instance = instance;
        }
    }
}

