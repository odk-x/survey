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

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.opendatakit.common.android.database.DataModelDatabaseHelper;
import org.opendatakit.common.android.database.DataModelDatabaseHelper.ColumnDefinition;
import org.opendatakit.common.android.database.DataModelDatabaseHelper.IdInstanceNameStruct;
import org.opendatakit.common.android.logic.FormInfo;
import org.opendatakit.common.android.logic.PropertyManager;
import org.opendatakit.common.android.provider.DataTableColumns;
import org.opendatakit.common.android.provider.FileProvider;
import org.opendatakit.common.android.provider.FormsColumns;
import org.opendatakit.common.android.provider.impl.CommonContentProvider;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.logic.DynamicPropertiesCallback;
import org.opendatakit.survey.android.utilities.EncryptionUtils;
import org.opendatakit.survey.android.utilities.EncryptionUtils.EncryptedFormInformation;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

/**
 * The WebKit does better if there is a content provider vending files to it.
 * This provider vends files under the Forms and Instances directories (only).
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class SubmissionProvider extends CommonContentProvider {
  private static final String ISO8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";

  private static final String t = "SubmissionProvider";

  public static final String XML_SUBMISSION_AUTHORITY = "org.opendatakit.survey.android.provider.submission.xml";
  public static final String JSON_SUBMISSION_AUTHORITY = "org.opendatakit.survey.android.provider.submission.json";

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
    ColumnDefinition cur = defn.parent;
    while (cur != null) {
      nesting.add(cur);
      cur = cur.parent;
    }

    HashMap<String, Object> elem = dataMap;
    for (int i = nesting.size() - 1; i >= 0; --i) {
      cur = nesting.get(i);
      if (elem.containsKey(cur.elementName)) {
        elem = (HashMap<String, Object>) elem.get(cur.elementName);
      } else {
        elem.put(cur.elementName, new HashMap<String, Object>());
        elem = (HashMap<String, Object>) elem.get(cur.elementName);
      }
    }
    elem.put(defn.elementName, value);
  }

  @SuppressWarnings("unchecked")
  private static final int generateXmlHelper(Document d, Element data, int idx, String key,
      Map<String, Object> values) {
    Object o = values.get(key);

    Element e = d.createElement(XML_DEFAULT_NAMESPACE, key);

    if (o == null) {
      Log.e(t, "Unexpected null value");
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
        nidx = generateXmlHelper(d, e, nidx, name, m);
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

    if (segments.size() != 3) {
      throw new IllegalArgumentException("Unknown URI (incorrect number of path segments!) " + uri);
    }

    final String appName = segments.get(0);
    final String tableId = segments.get(1);
    final String instanceId = (segments.size() >= 3 ? segments.get(2) : null);
    final String formId = uri.getQueryParameter("formId");
    final String formVersion = uri.getQueryParameter("formVersion");
    final SQLiteDatabase db = getDbHelper(getContext(), appName).getReadableDatabase();

    String tableName = DataModelDatabaseHelper.getDbTableName(db, tableId);
    if (tableName == null) {
      throw new IllegalArgumentException("Unknown URI (no matching tableId) " + uri);
    }

    final String dbTableName = "\"" + tableName + "\"";

    try {
      // get map of (elementKey -> ColumnDefinition)
      Map<String, ColumnDefinition> defns = DataModelDatabaseHelper.getColumnDefinitions(db,
          tableId);
      // get the id struct
      IdInstanceNameStruct ids = DataModelDatabaseHelper.getIds(db, formId);

      HashMap<String, Object> values = new HashMap<String, Object>();

      // issue query to retrieve the instanceId
      StringBuilder b = new StringBuilder();
      b.append("SELECT * FROM ").append(dbTableName).append(" WHERE ").append(DataTableColumns.ID)
          .append("=?").append(" group by ").append(DataTableColumns.ID).append(" having ")
          .append(DataTableColumns.SAVEPOINT_TIMESTAMP).append("=max(").append(DataTableColumns.SAVEPOINT_TIMESTAMP)
          .append(")").append(" and ").append(DataTableColumns.SAVEPOINT_TYPE).append("=?");

      String[] selectionArgs = new String[] { instanceId, "COMPLETE" };
      Cursor c = null;
      FileSet freturn = new FileSet(appName);

      try {
        c = db.rawQuery(b.toString(), selectionArgs);
        b.setLength(0);

        if (c.moveToFirst() && c.getCount() == 1) {
          Long timestamp = null;
          String instanceName = null;
          String formStateId = null;
          // OK. we have the record -- work through all the terms
          for (int i = 0; i < c.getColumnCount(); ++i) {
            String columnName = c.getColumnName(i);
            ColumnDefinition defn = defns.get(columnName);
            if (defn != null && !c.isNull(i)) {
              if ( defn.elementName == ids.instanceName ) {
                instanceName = c.getString(i);
              }
              // user-defined column
              Log.i(t, "element type: " + defn.elementType);
              if (defn.elementType.equals("string")) {
                String value = c.getString(i);
                putElementValue(values, defn, value);
              } else if (defn.elementType.equals("integer")) {
                Integer value = c.getInt(i);
                putElementValue(values, defn, value);
              } else if (defn.elementType.equals("number")) {
                Double value = c.getDouble(i);
                putElementValue(values, defn, value);
              } else if (defn.elementType.equals("boolean")) {
                Boolean value = (c.getInt(i) != 0);
                putElementValue(values, defn, value);
              } else if (defn.elementType.equals("date")) {
                String value = c.getString(i);
                putElementValue(values, defn, value);
              } else if (defn.elementType.equals("dateTime")) {
                String value = c.getString(i);
                putElementValue(values, defn, value);
              } else if (defn.elementType.equals("time")) {
                String value = c.getString(i);
                putElementValue(values, defn, value);
              } else if (defn.elementType.equals("array")) {
                String valueString = c.getString(i);
                ArrayList<Object> al = ODKFileUtils.mapper.readValue(valueString, ArrayList.class);
                putElementValue(values, defn, al);
              } else if (defn.elementType.equals("object")) {
                String valueString = c.getString(i);
                HashMap<String, Object> obj = ODKFileUtils.mapper.readValue(valueString,
                    HashMap.class);
                putElementValue(values, defn, obj);
              } else /* user-defined */{
                Log.i(t, "user-defined element type: " + defn.elementType);
                String valueString = c.getString(i);
                HashMap<String, Object> obj = ODKFileUtils.mapper.readValue(valueString,
                    HashMap.class);
                putElementValue(values, defn, obj);
              }

            } else if (columnName.equals(DataTableColumns.SAVEPOINT_TIMESTAMP)) {
              timestamp = c.getLong(i);
            } else if (columnName.equals(DataTableColumns.FORM_ID)) {
              formStateId = c.getString(i);
            }
          }

          // OK got all the values into the values map -- emit
          // contents
          b.setLength(0);
          File submissionXml = new File(
              ODKFileUtils.getInstanceFolder(appName, tableId, instanceId),
              (asXml ? "submission.xml" : "submission.json"));
          File manifest = new File(ODKFileUtils.getInstanceFolder(appName, tableId, instanceId),
              "manifest.json");
          submissionXml.delete();
          manifest.delete();
          freturn.instanceFile = submissionXml;

          if (asXml) {
            // Pre-processing -- collapse all geopoints into a
            // string-valued representation
            for (ColumnDefinition defn : defns.values()) {
              if (defn.elementType.equals("geopoint") || defn.elementType.equals("mimeUri")) {
                Map<String, Object> parent = null;
                List<ColumnDefinition> parents = new ArrayList<ColumnDefinition>();
                ColumnDefinition d = defn.parent;
                while (d != null) {
                  parents.add(d);
                  d = d.parent;
                }
                parent = values;
                for (int i = parents.size() - 1; i >= 0; --i) {
                  Object o = parent.get(parents.get(i).elementName);
                  if (o == null) {
                    parent = null;
                    break;
                  }
                  parent = (Map<String, Object>) o;
                }
                if (parent != null) {
                  Object o = parent.get(defn.elementName);
                  if (o != null) {
                    if (defn.elementType.equals("geopoint")) {
                      Map<String, Object> geopoint = (Map<String, Object>) o;
                      // OK. we have geopoint -- get the
                      // lat, long, alt, etc.
                      Double latitude = (Double) geopoint.get("latitude");
                      Double longitude = (Double) geopoint.get("longitude");
                      Double altitude = (Double) geopoint.get("altitude");
                      Double accuracy = (Double) geopoint.get("accuracy");
                      String gpt = "" + latitude + " " + longitude + " " + altitude + " "
                          + accuracy;
                      parent.put(defn.elementName, gpt);
                    } else if (defn.elementType.equals("mimeUri")) {
                      Map<String, Object> mimeuri = (Map<String, Object>) o;
                      String uriFragment = (String) mimeuri.get("uriFragment");
                      String contentType = (String) mimeuri.get("contentType");

                      File f = FileProvider.getAsFile(getContext(),
                          FileProvider.getAsUri(getContext(), appName, uriFragment));
                      if (f.equals(manifest)) {
                        throw new IllegalStateException("Unexpected collision with manifest.json");
                      }
                      freturn.addAttachmentFile(f, contentType);
                      parent.put(defn.elementName, f.getName());
                    } else {
                      throw new IllegalStateException("Unhandled transform case");
                    }
                  }
                }
              }
            }

            // Need to get Form definition in order to get info on
            // XML structure...
            String formSelection = FormsColumns.FORM_ID
                + "=?"
                + ((formVersion == null) ? (" AND " + FormsColumns.FORM_VERSION + " IS NULL")
                    : (" AND " + FormsColumns.FORM_VERSION + "=?"));
            String[] formSelectionArgs;
            if (formVersion == null) {
              String[] t = { formId };
              formSelectionArgs = t;
            } else {
              String[] t = { formId, formVersion };
              formSelectionArgs = t;
            }

            Cursor fc = null;
            try {
              fc = getContext().getContentResolver().query(
                  Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName), null, formSelection,
                  formSelectionArgs, null);
              if (fc != null && fc.moveToFirst() && fc.getCount() == 1) {

                FormInfo f = new FormInfo(appName, fc, false);
                fc.close();

                String datestamp = (new SimpleDateFormat(ISO8601_DATE_FORMAT, Locale.ENGLISH))
                    .format(new Date(timestamp));

                // For XML, we traverse the map to serialize it
                Document d = new Document();
                d.setStandalone(true);
                d.setEncoding("UTF-8");
                Element e = d.createElement(XML_DEFAULT_NAMESPACE,
                    (f.xmlRootElementName == null) ? "data" : f.xmlRootElementName);
                e.setPrefix("jr", XML_OPENROSA_NAMESPACE);
                e.setPrefix("", XML_DEFAULT_NAMESPACE);
                d.addChild(0, Node.ELEMENT, e);
                e.setAttribute("", "id", f.formId);
                if (f.formVersion != null) {
                  e.setAttribute("", "version", f.formVersion);
                }
                DynamicPropertiesCallback cb = new DynamicPropertiesCallback(getContext(), appName,
                    tableId, instanceId);

                int idx = 0;
                Element meta = d.createElement(XML_OPENROSA_NAMESPACE, "meta");
                Element v = d.createElement(XML_OPENROSA_NAMESPACE, "instanceID");
                v.addChild(0, Node.TEXT, instanceId);
                meta.addChild(idx++, Node.ELEMENT, v);
                meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);
                if (f.xmlDeviceIdPropertyName != null) {
                  String deviceId = propertyManager.getSingularProperty(f.xmlDeviceIdPropertyName,
                      cb);
                  if (deviceId != null) {
                    v = d.createElement(XML_OPENROSA_NAMESPACE, "deviceID");
                    v.addChild(0, Node.TEXT, deviceId);
                    meta.addChild(idx++, Node.ELEMENT, v);
                    meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);
                  }
                }
                if (f.xmlUserIdPropertyName != null) {
                  String userId = propertyManager.getSingularProperty(f.xmlUserIdPropertyName, cb);
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
                if ( instanceName != null ) {
                  v = d.createElement(XML_DEFAULT_NAMESPACE, "instanceName");
                  v.addChild(0, Node.TEXT, instanceName);
                  meta.addChild(idx++, Node.ELEMENT, v);
                  meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);
                }

                // these are extra metadata tags...
                v = d.createElement(XML_DEFAULT_NAMESPACE, "formID");
                v.addChild(0, Node.TEXT, formStateId);
                meta.addChild(idx++, Node.ELEMENT, v);
                meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

                // we may want to track incomplete or partial submissions in the
                // future...
                v = d.createElement(XML_DEFAULT_NAMESPACE, "savepointType");
                v.addChild(0, Node.TEXT, "COMPLETE");
                meta.addChild(idx++, Node.ELEMENT, v);
                meta.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);
                e.addChild(0, Node.IGNORABLE_WHITESPACE, NEW_LINE);
                e.addChild(1, Node.ELEMENT, meta);
                e.addChild(2, Node.IGNORABLE_WHITESPACE, NEW_LINE);

                idx = 3;
                ArrayList<String> entryNames = new ArrayList<String>();
                entryNames.addAll(values.keySet());
                Collections.sort(entryNames);
                for (String name : entryNames) {
                  idx = generateXmlHelper(d, e, idx, name, values);
                }

                KXmlSerializer serializer = new KXmlSerializer();

                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                serializer.setOutput(bo, "UTF-8");
                // setting the response content type emits the
                // xml header.
                // just write the body here...
                d.writeChildren(serializer);
                serializer.flush();
                bo.close();

                b.append(bo.toString("UTF-8"));

                // OK we have the document in the builder (b).
                String doc = b.toString();

                freturn.instanceFile = submissionXml;

                // see if the form is encrypted and we can
                // encrypt it...
                EncryptedFormInformation formInfo = EncryptionUtils.getEncryptedFormInformation(f,
                    instanceId);
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
                  exportFile(doc, submissionXml);
                }
              }
            } finally {
              if (fc != null && !fc.isClosed()) {
                fc.close();
              }
            }

          } else {
            // Pre-processing -- collapse all mimeUri into filename
            for (ColumnDefinition defn : defns.values()) {
              if (defn.elementType.equals("mimeUri")) {
                Map<String, Object> parent = null;
                List<ColumnDefinition> parents = new ArrayList<ColumnDefinition>();
                ColumnDefinition d = defn.parent;
                while (d != null) {
                  parents.add(d);
                  d = d.parent;
                }
                parent = values;
                for (int i = parents.size() - 1; i >= 0; --i) {
                  Object o = parent.get(parents.get(i).elementName);
                  if (o == null) {
                    parent = null;
                    break;
                  }
                  parent = (Map<String, Object>) o;
                }
                if (parent != null) {
                  Object o = parent.get(defn.elementName);
                  if (o != null) {
                    if (defn.elementType.equals("mimeUri")) {
                      Map<String, Object> mimeuri = (Map<String, Object>) o;
                      String uriFragment = (String) mimeuri.get("uriFragment");
                      String contentType = (String) mimeuri.get("contentType");
                      File f = FileProvider.getAsFile(getContext(),
                          FileProvider.getAsUri(getContext(), appName, uriFragment));
                      if (f.equals(manifest)) {
                        throw new IllegalStateException("Unexpected collision with manifest.json");
                      }
                      freturn.addAttachmentFile(f, contentType);
                      parent.put(defn.elementName, f.getName());
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
            formDef.put("model", DataModelDatabaseHelper.getDataModel(defns));
            wrapper.put("formDef", formDef);
            wrapper.put("data", values);
            wrapper.put("metadata", new HashMap<String, Object>());
            HashMap<String, Object> elem = (HashMap<String, Object>) wrapper.get("metadata");
            if ( instanceName != null ) {
              elem.put("instanceName", instanceName);
            }
            elem.put("saved", "COMPLETE");
            String datestamp = (new SimpleDateFormat(ISO8601_DATE_FORMAT, Locale.ENGLISH))
                .format(new Date(timestamp));
            elem.put("timestamp", datestamp);

            b.append(ODKFileUtils.mapper.writeValueAsString(wrapper));

            // OK we have the document in the builder (b).
            String doc = b.toString();
            exportFile(doc, submissionXml);
          }
          exportFile(freturn.serialize(getContext()), manifest);
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
    return null;
  }

  /**
   * This method actually writes the xml to disk.
   *
   * @param payload
   * @param path
   * @return
   */
  private static boolean exportFile(String payload, File outputFilePath) {
    // write xml file
    FileOutputStream os = null;
    OutputStreamWriter osw = null;
    try {
      os = new FileOutputStream(outputFilePath, false);
      osw = new OutputStreamWriter(os, "UTF-8");
      osw.write(payload);
      osw.flush();
      osw.close();
      return true;

    } catch (IOException e) {
      Log.e(t, "Error writing file");
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
