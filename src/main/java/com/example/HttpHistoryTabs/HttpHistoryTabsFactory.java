package com.example.HttpHistoryTabs;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import burp.api.montoya.ui.swing.SwingUtils;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HttpHistoryTabsFactory {
    private final MontoyaApi api;
    private final JTabbedPane mainTabbedPane;
    private final List<HttpHistorySubTab> subTabs = new CopyOnWriteArrayList<>();
    private final JPanel mainPanel;

    public HttpHistoryTabsFactory(MontoyaApi api) {
        this.api = api;
        this.mainTabbedPane = new JTabbedPane();

        JButton addButton = new JButton("+");
        addButton.addActionListener(e -> addNewSubTab());

        JButton removeButton = new JButton("-");
        removeButton.addActionListener(e -> removeSelectedSubTab());

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(addButton);
        controlPanel.add(removeButton);

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(mainTabbedPane, BorderLayout.CENTER);

        addNewSubTab();

        api.http().registerHttpHandler(new HttpHandler() {
            @Override
            public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
                return RequestToBeSentAction.continueWith(requestToBeSent);
            }

            @Override
            public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
                HttpRequestResponse requestResponse = HttpRequestResponse.httpRequestResponse(
                        responseReceived.initiatingRequest(),
                        responseReceived,
                        responseReceived.annotations()
                );

                SwingUtilities.invokeLater(() -> {
                    for (HttpHistorySubTab subTab : subTabs) {
                        if (subTab.matchesFilters(requestResponse)) {
                            subTab.addRequestResponse(requestResponse);
                        }
                    }
                });
                return ResponseReceivedAction.continueWith(responseReceived);
            }
        });
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void addNewSubTab() {
        int tabCount = mainTabbedPane.getTabCount();
        String newTabTitle = "Tab " + (tabCount + 1);
        HttpHistorySubTab newSubTab = new HttpHistorySubTab(api, newTabTitle, this);
        subTabs.add(newSubTab);

        JPanel tabPanel = new JPanel(new BorderLayout());
        tabPanel.setOpaque(false);
        JLabel titleLabel = new JLabel(newTabTitle + " ");
        JButton closeButton = new JButton("x");
        closeButton.setMargin(new Insets(0, 2, 0, 2));
        closeButton.addActionListener(e -> removeSubTab(newSubTab));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setOpaque(false);
        titlePanel.add(titleLabel);
        titlePanel.add(closeButton);

        mainTabbedPane.addTab(null, newSubTab.getUiComponent());
        mainTabbedPane.setTabComponentAt(mainTabbedPane.getTabCount() - 1, titlePanel);
        mainTabbedPane.setSelectedComponent(newSubTab.getUiComponent());
    }

    private void removeSelectedSubTab() {
        int selectedIndex = mainTabbedPane.getSelectedIndex();
        if (selectedIndex >= 0) {
            HttpHistorySubTab subTabToRemove = subTabs.get(selectedIndex);
            removeSubTab(subTabToRemove);
        }
    }

    private void removeSubTab(HttpHistorySubTab subTabToRemove) {
        if (subTabToRemove != null && subTabs.contains(subTabToRemove)) {
            mainTabbedPane.remove(subTabToRemove.getUiComponent());
            subTabs.remove(subTabToRemove);
            api.logging().logToOutput("Removed sub-tab: " + subTabToRemove.getTabTitle());
        }
    }

    private static class HttpHistorySubTab {
        private final MontoyaApi api;
        private final JPanel mainPanel;
        private final JTable historyTable;
        private final HistoryTableModel tableModel;
        private final HttpRequestEditor requestViewer;
        private final HttpResponseEditor responseViewer;
        private final JTextField filterTextField;
        private final List<HttpRequestResponse> displayedRequests = new ArrayList<>();
        private final String tabTitle;
        private final HttpHistoryTabsFactory factory;

        public HttpHistorySubTab(MontoyaApi api, String title, HttpHistoryTabsFactory factory) {
            this.api = api;
            this.tabTitle = title;
            this.factory = factory;
            UserInterface userInterface = api.userInterface();
            SwingUtils swingUtils = userInterface.swingUtils();

            filterTextField = new JTextField(30);
            JButton applyFilterButton = new JButton("Apply Filter");

            JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            filterPanel.add(new JLabel("Filter (e.g., host contains 'example.com'):"));
            filterPanel.add(filterTextField);
            filterPanel.add(applyFilterButton);

            tableModel = new HistoryTableModel();
            historyTable = new JTable(tableModel);
            historyTable.setAutoCreateRowSorter(true);
            JScrollPane tableScrollPane = new JScrollPane(historyTable);

            requestViewer = userInterface.createHttpRequestEditor();
            responseViewer = userInterface.createHttpResponseEditor();

            JSplitPane viewersSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, requestViewer.uiComponent(), responseViewer.uiComponent());
            viewersSplitPane.setResizeWeight(0.5);

            mainPanel = new JPanel(new BorderLayout());
            mainPanel.add(filterPanel, BorderLayout.NORTH);

            JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScrollPane, viewersSplitPane);
            mainSplitPane.setResizeWeight(0.4);
            mainPanel.add(mainSplitPane, BorderLayout.CENTER);

            historyTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int selectedRowInView = historyTable.getSelectedRow();
                    if (selectedRowInView >= 0) {
                        int modelRow = historyTable.convertRowIndexToModel(selectedRowInView);
                        HttpRequestResponse selectedEntry = tableModel.getRequestResponseAt(modelRow);
                        if (selectedEntry != null) {
                            requestViewer.setRequest(selectedEntry.request());
                            if (selectedEntry.response() != null) {
                                responseViewer.setResponse(selectedEntry.response());
                            } else {
                                responseViewer.setResponse(HttpResponse.httpResponse());
                            }
                        }
                    }
                }
            });
        }

        public Component getUiComponent() {
            return mainPanel;
        }

        public String getTabTitle() {
            return tabTitle;
        }

        public void addRequestResponse(HttpRequestResponse requestResponse) {
            SwingUtilities.invokeLater(() -> {
                tableModel.addEntry(requestResponse);
            });
        }

        public boolean matchesFilters(HttpRequestResponse requestResponse) {
            String filterText = filterTextField.getText().trim().toLowerCase();
            if (filterText.isEmpty()) {
                return true;
            }

            String host = requestResponse.request().httpService().host();
            String url = requestResponse.request().url();
            String requestBody = requestResponse.request().bodyToString().toLowerCase();
            String responseBody = (requestResponse.response() != null) ? requestResponse.response().bodyToString().toLowerCase() : "";

            if (host != null && host.toLowerCase().contains(filterText)) return true;
            if (url != null && url.toLowerCase().contains(filterText)) return true;
            if (requestBody.contains(filterText)) return true;
            return responseBody.contains(filterText);
        }
    }

    private static class HistoryTableModel extends AbstractTableModel {
        private final List<HttpRequestResponse> log = new ArrayList<>();
        private final String[] columnNames = {"#", "Host", "Method", "URL", "Status", "Length"};

        public void addEntry(HttpRequestResponse entry) {
            log.add(entry);
            fireTableRowsInserted(log.size() - 1, log.size() - 1);
        }

        public HttpRequestResponse getRequestResponseAt(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < log.size()) {
                return log.get(rowIndex);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return log.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HttpRequestResponse entry = getRequestResponseAt(rowIndex);
            if (entry == null) return null;

            switch (columnIndex) {
                case 0: return rowIndex + 1;
                case 1: return entry.request().httpService().host();
                case 2: return entry.request().method();
                case 3: return entry.request().path();
                case 4: return entry.response() != null ? entry.response().statusCode() : "N/A";
                case 5: return entry.response() != null ? entry.response().bodyToString().length() : 0;
                default: return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0 || columnIndex == 4 || columnIndex == 5) {
                return Integer.class;
            }
            return String.class;
        }
    }
} 