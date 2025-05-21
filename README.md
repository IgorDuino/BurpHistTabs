# HttpHistoryTabs Burp Suite Extension

This Burp Suite extension provides an "HttpHistoryTabs" tab, which functions similarly to the native "HTTP history" tab but with added multi-tab capabilities like Burp's "Repeater".

Convenient for testing multiple accounts or microservices.

## Features

- **Main Tab (`HttpHistoryTabs`):** A top-level tab in Burp Suite.
- **Sub-Tabs:** Dynamically create and delete sub-tabs within `HttpHistoryTabs`.
- **Individual Capture Filters:** Each sub-tab has its own configurable capture filter. (Bambda in future)


## Building the Extension


1. Install JDK 17 and Maven
2. Clone the repository.
3. Run `mvn clean package`

This will produce a JAR file (e.g., `HttpHistoryTabs-1.0-SNAPSHOT-jar-with-dependencies.jar`) in the `target` directory.

## Installation in Burp Suite

1.  Open Burp Suite - "Extensions" tab.
2.  Under "Installed", click "Add".
3.  In the "Add extension" dialog, click "Select file..." under "Extension details".
4.  Locate and select the JAR file you built (e.g., `HttpHistoryTabs-1.0-SNAPSHOT-jar-with-dependencies.jar`).
5.  Click "Next". Burp should load the extension, and you should see the "HttpHistoryTabs" tab appear.

