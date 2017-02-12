/*
 *
 * Copyright (c) 2013 - 2017 Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.commons.common.test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.xipki.commons.common.util.IoUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CanonicalizeCode {

    private static final String LICENSE_TEXT =
            "/*\n"
            + " *\n"
            + " * Copyright (c) 2013 - 2017 Lijun Liao\n"
            + " *\n"
            + " * This program is free software; you can redistribute it and/or modify\n"
            + " * it under the terms of the GNU Affero General Public License version 3\n"
            + " * as published by the Free Software Foundation with the addition of the\n"
            + " * following permission added to Section 15 as permitted in Section 7(a):\n"
            + " *\n"
            + " * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY\n"
            + " * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT\n"
            + " * OF THIRD PARTY RIGHTS.\n"
            + " *\n"
            + " * This program is distributed in the hope that it will be useful,\n"
            + " * but WITHOUT ANY WARRANTY; without even the implied warranty of\n"
            + " * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the\n"
            + " * GNU Affero General Public License for more details.\n"
            + " *\n"
            + " * You should have received a copy of the GNU Affero General Public License\n"
            + " * along with this program. If not, see <http://www.gnu.org/licenses/>.\n"
            + " *\n"
            + " * The interactive user interfaces in modified source and object code versions\n"
            + " * of this program must display Appropriate Legal Notices, as required under\n"
            + " * Section 5 of the GNU Affero General Public License.\n"
            + " *\n"
            + " * You can be released from the requirements of the license by purchasing\n"
            + " * a commercial license. Buying such a license is mandatory as soon as you\n"
            + " * develop commercial activities involving the XiPKI software without\n"
            + " * disclosing the source code of your own applications.\n"
            + " *\n"
            + " * For more information, please contact Lijun Liao at this\n"
            + " * address: lijun.liao@gmail.com\n"
            + " */\n\n";

    private final String baseDir;

    private final int baseDirLen;

    private CanonicalizeCode(String baseDir) {
        this.baseDir = baseDir.endsWith(File.separator) ? baseDir : baseDir + File.separator;
        this.baseDirLen = this.baseDir.length();
    }

    public static void main(final String[] args) {
        try {
            String baseDir = args[0];
            CanonicalizeCode canonicalizer = new CanonicalizeCode(baseDir);
            canonicalizer.canonicalize();
            canonicalizer.checkWarnings();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void canonicalize() throws Exception {
        canonicalizeDir(new File(baseDir));
    }

    private void canonicalizeDir(final File dir) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String filename = file.getName();
            if (file.isDirectory()) {
                if (!"target".equals(filename) && !"tbd".equals(filename)) {
                    canonicalizeDir(file);
                }
            } else {
                int idx = filename.lastIndexOf('.');
                String extension = (idx == -1) ? filename : filename.substring(idx + 1);
                extension = extension.toLowerCase();

                if ("java".equals(extension)) {
                    canonicalizeFile(file);
                }
            }
        }
    } // method canonicalizeDir

    private void canonicalizeFile(final File file) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(file));

        ByteArrayOutputStream writer = new ByteArrayOutputStream();

        try {
            String line;
            boolean skip = true;
            boolean lastLineEmpty = false;
            boolean licenseTextAdded = false;
            boolean thirdparty = false;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                if (lineNumber == 0 && line.startsWith("// #THIRDPARTY#")) {
                    thirdparty = true;
                    skip = false;
                }
                lineNumber++;

                if (line.trim().startsWith("package ") || line.trim().startsWith("import ")) {
                    if (!licenseTextAdded) {
                        if (!thirdparty) {
                            writer.write(LICENSE_TEXT.getBytes());
                        }
                        licenseTextAdded = true;
                    }
                    skip = false;
                }

                if (skip) {
                    continue;
                }

                String canonicalizedLine = canonicalizeLine(line);
                boolean addThisLine = true;
                if (canonicalizedLine.isEmpty()) {
                    if (!lastLineEmpty) {
                        lastLineEmpty = true;
                    } else {
                        addThisLine = false;
                    }
                } else {
                    lastLineEmpty = false;
                }

                if (addThisLine) {
                    writer.write(canonicalizedLine.getBytes());
                    writer.write('\n');
                }
            } // end while
        } finally {
            writer.close();
            reader.close();
        }

        byte[] oldBytes = IoUtil.read(file);
        byte[] newBytes = writer.toByteArray();
        if (!Arrays.equals(oldBytes, newBytes)) {
            File newFile = new File(file.getPath() + "-new");
            IoUtil.save(file, newBytes);
            newFile.renameTo(file);
            System.out.println(file.getPath().substring(baseDirLen));
        }
    } // method canonicalizeJavaFile

    private void checkWarnings() throws Exception {
        checkWarningsInDir(new File(baseDir));
    }

    private void checkWarningsInDir(final File dir) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().equals("target")
                        && !file.getName().equals("tbd")) {
                    checkWarningsInDir(file);
                }

                continue;
            } else {
                String filename = file.getName();
                int idx = filename.lastIndexOf('.');
                String extension = (idx == -1) ? filename : filename.substring(idx + 1);
                extension = extension.toLowerCase();

                if ("java".equals(extension)) {
                    checkWarningsInFile(file);
                }
            }
        }
    } // method checkWarningsInDir

    private void checkWarningsInFile(final File file) throws Exception {
        if (file.getName().equals("package-info.java")) {
            return;
        }

        BufferedReader reader = new BufferedReader(new FileReader(file));

        boolean authorsLineAvailable = false;
        boolean thirdparty = false;

        List<Integer> lineNumbers = new LinkedList<>();

        int lineNumber = 0;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("// #THIRDPARTY")) {
                    return;
                }

                if (!authorsLineAvailable && line.contains("* @author")) {
                    authorsLineAvailable = true;
                }

                if (line.length() > 100 && !line.contains("http")) {
                    lineNumbers.add(lineNumber);
                }
            } // end while
        } finally {
            reader.close();
        }

        if (!lineNumbers.isEmpty()) {
            System.out.println("Please check file " + file.getPath().substring(baseDirLen)
                + ": lines " + Arrays.toString(lineNumbers.toArray(new Integer[0])));
        }

        if (!authorsLineAvailable && !thirdparty) {
            System.out.println("Please check file " + file.getPath().substring(baseDirLen)
                    + ": no authors line");
        }
    } // method checkWarningsInJavaFile

    /**
     * replace tab by 4 spaces, delete white spaces at the end.
     */
    private static String canonicalizeLine(final String line) {
        if (line.trim().startsWith("//")) {
            // comments
            String nline = line.replace("\t", "    ");
            return removeTrailingSpaces(nline);
        }

        StringBuilder sb = new StringBuilder();
        int len = line.length();

        int lastNonSpaceCharIndex = 0;
        int index = 0;
        for (int i = 0; i < len; i++) {
            char ch = line.charAt(i);
            if (ch == '\t') {
                sb.append("    ");
                index += 4;
            } else if (ch == ' ') {
                sb.append(ch);
                index++;
            } else {
                sb.append(ch);
                index++;
                lastNonSpaceCharIndex = index;
            }
        }

        int numSpacesAtEnd = sb.length() - lastNonSpaceCharIndex;
        if (numSpacesAtEnd > 0) {
            sb.delete(lastNonSpaceCharIndex, sb.length());
        }

        return sb.toString();
    } // end canonicalizeJavaLine

    private static String removeTrailingSpaces(final String line) {
        final int n = line.length();
        int idx;
        for (idx = n - 1; idx >= 0; idx--) {
            char ch = line.charAt(idx);
            if (ch != ' ') {
                break;
            }
        }
        return (idx == n - 1) ?  line : line.substring(0, idx + 1);
    } // method removeTrailingSpaces

}
