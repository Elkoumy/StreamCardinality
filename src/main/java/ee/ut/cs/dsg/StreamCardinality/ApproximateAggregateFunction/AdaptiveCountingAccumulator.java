package ee.ut.cs.dsg.StreamCardinality.ApproximateAggregateFunction;

import ee.ut.cs.dsg.StreamCardinality.ApproximateCardinality.AdaptiveCounting;

public class AdaptiveCountingAccumulator <ACC>{
    Long f0;
    String f1;

    AdaptiveCounting acc = new AdaptiveCounting(2);
}
