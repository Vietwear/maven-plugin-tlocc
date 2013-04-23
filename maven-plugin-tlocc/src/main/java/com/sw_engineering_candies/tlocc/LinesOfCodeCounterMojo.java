/**
 * Copyright (C) 2012-2013, Markus Sprunck
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - The name of its contributor may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sw_engineering_candies.tlocc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import com.thoughtworks.xstream.XStream;

/**
 * Goal which counts the total lines of code
 * 
 * @goal tlocc
 * 
 * @phase test
 */
public class LinesOfCodeCounterMojo extends AbstractMojo {

	/** For each file type (extension) one entry in the map */
	private final Map<String, CountResult> fileTypes = new HashMap<String, CountResult>(100);

	/** List with all files to be counted */
	private final List<String> files = new ArrayList<String>(10000);

	/** Code lines of current file */
	private int currentFileEmptyLines = 0;

	/** Total code lines of current file */
	private int currentFileTotalLines = 0;

	/**
	 * Location of the file.
	 * 
	 * @parameter expression="${project.build.directory}"
	 * @required
	 */
	private final File outputDirectory = new File("");

	/**
	 * Project's source directory as specified in the POM.
	 * 
	 * @parameter expression="${project.build.sourceDirectory}"
	 * @readonly
	 * @required
	 */
	private final File sourceDirectory = new File("");

	/**
	 * Project's source directory for test code as specified in the POM.
	 * 
	 * @parameter expression="${project.build.testSourceDirectory}"
	 * @readonly
	 * @required
	 */
	private final File testSourceDirectory = new File("");

	/**
	 * Encoding of source files
	 * 
	 * @parameter expression="${encoding}"
	 *            default-value="${project.build.sourceEncoding}"
	 */
	private final String encoding = "UTF-8";

	@Override
	public void execute() throws MojoExecutionException {

		if (!ensureTargetDirectoryExists()) {
			getLog().error("Could not create target directory");
			return;
		}

		if (!sourceDirectory.exists()) {
			getLog().error("Source directory \"" + sourceDirectory + "\" is not valid.");
			return;
		}

		fillListWithAllFilesRecursiveTask(sourceDirectory, files);
		fillListWithAllFilesRecursiveTask(testSourceDirectory, files);

		for (final String filePath : files) {
			resetCurrentCounts();
			countCurrentFile(filePath);
			writeCurrentResultsToMavenLogger(filePath);
			storeCurrentResults(filePath);
		}

		writeOutputFileFromStoredResults();
	}

	private boolean ensureTargetDirectoryExists() {
		if (outputDirectory.exists()) {
			return true;
		}
		return outputDirectory.mkdirs();
	}

	public void resetCurrentCounts() {
		currentFileEmptyLines = 0;
		currentFileTotalLines = 0;
	}

	public void countCurrentFile(final String filePath) {
		Scanner scanner = null;
		try {
			scanner = new Scanner(new File(filePath), encoding);
			String line = scanner.nextLine();
			while (scanner.hasNext()) {
				currentFileTotalLines++;
				if (line.trim().isEmpty()) {
					currentFileEmptyLines++;
				}
				line = scanner.nextLine();
			}
		} catch (final IOException e) {
			getLog().error(e.getMessage());
		} finally {
			if (null != scanner) {
				scanner.close();
			}
		}
	}

	public void writeCurrentResultsToMavenLogger(final String filePath) {
		final StringBuffer message = new StringBuffer(100);
		message.append(currentFileEmptyLines).append('\t');
		message.append(currentFileTotalLines).append('\t');
		message.append(filePath);
		getLog().info(message);
	}

	public void storeCurrentResults(final String filePath) {
		final String extension = getExtension(filePath);
		if (fileTypes.containsKey(extension)) {
			fileTypes.get(extension).addEmpty(currentFileEmptyLines);
			fileTypes.get(extension).addTotal(currentFileTotalLines);
			fileTypes.get(extension).incrementFiles();
		} else {
			final CountResult item = new CountResult();
			item.addEmpty(currentFileEmptyLines);
			item.addTotal(currentFileTotalLines);
			item.incrementFiles();
			fileTypes.put(extension, item);
		}
	}

	private void writeOutputFileFromStoredResults() {
		OutputStreamWriter out = null;
		try {
			final StringBuffer path = new StringBuffer();
			path.append(outputDirectory);
			path.append(System.getProperty("file.separator"));
			path.append("tlocc-result.xml");

			final FileOutputStream fos = new FileOutputStream(path.toString());
			out = new OutputStreamWriter(fos, encoding);

			final XStream xstream = new XStream();
			xstream.alias("data", CountResult.class);
			out.write(xstream.toXML(fileTypes));

		} catch (final IOException e) {
			getLog().error(e.getMessage());
		} finally {
			if (null != out) {
				try {
					out.close();
				} catch (final IOException e) {
					getLog().error(e.getMessage());
				}
			}
		}
	}

	public static void fillListWithAllFilesRecursiveTask(final File root, final List<String> files) {
		if (root.isFile()) {
			files.add(root.getPath());
			return;
		}
		for (final File file : root.listFiles()) {
			if (file.isDirectory()) {
				fillListWithAllFilesRecursiveTask(file, files);
			} else {
				files.add(file.getPath());
			}
		}
	}

	public static String getExtension(final String filePath) {
		final int dotPos = filePath.lastIndexOf(".");
		if (-1 == dotPos) {
			return "undefined";
		} else {
			return filePath.substring(dotPos);
		}
	}

}
