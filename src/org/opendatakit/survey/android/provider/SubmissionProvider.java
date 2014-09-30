/*
 * Copyright (C) 2012 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.survey.android.provider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.CharEncoding;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.opendatakit.aggregate.odktables.rest.KeyValueStoreConstants;
import org.opendatakit.aggregate.odktables.rest.TableConstants;
import org.opendatakit.aggregate.odktables.rest.entity.Column;
import org.opendatakit.common.android.data.ColumnDefinition;
import org.opendatakit.common.android.data.ElementDataType;
import org.opendatakit.common.android.data.ElementType;
import org.opendatakit.common.android.database.DataModelDatabaseHelperFactory;
import org.opendatakit.common.android.database.DatabaseConstants;
import org.opendatakit.common.android.logic.PropertyManager;
import org.opendatakit.common.android.provider.DataTableColumns;
import org.opendatakit.common.android.provider.KeyValueStoreColumns;
import org.opendatakit.common.android.utilities.ODKDatabaseUtils;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.logic.DynamicPropertiesCallback;
import org.opendatakit.survey.android.utilities.EncryptionUtils;
import org.opendatakit.survey.android.utilities.EncryptionUtils.EncryptedFormInformation;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * The WebKit does better if there is a content provider vending files to it.
 * This provider vends files under the Forms and Instances directories (only).
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class SubmissionProvider extends ContentProvider {
  private static final String ISO8601_DATE_ONLY_FORMAT = "yyyy-MM-dd";
  private static final String ISO8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";

  private static final String t = "SubmissionProvider";

  public static final String XML_SUBMISSION_AUTHORITY = "org.opendatakit.common.android.provider.submission.xml";
  public static final String JSON_SUBMISSION_AUTHORITY = "org.opendatakit.common.android.provider.submission.json";

  public static final String XML_SUBMISSION_URL_PREFIX = ContentResolver.SCHEME_CONTENT + "://"
      + SubmissionProvider.XML_SUBMISSION_AUTHORITY;
  public static final String JSON_SUBMISSION_URL_PREFIX = ContentResolver.SCHEME_CONTENT + "://"
      + SubmissionProvider.JSON_SUBMISSION_AUTHORITY;

  private static final String XML_OPENROSA_NAMESPACE = "http://openrosa.org/xforms";
  private static final String XML_DEFAULT_NAMESPACE = null; // "http://opendatakit.org/xforms";
  // // any
  // arbitrary
  // namespace
  private static final String NEW_LINE = "\n";

  private PropertyManager propertyManager;

  @Override
  public boolean onCreate() {
    propertyManager = new PropertyManager(getContext());
    return true;
  }

  @SuppressWarnings("unchecked")
  private static final void putElementValue(HashMap<String, Object> dataMap, ColumnDefinition defn,
      Object value) {
    List<ColumnDefinition> nesting = new ArrayList<ColumnDefinition>();
    ColumnDefinition cur = defn.getParent();
    while (cur != null) {
      nesting.add(cur);
      cur = cur.getParent();
    }

    HashMap<String, Object> elem = dataMap;
    for (int i = nesting.size() - 1; i >= 0; --i) {
      cur = nesting.get(i);
      if (elem.containsKey(cur.getElementName())) {
        elem = (HashMap<String, Object>) elem.get(cur.getElementName());
      } else {
        elem.put(cur.getElementName(), new HashMap<String, Object>());
        elem = (HashMap<String, Object>) elem.get(cur.getElementName());
      }
    }
    elem.put(defn.getElementName(), value);
  }

  @SuppressWarnings("unchecked")
  private static final int generateXmlHelper(Document d, Element data, int idx, String key,
      Map<String, Object> values, WebLogger log) {
    Object o = values.get(key);

    Element e = d.createElement(XML_DEFAULT_NAMESPACE, key);

    if (o == null) {
      log.e(t, "Unexpected null value");
    } else if (o instanceof Integer) {
      e.addChild(0, Node.TEXT, ((Integer) o).toString());
    } else if (o instanceof Double) {
      e.addChild(0, Node.TEXT, ((Double) o).toString());
    } else if (o instanceof Boolean) {
      e.addChild(0, Node.TEXT, ((Boolean) o).toString());
    } else if (o instanceof String) {
      e.addChild(0, Node.TEXT, ((String) o));
    } else if (o instanceof List) {
      StringBuilder b = new StringBuilder();
      List<Object> al = (List<Object>) o;
      for (Object ob : al) {
        if (ob instanceof Integer) {
          b.append(((Integer) ob).toString());
        } else if (ob instanceof Double) {
          b.append(((Double) ob).toString());
        } else if (ob instanceof Boolean) {
          b.append(((Boolean) ob).toString());
        } else if (ob instanceof String) {
          b.append(((String) ob));
        } else {
          throw new IllegalArgumentException("Unexpected type in XML submission serializer");
        }
        b.append(" ");
      }
      e.addChild(0, Node.TEXT, b.toString().trim());
    } else if (o instanceof Map) {
      // it is an object...
      Map<String, Object> m = (Map<String, Object>) o;
      int nidx = 0;

      ArrayList<String> entryNames = new ArrayList<String>();
      entryNames.addAll(m.keySet());
      Collections.sort(entryNames);
      for (String name : entryNames) {
        nidx = generateXmlHelper(d, e, nidx, name, m, log);
      }
    } else {
      throw new IllegalArgumentException("Unexpected object type in XML submission serializer");
    }
    data.addChild(idx++, Node.ELEMENT, e);
    data.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);
    return idx;
  }

  /**
   * The incoming URI is of the form:
   * ..../appName/tableId/instanceId?formId=&formVersion=
   *
   * where instanceId is the DataTableColumns._ID
   */
  @SuppressWarnings("unchecked")
  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    final boolean asXml = uri.getAuthority().equalsIgnoreCase(XML_SUBMISSION_AUTHORITY);

    if (mode != null && !mode.equals("r")) {
      throw new IllegalArgumentException("Only read access is supported");
    }

    // URI == ..../appName/tableId/instanceId?formId=&formVersion=

    List<String> segments = uri.getPathSegments();

    if (segments.size() != 4) {
      throw new IllegalArgumentException("Unknown URI (incorrect number of path segments!) " + uri);
    }

    final String appName = segments.get(0);
    ODKFileUtils.verifyExternalStorageAvailability();
    ODKFileUtils.assertDirectoryStructure(appName);
    WebLogger log = WebLogger.getLogger(appName);

    final String tableId = segments.get(1);
    final String instanceId = segments.get(2);
    final String submissionInstanceId = segments.get(3);

    SQLiteDatabase db = null;
    try {
      db = DataModelDatabaseHelperFactory.getDatabase(getContext(), appName);

      boolean success = false;
      try {
        success = ODKDatabaseUtils.get().hasTableId(db, tableId);
      } catch (Exception e) {
        e.printStackTrace();
        throw new SQLException("Unknown URI (exception testing for tableId) " + uri);
      }
      if (!success) {
        throw new SQLException("Unknown URI (missing data table for tableId) " + uri);
      }

      final String dbTableName = "\"" + tableId + "\"";

      // Get the table properties specific to XML submissions

      String xmlInstanceName = null;
      String xmlRootElementName = null;
      String xmlDeviceIdPropertyName = null;
      String xmlUserIdPropertyName = null;
      String xmlBase64RsaPublicKey = null;

      try {

        Cursor c = null;
        try {
          c = db.query(DatabaseConstants.KEY_VALUE_STORE_ACTIVE_TABLE_NAME, new String[] {
              KeyValueStoreColumns.KEY, KeyValueStoreColumns.VALUE }, KeyValueStoreColumns.TABLE_ID
              + "=? AND " + KeyValueStoreColumns.PARTITION + "=? AND "
              + KeyValueStoreColumns.ASPECT + "=? AND " + KeyValueStoreColumns.KEY
              + " IN (?,?,?,?,?)", new String[] { tableId, KeyValueStoreConstants.PARTITION_TABLE,
              KeyValueStoreConstants.ASPECT_DEFAULT, KeyValueStoreConstants.XML_INSTANCE_NAME,
              KeyValueStoreConstants.XML_ROOT_ELEMENT_NAME,
              KeyValueStoreConstants.XML_DEVICE_ID_PROPERTY_NAME,
              KeyValueStoreConstants.XML_USER_ID_PROPERTY_NAME,
              KeyValueStoreConstants.XML_BASE64_RSA_PUBLIC_KEY }, null, null, null);
          if (c.getCount() > 0) {
            c.moveToFirst();
            int idxKey = c.getColumnIndex(KeyValueStoreColumns.KEY);
            int idxValue = c.getColumnIndex(KeyValueStoreColumns.VALUE);
            do {
              String key = c.getString(idxKey);
              String value = c.getString(idxValue);
              if (KeyValueStoreConstants.XML_INSTANCE_NAME.equals(key)) {
                xmlInstanceName = value;
              } else if (KeyValueStoreConstants.XML_ROOT_ELEMENT_NAME.equals(key)) {
                xmlRootElementName = value;
              } else if (KeyValueStoreConstants.XML_DEVICE_ID_PROPERTY_NAME.equals(key)) {
                xmlDeviceIdPropertyName = value;
              } else if (KeyValueStoreConstants.XML_USER_ID_PROPERTY_NAME.equals(key)) {
                xmlUserIdPropertyName = value;
              } else if (KeyValueStoreConstants.XML_BASE64_RSA_PUBLIC_KEY.equals(key)) {
                xmlBase64RsaPublicKey = value;
              }
            } while (c.moveToNext());
          }
        } finally {
          c.close();
          c = null;
        }

        List<Column> columns = ODKDatabaseUtils.get().getUserDefinedColumns(db, tableId);
        ArrayList<ColumnDefinition> orderedDefns = ColumnDefinition.buildColumnDefinitions(columns);

        // Retrieve the values of the record to be emitted...

        HashMap<String, Object> values = new HashMap<String, Object>();

        // issue query to retrieve the instanceId
        StringBuilder b = new StringBuilder();
        b.append("SELECT * FROM ").append(dbTableName).append(" WHERE ")
            .append(DataTableColumns.ID).append("=?").append(" group by ")
            .append(DataTableColumns.ID).append(" having ")
            .append(DataTableColumns.SAVEPOINT_TIMESTAMP).append("=max(")
            .append(DataTableColumns.SAVEPOINT_TIMESTAMP).append(")").append(" and ")
            .append(DataTableColumns.SAVEPOINT_TYPE).append("=?");

        String[] selectionArgs = new String[] { instanceId, "COMPLETE" };
        FileSet freturn = new FileSet(appName);

        String datestamp = null;

        try {
          c = db.rawQuery(b.toString(), selectionArgs);
          b.setLength(0);

          if (c.moveToFirst() && c.getCount() == 1) {
            String rowETag = null;
            String filterType = null;
            String filterValue = null;
            String formId = null;
            String locale = null;
            String savepointType = null;
            String savepointCreator = null;
            String savepointTimestamp = null;
            String instanceName = null;

            // OK. we have the record -- work through all the terms
            for (int i = 0; i < c.getColumnCount(); ++i) {
              String columnName = c.getColumnName(i);
              ColumnDefinition defn = ColumnDefinition.find(orderedDefns, columnName);
              if (defn != null && !c.isNull(i)) {
                if (xmlInstanceName != null && defn.getElementName().equals(xmlInstanceName)) {
                  instanceName = ODKDatabaseUtils.get().getIndexAsString(c, i);
                }
                // user-defined column
                ElementType type = defn.getType();
                ElementDataType dataType = type.getDataType();

                log.i(t, "element type: " + defn.getElementType());
                if (dataType == ElementDataType.integer) {
                  Integer value = ODKDatabaseUtils.get().getIndexAsType(c, Integer.class, i);
                  putElementValue(values, defn, value);
                } else if (dataType == ElementDataType.number) {
                  Double value = ODKDatabaseUtils.get().getIndexAsType(c, Double.class, i);
                  putElementValue(values, defn, value);
                } else if (dataType == ElementDataType.bool) {
                  Integer tmp = ODKDatabaseUtils.get().getIndexAsType(c, Integer.class, i);
                  Boolean value = tmp == null ? null : (tmp != 0);
                  putElementValue(values, defn, value);
                } else if (type.getElementType().equals("date")) {
                  String value = ODKDatabaseUtils.get().getIndexAsString(c, i);
                  String jrDatestamp = (value == null) ? null : (new SimpleDateFormat(
                      ISO8601_DATE_ONLY_FORMAT, Locale.ENGLISH)).format(new Date(TableConstants
                      .milliSecondsFromNanos(value)));
                  putElementValue(values, defn, jrDatestamp);
                } else if (type.getElementType().equals("dateTime")) {
                  String value = ODKDatabaseUtils.get().getIndexAsString(c, i);
                  String jrDatestamp = (value == null) ? null : (new SimpleDateFormat(
                      ISO8601_DATE_FORMAT, Locale.ENGLISH)).format(new Date(TableConstants
                      .milliSecondsFromNanos(value)));
                  putElementValue(values, defn, jrDatestamp);
                } else if (type.getElementType().equals("time")) {
                  String value = ODKDatabaseUtils.get().getIndexAsString(c, i);
                  putElementValue(values, defn, value);
                } else if (dataType == ElementDataType.array) {
                  ArrayList<Object> al = ODKDatabaseUtils.get().getIndexAsType(c, ArrayList.class,
                      i);
                  putElementValue(values, defn, al);
                } else if (dataType == ElementDataType.string) {
                  String value = ODKDatabaseUtils.get().getIndexAsString(c, i);
                  putElementValue(values, defn, value);
                } else /* unrecognized */{
                  throw new IllegalStateException("unrecognized data type: "
                      + defn.getElementType());
                }

              } else if (columnName.equals(DataTableColumns.SAVEPOINT_TIMESTAMP)) {
                savepointTimestamp = ODKDatabaseUtils.get().getIndexAsString(c, i);
              } else if (columnName.equals(DataTableColumns.ROW_ETAG)) {
                rowETag = ODKDatabaseUtils.get().getIndexAsString(c, i);
              } else if (columnName.equals(DataTableColumns.FILTER_TYPE)) {
                filterType = ODKDatabaseUtils.get().getIndexAsString(c, i);
              } else if (columnName.equals(DataTableColumns.FILTER_VALUE)) {
                filterValue = ODKDatabaseUtils.get().getIndexAsString(c, i);
              } else if (columnName.equals(DataTableColumns.FORM_ID)) {
                formId = ODKDatabaseUtils.get().getIndexAsString(c, i);
              } else if (columnName.equals(DataTableColumns.LOCALE)) {
                locale = ODKDatabaseUtils.get().getIndexAsString(c, i);
              } else if (columnName.equals(DataTableColumns.FORM_ID)) {
                formId = ODKDatabaseUtils.get().getIndexAsString(c, i);
              } else if (columnName.equals(DataTableColumns.SAVEPOINT_TYPE)) {
                savepointType = ODKDatabaseUtils.get().getIndexAsString(c, i);
              } else if (columnName.equals(DataTableColumns.SAVEPOINT_CREATOR)) {
                savepointCreator = ODKDatabaseUtils.get().getIndexAsString(c, i);
              }
            }

            // OK got all the values into the values map -- emit
            // contents
            b.setLength(0);
            File submissionXml = new File(ODKFileUtils.getInstanceFolder(appName, tableId,
                instanceId), (asXml ? "submission.xml" : "submission.json"));
            File manifest = new File(ODKFileUtils.getInstanceFolder(appName, tableId, instanceId),
                "manifest.json");
            submissionXml.delete();
            manifest.delete();
            freturn.instanceFile = submissionXml;

            if (asXml) {
              // Pre-processing -- collapse all geopoints into a
              // string-valued representation
              for (ColumnDefinition defn : orderedDefns) {
                ElementType type = defn.getType();
                ElementDataType dataType = type.getDataType();
                if (dataType == ElementDataType.object
                    && (type.getElementType().equals("geopoint") || type.getElementType().equals(
                        "mimeUri"))) {
                  Map<String, Object> parent = null;
                  List<ColumnDefinition> parents = new ArrayList<ColumnDefinition>();
                  ColumnDefinition d = defn.getParent();
                  while (d != null) {
                    parents.add(d);
                    d = d.getParent();
                  }
                  parent = values;
                  for (int i = parents.size() - 1; i >= 0; --i) {
                    Object o = parent.get(parents.get(i).getElementName());
                    if (o == null) {
                      parent = null;
                      break;
                    }
                    parent = (Map<String, Object>) o;
                  }
                  if (parent != null) {
                    Object o = parent.get(defn.getElementName());
                    if (o != null) {
                      if (type.getElementType().equals("geopoint")) {
                        Map<String, Object> geopoint = (Map<String, Object>) o;
                        // OK. we have geopoint -- get the
                        // lat, long, alt, etc.
                        Double latitude = (Double) geopoint.get("latitude");
                        Double longitude = (Double) geopoint.get("longitude");
                        Double altitude = (Double) geopoint.get("altitude");
                        Double accuracy = (Double) geopoint.get("accuracy");
                        String gpt = "" + latitude + " " + longitude + " " + altitude + " "
                            + accuracy;
                        parent.put(defn.getElementName(), gpt);
                      } else if (type.getElementType().equals("mimeUri")) {
                        Map<String, Object> mimeuri = (Map<String, Object>) o;
                        String uriFragment = (String) mimeuri.get("uriFragment");
                        String contentType = (String) mimeuri.get("contentType");

                        if (uriFragment != null) {
                          File f = ODKFileUtils.getAsFile(appName, uriFragment);
                          if (f.equals(manifest)) {
                            throw new IllegalStateException(
                                "Unexpected collision with manifest.json");
                          }
                          freturn.addAttachmentFile(f, contentType);
                          parent.put(defn.getElementName(), f.getName());
                        }
                      } else {
                        throw new IllegalStateException("Unhandled transform case");
                      }
                    }
                  }
                }
              }

              datestamp = (new SimpleDateFormat(ISO8601_DATE_FORMAT, Locale.ENGLISH))
                  .format(new Date(TableConstants.milliSecondsFromNanos(savepointTimestamp)));

              // For XML, we traverse the map to serialize it
              Document d = new Document();
              d.setStandalone(true);
              d.setEncoding(CharEncoding.UTF_8);
              Element e = d.createElement(XML_DEFAULT_NAMESPACE,
                  (xmlRootElementName == null) ? "data" : xmlRootElementName);
              e.setPrefix("jr", XML_OPENROSA_NAMESPACE);
              e.setPrefix("", XML_DEFAULT_NAMESPACE);
              d.addChild(0, Node.ELEMENT, e);
              e.setAttribute("", "id", tableId);
              DynamicPropertiesCallback cb = new DynamicPropertiesCallback(getContext(), appName,
                  tableId, instanceId);

              int idx = 0;
              Element meta = d.createElement(XML_OPENROSA_NAMESPACE, "meta");

              Element v = d.createElement(XML_OPENROSA_NAMESPACE, "instanceID");
              v.addChild(0, Node.TEXT, submissionInstanceId);
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              if (xmlDeviceIdPropertyName != null) {
                String deviceId = propertyManager.getSingularProperty(xmlDeviceIdPropertyName, cb);
                if (deviceId != null) {
                  v = d.createElement(XML_OPENROSA_NAMESPACE, "deviceID");
                  v.addChild(0, Node.TEXT, deviceId);
                  meta.addChild(idx++, Node.ELEMENT, v);
                  meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);
                }
              }
              if (xmlUserIdPropertyName != null) {
                String userId = propertyManager.getSingularProperty(xmlUserIdPropertyName, cb);
                if (userId != null) {
                  v = d.createElement(XML_OPENROSA_NAMESPACE, "userID");
                  v.addChild(0, Node.TEXT, userId);
                  meta.addChild(idx++, Node.ELEMENT, v);
                  meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);
                }
              }
              v = d.createElement(XML_OPENROSA_NAMESPACE, "timeEnd");
              v.addChild(0, Node.TEXT, datestamp);
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              // these are extra metadata tags...
              if (instanceName != null) {
                v = d.createElement(XML_DEFAULT_NAMESPACE, "instanceName");
                v.addChild(0, Node.TEXT, instanceName);
                meta.addChild(idx++, Node.ELEMENT, v);
                meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);
              } else {
                v = d.createElement(XML_DEFAULT_NAMESPACE, "instanceName");
                v.addChild(0, Node.TEXT, savepointTimestamp);
                meta.addChild(idx++, Node.ELEMENT, v);
                meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);
              }

              // these are extra metadata tags...
              // rowID
              v = d.createElement(XML_DEFAULT_NAMESPACE, "rowID");
              v.addChild(0, Node.TEXT, instanceId);
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              // rowETag
              v = d.createElement(XML_DEFAULT_NAMESPACE, "rowETag");
              if (rowETag != null) {
                v.addChild(0, Node.TEXT, rowETag);
              }
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              // filterType
              v = d.createElement(XML_DEFAULT_NAMESPACE, "filterType");
              if (filterType != null) {
                v.addChild(0, Node.TEXT, filterType);
              }
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              // filterValue
              v = d.createElement(XML_DEFAULT_NAMESPACE, "filterValue");
              if (filterValue != null) {
                v.addChild(0, Node.TEXT, filterValue);
              }
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              // formID
              v = d.createElement(XML_DEFAULT_NAMESPACE, "formID");
              v.addChild(0, Node.TEXT, formId);
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              // locale
              v = d.createElement(XML_DEFAULT_NAMESPACE, "locale");
              v.addChild(0, Node.TEXT, locale);
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              // savepointType
              v = d.createElement(XML_DEFAULT_NAMESPACE, "savepointType");
              v.addChild(0, Node.TEXT, savepointType);
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              // savepointCreator
              v = d.createElement(XML_DEFAULT_NAMESPACE, "savepointCreator");
              if (savepointCreator != null) {
                v.addChild(0, Node.TEXT, savepointCreator);
              }
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              // savepointTimestamp
              v = d.createElement(XML_DEFAULT_NAMESPACE, "savepointTimestamp");
              v.addChild(0, Node.TEXT, savepointTimestamp);
              meta.addChild(idx++, Node.ELEMENT, v);
              meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              // and insert the meta block into the XML

              e.addChild(0, Node.IGNORABLE_WHITESPACE, NEW_LINE);
              e.addChild(1, Node.ELEMENT, meta);
              e.addChild(2, Node.IGNORABLE_WHITESPACE, NEW_LINE);

              idx = 3;
              ArrayList<String> entryNames = new ArrayList<String>();
              entryNames.addAll(values.keySet());
              Collections.sort(entryNames);
              for (String name : entryNames) {
                idx = generateXmlHelper(d, e, idx, name, values, log);
              }

              KXmlSerializer serializer = new KXmlSerializer();

              ByteArrayOutputStream bo = new ByteArrayOutputStream();
              serializer.setOutput(bo, CharEncoding.UTF_8);
              // setting the response content type emits the
              // xml header.
              // just write the body here...
              d.writeChildren(serializer);
              serializer.flush();
              bo.close();

              b.append(bo.toString(CharEncoding.UTF_8));

              // OK we have the document in the builder (b).
              String doc = b.toString();

              freturn.instanceFile = submissionXml;

              // see if the form is encrypted and we can
              // encrypt it...
              EncryptedFormInformation formInfo = EncryptionUtils.getEncryptedFormInformation(
                  tableId, xmlBase64RsaPublicKey, instanceId);
              if (formInfo != null) {
                File submissionXmlEnc = new File(submissionXml.getParentFile(),
                    submissionXml.getName() + ".enc");
                submissionXmlEnc.delete();
                // if we are encrypting, the form cannot be
                // reopened afterward
                // and encrypt the submission (this is a
                // one-way operation)...
                if (!EncryptionUtils.generateEncryptedSubmission(freturn, doc, submissionXml,
                    submissionXmlEnc, formInfo)) {
                  return null;
                }
                // at this point, the freturn object has
                // been re-written with the encrypted media
                // and xml files.
              } else {
                exportFile(doc, submissionXml, log);
              }

            } else {
              // Pre-processing -- collapse all mimeUri into filename
              for (ColumnDefinition defn : orderedDefns) {
                ElementType type = defn.getType();
                ElementDataType dataType = type.getDataType();

                if (dataType == ElementDataType.object && type.getElementType().equals("mimeUri")) {
                  Map<String, Object> parent = null;
                  List<ColumnDefinition> parents = new ArrayList<ColumnDefinition>();
                  ColumnDefinition d = defn.getParent();
                  while (d != null) {
                    parents.add(d);
                    d = d.getParent();
                  }
                  parent = values;
                  for (int i = parents.size() - 1; i >= 0; --i) {
                    Object o = parent.get(parents.get(i).getElementName());
                    if (o == null) {
                      parent = null;
                      break;
                    }
                    parent = (Map<String, Object>) o;
                  }
                  if (parent != null) {
                    Object o = parent.get(defn.getElementName());
                    if (o != null) {
                      if (dataType == ElementDataType.object
                          && type.getElementType().equals("mimeUri")) {
                        Map<String, Object> mimeuri = (Map<String, Object>) o;
                        String uriFragment = (String) mimeuri.get("uriFragment");
                        String contentType = (String) mimeuri.get("contentType");
                        File f = ODKFileUtils.getAsFile(appName, uriFragment);
                        if (f.equals(manifest)) {
                          throw new IllegalStateException("Unexpected collision with manifest.json");
                        }
                        freturn.addAttachmentFile(f, contentType);
                        parent.put(defn.getElementName(), f.getName());
                      } else {
                        throw new IllegalStateException("Unhandled transform case");
                      }
                    }
                  }
                }
              }

              // For JSON, we construct the model, then emit model +
              // meta + data
              HashMap<String, Object> wrapper = new HashMap<String, Object>();
              wrapper.put("tableId", tableId);
              wrapper.put("instanceId", instanceId);
              HashMap<String, Object> formDef = new HashMap<String, Object>();
              formDef.put("table_id", tableId);
              formDef.put("model", ColumnDefinition.getDataModel(orderedDefns));
              wrapper.put("formDef", formDef);
              wrapper.put("data", values);
              wrapper.put("metadata", new HashMap<String, Object>());
              HashMap<String, Object> elem = (HashMap<String, Object>) wrapper.get("metadata");
              if (instanceName != null) {
                elem.put("instanceName", instanceName);
              }
              elem.put("saved", "COMPLETE");
              elem.put("timestamp", datestamp);

              b.append(ODKFileUtils.mapper.writeValueAsString(wrapper));

              // OK we have the document in the builder (b).
              String doc = b.toString();
              exportFile(doc, submissionXml, log);
            }
            exportFile(freturn.serializeUriFragmentList(getContext()), manifest, log);
            return ParcelFileDescriptor.open(manifest, ParcelFileDescriptor.MODE_READ_ONLY);

          }
        } finally {
          if (c != null && !c.isClosed()) {
            c.close();
          }
        }

      } catch (JsonParseException e) {
        e.printStackTrace();
      } catch (JsonMappingException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

    } finally {
      if (db != null) {
        db.close();
      }
    }
    return null;
  }

  /**
   * This method actually writes the JSON appName-relative manifest to disk.
   *
   * @param payload
   * @param path
   * @return
   */
  private static boolean exportFile(String payload, File outputFilePath, WebLogger log) {
    // write xml file
    FileOutputStream os = null;
    OutputStreamWriter osw = null;
    try {
      os = new FileOutputStream(outputFilePath, false);
      osw = new OutputStreamWriter(os, CharEncoding.UTF_8);
      osw.write(payload);
      osw.flush();
      osw.close();
      return true;

    } catch (IOException e) {
      log.e(t, "Error writing file");
      e.printStackTrace();
      try {
        osw.close();
        os.close();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
      return false;
    }
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public String getType(Uri uri) {
    return null;
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
      String sortOrder) {
    return null;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }

}
