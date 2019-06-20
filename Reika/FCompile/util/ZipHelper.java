package Reika.FCompile.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Deprecated
public class ZipHelper { // Not parsable by factorio mod portal

	private final String input;
	private final String output;

	private final List<String> fileList = new ArrayList<String>();

	private ZipHelper(String in, String out) {
		input = in;
		output = out;
	}

	public static void zipFolder(String in, String out) {
		ZipHelper appZip = new ZipHelper(in, out);
		appZip.readInput(new File(in));
		appZip.makeZIP();
	}

	private void makeZIP() {
		byte[] buffer = new byte[1024];
		String source = new File(input).getName();
		FileOutputStream fos = null;
		ZipOutputStream zos = null;
		try {
			fos = new FileOutputStream(output);
			zos = new ZipOutputStream(fos);

			FileInputStream in = null;

			for (String file : fileList) {
				ZipEntry ze = new ZipEntry(source + File.separator + file);
				zos.putNextEntry(ze);
				try {
					in = new FileInputStream(input + File.separator + file);
					int len;
					while ((len = in.read(buffer)) > 0) {
						zos.write(buffer, 0, len);
					}
				}
				finally {
					in.close();
				}
			}

			zos.closeEntry();
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
		finally {
			try {
				zos.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void readInput(File node) {
		// add file only
		if (node.isFile()) {
			fileList.add(this.generateZipEntry(node.toString()));
		}

		if (node.isDirectory()) {
			String[] subNote = node.list();
			for (String filename : subNote) {
				this.readInput(new File(node, filename));
			}
		}
	}

	private String generateZipEntry(String file) {
		return file.substring(input.length() + 1, file.length());
	}
}