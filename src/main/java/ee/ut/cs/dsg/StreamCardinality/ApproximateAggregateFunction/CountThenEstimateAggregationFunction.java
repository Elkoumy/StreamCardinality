package ee.ut.cs.dsg.StreamCardinality.ApproximateAggregateFunction;

import ee.ut.cs.dsg.StreamCardinality.ApproximateCardinality.CardinalityMergeException;
import ee.ut.cs.dsg.StreamCardinality.ApproximateCardinality.CountThenEstimate;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple3;

public class CountThenEstimateAggregationFunction implements AggregateFunction<Tuple3<Long, String, Long>, CountThenEstimateAccumulator, Tuple3<Long,String,Long>> {
    @Override
    public CountThenEstimateAccumulator createAccumulator() { return new CountThenEstimateAccumulator(); }

    @Override
    public CountThenEstimateAccumulator merge(CountThenEstimateAccumulator a, CountThenEstimateAccumulator b) {
        try {
            a.acc = (CountThenEstimate) a.acc.merge(b.acc);
        } catch (CardinalityMergeException e) {
            e.printStackTrace();
        }
        return a;
    }

    @Override
    public CountThenEstimateAccumulator add(Tuple3<Long, String, Long> value, CountThenEstimateAccumulator acc) {
        acc.f0 = value.f0;
        acc.f1 = value.f1;
        long val = Math.round(value.f2);
        acc.acc.offer(val);
        return acc;
    }

    @Override
    public Tuple3<Long, String, Long> getResult(CountThenEstimateAccumulator acc) {
        Tuple3<Long,String,Long> res = new Tuple3<>();
        res.f0 = acc.f0;
        res.f1 = acc.f1;
        res.f2 = acc.acc.cardinality();
        return res;
    }
}
