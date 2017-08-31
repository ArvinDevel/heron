//  Copyright 2017 Twitter. All rights reserved.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.twitter.heron.dsl.impl.streamlets;

import java.util.Set;
import java.util.function.BiFunction;

import com.twitter.heron.api.topology.TopologyBuilder;
import com.twitter.heron.dsl.WindowConfig;
import com.twitter.heron.dsl.impl.bolts.JoinBolt;

/**
 * A Streamlet is a (potentially unbounded) ordered collection of tuples.
 Streamlets originate from pub/sub systems(such Pulsar/Kafka), or from static data(such as
 csv files, HDFS files), or for that matter any other source. They are also created by
 transforming existing Streamlets using operations such as map/flatMap, etc.
 Besides the tuples, a Streamlet has the following properties associated with it
 a) name. User assigned or system generated name to refer the streamlet
 b) nPartitions. Number of partitions that the streamlet is composed of. The nPartitions
 could be assigned by the user or computed by the system
 */
public class JoinStreamlet<K, V1, V2, VR> extends KVStreamletImpl<K, VR> {
  private KVStreamletImpl<K, V1> left;
  private KVStreamletImpl<K, V2> right;
  private WindowConfig windowCfg;
  private BiFunction<? super V1, ? super V2, ? extends VR> joinFn;

  public JoinStreamlet(KVStreamletImpl<K, V1> left, KVStreamletImpl<K, V2> right,
                       WindowConfig windowCfg,
                       BiFunction<? super V1, ? super V2, ? extends VR> joinFn) {
    this.left = left;
    this.right = right;
    this.windowCfg = windowCfg;
    this.joinFn = joinFn;
    setNumPartitions(left.getNumPartitions());
  }

  private void calculateName(Set<String> stageNames) {
    int index = 1;
    String name;
    while (true) {
      name = new StringBuilder("join").append(index).toString();
      if (!stageNames.contains(name)) {
        break;
      }
      index++;
    }
    setName(name);
  }

  public TopologyBuilder build(TopologyBuilder bldr, Set<String> stageNames) {
    left.build(bldr, stageNames);
    right.build(bldr, stageNames);
    if (getName() == null) {
      calculateName(stageNames);
    }
    if (stageNames.contains(getName())) {
      throw new RuntimeException("Duplicate Names");
    }
    stageNames.add(getName());
    bldr.setBolt(getName(),
        new JoinBolt<K, V1, V2, VR>(left.getName(), right.getName(), windowCfg, joinFn),
        getNumPartitions())
        .customGrouping(left.getName(), new JoinCustomGrouping<K, V1>())
        .customGrouping(right.getName(), new JoinCustomGrouping<K, V2>());
    return bldr;
  }
}
