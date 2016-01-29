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

import de.markiewb.netbeans.plugins.eclipse.formatter.EclipseFormatter.CannotLoadConfigurationException;
import de.markiewb.netbeans.plugins.eclipse.formatter.EclipseFormatter.ProfileNotFoundException;
import static de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.ECLIPSE_FORMATTER_ACTIVE_PROFILE;
import static de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.ECLIPSE_FORMATTER_ENABLED;
import static de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.ECLIPSE_FORMATTER_LOCATION;
import static de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.PRESERVE_BREAKPOINTS;
import static de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.PROJECT_PREF_FILE;
import static de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.SHOW_NOTIFICATIONS;
import static de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.USE_PROJECT_PREFS;
import static de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.getActivePreferences;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.guards.GuardedSectionManager;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.NotificationDisplayer;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;

/**
 *
 * @author markiewb
 */
public class FormatJavaAction {

    public void formatWithNetBeans(final boolean showNotifications, final boolean hasGuardedSections, final boolean isEclipseFormatterEnabled, final boolean isJava, EclipseFormatterUtilities u, final StyledDocument styledDoc, boolean forSave) {
        if (showNotifications) {
            String detail = "";
            if (hasGuardedSections && isEclipseFormatterEnabled) {
                detail += "Because file contains guarded sections. ";
            }
            if (!isJava) {
                detail += "Because file isn't a Java file. ";
            }

            NotificationDisplayer.getDefault().notify("Format using NetBeans formatter", EclipseFormatterUtilities.iconNetBeans, detail, null);
        }
        StatusDisplayer.getDefault().setStatusText("Format using NetBeans formatter");
        u.reformatWithNetBeans(styledDoc, forSave);
    }

    void format(final StyledDocument styledDoc, boolean forSave) {
        GuardedSectionManager guards = GuardedSectionManager.getInstance(styledDoc);
        EclipseFormatterUtilities u = new EclipseFormatterUtilities();
        final boolean hasGuardedSections = guards != null;
        final boolean isJava = EclipseFormatterUtilities.isJava(styledDoc);
        Preferences pref = getActivePreferences(styledDoc);

        final boolean isEclipseFormatterEnabled = pref.getBoolean(ECLIPSE_FORMATTER_ENABLED, false);
        final boolean showNotifications = pref.getBoolean(SHOW_NOTIFICATIONS, false);
        final boolean preserveBreakpoints = pref.getBoolean(PRESERVE_BREAKPOINTS, true);
        final boolean useProjectPrefs = pref.getBoolean(USE_PROJECT_PREFS, true);
        if (!hasGuardedSections && isJava && isEclipseFormatterEnabled) {
            String formatterFile = pref.get(ECLIPSE_FORMATTER_LOCATION, null);
            String formatterProfile = pref.get(ECLIPSE_FORMATTER_ACTIVE_PROFILE, "");
            //use ${projectdir}/.settings/org.eclipse.jdt.core.prefs, if activated in options
            if (useProjectPrefs) {
                FileObject fileForDocument = NbEditorUtilities.getFileObject(styledDoc);
                if (null != fileForDocument) {

                    Project project = FileOwnerQuery.getOwner(fileForDocument);
                    if (null != project) {
                        FileObject projectDirectory = project.getProjectDirectory();
                        FileObject preferenceFile = projectDirectory.getFileObject(".settings/" + PROJECT_PREF_FILE);
                        if (null != preferenceFile) {
                            formatterFile = preferenceFile.getPath();
                        }
                    }
                }
            }

            if (!new File(formatterFile).exists()) {
                //fallback to NB
                formatWithNetBeans(showNotifications, hasGuardedSections, isEclipseFormatterEnabled, isJava, u, styledDoc, forSave);
                return;
            }

            final EclipseFormatter formatter = EclipseFormatterUtilities.getEclipseFormatter(formatterFile, formatterProfile);

            try {
                u.reFormatWithEclipse(styledDoc, formatter, forSave, preserveBreakpoints);
            } catch (ProfileNotFoundException e) {
                NotifyDescriptor notify = new NotifyDescriptor.Message(String.format("<html>Profile '%s' not found in <tt>%s</tt><br><br>Please configure a valid one in the project properties OR at Tools|Options|Java|Eclipse Formatter!", formatterProfile, formatterFile), NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notify(notify);
            } catch (CannotLoadConfigurationException e) {
                NotifyDescriptor notify = new NotifyDescriptor.Message(String.format("<html>Could not find configuration file %s.<br>Make sure the file exists and it can be read.", formatterFile), NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notify(notify);
                return;
            }

            String msg = "";
            if (de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.isWorkspaceMechanicFile(formatterFile)) {
                //Workspace mechanic file
                msg = String.format("Using %s", formatterFile);
            } else if (de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.isXMLConfigurationFile(formatterFile)) {
                //XML file
                msg = String.format("Using profile '%s' from %s", formatterProfile, formatterFile);
            } else if (de.markiewb.netbeans.plugins.eclipse.formatter.Preferences.isProjectSetting(formatterFile)) {
                //org.eclipse.jdt.core.prefs
                msg = String.format("Using %s", formatterFile);
            }

            if (showNotifications) {
                NotificationDisplayer.getDefault().notify("Format using Eclipse formatter", EclipseFormatterUtilities.iconEclipse, msg, null);
            }
            StatusDisplayer.getDefault().setStatusText("Format using Eclipse formatter: " + msg);

        } else {
            formatWithNetBeans(showNotifications, hasGuardedSections, isEclipseFormatterEnabled, isJava, u, styledDoc, forSave);
        }
    }

}
