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
package org.pageseeder.flint.lucene.facet;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.pageseeder.flint.lucene.search.DocumentCounter;
import org.pageseeder.flint.lucene.search.FieldDocumentCounter;
import org.pageseeder.flint.lucene.search.Filter;
import org.pageseeder.flint.lucene.search.Terms;
import org.pageseeder.flint.lucene.util.Beta;
import org.pageseeder.flint.lucene.util.Bucket;
import org.pageseeder.flint.lucene.util.Bucket.Entry;
import org.pageseeder.flint.lucene.util.Dates;
import org.pageseeder.xmlwriter.XMLWritable;
import org.pageseeder.xmlwriter.XMLWriter;

/**
 * A facet implementation using a simple index field.
 *
 * @author Jean-Baptiste Reure
 * @version 14 July 2017
 */
@Beta
public abstract class FlexibleRangeFacet implements XMLWritable {

  /**
   * The default number of facet values if not specified.
   */
  public static final int DEFAULT_MAX_NUMBER_OF_VALUES = 10;

  /**
   * The name of this facet
   */
  private final String _name;

  /**
   * The queries used to calculate each facet.
   */
  protected transient Bucket<Range> _bucket;

  /**
   * If the facet was computed in a "flexible" way
   */
  protected transient boolean flexible = false;

  /**
   * The total number of results containing the field used in this facet
   */
  protected transient int totalResults = 0;

  /**
   * The total number of ranges containing the field used in this facet
   */
  protected transient int totalRanges = 0;

  /**
   * Creates a new facet with the specified name;
   *
   * @param name     The name of the facet.
   * @param numeric  If this facet is numeric
   * @param r        If this facet is a date
   */
  protected FlexibleRangeFacet(String name) {
    this._name = name;
  }

  /**
   * Returns the name of the field.
   * @return the name of the field.
   */
  public String name() {
    return this._name;
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
      this.totalRanges = 0;
      this.totalResults = 0;
      // find all terms
      List<Term> terms = Terms.terms(searcher.getIndexReader(), this._name);
      // Otherwise, re-compute the query without the corresponding filter 
      Query filtered = base;
      if (filters != null) {
        this.flexible = true;
        for (Filter filter : filters) {
          if (!this._name.equals(filter.name()))
            filtered = filter.filterQuery(filtered);
        }
      }
      DocumentCounter counter = new DocumentCounter();
      Map<Range, Integer> ranges = new HashMap<>();
      for (Term t : terms) {
        // find range
        Range r = findRange(t);
        if (r == null) r = OTHER;
        // find count
        BooleanQuery query = new BooleanQuery();
        query.add(filtered, Occur.MUST);
        query.add(termToQuery(t), Occur.MUST);
        searcher.search(query, counter);
        int count = counter.getCount();
        if (count > 0) {
          // add to map
          Integer ec = ranges.get(r);
          ranges.put(r, Integer.valueOf(count + (ec == null ? 0 : ec.intValue())));
        }
        counter.reset();
      }
      this.totalRanges = ranges.size();
      // add to bucket
      Bucket<Range> b = new Bucket<Range>(size);
      for (Range r : ranges.keySet()) {
        b.add(r, ranges.get(r));
      }
      this._bucket = b;
      // compute total results
      FieldDocumentCounter totalCounter = new FieldDocumentCounter(this._name);
      searcher.search(filtered, totalCounter);
      this.totalResults = totalCounter.getCount();
    }
  }

  /**
   * Computes each facet option.
   *
   * <p>Same as <code>compute(searcher, base, 10);</code>.
   *
   * <p>Defaults to 10.
   *
   * @see #compute(Searcher, Query, int)
   *
   * @param searcher the index search to use.
   * @param base     the base query.
   *
   * @throws IOException if thrown by the searcher.
   */
  public void compute(IndexSearcher searcher, Query base, int size) throws IOException {
    compute(searcher, base, null, size);
  }

  /**
   * Computes each facet option.
   *
   * <p>Same as <code>compute(searcher, base, 10);</code>.
   *
   * <p>Defaults to 10.
   *
   * @see #compute(Searcher, Query, int)
   *
   * @param searcher the index search to use.
   * @param base     the base query.
   *
   * @throws IOException if thrown by the searcher.
   */
  public void compute(IndexSearcher searcher, Query base) throws IOException {
    compute(searcher, base, null, DEFAULT_MAX_NUMBER_OF_VALUES);
  }

  /**
   * Computes each facet option as a flexible facet.
   *
   * <p>Same as <code>computeFlexible(searcher, base, filters, 10);</code>.
   *
   * <p>Defaults to 10.
   *
   * @see #computeFlexible(IndexSearcher, Query, List, int)
   *
   * @param searcher the index search to use.
   * @param base     the base query.
   * @param filters  the filters applied to the base query
   *
   * @throws IOException if thrown by the searcher.
   */
  public void compute(IndexSearcher searcher, Query base, List<Filter> filters) throws IOException {
    compute(searcher, base, filters, DEFAULT_MAX_NUMBER_OF_VALUES);
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
    // find all terms
    List<Term> terms = Terms.terms(searcher.getIndexReader(), this._name);
    DocumentCounter counter = new DocumentCounter();
    Map<Range, Integer> ranges = new HashMap<>();
    for (Term t : terms) {
      // find the range
      Range r = findRange(t);
      if (r == null) r = OTHER;
      // find number
      searcher.search(termToQuery(t), counter);
      int count = counter.getCount();
      if (count > 0) {
        // add to map
        Integer ec = ranges.get(r);
        ranges.put(r, Integer.valueOf(count + (ec == null ? 0 : ec.intValue())));
      }
      counter.reset();
    }
    // set totals
    this.totalResults = 0;
    this.totalRanges = ranges.size();
    // add to bucket
    Bucket<Range> b = new Bucket<Range>(size);
    for (Range r : ranges.keySet()) {
      b.add(r, ranges.get(r));
    }
    this._bucket = b;
  }

  /**
   * Create a query for the term given.
   * 
   * @param t the term
   * 
   * @return the query
   */
  protected Query termToQuery(Term t) {
    return new TermQuery(t);
  }

  protected abstract String getType();

  protected abstract void rangeToXML(Range range, int cardinality, XMLWriter xml) throws IOException;

  protected abstract Range findRange(Term t);

  @Override
  public void toXML(XMLWriter xml) throws IOException {
    xml.openElement("facet", true);
    xml.attribute("name", this._name);
    xml.attribute("type", getType());
    xml.attribute("flexible", String.valueOf(this.flexible));
    if (!this.flexible) {
      xml.attribute("total-ranges", this.totalRanges);
      xml.attribute("total-results", this.totalResults);
    }
    if (this._bucket != null) {
      for (Entry<Range> e : this._bucket.entrySet()) {
        if (e.item() == OTHER) {
          xml.openElement("remaining-range");
          xml.attribute("cardinality", e.count());
          xml.closeElement();
        } else {
          rangeToXML(e.item(), e.count(), xml);
        }
      }
    }
    xml.closeElement();
  }

  public Bucket<Range> getValues() {
    return this._bucket;
  }

  public int getTotalResults() {
    return this.totalResults;
  }

  public int getTotalRanges() {
    return this.totalRanges;
  }

  public static class Range implements Comparable<Range> {
    private final String min;
    private final String max;
    private boolean includeMin;
    private boolean includeMax;
    private Range(String mi, boolean withMin, String ma, boolean withMax) {
      this.max = ma;
      this.min = mi;
      this.includeMin = withMin;
      this.includeMax = withMax;
    }
    public String getMin() {
      return this.min;
    }
    public String getMax() {
      return this.max;
    }
    protected boolean includeMax() {
      return this.includeMax;
    }
    protected boolean includeMin() {
      return this.includeMin;
    }
    @Override
    public String toString() {
      return (this.includeMin?'[':'{')+this.min+'-'+this.max+(this.includeMax?']':'}');
    }
    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Range) {
        Range r = (Range) obj;
        return ((r.min == null && this.min == null) || (r.min != null && r.min.equals(this.min))) &&
               ((r.max == null && this.max == null) || (r.max != null && r.max.equals(this.max))) &&
               this.includeMin == r.includeMin && this.includeMax == r.includeMax;
      }
      return false;
    }
    @Override
    public int hashCode() {
      return (this.min != null ? this.min.hashCode() * 13 : 13) +
             (this.max != null ? this.max.hashCode() * 11 : 11) +
             (this.includeMin ? 17 : 7) +
             (this.includeMax ? 5  : 3);
    }
    @Override
    public int compareTo(Range o) {
      if (this.min == null) {
        if (o.min != null) return -1;
        if (this.max == null) return -1;
        if (o.max == null) return 1;
        return this.max.compareTo(o.max);
      } else {
        if (o.min == null) return 1;
        return this.min.compareTo(o.min);
      }
    }
    public static Range stringRange(String mi, String ma) {
      return stringRange(mi, true, ma, true);
    }
    public static Range stringRange(String mi, boolean withMin, String ma, boolean withMax) {
      return new Range(mi, true, ma, true);
    }
    public static Range numericRange(Number mi, Number ma) {
      return numericRange(mi, true, ma, true);
    }
    public static Range numericRange(Number mi, boolean withMin, Number ma, boolean withMax) {
      return new Range(mi == null ? null : mi.toString(), withMin, ma == null ? null : ma.toString(), withMax);
    }
    public static Range dateRange(Date mi, Date ma, Resolution res) {
      return dateRange(mi, true, ma, true, res);
    }
    public static Range dateRange(Date mi, boolean withMin, Date ma, boolean withMax, Resolution res) {
      return new Range(Dates.toString(mi, res), withMin, Dates.toString(ma, res), withMax);
    }
  }

  protected static final Range OTHER = new Range((String) null, false, (String) null, false);
}