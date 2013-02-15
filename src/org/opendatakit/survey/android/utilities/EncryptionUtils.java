/*
 * Copyright (C) 2011-2013 University of Washington
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.opendatakit.common.android.utilities.Base64Wrapper;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.survey.android.logic.FormInfo;
import org.opendatakit.survey.android.provider.FileSet;
import org.opendatakit.survey.android.provider.FileSet.MimeFile;

import android.util.Log;

/**
 * Utility class for encrypting submissions during the SaveToDiskTask.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class EncryptionUtils {
	private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
	private static final String t = "EncryptionUtils";
	public static final String RSA_ALGORITHM = "RSA";
	// the symmetric key we are encrypting with RSA is only 256 bits... use
	// SHA-256
	public static final String ASYMMETRIC_ALGORITHM = "RSA/NONE/OAEPWithSHA256AndMGF1Padding";
	public static final String SYMMETRIC_ALGORITHM = "AES/CFB/PKCS5Padding";
	public static final String UTF_8 = "UTF-8";
	public static final int SYMMETRIC_KEY_LENGTH = 256;
	public static final int IV_BYTE_LENGTH = 16;

	// tags in the submission manifest

	private static final String XML_ENCRYPTED_TAG_NAMESPACE = "http://www.opendatakit.org/xforms/encrypted";
	private static final String XML_OPENROSA_NAMESPACE = "http://openrosa.org/xforms";
	private static final String DATA = "data";
	private static final String ID = "id";
	private static final String VERSION = "version";
	private static final String ENCRYPTED = "encrypted";
	private static final String BASE64_ENCRYPTED_KEY = "base64EncryptedKey";
	private static final String ENCRYPTED_XML_FILE = "encryptedXmlFile";
	private static final String META = "meta";
	private static final String INSTANCE_ID = "instanceID";
	private static final String MEDIA = "media";
	private static final String FILE = "file";
	private static final String BASE64_ENCRYPTED_ELEMENT_SIGNATURE = "base64EncryptedElementSignature";
	private static final String NEW_LINE = "\n";

	private EncryptionUtils() {
	};

	public static final class EncryptedFormInformation {
		public final String formId;
		public final String formVersion;
		public final String instanceId;
		public final String base64EncryptedFileRsaPublicKey;
		public final PublicKey rsaPublicKey;
		public final String base64RsaEncryptedSymmetricKey;
		public final SecretKeySpec symmetricKey;
		public final byte[] ivSeedArray;
		private int ivCounter = 0;
		public final StringBuilder elementSignatureSource = new StringBuilder();
		public final Base64Wrapper wrapper;

		EncryptedFormInformation(FormInfo fi, String instanceId,
				PublicKey rsaPublicKey, Base64Wrapper wrapper) {
			this.formId = fi.formId;
			this.formVersion = fi.formVersion;
			this.instanceId = instanceId;
			this.base64EncryptedFileRsaPublicKey = fi.xmlBase64RsaPublicKey;
			this.rsaPublicKey = rsaPublicKey;
			this.wrapper = wrapper;

			// generate the symmetric key from random bits...

			SecureRandom r = new SecureRandom();
			byte[] key = new byte[SYMMETRIC_KEY_LENGTH / 8];
			r.nextBytes(key);
			SecretKeySpec sk = new SecretKeySpec(key, SYMMETRIC_ALGORITHM);
			symmetricKey = sk;

			// construct the fixed portion of the iv -- the ivSeedArray
			// this is the md5 hash of the instanceID and the symmetric key
			try {
				MessageDigest md = MessageDigest.getInstance("MD5");
				md.update(instanceId.getBytes(UTF_8));
				md.update(key);
				byte[] messageDigest = md.digest();
				ivSeedArray = new byte[IV_BYTE_LENGTH];
				for (int i = 0; i < IV_BYTE_LENGTH; ++i) {
					ivSeedArray[i] = messageDigest[(i % messageDigest.length)];
				}
			} catch (NoSuchAlgorithmException e) {
				Log.e(t, e.toString());
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			} catch (UnsupportedEncodingException e) {
				Log.e(t, e.toString());
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			}

			// construct the base64-encoded RSA-encrypted symmetric key
			try {
				Cipher pkCipher;
				pkCipher = Cipher.getInstance(ASYMMETRIC_ALGORITHM);
				// write AES key
				pkCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
				byte[] pkEncryptedKey = pkCipher.doFinal(key);
				String alg = pkCipher.getAlgorithm();
				Log.i(t, "AlgorithmUsed: " + alg);
				base64RsaEncryptedSymmetricKey = wrapper
						.encodeToString(pkEncryptedKey);

			} catch (NoSuchAlgorithmException e) {
				Log.e(t, "Unable to encrypt the symmetric key");
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			} catch (NoSuchPaddingException e) {
				Log.e(t, "Unable to encrypt the symmetric key");
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			} catch (InvalidKeyException e) {
				Log.e(t, "Unable to encrypt the symmetric key");
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			} catch (IllegalBlockSizeException e) {
				Log.e(t, "Unable to encrypt the symmetric key");
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			} catch (BadPaddingException e) {
				Log.e(t, "Unable to encrypt the symmetric key");
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			}

			// start building elementSignatureSource...
			appendElementSignatureSource(formId);
			if (formVersion != null) {
				appendElementSignatureSource(formVersion.toString());
			}
			appendElementSignatureSource(base64RsaEncryptedSymmetricKey);

			appendElementSignatureSource(instanceId);
		}

		public void appendElementSignatureSource(String value) {
			elementSignatureSource.append(value).append("\n");
		}

		public void appendSubmissionFileSignatureSource(String contents,
				File file) {
			String md5Hash = ODKFileUtils.getNakedMd5Hash(contents);
			appendElementSignatureSource(file.getName() + "::" + md5Hash);
		}

		public void appendFileSignatureSource(File file) {
			String md5Hash = ODKFileUtils.getNakedMd5Hash(file);
			appendElementSignatureSource(file.getName() + "::" + md5Hash);
		}

		public String getBase64EncryptedElementSignature() {
			// Step 0: construct the text of the elements in
			// elementSignatureSource (done)
			// Where...
			// * Elements are separated by newline characters.
			// * Filename is the unencrypted filename (no .enc suffix).
			// * Md5 hashes of the unencrypted files' contents are converted
			// to zero-padded 32-character strings before concatenation.
			// Assumes this is in the order:
			// formId
			// version (omitted if null)
			// base64RsaEncryptedSymmetricKey
			// instanceId
			// for each media file { filename "::" md5Hash }
			// submission.xml "::" md5Hash

			// Step 1: construct the (raw) md5 hash of Step 0.
			byte[] messageDigest;
			try {
				MessageDigest md = MessageDigest.getInstance("MD5");
				md.update(elementSignatureSource.toString().getBytes(UTF_8));
				messageDigest = md.digest();
			} catch (NoSuchAlgorithmException e) {
				Log.e(t, e.toString());
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			} catch (UnsupportedEncodingException e) {
				Log.e(t, e.toString());
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			}

			// Step 2: construct the base64-encoded RSA-encrypted md5
			try {
				Cipher pkCipher;
				pkCipher = Cipher.getInstance(ASYMMETRIC_ALGORITHM);
				// write AES key
				pkCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
				byte[] pkEncryptedKey = pkCipher.doFinal(messageDigest);
				return wrapper.encodeToString(pkEncryptedKey);

			} catch (NoSuchAlgorithmException e) {
				Log.e(t, "Unable to encrypt the symmetric key");
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			} catch (NoSuchPaddingException e) {
				Log.e(t, "Unable to encrypt the symmetric key");
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			} catch (InvalidKeyException e) {
				Log.e(t, "Unable to encrypt the symmetric key");
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			} catch (IllegalBlockSizeException e) {
				Log.e(t, "Unable to encrypt the symmetric key");
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			} catch (BadPaddingException e) {
				Log.e(t, "Unable to encrypt the symmetric key");
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			}
		}

		public Cipher getCipher() throws InvalidKeyException,
				InvalidAlgorithmParameterException, NoSuchAlgorithmException,
				NoSuchPaddingException {
			++ivSeedArray[ivCounter % ivSeedArray.length];
			++ivCounter;
			IvParameterSpec baseIv = new IvParameterSpec(ivSeedArray);
			Cipher c = Cipher.getInstance(EncryptionUtils.SYMMETRIC_ALGORITHM);
			c.init(Cipher.ENCRYPT_MODE, symmetricKey, baseIv);
			return c;
		}
	}

	/**
	 * Retrieve the encryption information for this uri.
	 *
	 * @param mUri
	 *            either an instance URI (if previously saved) or a form URI
	 * @param instanceMetadata
	 * @return
	 */
	public static EncryptedFormInformation getEncryptedFormInformation(
			FormInfo fi, String instanceId) {

		// fetch the form information
		String base64RsaPublicKey = fi.xmlBase64RsaPublicKey;
		PublicKey pk;
		Base64Wrapper wrapper;

		if (base64RsaPublicKey == null || base64RsaPublicKey.length() == 0) {
			return null; // this is legitimately not an encrypted form
		}

		// submission must have an OpenRosa metadata block with a non-null
		// instanceID value.
		if (instanceId == null) {
			Log.e(t,
					"No OpenRosa metadata block or no instanceId defined in that block");
			return null;
		}

		int version = android.os.Build.VERSION.SDK_INT;
		if (version < 8) {
			Log.e(t, "Phone does not support encryption.");
			return null; // save unencrypted
		}

		// this constructor will throw an exception if we are not
		// running on version 8 or above (if Base64 is not found).
		try {
			wrapper = new Base64Wrapper();
		} catch (ClassNotFoundException e) {
			Log.e(t, "Phone does not have Base64 class but API level is "
					+ version);
			e.printStackTrace();
			return null; // save unencrypted
		}

		// OK -- Base64 decode (requires API Version 8 or higher)
		byte[] publicKey = wrapper.decode(base64RsaPublicKey);
		X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKey);
		KeyFactory kf;
		try {
			kf = KeyFactory.getInstance(RSA_ALGORITHM);
		} catch (NoSuchAlgorithmException e) {
			Log.e(t, "Phone does not support RSA encryption.");
			e.printStackTrace();
			return null;
		}
		try {
			pk = kf.generatePublic(publicKeySpec);
		} catch (InvalidKeySpecException e) {
			e.printStackTrace();
			Log.e(t, "Invalid RSA public key.");
			return null;
		}
		return new EncryptedFormInformation(fi, instanceId, pk, wrapper);
	}

	private static void encryptFile(File file, File encryptedFile,
			EncryptedFormInformation formInfo) throws IOException,
			NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException, InvalidAlgorithmParameterException {

		// add elementSignatureSource for this file...
		formInfo.appendFileSignatureSource(file);

		try {
			Cipher c = formInfo.getCipher();

			OutputStream fout;
			fout = new FileOutputStream(encryptedFile);
			fout = new CipherOutputStream(fout, c);
			InputStream fin;
			fin = new FileInputStream(file);
			byte[] buffer = new byte[2048];
			int len = fin.read(buffer);
			while (len != -1) {
				fout.write(buffer, 0, len);
				len = fin.read(buffer);
			}
			fin.close();
			fout.flush();
			fout.close();
			Log.i(t,
					"Encrpyted:" + file.getName() + " -> "
							+ encryptedFile.getName());
		} catch (IOException e) {
			Log.e(t, "Error encrypting: " + file.getName() + " -> "
					+ encryptedFile.getName());
			e.printStackTrace();
			throw e;
		} catch (NoSuchAlgorithmException e) {
			Log.e(t, "Error encrypting: " + file.getName() + " -> "
					+ encryptedFile.getName());
			e.printStackTrace();
			throw e;
		} catch (NoSuchPaddingException e) {
			Log.e(t, "Error encrypting: " + file.getName() + " -> "
					+ encryptedFile.getName());
			e.printStackTrace();
			throw e;
		} catch (InvalidKeyException e) {
			Log.e(t, "Error encrypting: " + file.getName() + " -> "
					+ encryptedFile.getName());
			e.printStackTrace();
			throw e;
		} catch (InvalidAlgorithmParameterException e) {
			Log.e(t, "Error encrypting: " + file.getName() + " -> "
					+ encryptedFile.getName());
			e.printStackTrace();
			throw e;
		}
	}

	private static void encryptIntoFile(String contents, File submissionFile,
			File encryptedFile, EncryptedFormInformation formInfo)
			throws IOException, NoSuchAlgorithmException,
			NoSuchPaddingException, InvalidKeyException,
			InvalidAlgorithmParameterException {

		// add elementSignatureSource for this file...
		formInfo.appendSubmissionFileSignatureSource(contents, submissionFile);

		try {
			Cipher c = formInfo.getCipher();

			OutputStream fout;
			fout = new FileOutputStream(encryptedFile);
			fout = new CipherOutputStream(fout, c);
			InputStream fin;
			fin = new ByteArrayInputStream(contents.getBytes("UTF-8"));
			byte[] buffer = new byte[2048];
			int len = fin.read(buffer);
			while (len != -1) {
				fout.write(buffer, 0, len);
				len = fin.read(buffer);
			}
			fin.close();
			fout.flush();
			fout.close();
			Log.i(t, "Encrpyted: content -> " + encryptedFile.getName());
		} catch (IOException e) {
			Log.e(t, "Error encrypting: content -> " + encryptedFile.getName());
			e.printStackTrace();
			throw e;
		} catch (NoSuchAlgorithmException e) {
			Log.e(t, "Error encrypting: content -> " + encryptedFile.getName());
			e.printStackTrace();
			throw e;
		} catch (NoSuchPaddingException e) {
			Log.e(t, "Error encrypting: content -> " + encryptedFile.getName());
			e.printStackTrace();
			throw e;
		} catch (InvalidKeyException e) {
			Log.e(t, "Error encrypting: content -> " + encryptedFile.getName());
			e.printStackTrace();
			throw e;
		} catch (InvalidAlgorithmParameterException e) {
			Log.e(t, "Error encrypting: content -> " + encryptedFile.getName());
			e.printStackTrace();
			throw e;
		}
	}

	public static boolean deletePlaintextFiles(File instanceXml) {
		// NOTE: assume the directory containing the instanceXml contains ONLY
		// files related to this one instance.
		File instanceDir = instanceXml.getParentFile();

		boolean allSuccessful = true;
		// encrypt files that do not end with ".enc", and do not start with ".";
		// ignore directories
		File[] allFiles = instanceDir.listFiles();
		for (File f : allFiles) {
			if (f.equals(instanceXml))
				continue; // don't touch instance file
			if (f.isDirectory())
				continue; // don't handle directories
			if (!f.getName().endsWith(".enc")) {
				// not an encrypted file -- delete it!
				allSuccessful = allSuccessful & f.delete(); // DO NOT
															// short-circuit
			}
		}
		return allSuccessful;
	}

	private static List<MimeFile> encryptSubmissionFiles(FileSet fileSet,
			String submission, File submissionXml, File submissionXmlEnc,
			EncryptedFormInformation formInfo) {

		// encrypt files that do not end with ".enc"
		List<MimeFile> filesToProcess = new ArrayList<MimeFile>();
		for (MimeFile f : fileSet.attachmentFiles) {
			if (f.file.getName().endsWith(".enc")) {
				f.file.delete(); // try to delete this (leftover junk)
			} else {
				filesToProcess.add(f);
			}
		}
		// encrypt here...
		for (MimeFile f : filesToProcess) {
			try {
				File encryptedFile = new File(f.file.getParentFile(),
						f.file.getName() + ".enc");
				encryptFile(f.file, encryptedFile, formInfo);
				f.file = encryptedFile;
				f.contentType = APPLICATION_OCTET_STREAM;
			} catch (IOException e) {
				return null;
			} catch (InvalidKeyException e) {
				return null;
			} catch (NoSuchAlgorithmException e) {
				return null;
			} catch (NoSuchPaddingException e) {
				return null;
			} catch (InvalidAlgorithmParameterException e) {
				return null;
			}
		}

		// encrypt the submission.xml as the last file...
		try {
			encryptIntoFile(submission, submissionXml, submissionXmlEnc,
					formInfo);
			// TODO: attachments remain in plaintext on the sdcard until
			// instance is deleted
			fileSet.addAttachmentFile(submissionXmlEnc,
					APPLICATION_OCTET_STREAM);
		} catch (IOException e) {
			return null;
		} catch (InvalidKeyException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		} catch (NoSuchPaddingException e) {
			return null;
		} catch (InvalidAlgorithmParameterException e) {
			return null;
		}

		return filesToProcess;
	}

	/**
	 * Constructs the encrypted attachments, encrypted form xml, and the
	 * plaintext submission manifest (with signature) for the form submission.
	 *
	 * Does not delete any of the original files.
	 *
	 * @param instanceXml
	 * @param submissionXmlEnc
	 * @param metadata
	 * @param formInfo
	 * @return
	 */
	public static boolean generateEncryptedSubmission(FileSet fileSet,
			String submission, File submissionXml, File submissionXmlEnc,
			EncryptedFormInformation formInfo) {

		// Step 1: encrypt the submission and all the media files...
		List<MimeFile> mediaFiles = encryptSubmissionFiles(fileSet, submission,
				submissionXml, submissionXmlEnc, formInfo);
		if (mediaFiles == null) {
			return false; // something failed...
		}

		// Step 2: build the encrypted-submission manifest (overwrites
		// submission.xml)...
		if (!writeSubmissionManifest(formInfo, submissionXml, submissionXmlEnc,
				mediaFiles)) {
			return false;
		}
		fileSet.instanceFile = submissionXml;
		return true;
	}

	private static boolean writeSubmissionManifest(
			EncryptedFormInformation formInfo, File submissionXml,
			File submissionXmlEnc, List<MimeFile> mediaFiles) {

		Document d = new Document();
		d.setStandalone(true);
		d.setEncoding(UTF_8);
		Element e = d.createElement(XML_ENCRYPTED_TAG_NAMESPACE, DATA);
		e.setPrefix(null, XML_ENCRYPTED_TAG_NAMESPACE);
		e.setAttribute(null, ID, formInfo.formId);
		if (formInfo.formVersion != null) {
			e.setAttribute(null, VERSION, formInfo.formVersion);
		}
		e.setAttribute(null, ENCRYPTED, "yes");
		d.addChild(0, Node.ELEMENT, e);

		int idx = 0;
		Element c;
		c = d.createElement(XML_ENCRYPTED_TAG_NAMESPACE, BASE64_ENCRYPTED_KEY);
		c.addChild(0, Node.TEXT, formInfo.base64RsaEncryptedSymmetricKey);
		e.addChild(idx++, Node.ELEMENT, c);

		c = d.createElement(XML_OPENROSA_NAMESPACE, META);
		c.setPrefix("orx", XML_OPENROSA_NAMESPACE);
		{
			Element instanceTag = d.createElement(XML_OPENROSA_NAMESPACE,
					INSTANCE_ID);
			instanceTag.addChild(0, Node.TEXT, formInfo.instanceId);
			c.addChild(0, Node.ELEMENT, instanceTag);
		}
		e.addChild(idx++, Node.ELEMENT, c);
		e.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);

		for (MimeFile file : mediaFiles) {
			c = d.createElement(XML_ENCRYPTED_TAG_NAMESPACE, MEDIA);
			Element fileTag = d
					.createElement(XML_ENCRYPTED_TAG_NAMESPACE, FILE);
			fileTag.addChild(0, Node.TEXT, file.file.getName());
			c.addChild(0, Node.ELEMENT, fileTag);
			e.addChild(idx++, Node.ELEMENT, c);
			e.addChild(idx++, Node.IGNORABLE_WHITESPACE, NEW_LINE);
		}

		c = d.createElement(XML_ENCRYPTED_TAG_NAMESPACE, ENCRYPTED_XML_FILE);
		c.addChild(0, Node.TEXT, submissionXmlEnc.getName());
		e.addChild(idx++, Node.ELEMENT, c);

		c = d.createElement(XML_ENCRYPTED_TAG_NAMESPACE,
				BASE64_ENCRYPTED_ELEMENT_SIGNATURE);
		c.addChild(0, Node.TEXT, formInfo.getBase64EncryptedElementSignature());
		e.addChild(idx++, Node.ELEMENT, c);

		FileOutputStream out;
		try {
			out = new FileOutputStream(submissionXml);
			OutputStreamWriter writer = new OutputStreamWriter(out, UTF_8);

			KXmlSerializer serializer = new KXmlSerializer();
			serializer.setOutput(writer);
			// setting the response content type emits the xml header.
			// just write the body here...
			d.writeChildren(serializer);
			serializer.flush();
			writer.flush();
			writer.close();
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
			Log.e(t, "Error writing submission.xml for encrypted submission: "
					+ submissionXml.getParentFile().getName());
			return false;
		} catch (UnsupportedEncodingException ex) {
			ex.printStackTrace();
			Log.e(t, "Error writing submission.xml for encrypted submission: "
					+ submissionXml.getParentFile().getName());
			return false;
		} catch (IOException ex) {
			ex.printStackTrace();
			Log.e(t, "Error writing submission.xml for encrypted submission: "
					+ submissionXml.getParentFile().getName());
			return false;
		}

		return true;
	}
}
