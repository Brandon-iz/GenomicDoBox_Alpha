package ch.cyberduck.binding;

/*
 * Copyright (c) 2002-2016 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.binding.application.NSAlert;
import ch.cyberduck.binding.application.NSButton;
import ch.cyberduck.binding.application.NSCell;
import ch.cyberduck.binding.application.NSPrintInfo;
import ch.cyberduck.binding.application.NSPrintOperation;
import ch.cyberduck.binding.application.NSPrintPanel;
import ch.cyberduck.binding.application.NSTextField;
import ch.cyberduck.binding.application.NSTextView;
import ch.cyberduck.binding.application.NSView;
import ch.cyberduck.binding.application.NSWindow;
import ch.cyberduck.binding.application.SheetCallback;
import ch.cyberduck.binding.application.WindowListener;
import ch.cyberduck.binding.foundation.NSAttributedString;
import ch.cyberduck.binding.foundation.NSDictionary;
import ch.cyberduck.binding.foundation.NSNotification;
import ch.cyberduck.core.DefaultProviderHelpService;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.LocaleFactory;
import ch.cyberduck.core.diagnostics.ReachabilityFactory;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.local.BrowserLauncherFactory;
import ch.cyberduck.core.notification.NotificationAlertCallback;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.threading.AlertCallback;
import ch.cyberduck.core.threading.DefaultFailureDiagnostics;
import ch.cyberduck.core.threading.FailureDiagnostics;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.rococoa.Foundation;
import org.rococoa.ID;
import org.rococoa.cocoa.foundation.NSPoint;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class WindowController extends BundleController implements NSWindow.Delegate {
    private static final Logger log = Logger.getLogger(WindowController.class);

    protected static final String DEFAULT = LocaleFactory.localizedString("Default");

    public WindowController() {
        super();
    }

    @Override
    public void invalidate() {
        listeners.clear();
        if(window != null) {
            window.setDelegate(null);
        }
        super.invalidate();
    }

    /**
     * The window this controller is owner of
     */
    @Outlet
    protected NSWindow window;

    private Set<WindowListener> listeners
            = Collections.synchronizedSet(new HashSet<WindowListener>());

    /**
     * @param listener Callback on window close
     */
    public void addListener(final WindowListener listener) {
        listeners.add(listener);
    }

    /**
     * @param listener Callback on window close
     */
    public void removeListener(final WindowListener listener) {
        listeners.remove(listener);
    }

    public void setWindow(final NSWindow window) {
        this.window = window;
        window.setReleasedWhenClosed(!this.isSingleton());
        window.setDelegate(this.id());
    }

    public NSWindow window() {
        return window;
    }

    /**
     * A singleton window is not released when closed and the controller is not invalidated
     *
     * @return Always false
     * @see #invalidate()
     * @see ch.cyberduck.binding.application.NSWindow#setReleasedWhenClosed(boolean)
     */
    public boolean isSingleton() {
        return false;
    }

    /**
     * @return True if the controller window is on screen.
     */
    public boolean isVisible() {
        final NSWindow w = this.window();
        if(null == w) {
            return false;
        }
        return w.isVisible();
    }

    @Override
    public void windowDidBecomeKey(final NSNotification notification) {
        //
    }

    @Override
    public void windowDidResignKey(final NSNotification notification) {
        //
    }

    @Override
    public void windowDidBecomeMain(final NSNotification notification) {
        //
    }

    @Override
    public void windowDidResignMain(final NSNotification notification) {
        //
    }

    public void windowWillEnterFullScreen(final NSNotification notification) {
        //
    }

    public void windowWillExitFullScreen(final NSNotification notification) {
        //
    }

    public void windowDidFailToEnterFullScreen(final NSWindow window) {
        log.error("Error entering full screen");
    }

    /**
     * @see ch.cyberduck.binding.application.NSWindow.Delegate
     */
    @Override
    public boolean windowShouldClose(final NSWindow sender) {
        return true;
    }

    /**
     * Override this method if the controller should not be invalidated after its window closes
     */
    @Override
    public void windowWillClose(final NSNotification notification) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Window will close %s", notification));
        }
        for(WindowListener listener : listeners.toArray(new WindowListener[listeners.size()])) {
            listener.windowWillClose();
        }
        if(!this.isSingleton()) {
            //If the window is closed it is assumed the controller object is no longer used
            this.invalidate();
        }
    }

    /**
     * Position this controller's window relative to other open windows
     */
    protected NSPoint cascade(final NSPoint point) {
        return window.cascadeTopLeftFromPoint(point);
    }

    /**
     * @param toggle Checkbox
     * @param select Selected
     */
    protected void setState(final NSButton toggle, final boolean select) {
        if(select) {
            toggle.performClick(null);
        }
        toggle.setState(select ? NSCell.NSOnState : NSCell.NSOffState);
    }

    @Override
    public boolean alert(final Host host, final BackgroundException failure,
                         final StringBuilder transcript) {
        return new PanelAlertCallback(this).alert(host, failure, transcript);
    }

    /**
     * @param alert Sheet
     * @return Return code from the dialog if called from background thread.
     */
    @Override
    public int alert(final NSAlert alert) {
        return this.alert(alert, (String) null);
    }

    /**
     * @param alert Sheet
     * @param help  Help URL
     * @return Button selection
     */
    public int alert(final NSAlert alert, final String help) {
        final int[] response = new int[1];
        this.alert(alert, new SheetCallback() {
            @Override
            public void callback(final int returncode) {
                response[0] = returncode;
            }
        }, help);
        return response[0];
    }

    /**
     * Display alert as sheet to the window of this controller
     *
     * @param alert    Sheet
     * @param callback Dismissed notification
     */
    public void alert(final NSAlert alert, final SheetCallback callback) {
        this.alert(alert, callback, null);
    }

    /**
     * @param alert    Sheet
     * @param callback Dismissed notification
     * @param help     Help URL
     */
    public void alert(final NSAlert alert, final SheetCallback callback, final String help) {
        final SheetController c = new AlertController(this, alert) {
            @Override
            public void callback(final int returncode) {
                callback.callback(returncode);
            }

            @Override
            protected void help() {
                if(StringUtils.isBlank(help)) {
                    super.help();
                }
                else {
                    BrowserLauncherFactory.get().open(help);
                }
            }
        };
        c.beginSheet();
    }

    /**
     * Attach a sheet to this window
     *
     * @param sheet The sheet to be attached to this window
     * @see SheetController#beginSheet()
     */
    protected void alert(final NSWindow sheet) {
        this.alert(sheet, new SheetCallback() {
            @Override
            public void callback(final int returncode) {
                //
            }
        });
    }

    /**
     * Attach a sheet to this window
     *
     * @param sheet    The sheet to be attached to this window
     * @param callback The callback to call after the sheet is dismissed
     * @see SheetController#beginSheet()
     */
    protected void alert(final NSWindow sheet, final SheetCallback callback) {
        final SheetController c = new SheetController(this) {
            @Override
            public void callback(final int returncode) {
                callback.callback(returncode);
            }

            @Override
            public NSWindow window() {
                return sheet;
            }
        };
        c.beginSheet();
    }

    protected void updateField(final NSTextView f, final String value) {
        f.setString(StringUtils.isNotBlank(value) ? value : StringUtils.EMPTY);
    }

    protected void updateField(final NSTextField f, final String value) {
        f.setStringValue(StringUtils.isNotBlank(value) ? value : StringUtils.EMPTY);
    }

    protected void updateField(final NSTextField f, final String value, final NSDictionary attributes) {
        f.setAttributedStringValue(NSAttributedString.attributedStringWithAttributes(StringUtils.isNotBlank(value) ? value : StringUtils.EMPTY, attributes));

    }

    @Action
    public void helpButtonClicked(final NSButton sender) {
        BrowserLauncherFactory.get().open(PreferencesFactory.get().getProperty("website.help"));
    }

    protected void print(NSView view) {
        NSPrintInfo print = NSPrintInfo.sharedPrintInfo();
        print.setOrientation(NSPrintInfo.NSPrintingOrientation.NSLandscapeOrientation);
        NSPrintOperation op = NSPrintOperation.printOperationWithView_printInfo(view, print);
        op.setShowsPrintPanel(true);
        final NSPrintPanel panel = op.printPanel();
        panel.setOptions(panel.options() | NSPrintPanel.NSPrintPanelShowsOrientation
                | NSPrintPanel.NSPrintPanelShowsPaperSize | NSPrintPanel.NSPrintPanelShowsScaling);
        op.runOperationModalForWindow_delegate_didRunSelector_contextInfo(this.window(), this.id(),
                Foundation.selector("printOperationDidRun:success:contextInfo:"), null);
    }

    public void printOperationDidRun_success_contextInfo(NSPrintOperation op, boolean success, ID contextInfo) {
        if(!success) {
            log.warn(String.format("Printing failed for context %s", contextInfo));
        }
    }

    private static final class PanelAlertCallback implements AlertCallback {

        private final WindowController controller;

        private final FailureDiagnostics<Exception> diagnostics
                = new DefaultFailureDiagnostics();

        private final NotificationAlertCallback notification
                = new NotificationAlertCallback();

        public PanelAlertCallback(final WindowController controller) {
            this.controller = controller;
        }

        @Override
        public boolean alert(final Host host, final BackgroundException failure, final StringBuilder log) {
            notification.alert(host, failure, log);
            if(controller.isVisible()) {
                final NSAlert alert = NSAlert.alert(
                        null == failure.getMessage() ? LocaleFactory.localizedString("Unknown") : failure.getMessage(),
                        null == failure.getDetail() ? LocaleFactory.localizedString("Unknown") : failure.getDetail(),
                        LocaleFactory.localizedString("Try Again", "Alert"), // default button
                        diagnostics.determine(failure) == FailureDiagnostics.Type.network
                                ? LocaleFactory.localizedString("Network Diagnostics", "Alert") : null, //other button
                        LocaleFactory.localizedString("Cancel", "Alert") // alternate button
                );
                alert.setShowsHelp(true);
                final AlertController c = new AlertController(controller, alert) {
                    @Override
                    public void callback(final int returncode) {
                        if(returncode == ALTERNATE_OPTION) {
                            ReachabilityFactory.get().diagnose(host);
                        }
                    }

                    @Override
                    protected void help() {
                        new DefaultProviderHelpService().help(host.getProtocol());
                    }
                };
                c.beginSheet();
                if(c.returnCode() == SheetCallback.DEFAULT_OPTION) {
                    return true;
                }
            }
            return false;
        }
    }
}
