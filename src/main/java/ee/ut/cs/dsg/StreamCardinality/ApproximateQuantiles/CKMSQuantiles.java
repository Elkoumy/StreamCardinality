/*
   Copyright 2012 Andrew Wang (andrew@umbrant.com)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package ee.ut.cs.dsg.StreamCardinality.ApproximateQuantiles;

import java.util.*;

/**
 * Implementation of the Cormode, Korn, Muthukrishnan, and Srivastava algorithm
 * for streaming calculation of targeted high-percentile epsilon-approximate
 * quantiles.
 * 
 * This is a generalization of the earlier work by Greenwald and Khanna (GK),
 * which essentially allows different error bounds on the targeted quantiles,
 * which allows for far more efficient calculation of high-percentiles.
 * 
 * 
 * See: Cormode, Korn, Muthukrishnan, and Srivastava
 * "Effective Computation of Biased Quantiles over Data Streams" in ICDE 2005
 * 
 * Greenwald and Khanna,
 * "Space-efficient online computation of quantile summaries" in SIGMOD 2001
 * 
 */
public class CKMSQuantiles implements IQuantiles<Long> {
    /**
     * Total number of items in stream.
     */
    private int count = 0;

    /**
     * Used for tracking incremental compression.
     */
    private int compressIdx = 0;

    /**
     * Current list of sampled items, maintained in sorted order with error bounds.
     */
    protected LinkedList<Item> sample;
    
    /**
     * Buffers incoming items to be inserted in batch.
     */
    private long[] buffer = new long[500];
    
    private int bufferCount = 0;
    
    /**
     * Array of Quantiles that we care about, along with desired error.
     */
    private final Quantile quantiles[];
    
    public CKMSQuantiles(Quantile[] quantiles) {
        this.quantiles = quantiles;
        this.sample = new LinkedList<Item>();
    }

    /**
     * added by Elkoumy to facilitate the creation
     * 22/2/19
     * UT
     * @param q
     * @param error
     */
    public CKMSQuantiles(double[] q,double error) {
        List<Quantile> quantiles = new ArrayList<Quantile>();
//        quantiles.add(new Quantile(0.50, 0.050));
        for(int i=0;i<q.length; i++){
            quantiles.add(new Quantile(q[i], error));
        }
        this.quantiles = quantiles.toArray(new Quantile[] {});
        this.sample = new LinkedList<Item>();
    }
  
    /**
     * Add a new value from the stream.
     * 
     * @param value
     */
    @Override
    public void offer(Long value) {
        buffer[bufferCount] = value;
        bufferCount++;

        if (bufferCount == buffer.length) {
            insertBatch();
            compress();
        }
    }


    /**
     * inserting multiple values into the quantile
     * Elkoumy 22/2/19
     * UT
     * @param value
     */
    private void insertMultiple(Long[] value){

        for ( int i =0; i< value.length; i++ )
        {
            buffer[bufferCount]= value[i];
            bufferCount++;

        }
        if (bufferCount == buffer.length) {
            insertBatch();
            compress();
        }
    }

    /**
     * Merging two items
     * @param other the external item
     *
     * @return
     *
     * Elkoumy 22/2/19
     * UT
     */
    public CKMSQuantiles merge(CKMSQuantiles other){

        ListIterator<Item> it = other.sample.listIterator();
        Item item = it.next();
        Long[] buffer = new Long[other.count];
        int cnt=0;
        while (it.nextIndex() < other.sample.size() ) {
            buffer[cnt]=item.value;
                item = it.next();
                cnt++;
            }
        this.insertMultiple(buffer);
        return this;
    }

    public CKMSQuantiles clone(){
        CKMSQuantiles res= new CKMSQuantiles(this.quantiles);

        ListIterator<Item> it = this.sample.listIterator();
        Item item = it.next();
        Long[] buffer = new Long[this.count];
        int cnt=0;
        while (it.nextIndex() < this.sample.size() ) {
            buffer[cnt]=item.value;
            item = it.next();
            cnt++;
        }
        res.insertMultiple(buffer);
        return res;
    }

    /**
     * Get the estimated value at the specified quantile.
     * 
     * @param q Queried quantile, e.g. 0.50 or 0.99.
     * @return Estimated value at that quantile.
     */
    @Override
    public Long getQuantile(double q) throws QuantilesException {
        // clear the buffer
        insertBatch();
        compress();

        if (sample.size() == 0) {
            throw new QuantilesException("No samples present");
        }

        int rankMin = 0;
        int desired = (int) (q * count);

        ListIterator<Item> it = sample.listIterator();
        Item prev, cur;
        cur = it.next();
        while (it.hasNext()) {
            prev = cur;
            cur = it.next();

            rankMin += prev.g;

            if (rankMin + cur.g + cur.delta > desired + (allowableError(desired) / 2)) {
                return prev.value;
            }
        }

        // edge case of wanting max value
        return sample.getLast().value;
    }
    
    /**
     * Specifies the allowable error for this rank, depending on which quantiles
     * are being targeted.
     * 
     * This is the f(r_i, n) function from the CKMS paper. It's basically how wide
     * the range of this rank can be.
     * 
     * @param rank the index in the list of samples
     */
    private double allowableError(int rank) {
        // NOTE: according to CKMS, this should be count, not size, but this leads
        // to error larger than the error bounds. Leaving it like this is
        // essentially a HACK, and blows up memory, but does "work".
        //int size = count;
        int size = sample.size();
        double minError = size + 1;
        
        for (Quantile q : quantiles) {
            double error;
            if (rank <= q.quantile * size) {
                error = q.u * (size - rank);
            } else {
                error = q.v * rank;
            }
            if (error < minError) {
                minError = error;
            }
        }

        return minError;
    }
    
    private void insertBatch() {
        if (bufferCount == 0) {
          return;
        }

        Arrays.sort(buffer, 0, bufferCount);

        // Base case: no samples
        int start = 0;
        if (sample.size() == 0) {
          Item newItem = new Item(buffer[0], 1, 0);
          sample.add(newItem);
          start++;
          count++;
        }

        ListIterator<Item> it = sample.listIterator();
        Item item = it.next();
        
        for (int i = start; i < bufferCount; i++) {
            long v = buffer[i];
            while (it.nextIndex() < sample.size() && item.value < v) {
                item = it.next();
            }
            
            // If we found that bigger item, back up so we insert ourselves before it
            if (item.value > v) {
                it.previous();
            }
            
            // We use different indexes for the edge comparisons, because of the above
            // if statement that adjusts the iterator
            int delta;
            if (it.previousIndex() == 0 || it.nextIndex() == sample.size()) {
                delta = 0;
            } else {
                delta = ((int) Math.floor(allowableError(it.nextIndex()))) - 1;
            }
            
            Item newItem = new Item(v, 1, delta);
            it.add(newItem);
            count++;
            item = newItem;
        }

        bufferCount = 0;
    }
    
    /**
     * Try to remove extraneous items from the set of sampled items. This checks
     * if an item is unnecessary based on the desired error bounds, and merges it
     * with the adjacent item if it is.
     */
    private void compress() {
        if (sample.size() < 2) {
          return;
        }

        ListIterator<Item> it = sample.listIterator();
        int removed = 0;

        Item prev = null;
        Item next = it.next();

        while (it.hasNext()) {
            prev = next;
            next = it.next();

            if (prev.g + next.g + next.delta <= allowableError(it.previousIndex())) {
                next.g += prev.g;
                // Remove prev. it.remove() kills the last thing returned.
                it.previous();
                it.previous();
                it.remove();
                // it.next() is now equal to next, skip it back forward again
                it.next();
                removed++;
            }
        }
    }
    
    private class Item {
        public final long value;
        public int g;
        public final int delta;

        public Item(long value, int lower_delta, int delta) {
            this.value = value;
            this.g = lower_delta;
            this.delta = delta;
        }

        @Override
        public String toString() {
            return String.format("%d, %d, %d", value, g, delta);
        }
    }
    
    public static class Quantile {
        public final double quantile;
        public final double error;
        public final double u;
        public final double v;

        public Quantile(double quantile, double error) {
            this.quantile = quantile;
            this.error = error;
            u = 2.0 * error / (1.0 - quantile);
            v = 2.0 * error / quantile;
        }

        @Override
        public String toString() {
            return String.format("Q{q=%.3f, eps=%.3f})", quantile, error);
        }
    }

}