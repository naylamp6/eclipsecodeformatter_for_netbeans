/*
 * Copyright (c) 2013 markiewb.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    markiewb - initial API and implementation and/or initial documentation
 *    Saad Mufti <saad.mufti@teamaol.com> 
 */
package de.markiewb.netbeans.plugins.eclipse.formatter;

import de.markiewb.netbeans.plugins.eclipse.formatter.xml.ConfigReadException;
import de.markiewb.netbeans.plugins.eclipse.formatter.xml.ConfigReader;
import de.markiewb.netbeans.plugins.eclipse.formatter.xml.Profile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.openide.filesystems.FileUtil;
import org.xml.sax.SAXException;

public final class EclipseFormatter {

    private final String formatterFile;
    private final String formatterProfile;

    EclipseFormatter(String formatterFile, String formatterProfile) {
        this.formatterFile = formatterFile;
        this.formatterProfile = formatterProfile;
    }

    public String forCode(final String code, int startOffset, int endOffset) {
        String result = null;
        if (code != null) {
            result = this.format(code, startOffset, endOffset);
        }
        return result;
    }

    // returns null if format resulted in no change
    private String format(final String code, int startOffset, int endOffset) {
        final int opts
                = CodeFormatter.K_COMPILATION_UNIT + CodeFormatter.F_INCLUDE_COMMENTS;
        Map<String, String> allConfig = readConfig();

        CodeFormatter formatter = ToolFactory.createCodeFormatter(allConfig);
        final TextEdit te = formatter.format(opts, code, startOffset, endOffset - startOffset, 0, null);
        final IDocument dc = new Document(code);
        String formattedCode = null;
        if ((te != null) && (te.getChildrenSize() > 0)) {
            try {
                te.apply(dc);
            } catch (Exception ex) {
                System.err.println("Code could not be formatted!" + ex);
                return null;
            }
            formattedCode = dc.get();
        }
        return formattedCode;
    }

    private Map<String, String> getFormattingOptions() {
        Map<String, String> options = DefaultCodeFormatterConstants.getJavaConventionsSettings();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_6);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_6);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_6);
//      For checking whether the Eclipse formatter works,
//      without needing an Eclipse formatter XML file:
//        options.put(
//		DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_ENUM_CONSTANTS,
//		DefaultCodeFormatterConstants.createAlignmentValue(
//		true,
//		DefaultCodeFormatterConstants.WRAP_ONE_PER_LINE,
//		DefaultCodeFormatterConstants.INDENT_ON_COLUMN));

        return options;
    }

    /**
     *
     * @return profile of <code>null</code> if profile with name not found
     */
    private Profile getProfileByName(List<Profile> profiles, String name) {
        if (null == name) {
            return null;
        }
        for (Profile profile : profiles) {
            if (null != profile && name.equals(profile.getName())) {
                return profile;
            }
        }
        return null;
    }

    private Map<String, String> readConfig() throws ProfileNotFoundException {
        Map<String, String> allConfig = new HashMap<>();
        final Map<String, String> configFromStatic = getFormattingOptions();
        try {
            final File file = new File(formatterFile);
            Map<String, String> configFromFile = new LinkedHashMap<>();
            if (Preferences.isWorkspaceMechanicFile(formatterFile)) {
                configFromFile.putAll(readConfigFromWorkspaceMechanicFile(file));
            } else if (Preferences.isXMLConfigurationFile(formatterFile)) {
                configFromFile.putAll(readConfigFromFormatterXmlFile(file));
            } else if (Preferences.isProjectSetting(formatterFile)) {
                configFromFile.putAll(readConfigFromProjectSettings(file));
            }

            allConfig.putAll(configFromStatic);
            allConfig.putAll(configFromFile);
        } catch (Exception ex) {
            System.err.println("Could not load configuration: " + formatterFile + ex);

            throw new CannotLoadConfigurationException(ex);
        }
        return allConfig;
    }

    private Map<String, String> readConfigFromFormatterXmlFile(final File file) throws ConfigReadException, ProfileNotFoundException, IOException, SAXException {
        Map<String, String> configFromFile;
        List<Profile> profiles = new ConfigReader().read(FileUtil.normalizeFile(file));
        String name = formatterProfile;
        if (profiles.isEmpty()) {
            //no config found
            throw new ProfileNotFoundException("No profiles found in " + formatterFile);
        }
        Profile profile = getProfileByName(profiles, name);
        if (null == profile) {
            throw new ProfileNotFoundException("profile " + name + " not found in " + formatterFile);
        }
        configFromFile = profile.getSettings();
        return configFromFile;
    }

    private Map<String, String> readConfigFromWorkspaceMechanicFile(final File file) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        Properties properties = new Properties();
        try (FileInputStream is = new FileInputStream(file)) {
            properties.load(is);
        }
        final String prefix = "/instance/org.eclipse.jdt.core/";
        for (Object object : properties.keySet()) {
            String key = (String) object;
            if (key.startsWith(prefix)) {
                String value = properties.getProperty(key);
                result.put(key.substring(prefix.length()), value);
            }
        }
        return result;
    }
    
    private Map<String, String> readConfigFromProjectSettings(final File file) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        Properties properties = new Properties();
        try (FileInputStream is = new FileInputStream(file)) {
            properties.load(is);
        }
        for (Object object : properties.keySet()) {
            String key = (String) object;
            result.put(key, properties.getProperty(key));
        }
        return result;
    }

    public class CannotLoadConfigurationException extends RuntimeException {

        public CannotLoadConfigurationException(Exception ex) {
            super(ex);
        }
    }

    public class ProfileNotFoundException extends RuntimeException {

        public ProfileNotFoundException(String message) {
            super(message);
        }
    }

}
