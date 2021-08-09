package Reika.FCompile.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileIO {

	public static ArrayList<String> getFileAsLines(String path) throws IOException {
		return getFileAsLines(getReader(path));
	}

	public static ArrayList<String> getFileAsLines(URL url, int timeout) throws IOException {
		BufferedReader r = getReader(url, timeout);
		return r != null ? getFileAsLines(r) : null;
	}

	public static ArrayList<String> getFileAsLines(File f) throws IOException {
		return getFileAsLines(getReader(f));
	}

	public static ArrayList<String> getFileAsLines(InputStream in) throws IOException {
		return getFileAsLines(getReader(in));
	}

	public static ArrayList<String> getFileAsLines(BufferedReader r) throws IOException {
		ArrayList<String> li = new ArrayList();
		String line = "";
		while (line != null) {
			line = r.readLine();
			if (line != null) {
				li.add(line);
			}
		}
		r.close();
		return li;
	}

	public static BufferedReader getReader(File f) {
		try {
			return new BufferedReader(new InputStreamReader(new FileInputStream(f)));
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static BufferedReader getReader(InputStream in) {
		try {
			return new BufferedReader(new InputStreamReader(in));
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static BufferedReader getReader(String path) {
		try {
			return new BufferedReader(new InputStreamReader(new FileInputStream(path)));
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static BufferedReader getReader(URL url, int timeout) {
		try {
			URLConnection c = url.openConnection();
			c.setConnectTimeout(timeout);
			return new BufferedReader(new InputStreamReader(c.getInputStream()));
		}
		catch (UnknownHostException e) { // Server not found
			e.printStackTrace();
		}
		catch (ConnectException e) { // Redirect/tampering
			e.printStackTrace();

		}
		catch (SocketTimeoutException e) { // Slow internet, cannot load a text
			// file...
			e.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void deleteDirectoryWithContents(File folder) {
		File[] files = folder.listFiles();
		if (files != null) { //some JVMs return null for empty dirs
			for (File f : files) {
				if (f.isDirectory()) {
					deleteDirectoryWithContents(f);
				}
				else {
					//System.out.println("Deleting raw file " + f);
					f.delete();
				}
			}
		}
		//System.out.println("Deleting folder " + folder);
		folder.delete();
	}

	public static void writeLinesToFile(String s, ArrayList<String> li, boolean keepContents) throws IOException {
		writeLinesToFile(new File(s), li, keepContents);
	}

	public static void writeLinesToFile(File f, ArrayList<String> li, boolean keepContents) throws IOException {
		writeLinesToFile(new BufferedWriter(new PrintWriter(new FileWriter(f, keepContents))), li, keepContents);
	}

	public static void writeLinesToFile(BufferedWriter p, ArrayList<String> li, boolean keepContents) throws IOException {
		String sep = System.getProperty("line.separator");
		for (String s : li) {
			p.write(s + sep);
		}
		p.flush();
		p.close();
	}

	/* Not parsable by factorio mod portal
		// Uses java.util.zip to create zip file
		public static void zipFolder(File source, File zipPath) throws IOException {
		final Path sourceFolderPath = source.toPath();
		final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath));
		Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				zos.putNextEntry(new ZipEntry(sourceFolderPath.relativize(file).toString()));
				Files.copy(file, zos);
				zos.closeEntry();
				return FileVisitResult.CONTINUE;
			}
		});
		zos.close();
		}*/

	public static void zipFolder(String srcFolder, String destZipFile) throws IOException {
		ZipOutputStream zip = null;
		FileOutputStream fileWriter = null;

		fileWriter = new FileOutputStream(destZipFile);
		zip = new ZipOutputStream(fileWriter);

		addFolderToZip("", srcFolder, zip);
		zip.flush();
		zip.close();
	}

	private static void addFileToZip(String path, String srcFile, ZipOutputStream zip) throws IOException {

		File folder = new File(srcFile);
		if (folder.isDirectory()) {
			addFolderToZip(path, srcFile, zip);
		}
		else {
			byte[] buf = new byte[1024];
			int len;
			FileInputStream in = new FileInputStream(srcFile);
			zip.putNextEntry(new ZipEntry(path + "/" + folder.getName()));
			while ((len = in.read(buf)) > 0) {
				zip.write(buf, 0, len);
			}
			in.close();
		}
	}

	private static void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) throws IOException {
		File folder = new File(srcFolder);

		for (String fileName : folder.list()) {
			if (path.equals("")) {
				addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
			}
			else {
				addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
			}
		}
	}

}
