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
package org.pageseeder.flint.index;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.document.Document;
import org.pageseeder.flint.IndexException;
import org.pageseeder.flint.util.FlintEntityResolver;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This handler makes a Lucene 5 Document out of a properly formatted XML document.
 *
 * <p>The XML document must validate the Lucene Index Document DTD.
 *
 * <p>For example:
 *
 * <pre>{@code
 * <document>
 *   <field name="modified" store="yes" index="yes" parse-date-as="MMM dd, yy" resolution="day">Jan 12, 02</field>
 *   <field name="path"     store="yes" index="no" >C:\documents\00023.xml</field>
 *   <field name="webpath"  store="yes" index="no" >/documents/doc-23.xml</field>
 *   <field name="text" store="compress" index="tokenised" >
 *     Truly to speak, and with no addition,
 *     We go to gain a little patch of ground
 *     That hath in it no profit but the name.
 *     To pay five ducats, five, I would not farm it;
 *     Nor will it yield to Norway or the Pole
 *     A ranker rate, should it be sold in fee.
 *   </field>
 * </document>
 * }</pre>
 *
 * @see <a href="http://www.weborganic.org/code/flint/schema/index-documents-1.0.dtd">Index Documents 1.0 Schema</a>
 * @see <a href="http://www.weborganic.org/code/flint/schema/index-documents-2.0.dtd">Index Documents 2.0 Schema</a>
 * @see <a href="http://www.weborganic.org/code/flint/schema/index-documents-2.0.dtd">Index Documents 3.0 Schema</a>
 *
 * @author Christophe Lauret
 * @author Jean-Baptiste Reure
 *
 * @version 1 September 2015
 */
public final class IndexParser {

  /**
   * THe XML reader to use.
   */
  private final XMLReader _reader;

  /**
   * Creates a new IndexParser.
   *
   * @param reader    The XML reader to use.
   */
  protected IndexParser(XMLReader reader) {
    this._reader = reader;
    this._reader.setEntityResolver(FlintEntityResolver.getInstance());
    this._reader.setErrorHandler(new FlintErrorHandler());
  }

// public static methods -----------------------------------------------------------------------

  /**
   * Make a collection Lucene documents to be indexed from the XML file given.
   *
   * <p>The XML file must conform to the DTD defined in this class.
   *
   * <p>Ensure that the reader uses the correct encoding.
   *
   * @param source The source to read.
   *
   * @return A collection of Lucene documents made from the file.
   *
   * @throws IndexException Should an error occur while parsing the file.
   */
  public synchronized List<Document> process(InputSource source) throws IndexException {
    try {
      IndexDocumentHandler handler = new AutoHandler(this._reader);
      this._reader.setContentHandler(handler);
      this._reader.parse(source);
      return handler.getDocuments();
    } catch (SAXException ex) {
      throw new IndexException("An SAX error occurred while parsing source "+source.getSystemId()+": "+ex.getMessage(), ex);
    } catch (IOException ex) {
      ex.printStackTrace();
      throw new IndexException("An I/O error occurred while parsing the file "+source.getSystemId()+": "+ex.getMessage(), ex);
    }
  }

  /**
   * Returns a list of Lucene documents to be indexed from the XML file given.
   *
   * <p>The XML file must conform to the DTD defined in this class.
   *
   * @see #make(java.io.Reader)
   *
   * @param f the file to be read
   *
   * @return A collection of Lucene documents made from the file.
   *
   * @throws IndexException Should an error occur while parsing the file.
   */
  public synchronized List<Document> process(File f) throws IndexException {
    try {
      InputSource source = new InputSource(new InputStreamReader(new FileInputStream(f), "utf-8"));
      source.setSystemId(f.toURI().toURL().toExternalForm());
      return process(source);
    } catch (IOException ex) {
      throw new IndexException("I/O error occurred while generating file input source: "+ex.getMessage(), ex);
    }
  }

  // Inner class to determine which handler to use --------------------------------------------------

  /**
   * A content handler to determine the version used.
   *
   * @author Christophe Lauret
   * @version 1 March 2010
   */
  private static final class AutoHandler extends DefaultHandler implements IndexDocumentHandler {

    /**
     * The reader in use.
     */
    private final XMLReader _reader;

    /**
     * The handler in use.
     */
    private IndexDocumentHandler _handler;

    /**
     * Create a new auto handler for the specified XML reader.
     *
     * @param reader   The XML Reader in use.
     */
    public AutoHandler(XMLReader reader) {
      this._reader = reader;
    }

    /**
     * Once element "documents" is matched, the reader is assigned the appropriate handler.
     *
     * {@inheritDoc}
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
      if ("documents".equals(qName) || "documents".equals(localName)) {
        String version = atts.getValue("version");
        // Version 3.0
        if ("3.0".equals(version)) {
          this._handler = new IndexDocumentHandler_3_0();
        // Version 2.0
        } else if ("2.0".equals(version)) {
          this._handler = new IndexDocumentHandler_2_0();
        // Assume version 1.0
        } else {
          this._handler = new IndexDocumentHandler_1_0();
        }
        // Start processing the document with the new handler
        this._handler.startDocument();
        this._handler.startElement(uri, localName, qName, atts);
        // Reassign the content handler
        this._reader.setContentHandler(this._handler);
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Document> getDocuments() {
      if (this._handler == null) return Collections.emptyList();
      return this._handler.getDocuments();
    }
  }

}
