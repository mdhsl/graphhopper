/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage.index;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.AllEdgesSkipIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import gnu.trove.list.TIntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * As the graph is filled and prepared before the index is created we need to
 * ignore the introduced shortcuts e.g. for calculating closest edges.
 *
 * @author Peter Karich
 */
public class Location2NodesNtreeLG extends Location2NodesNtree {

    private final static EdgeFilter NO_SHORTCUT = new EdgeFilter() {
        @Override public boolean accept(EdgeIterator iter) {
            return !((EdgeSkipIterator) iter).isShortcut();
        }
    };
    private LevelGraph lg;

    public Location2NodesNtreeLG(LevelGraph g, Directory dir) {
        super(g, dir);
        lg = g;
    }

    @Override
    protected void sortNodes(TIntList nodes) {        
        // nodes with high level should come first to be covered by lower level nodes
        ArrayList<Integer> list = Helper.tIntListToArrayList(nodes);
        Collections.sort(list, new Comparator<Integer>() {
            @Override public int compare(Integer o1, Integer o2) {
                return lg.getLevel(o2) - lg.getLevel(o1);
            }
        });
        nodes.clear();
        nodes.addAll(list);
    }

    @Override
    protected AllEdgesIterator getAllEdges() {
        final AllEdgesSkipIterator tmpIter = lg.getAllEdges();
        return new AllEdgesIterator() {
            @Override public int maxId() {
                return tmpIter.maxId();
            }

            @Override public boolean next() {
                while (tmpIter.next()) {
                    if (!tmpIter.isShortcut())
                        return true;
                }
                return false;
            }

            @Override public int edge() {
                return tmpIter.edge();
            }

            @Override public int baseNode() {
                return tmpIter.baseNode();
            }

            @Override public int adjNode() {
                return tmpIter.adjNode();
            }

            @Override public PointList wayGeometry() {
                return tmpIter.wayGeometry();
            }

            @Override public void wayGeometry(PointList list) {
                tmpIter.wayGeometry(list);
            }

            @Override public double distance() {
                return tmpIter.distance();
            }

            @Override public void distance(double dist) {
                tmpIter.distance(dist);
            }

            @Override public int flags() {
                return tmpIter.flags();
            }

            @Override public void flags(int flags) {
                tmpIter.flags(flags);
            }

            @Override public boolean isEmpty() {
                return tmpIter.isEmpty();
            }
        };
    }

    @Override
    protected EdgeIterator getEdges(int node) {
        return lg.getEdges(node, NO_SHORTCUT);
    }
}
