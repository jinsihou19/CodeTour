package org.uom.lefterisxris.codetour.tours.service;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;
import org.uom.lefterisxris.codetour.tours.domain.Step;
import org.uom.lefterisxris.codetour.tours.state.StateManager;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.regex.Pattern;

import static org.uom.lefterisxris.codetour.tours.service.Utils.renderFullDoc;

/**
 * Renders a Popup which includes the Step Documentation
 *
 * @author Eleftherios Chrysochoidis
 * Date: 8/5/2022
 */
public class StepRendererPane extends JPanel {

    private static final Pattern JAVA_FILE_PATTERN = java.util.regex.Pattern.compile("([\\w.]+\\.java):(\\d+)");
    private static final Pattern METHOD_PATTERN = java.util.regex.Pattern.compile("([\\w.]+)#([\\w]+)");
    private static final Pattern JBCEF_METHOD_PATTERN = Pattern.compile("file:///jbcefbrowser/([\\w.]+)#([\\w]+)");

    private final Step step;
    private final Project project;

    public StepRendererPane(Step step, Project project) {
        super(true);
        this.step = step;
        this.project = project;
        init();
    }

    private boolean matchCode(String url) {
        return JAVA_FILE_PATTERN.matcher(url).matches()
                || METHOD_PATTERN.matcher(url).matches()
                || JBCEF_METHOD_PATTERN.matcher(url).matches();
    }

    private JComponent markdownJCEFHtmlPanelForRender() {
        final String stepDoc = renderFullDoc(
                StateManager.getInstance().getState(project).getStepMetaLabel(step.getTitle()),
                step.getDescription(),
                step.getFile() != null ? String.format("%s:%s", step.getFile(), step.getLine()) : "");

//        CefApp.getInstance().registerSchemeHandlerFactory(
//                "file",
//                "",
//                (cefBrowser, cefFrame, s, cefRequest) -> new ResourceHandler());

        JBCefBrowser browser = new JBCefBrowser();

        browser.loadHTML(stepDoc);
        browser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request,
                                          boolean user_gesture, boolean is_redirect) {
                return dealWithJCEFLink(request.getURL());
            }
        }, browser.getCefBrowser());

        return browser.getComponent();
    }

    private boolean dealWithJCEFLink(String link) {
        if (link.startsWith("http://") || link.startsWith("https://")) {
            BrowserUtil.browse(link);
            return true;
        } else if (link.startsWith("navigate://") || matchCode(link)) {
            Navigator.navigateCode(link, project);
            return true;
        }
        return false;
    }

    protected void init() {
        setLayout(new BorderLayout());
        add(markdownJCEFHtmlPanelForRender(), BorderLayout.CENTER);
    }
}
