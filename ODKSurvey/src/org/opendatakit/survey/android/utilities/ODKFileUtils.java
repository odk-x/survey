/*
 * Copyright (C) 2009 University of Washington
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

package org.opendatakit.survey.android.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kxml2.io.KXmlParser;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * Static methods used for common file operations.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class ODKFileUtils {
    private final static String t = "FileUtils";

    // Used to validate and display valid form names.
    public static final String VALID_FILENAME = "[ _\\-A-Za-z0-9]*.x[ht]*ml";

    
    public static boolean createFolder(String path) {
        boolean made = true;
        File dir = new File(path);
        if (!dir.exists()) {
            made = dir.mkdirs();
        }
        return made;
    }


    public static byte[] getFileAsBytes(File file) {
        byte[] bytes = null;
        InputStream is = null;
        try {
            is = new FileInputStream(file);

            // Get the size of the file
            long length = file.length();
            if (length > Integer.MAX_VALUE) {
                Log.e(t, "File " + file.getName() + "is too large");
                return null;
            }

            // Create the byte array to hold the data
            bytes = new byte[(int) length];

            // Read in the bytes
            int offset = 0;
            int read = 0;
            try {
                while (offset < bytes.length && read >= 0) {
                    read = is.read(bytes, offset, bytes.length - offset);
                    offset += read;
                }
            } catch (IOException e) {
                Log.e(t, "Cannot read " + file.getName());
                e.printStackTrace();
                return null;
            }

            // Ensure all the bytes have been read in
            if (offset < bytes.length) {
                try {
                    throw new IOException("Could not completely read file " + file.getName());
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            return bytes;

        } catch (FileNotFoundException e) {
            Log.e(t, "Cannot find " + file.getName());
            e.printStackTrace();
            return null;

        } finally {
            // Close the input stream
            try {
                is.close();
            } catch (IOException e) {
                Log.e(t, "Cannot close input stream for " + file.getName());
                e.printStackTrace();
                return null;
            }
        }
    }


    public static String getMd5Hash(File file) {
        try {
            // CTS (6/15/2010) : stream file through digest instead of handing it the byte[]
            MessageDigest md = MessageDigest.getInstance("MD5");
            int chunkSize = 256;

            byte[] chunk = new byte[chunkSize];

            // Get the size of the file
            long lLength = file.length();

            if (lLength > Integer.MAX_VALUE) {
                Log.e(t, "File " + file.getName() + "is too large");
                return null;
            }

            int length = (int) lLength;

            InputStream is = null;
            is = new FileInputStream(file);

            int l = 0;
            for (l = 0; l + chunkSize < length; l += chunkSize) {
                is.read(chunk, 0, chunkSize);
                md.update(chunk, 0, chunkSize);
            }

            int remaining = length - l;
            if (remaining > 0) {
                is.read(chunk, 0, remaining);
                md.update(chunk, 0, remaining);
            }
            byte[] messageDigest = md.digest();

            BigInteger number = new BigInteger(1, messageDigest);
            String md5 = number.toString(16);
            while (md5.length() < 32)
                md5 = "0" + md5;
            is.close();
            return md5;

        } catch (NoSuchAlgorithmException e) {
            Log.e("MD5", e.getMessage());
            return null;

        } catch (FileNotFoundException e) {
            Log.e("No Cache File", e.getMessage());
            return null;
        } catch (IOException e) {
            Log.e("Problem reading from file", e.getMessage());
            return null;
        }

    }


    public static Bitmap getBitmapScaledToDisplay(File f, int screenHeight, int screenWidth) {
        // Determine image size of f
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);

        int heightScale = o.outHeight / screenHeight;
        int widthScale = o.outWidth / screenWidth;

        // Powers of 2 work faster, sometimes, according to the doc.
        // We're just doing closest size that still fills the screen.
        int scale = Math.max(widthScale, heightScale);

        // get bitmap with scale ( < 1 is the same as 1)
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = scale;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), options);
        if (b != null) {
        Log.i(t,
            "Screen is " + screenHeight + "x" + screenWidth + ".  Image has been scaled down by "
                    + scale + " to " + b.getHeight() + "x" + b.getWidth());
        }
        return b;
    }

//
//    public static boolean copyFile(File sourceFile, File destFile) {
//        if (sourceFile.exists()) {
//            FileChannel src;
//            try {
//                src = new FileInputStream(sourceFile).getChannel();
//                FileChannel dst = new FileOutputStream(destFile).getChannel();
//                dst.transferFrom(src, 0, src.size());
//                src.close();
//                dst.close();
//                return true;
//            } catch (FileNotFoundException e) {
//                Log.e(t, "FileNotFoundExeception while copying media file");
//                e.printStackTrace();
//                return false;
//            } catch (IOException e) {
//                Log.e(t, "IOExeception while copying media file");
//                e.printStackTrace();
//                return false;
//            }
//        } else {
//            Log.e(t, "Source file does not exist: " + sourceFile.getAbsolutePath());
//            return false;
//        }
//
//    }

    public static final String FORMID = "formid";
    public static final String VERSION = "version";
    public static final String TITLE = "title";
    public static final String SUBMISSIONURI = "submission";
    public static final String ROOTREF = "rootRef";
    public static final String SUBMISSIONREF = "submissionRef";
    public static final String ENCRYPTEDREF = "encryptedRef-"; // prepended to nodeset; present if encrypted
    public static final String INSTANCEIDREF = "instanceIDRef";
    public static final String FIELDKEYREF = "base64EncryptedFieldKeyRef";
    public static final String BASE64_RSA_PUBLIC_KEY = "base64RsaPublicKey";
    public static final String BASE64_ENCRYPTED_FIELD_RSA_PUBLIC_KEY = "base64EncryptedFieldRsaPublicKey";

    public static class ParseException extends RuntimeException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -3114591479411925707L;

		public ParseException(String errorMsg, Throwable t) {
			super(errorMsg, t);
		}
	};
    
	public static Document getXMLDocument(Reader reader)  {
		Document doc = new Document();

		try{
			KXmlParser parser = new KXmlParser();
			parser.setInput(reader);
			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
			doc.parse(parser);
		}  catch (XmlPullParserException e) {
		    String errorMsg = "XML Syntax Error at Line: " + e.getLineNumber() +", Column: "+ e.getColumnNumber()+ "!";
			System.err.println(errorMsg);
			e.printStackTrace();
			throw new ParseException(errorMsg, e);
		} catch(Exception e){
			//#if debug.output==verbose || debug.output==exception
		    String errorMsg = "Unhandled Exception while Parsing XForm";
		    System.err.println(errorMsg);
			e.printStackTrace();
			throw new ParseException(errorMsg, e);
			//#endif
		}

		try {
			reader.close();
		} catch (IOException e) {
			System.out.println("Error closing reader");
			e.printStackTrace();
		}
		
		return doc;
	}

	public static String getXMLText (Node n, boolean trim) {
		return (n.getChildCount() == 0 ? null : getXMLText(n, 0, trim));
	}

	/**
	* reads all subsequent text nodes and returns the combined string
	* needed because escape sequences are parsed into consecutive text nodes
	* e.g. "abc&amp;123" --> (abc)(&)(123)
	**/
	public static String getXMLText (Node node, int i, boolean trim) {
		StringBuffer strBuff = null;

		String text = node.getText(i);
		if (text == null)
			return null;

		for (i++; i < node.getChildCount() && node.getType(i) == Node.TEXT; i++) {
			if (strBuff == null)
				strBuff = new StringBuffer(text);

			strBuff.append(node.getText(i));
		}
		if (strBuff != null)
			text = strBuff.toString();

		if (trim)
			text = text.trim();

		return text;
	}

    public static Document parseXMLDocument(File xmlFile) {
        InputStream is;
        try {
            is = new FileInputStream(xmlFile);
        } catch (FileNotFoundException e1) {
            throw new IllegalStateException(e1);
        }

        InputStreamReader isr;
        try {
            isr = new InputStreamReader(is, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            Log.w(t, "UTF 8 encoding unavailable, trying default encoding");
            isr = new InputStreamReader(is);
        }

        Document doc;
        try {
            doc = getXMLDocument(isr);
            return doc;
        } finally {
            try {
                isr.close();
            } catch (IOException e) {
                Log.w(t, xmlFile.getAbsolutePath() + " Error closing form reader");
                e.printStackTrace();
            }
        }
    }

    public static HashMap<String, String> parseXML(File xmlFile) {
        HashMap<String, String> fields = new HashMap<String, String>();
        InputStream is;
        try {
            is = new FileInputStream(xmlFile);
        } catch (FileNotFoundException e1) {
            throw new IllegalStateException(e1);
        }

        InputStreamReader isr;
        try {
            isr = new InputStreamReader(is, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            Log.w(t, "UTF 8 encoding unavailable, trying default encoding");
            isr = new InputStreamReader(is);
        }

        if (isr != null) {

            Document doc;
            try {
                doc = getXMLDocument(isr);
            } finally {
                try {
                    isr.close();
                } catch (IOException e) {
                    Log.w(t, xmlFile.getAbsolutePath() + " Error closing form reader");
                    e.printStackTrace();
                }
            }

            String xforms = "http://www.w3.org/2002/xforms";
            String html = doc.getRootElement().getNamespace();
            
            Element head = doc.getRootElement().getElement(html, "head");
            Element title = head.getElement(html, "title");
            if (title != null) {
                fields.put(TITLE, getXMLText(title, true));
            } 
            
            Element model = getChildElement(head, "model");
            // TODO: this assumes the first instance element in XForms is the primary one. OK?
            Element cur = getChildElement(model,"instance");
            
            int idx = cur.getChildCount();
            int i;
            for (i = 0; i < idx; ++i) {
                if (cur.getType(i) == Node.ELEMENT) {
                    break;
                }
            }

            if (i < idx) {
                cur = cur.getElement(i); // this is the first data element
                fields.put(ROOTREF, cur.getName());
                String id = cur.getAttributeValue(null, "id");
                String xmlns = cur.getNamespace();
                String version = cur.getAttributeValue(null, "version");

                fields.put(FORMID, (id == null) ? xmlns : id);
                fields.put(VERSION, version);

            } else {
                throw new IllegalStateException(xmlFile.getAbsolutePath() + " could not be parsed");
            }
            try {
                Element submission = model.getElement(xforms, "submission");
                String submissionUri = submission.getAttributeValue(null, "action");
                String submissionKey = submission.getAttributeValue(null, BASE64_RSA_PUBLIC_KEY);
                // submissionRef is either the root of the form if not specified or
                // the specified reference with any leading slashes removed.
                String submissionRef = submission.getAttributeValue(null, "ref");
                submissionRef = (submissionRef==null) ? fields.get(ROOTREF) :
                			(submissionRef.startsWith("/") ? submissionRef.substring(1) : submissionRef);
                fields.put(SUBMISSIONURI, submissionUri);
                fields.put(BASE64_RSA_PUBLIC_KEY, submissionKey);
                fields.put(SUBMISSIONREF, submissionRef);
            } catch (Exception e) {
                Log.i(t, xmlFile.getAbsolutePath() + " does not have a submission element");
                // and that's totally fine.
                fields.put(SUBMISSIONREF, fields.get(ROOTREF));
            }
            
            String path = fields.get(SUBMISSIONREF);
            String[] elements = path.split("/");
            if ( !elements[0].equals(cur.getName()) ) {
            	throw new IllegalStateException("submission ref not contained within root");
            }
            Element sub = cur;
            for ( int j = 1 ; j < elements.length ; ++j ) {
            	for ( int k = 0 ; k < sub.getChildCount() ; ++k ) {
            		if ( sub.getType(k) != Node.ELEMENT ) continue;
            		Element e = sub.getElement(k);
            		if ( e.getName().equals(elements[j]) ) {
            			sub = e;
            			break;
            		}
            	}
            }
            // we have the submission block...
            Element meta = getMetaBlock(sub);
            if ( meta != null ) {
	            // find the instanceId key, if it has one...
	            Element instanceIdKey = null;
	            int k;
	            for ( k = 0 ; k < meta.getChildCount() ; ++k ) {
	        		if ( meta.getType(k) != Node.ELEMENT ) continue;
	        		Element eCandidate = meta.getElement(k);
	        		if ( eCandidate.getName().equals("instanceID") ) {
	        			instanceIdKey = eCandidate;
	        			break;
	        		}
	        	}
	            if ( instanceIdKey != null ) {
	            	// save the instanceID key...
		            String noderef = getRef(instanceIdKey, fields, sub);
		            fields.put(INSTANCEIDREF, noderef);
	            }
	            
	            // now find the encrypted field key, if it has one...
	            Element eFieldKey = null;
	            for ( k = 0 ; k < meta.getChildCount() ; ++k ) {
	        		if ( meta.getType(k) != Node.ELEMENT ) continue;
	        		Element eCandidate = meta.getElement(k);
	        		if ( eCandidate.getName().equals("base64EncryptedFieldKey") ) {
	        			eFieldKey = eCandidate;
	        			break;
	        		}
	        	}
	            
	            if ( eFieldKey != null ) {
	            	// looks like there is per-element encryption...
	            	
		            // this is the xpath to the encryption field key
		            String noderef = getRef(eFieldKey, fields, sub);
		            fields.put(FIELDKEYREF, noderef);
		        
		            // and now search through the bind elements to find the bind for that key
		            // so we can get the rsa public key with which to encrypt it...
		            // and also traverse all binds, finding those that are encrypted...
		            for ( int j = 0; j < model.getChildCount() ; ++j ) {
		            	if ( model.getType(j) != Node.ELEMENT ) continue;
		            	Element e = model.getElement(j);
		            	if ( e.getName().equals("bind") ) {
		            		String nodeset = e.getAttributeValue(null, "nodeset");
		            		if ( nodeset.equals(noderef) ) {
		            			// OK -- this is the bind for this element...
		            			String rsaKey = e.getAttributeValue(null, BASE64_RSA_PUBLIC_KEY);
		            			fields.put(BASE64_ENCRYPTED_FIELD_RSA_PUBLIC_KEY, rsaKey);
		            		}
		            		String v = e.getAttributeValue(null, "encrypted");
		    				if ( v != null && ("true".equalsIgnoreCase(v) || "true()".equalsIgnoreCase(v))) {
		    					fields.put(ENCRYPTEDREF+nodeset, "true");
		    				}
		            	}
		            }
	            }
            }
            
            // and now emit the sequence of nested fields in the form definition...
            int termCount = 0;
            for ( i = 0 ; i < sub.getChildCount() ; ++i ) {
            	if ( sub.getType(i) != Node.ELEMENT ) continue;
            	termCount = recordElement(sub.getElement(i), fields, termCount, sub);
            }
            
        }
        return fields;
    }
    
    private static String getRef(Element eKey, Map<String,String> fields, Element sub) {
        List<Element> nesting = new ArrayList<Element>();
        nesting.add(eKey);
        Element e = (Element) eKey.getParent();
        while ( e != sub ) {
        	nesting.add(e);
        	e = (Element) e.getParent();
        }
        Collections.reverse(nesting);
        
        StringBuilder b = new StringBuilder();
        b.append(fields.get(ODKFileUtils.SUBMISSIONREF));
        for ( Element elem : nesting ) {
        	b.append("/").append(elem.getName());
        }
        return b.toString();
    }
    
    private static int recordElement(Element cur, Map<String,String> fields, int termCount, Element sub) {
    	// form up the element path...
        // this is the xpath to the encryption field key
        String noderef = getRef(cur, fields, sub);
        
        ++termCount;
        fields.put(ODKFileUtils.SUBMISSIONREF + Integer.toString(- termCount ), noderef);
        
        for ( int i = 0 ; i < cur.getChildCount() ; ++i ) {
        	if ( cur.getType(i) != Node.ELEMENT ) continue;
        	termCount = recordElement(cur.getElement(i), fields, termCount, sub);
        }
        return termCount;
    }

    private static Element getMetaBlock(Element cur) {
    	for ( int k = 0 ; k < cur.getChildCount() ; ++k ) {
    		if ( cur.getType(k) != Node.ELEMENT ) continue;
    		Element e = cur.getElement(k);
    		if ( e.getName().equals("meta") ) {
    			return e;
    		} else {
    			Element f = getMetaBlock(e);
    			if ( f != null ) return f;
    		}
    	}
    	return null;
    }
    
    // needed because element.getelement fails when there are attributes
    private static Element getChildElement(Element parent, String childName) {
        Element e = null;
        int c = parent.getChildCount();
        int i = 0;
        for (i = 0; i < c; i++) {
            if (parent.getType(i) == Node.ELEMENT) {
                if (parent.getElement(i).getName().equalsIgnoreCase(childName)) {
                    return parent.getElement(i);
                }
            }
        }
        return e;
    }
}
