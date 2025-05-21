package com.example.HttpHistoryTabs;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.extension.ExtensionUnloadingHandler;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.swing.SwingUtils;

public class HttpHistoryTabsExtension implements BurpExtension {
    private MontoyaApi api;
    private Logging logging;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        this.logging = api.logging();
        SwingUtils swingUtils = api.userInterface().swingUtils();

        // Set extension name
        api.extension().setName("HttpHistoryTabs");

        logging.logToOutput("HttpHistoryTabs extension loading...");

        // Register the main tab
        HttpHistoryTabsFactory httpHistoryTabsFactory = new HttpHistoryTabsFactory(api);
        api.userInterface().registerSuiteTab("HttpHistoryTabs", httpHistoryTabsFactory.getMainPanel());


        // Register an unloading handler
        api.extension().registerUnloadingHandler(new ExtensionUnloadingHandler() {
            @Override
            public void extensionUnloaded() {
                logging.logToOutput("HttpHistoryTabs extension unloaded.");
            }
        });

        logging.logToOutput("HttpHistoryTabs extension loaded successfully.");
    }
} 