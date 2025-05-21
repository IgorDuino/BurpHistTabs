package com.example.HttpHistoryTabs;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.extension.ExtensionUnloadingHandler;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.swing.SwingUtils;

public class HttpHistoryTabsExtension implements BurpExtension {
    private MontoyaApi api;
    private Logging logging;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        this.logging = api.logging();
        SwingUtils swingUtils = api.userInterface().swingUtils();

        api.extension().setName("HttpHistoryTabs");

        logging.logToOutput("HttpHistoryTabs extension loading...");

        HttpHistoryTabsFactory httpHistoryTabsFactory = new HttpHistoryTabsFactory(api);
        api.userInterface().registerSuiteTab("HttpHistoryTabs", httpHistoryTabsFactory.getMainPanel());

        api.extension().registerUnloadingHandler(new ExtensionUnloadingHandler() {
            @Override
            public void extensionUnloaded() {
                logging.logToOutput("HttpHistoryTabs extension unloaded.");
            }
        });

        logging.logToOutput("HttpHistoryTabs extension loaded successfully.");
    }
}