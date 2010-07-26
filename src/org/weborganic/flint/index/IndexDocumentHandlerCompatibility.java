package org.weborganic.flint.index;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * The handler for the Flint Index Documents format version 1.
 *
 * @see <a href="http://weborganic.org/code/flint/schema/index-documents-1.0.dtd">Index Documents 1.0 Schema</a>
 * 
 * @author Christophe Lauret
 * @version 2 March 2010
 */
final class IndexDocumentHandlerCompatibility extends DefaultHandler implements IndexDocumentHandler {

  /**
   * Use the GMT time zone.
   */
  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = Logger.getLogger(IndexDocumentHandlerCompatibility.class);

  // class attributes
  // -------------------------------------------------------------------------------------------

  /**
   * Date parser instances.
   */
  private final Map<String, DateFormat> dfs = new HashMap<String, DateFormat>();

  /**
   * The list of Lucene documents produced by this handler.
   */
  private List<Document> documents;

  // state variables for documents and fields
  // ----------------------------------------------------------------------------------------------

  /**
   * The current document being processed.
   */
  private Document _document;

  /**
   * Flag to indicate whether a field is being processed (affects the behaviour of characters())
   */
  private boolean _isField = false;

  /**
   * Flag to indicate whether a field is being processed (affects the behaviour of characters())
   */
  private boolean _isCSV = false;

  /**
   * Flag to indicate that the current field should be compressed (may result in two fields).
   */
  private boolean _isCompressed = false;

  /**
   * The field builder. 
   */
  private FieldBuilder builder = new FieldBuilder(); 

  /**
   * The characters found within a field.
   */
  private StringBuilder _value;

  // constructors
  // ----------------------------------------------------------------------------------------------

  /**
   * {@inheritDoc}
   */
  public List<Document> getDocuments() {
    return this.documents;
  }

  // SAX Methods
  // ----------------------------------------------------------------------------------------------

  /**
   * Receives notification of the beginning of the document.
   *
   * <p>Initialise this handler.
   */
  public void startDocument() {
    LOGGER.debug("Start processing iXML documents (compatibility version)");
    this.documents = new ArrayList<Document>();
  }

  /**
   * Receives notification of the end of the document.
   */
  public void endDocument() {
    LOGGER.debug("End processing iXML document");
  }

  /**
   * {@inheritDoc}
   */
  public void startElement(String uri, String localName, String qName, Attributes attributes) {
    if ("field".equals(qName)) {
      startFieldElement(attributes);
    } else if ("document".equals(qName) || "fragment".equals(qName)) {
      startDocumentElement(attributes);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void endElement(String uri, String localName, String qName) {
    if ("field".equals(qName)) {
      endFieldElement();
    } else if ("document".equals(qName) || "fragment".equals(qName)) {
      endDocumentElement();
    }
  }

  /**
   * Receives notification of character data inside an element.
   *
   * <p>Prints those characters.
   *
   * <p>Replace the '\n' (newline character) by a space.
   *
   * @param ch     The characters
   * @param start  The start position in the character array.
   * @param length The number of characters to use from the character array.
   *
   * @throws SAXException Any SAX exception, possibly wrapping another exception.
   */
  public void characters(char[] ch, int start, int length) throws SAXException {
    if (this._isField) {
      for (int i = start; i < (length+start); i++) {
        this._value.append(filterChar(ch[i]));
      }
    }
  }

  // private helpers
  // -------------------------------------------------------------------------------------------

  /**
   * Handles the start of a 'document' element.
   * 
   * @param atts The attributes to handles.
   */
  private void startDocumentElement(Attributes atts) {
    LOGGER.debug("Parsing new index document");
    this._document = new Document();
//    this._document.add(new Field(IndexManager.CONTENT_ID_FIELD, this.contentID, Store.YES, Index.NOT_ANALYZED));
  }

  /**
   * Handles the end of a 'document' element.
   */
  private void endDocumentElement() {
    LOGGER.debug("Storing document");
    if (this._document.getFields().isEmpty()) {
      LOGGER.warn("This document is empty - will not be stored");
    } else {
      this.documents.add(this._document);
    }
    this._document = null;
  }

  /**
   * Handles the start of a new 'field' element
   * 
   * @param atts The attributes to handles.
   */
  private void startFieldElement(Attributes atts) {
    this.builder.name(atts.getValue("name"));
    String ftype = atts.getValue("type");
    this.builder.index(ftype == null ? FieldBuilder.toFieldIndex(atts.getValue("index")) : fieldTypeToIndex(ftype));
    // handle compression
    String store = ftype == null ? atts.getValue("store") : fieldTypeToStore(ftype);
    if ("compress".equals(store)) {
      this._isCompressed = true;
      this.builder.store(Store.NO);
    } else {
      this._isCompressed = false;
      this.builder.store(store);
    }
    // Optional attributes
    this.builder.termVector(atts.getValue("term-vector"));
    this.builder.boost(atts.getValue("boost"));
    // Date handling
    this.builder.dateFormat(toDateFormat(atts.getValue("date-format")));
    this.builder.resolution(atts.getValue("date-resolution"));
    // other attributes are ignored
    // Set attributes ready for recording content
    this._value   = new StringBuilder();
    this._isField = true;
    this._isCSV = "true".equals(atts.getValue("comma-separated"));
  }

  private static String fieldTypeToStore(String type) {
    if (type == null)                            return null;
    else if ("".equals(type))                    return "yes";
    else if ("keyword".equalsIgnoreCase(type))   return "yes";
    else if ("unindexed".equalsIgnoreCase(type)) return "yes";
    else if ("text".equalsIgnoreCase(type))      return "yes";
    else if ("stored".equalsIgnoreCase(type))    return "yes";
    else if ("unstored".equalsIgnoreCase(type))  return "no";
    else if ("system".equalsIgnoreCase(type))    return "no";
    else return "yes";
  }

  private static Field.Index fieldTypeToIndex(String type) {
    if (type == null)                            return null;
    else if ("".equals(type))                    return Field.Index.ANALYZED;
    else if ("keyword".equalsIgnoreCase(type))   return Field.Index.ANALYZED;
    else if ("unindexed".equalsIgnoreCase(type)) return Field.Index.NO;
    else if ("text".equalsIgnoreCase(type))      return Field.Index.ANALYZED_NO_NORMS;
    else if ("stored".equalsIgnoreCase(type))    return Field.Index.ANALYZED_NO_NORMS;
    else if ("unstored".equalsIgnoreCase(type))  return Field.Index.ANALYZED_NO_NORMS;
    else if ("system".equalsIgnoreCase(type))    return Field.Index.NOT_ANALYZED;
    else return Field.Index.ANALYZED;
  }

  /**
   * Handles the end of a 'field' element.
   */
  private void endFieldElement() {
    try {
      // construct the field
      if (this._isCSV) {
        String[] values = this._value.toString().split(",");
        for (int i = 0; i < values.length; i++) {
          if (values[i].length() > 0)
            addFieldToDocument(values[i]);
        }
        // add it with all the values but not stored
        this.builder.store(Store.NO);
        addFieldToDocument(this._value.toString());
      } else addFieldToDocument(this._value.toString());

    } catch (IllegalStateException ex) {
      LOGGER.warn("Unable to create field: "+this.builder.name(), ex);
    } catch (IllegalArgumentException ex) {
      LOGGER.warn("Unable to create field: "+this.builder.name(), ex);
    }
    // reset the class attributes involved in this field
    resetField();
  }
  
  private void addFieldToDocument(String value) {
    this.builder.value(value);
    // compressed field
    if (this._isCompressed) {
      if (this.builder.index() == Index.NO) {
        this._document.add(this.builder.buildCompressed());
      } else {
        this._document.add(this.builder.build());
        this._document.add(this.builder.buildCompressed());
      }
    // uncompressed fields.
    } else  {
      this._document.add(this.builder.build());
    }
  }

  /**
   * Store the date format specified.
   *
   * <p>Set the current date format to <code>null<code> if the format is <code>null</code>.
   *
   * <p>Otherwise retrieve from hashtable or create an instance if it's
   * never been created.
   * 
   * @param format The date format used.
   */
  private DateFormat toDateFormat(String format) {
    if (format == null) return null;
    DateFormat df = this.dfs.get(format);
    // Date format not processed yet
    if (df == null) {
      try {
        df = new SimpleDateFormat(format);
        df.setTimeZone(GMT);
        this.dfs.put(format, df);
      } catch (Exception ex) {
        LOGGER.warn("Ignoring unusable date format '"+format+"'", ex);
      }
    }
    return df;
  }

  /**
   * Filter the char specified to be printed.
   *
   * @param c The char to filter
   * @return The char to print
   */
  private static char filterChar(char c) {
    switch (c) {
      case '\n' : return ' ';
      case '\t' : return ' ';
      default: return c;
    }
  }

  /**
   * Reset the fields attributes.
   */
  private void resetField() {
    this._isField = false;
    this.builder.reset();
    this._value = null;
  }

}