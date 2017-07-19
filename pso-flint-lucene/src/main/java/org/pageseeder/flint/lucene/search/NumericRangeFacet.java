/*
 * Copyright 2015 Allette Systems (Australia)
 * http://www.allette.com.au
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pageseeder.flint.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.pageseeder.flint.indexing.FlintField.NumericType;
import org.pageseeder.flint.lucene.query.NumericRange;
import org.pageseeder.flint.lucene.util.Beta;
import org.pageseeder.flint.lucene.util.Bucket;
import org.pageseeder.xmlwriter.XMLWriter;

/**
 * A facet implementation using a simple index field.
 *
 * @author Jean-Baptiste Reure
 * @version 14 July 2017
 */
@Beta
public class NumericRangeFacet extends FlexibleRangeFacet {

  /**
   * If this facet is a number
   */
  private final NumericType _numeric;

  private final List<Range> _ranges = new ArrayList<>();

  /**
   * Creates a new facet with the specified name;
   *
   * @param name     The name of the facet.
   * @param maxterms The maximum number of terms to return
   */
  private NumericRangeFacet(String name, NumericType numeric, List<Range> ranges) {
    super(name);
    this._numeric = numeric;
    this._ranges.addAll(ranges);
  }

  /**
   * Computes each facet option as a flexible facet.
   * All filters but the ones using the same field as this facet are applied to the base query before computing the numbers.
   *
   * @param searcher the index search to use.
   * @param base     the base query.
   * @param filters  the filters applied to the base query
   * @param size     the maximum number of field values to compute.
   *
   * @throws IOException if thrown by the searcher.
   */
  public void compute(IndexSearcher searcher, Query base, List<Filter> filters, int size) throws IOException {
    // If the base is null, simply calculate for each query
    if (base == null) {
      compute(searcher, size);
    } else {
      if (size < 0) throw new IllegalArgumentException("size < 0");
      // Otherwise, re-compute the query without the corresponding filter 
      Query filtered = base;
      if (filters != null) {
        this.flexible = true;
        for (Filter filter : filters) {
          if (!name().equals(filter.name()))
            filtered = filter.filterQuery(filtered);
        }
      }
      this.totalRanges = 0;
      Bucket<Range> bucket = new Bucket<Range>(size);
      DocumentCounter counter = new DocumentCounter();
      for (Range r : this._ranges) {
        // build query
        BooleanQuery query = new BooleanQuery();
        query.add(filtered, Occur.MUST);
        query.add(rangeToQuery(r), Occur.MUST);
        searcher.search(query, counter);
        int count = counter.getCount();
        // add to bucket
        bucket.add(r, count);
        counter.reset();
        if (count > 0) this.totalRanges++;
      }
      this._bucket = bucket;
      // compute total results
      FieldDocumentCounter totalCounter = new FieldDocumentCounter(name());
      searcher.search(filtered, totalCounter);
      this.totalResults = totalCounter.getCount();
    }
  }

  /**
   * Computes each facet option without a base query.
   *
   * @param searcher the index search to use.
   * @param size     the number of facet values to calculate.
   *
   * @throws IOException if thrown by the searcher.
   */
  protected void compute(IndexSearcher searcher, int size) throws IOException {
    this.totalRanges = 0;
    Bucket<Range> bucket = new Bucket<Range>(size);
    DocumentCounter counter = new DocumentCounter();
    for (Range r : this._ranges) {
      // build query
      searcher.search(rangeToQuery(r), counter);
      int count = counter.getCount();
      // add to bucket
      bucket.add(r, count);
      counter.reset();
      if (count > 0) this.totalRanges++;
    }
    this._bucket = bucket;
    // set totals
    this.totalResults = 0;
  }

  /**
   * Create a query for the term given, using the numeric type if there is one.
   * 
   * @param t the term
   * 
   * @return the query
   */
  protected Query rangeToQuery(Range r) {
    switch (this._numeric) {
      case INT:
        return NumericRange.newIntRange(name(), r.getMin() == null ? null : Integer.parseInt(r.getMin()),
                                                r.getMax() == null ? null : Integer.parseInt(r.getMax()),
                                                r.includeMin(), r.includeMax()).toQuery();
      case LONG:
        return NumericRange.newLongRange(name(), r.getMin() == null ? null : Long.parseLong(r.getMin()),
                                                 r.getMax() == null ? null : Long.parseLong(r.getMax()),
                                                 r.includeMin(), r.includeMax()).toQuery();
      case DOUBLE:
        return NumericRange.newDoubleRange(name(), r.getMin() == null ? null : Double.parseDouble(r.getMin()),
                                                   r.getMax() == null ? null : Double.parseDouble(r.getMax()),
                                                   r.includeMin(), r.includeMax()).toQuery();
      case FLOAT:
        return NumericRange.newFloatRange(name(), r.getMin() == null ? null : Float.parseFloat(r.getMin()),
                                                  r.getMax() == null ? null : Float.parseFloat(r.getMax()),
                                                  r.includeMin(), r.includeMax()).toQuery();
    }
    return null;
  }

  @Override
  protected String termToText(Term t) {
    return null;
  }

  @Override
  protected Query termToQuery(Term t) {
    return null;
  }

  @Override
  protected Range findRange(Term t) {
    return null;
  }

  @Override
  protected String getType() {
    return "numeric-range";
  }

  @Override
  protected void rangeToXML(Range range, int cardinality, XMLWriter xml) throws IOException {
    xml.openElement("range");
    if (range.getMin() != null) xml.attribute("min", range.getMin());
    if (range.getMax() != null) xml.attribute("max", range.getMax());
    xml.attribute("cardinality", cardinality);
    xml.closeElement();
  }

  // Builder ------------------------------------------------------------------------------------------
  public static class Builder {

    private final List<Range> ranges = new ArrayList<>();

    private NumericType numeric = null;

    private String name = null;

    public Builder numeric(NumericType num) {
      this.numeric = num;
      return this;
    }

    public Builder name(String n) {
      this.name = n;
      return this;
    }

    public Builder addRange(Number min, boolean withMin, Number max, boolean withMax) {
      this.ranges.add(Range.numericRange(min, withMin, max, withMax));
      return this;
    }

    public Builder addRange(Range range) {
      this.ranges.add(range);
      return this;
    }

    /**
     * Will include min and max.
     * @param min
     * @param max
     * @return
     */
    public Builder addRange(Number min, Number max) {
      return addRange(min, true, max, true);
    }

    public NumericRangeFacet build() {
      if (this.name == null) throw new NullPointerException("Must have a field name");
      if (this.numeric == null) throw new NullPointerException("Must have a numeric type");
      NumericRangeFacet fr = new NumericRangeFacet(this.name, this.numeric, this.ranges);
      return fr;
    }
  }
}
