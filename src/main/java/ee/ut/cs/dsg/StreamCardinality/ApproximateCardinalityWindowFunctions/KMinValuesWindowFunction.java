package ee.ut.cs.dsg.StreamCardinality.ApproximateCardinalityWindowFunctions;

import de.tub.dima.scotty.core.windowFunction.AggregateFunction;
import de.tub.dima.scotty.core.windowFunction.CloneablePartialStateFunction;
import ee.ut.cs.dsg.StreamCardinality.ApproximateCardinality.KMinValues;
import org.apache.flink.api.java.tuple.Tuple3;
import org.streaminer.util.hash.MurmurHash;

public class KMinValuesWindowFunction implements AggregateFunction<Tuple3<Long, String, Long>,
        Tuple3<Long, String, KMinValues>,
        Tuple3<Long, String, Long>>,
        CloneablePartialStateFunction<Tuple3<Long, String, KMinValues>>{

    KMinValuesWindowFunction(){}

    @Override
    public Tuple3<Long, String, KMinValues> lift(Tuple3<Long, String, Long> inputTuple) {
        KMinValues mh = new KMinValues(3, MurmurHash.getInstance());
        mh.offer(Math.round(inputTuple.f2));
        return new Tuple3<>(inputTuple.f0, inputTuple.f1, mh);

    }

    @Override
    public Tuple3<Long, String, Long> lower(Tuple3<Long, String, KMinValues> aggregate) {
        return new Tuple3<>(aggregate.f0, aggregate.f1, aggregate.f2.cardinality());
    }

    @Override
    public Tuple3<Long, String, KMinValues> combine(Tuple3<Long, String, KMinValues> partialAggregate1, Tuple3<Long, String, KMinValues> partialAggregate2) {
        try {
            return new Tuple3<>(partialAggregate1.f0, partialAggregate1.f1, (KMinValues) partialAggregate2.f2.merge(partialAggregate2.f2));
        } catch (KMinValues.KMinValuesException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Tuple3<Long, String, KMinValues> liftAndCombine(Tuple3<Long, String, KMinValues> partialAggregate, Tuple3<Long, String, Long> inputTuple) {
        partialAggregate.f2.offer(Math.round(inputTuple.f2));
        return partialAggregate;
    }

    @Override
    public Tuple3<Long, String, KMinValues> clone(Tuple3<Long, String, KMinValues> partialAggregate) {
        return new Tuple3<>(partialAggregate.f0, partialAggregate.f1, (KMinValues) partialAggregate.f2.clone());
    }
}

/*

 */
