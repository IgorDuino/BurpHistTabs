package com.example.HttpHistoryTabs;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import burp.api.montoya.ui.swing.SwingUtils;
import burp.api.montoya.repeater.Repeater;
import burp.api.montoya.intruder.Intruder;
import burp.api.montoya.scope.Scope;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
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
        addButton.setFocusPainted(false);
        addButton.setMargin(new Insets(1, 4, 1, 4));

        mainTabbedPane.putClientProperty("JTabbedPane.trailingComponent", addButton);

        mainPanel = new JPanel(new BorderLayout());
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
                        responseReceived.annotations());

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

    private void removeSubTab(HttpHistorySubTab subTabToRemove) {
        if (subTabToRemove != null && subTabs.contains(subTabToRemove)) {
            mainTabbedPane.remove(subTabToRemove.getUiComponent());
            subTabs.remove(subTabToRemove);
            api.logging().logToOutput("Removed sub-tab: " + subTabToRemove.getTabTitle());

            if (subTabs.isEmpty()) {
                addNewSubTab();
            }
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

        private boolean isRequestResponseViewVisible = true;
        private boolean isRequestResponseViewAtBottom = false;

        private final JSplitPane viewersSplitPane;
        private final JPanel requestResponseViewPanel;

        private final JScrollPane tableScrollPane;
        private JSplitPane mainHorizontalSplitPane;
        private final JPanel dynamicContentPanel;

        private final JButton hideViewButton;
        private final JButton showViewButton;
        private final JButton togglePositionButton;
        private final JPanel viewControlsPanel;

        public HttpHistorySubTab(MontoyaApi api, String title, HttpHistoryTabsFactory factory) {
            this.api = api;
            this.tabTitle = title;
            this.factory = factory;
            UserInterface userInterface = api.userInterface();
            SwingUtils swingUtils = userInterface.swingUtils();

            filterTextField = new JTextField(30);
            JButton applyFilterButton = new JButton("Apply Filter");

            JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            filterPanel.add(new JLabel("Regex filter:"));
            filterPanel.add(filterTextField);
            filterPanel.add(applyFilterButton);

            tableModel = new HistoryTableModel();
            historyTable = new JTable(tableModel);
            historyTable.setAutoCreateRowSorter(true);
            tableScrollPane = new JScrollPane(historyTable);

            requestViewer = userInterface.createHttpRequestEditor(EditorOptions.READ_ONLY);
            responseViewer = userInterface.createHttpResponseEditor(EditorOptions.READ_ONLY);

            viewersSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, requestViewer.uiComponent(),
                    responseViewer.uiComponent());
            viewersSplitPane.setResizeWeight(0.5);

            requestResponseViewPanel = new JPanel(new BorderLayout());
            requestResponseViewPanel.add(viewersSplitPane, BorderLayout.CENTER);

            hideViewButton = new JButton("Hide View");
            showViewButton = new JButton("Show View");
            togglePositionButton = new JButton("Toggle View Position");

            showViewButton.setVisible(false);

            viewControlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            viewControlsPanel.add(hideViewButton);
            viewControlsPanel.add(showViewButton);
            viewControlsPanel.add(togglePositionButton);

            JPanel topActionPanel = new JPanel(new BorderLayout());
            topActionPanel.add(filterPanel, BorderLayout.WEST);
            topActionPanel.add(viewControlsPanel, BorderLayout.EAST);

            mainPanel = new JPanel(new BorderLayout());
            mainPanel.add(topActionPanel, BorderLayout.NORTH);

            dynamicContentPanel = new JPanel(new BorderLayout());
            mainPanel.add(dynamicContentPanel, BorderLayout.CENTER);

            updateViewLayout();

            hideViewButton.addActionListener(e -> {
                isRequestResponseViewVisible = false;
                updateViewLayout();
            });

            showViewButton.addActionListener(e -> {
                isRequestResponseViewVisible = true;
                updateViewLayout();
            });

            togglePositionButton.addActionListener(e -> {
                if (isRequestResponseViewVisible) {
                    isRequestResponseViewAtBottom = !isRequestResponseViewAtBottom;
                    updateViewLayout();
                }
            });

            historyTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int selectedRowInView = historyTable.getSelectedRow();
                    if (selectedRowInView >= 0) {
                        boolean visibilityJustChanged = false;
                        if (!isRequestResponseViewVisible) {
                            isRequestResponseViewVisible = true;
                            visibilityJustChanged = true;
                        }

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

                        if (visibilityJustChanged) {
                            updateViewLayout();
                        }
                    }
                }
            });

            // Add context menu to historyTable
            JPopupMenu contextMenu = new JPopupMenu();
            JMenuItem sendToRepeaterItem = new JMenuItem("Send to Repeater");
            JMenuItem sendToIntruderItem = new JMenuItem("Send to Intruder");
            JMenuItem addToScopeItem = new JMenuItem("Add to Scope");
            contextMenu.add(sendToRepeaterItem);
            contextMenu.add(sendToIntruderItem);
            contextMenu.add(addToScopeItem);

            sendToRepeaterItem.addActionListener(e -> {
                int row = historyTable.getSelectedRow();
                if (row >= 0) {
                    int modelRow = historyTable.convertRowIndexToModel(row);
                    HttpRequestResponse reqResp = tableModel.getRequestResponseAt(modelRow);
                    if (reqResp != null) {
                        api.repeater().sendToRepeater(reqResp.request(), null);
                    }
                }
            });

            sendToIntruderItem.addActionListener(e -> {
                int row = historyTable.getSelectedRow();
                if (row >= 0) {
                    int modelRow = historyTable.convertRowIndexToModel(row);
                    HttpRequestResponse reqResp = tableModel.getRequestResponseAt(modelRow);
                    if (reqResp != null) {
                        api.intruder().sendToIntruder(reqResp.request());
                    }
                }
            });

            addToScopeItem.addActionListener(e -> {
                int row = historyTable.getSelectedRow();
                if (row >= 0) {
                    int modelRow = historyTable.convertRowIndexToModel(row);
                    HttpRequestResponse reqResp = tableModel.getRequestResponseAt(modelRow);
                    if (reqResp != null) {
                        String url = reqResp.request().url();
                        api.scope().includeInScope(url);
                    }
                }
            });

            historyTable.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showMenu(e);
                    }
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showMenu(e);
                    }
                }

                private void showMenu(java.awt.event.MouseEvent e) {
                    int row = historyTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < historyTable.getRowCount()) {
                        historyTable.setRowSelectionInterval(row, row);
                        contextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            });
        }

        private void updateViewLayout() {

            dynamicContentPanel.removeAll();

            if (!isRequestResponseViewVisible) {
                dynamicContentPanel.add(tableScrollPane, BorderLayout.CENTER);
                togglePositionButton.setEnabled(false);
                togglePositionButton.setText(isRequestResponseViewAtBottom ? "Move to Right" : "Move to Bottom");
                hideViewButton.setVisible(false);
                showViewButton.setVisible(true);
            } else {
                togglePositionButton.setEnabled(true);
                hideViewButton.setVisible(true);
                showViewButton.setVisible(false);
                if (isRequestResponseViewAtBottom) {
                    togglePositionButton.setText("Move to Right");
                    dynamicContentPanel.add(tableScrollPane, BorderLayout.CENTER);
                    dynamicContentPanel.add(requestResponseViewPanel, BorderLayout.SOUTH);
                    requestResponseViewPanel
                            .setPreferredSize(new Dimension(0, (int) (dynamicContentPanel.getHeight() * 0.5)));
                } else {
                    togglePositionButton.setText("Move to Bottom");
                    mainHorizontalSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScrollPane,
                            requestResponseViewPanel);
                    mainHorizontalSplitPane.setResizeWeight(0.4);
                    dynamicContentPanel.add(mainHorizontalSplitPane, BorderLayout.CENTER);
                }
            }

            dynamicContentPanel.revalidate();
            dynamicContentPanel.repaint();
            mainPanel.revalidate();
            mainPanel.repaint();
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
            String responseBody = (requestResponse.response() != null)
                    ? requestResponse.response().bodyToString().toLowerCase()
                    : "";

            if (host != null && host.toLowerCase().contains(filterText))
                return true;
            if (url != null && url.toLowerCase().contains(filterText))
                return true;
            if (requestBody.contains(filterText))
                return true;
            return responseBody.contains(filterText);
        }
    }

    private static class HistoryTableModel extends AbstractTableModel {
        private final List<HttpRequestResponse> log = new ArrayList<>();
        private final String[] columnNames = { "#", "Host", "Method", "URL", "Status", "Length" };

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
            if (entry == null)
                return null;

            switch (columnIndex) {
                case 0:
                    return rowIndex + 1;
                case 1:
                    return entry.request().httpService().host();
                case 2:
                    return entry.request().method();
                case 3:
                    return entry.request().path();
                case 4:
                    return entry.response() != null ? entry.response().statusCode() : "N/A";
                case 5:
                    return entry.response() != null ? entry.response().bodyToString().length() : 0;
                default:
                    return "";
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