package org.uom.lefterisxris.codetour.tours.service;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightVirtualFile;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil;
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel;
import org.uom.lefterisxris.codetour.tours.domain.Step;
import org.uom.lefterisxris.codetour.tours.state.StateManager;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

import static org.uom.lefterisxris.codetour.tours.service.Utils.renderFullDoc;

/**
 * Renders a Popup which includes the Step Documentation
 *
 * @author Eleftherios Chrysochoidis
 * Date: 8/5/2022
 */
public class StepRendererPane extends JPanel {
    private final Step step;
    private final Project project;

    public StepRendererPane(Step step, Project project) {
        super(true);
        this.step = step;
        this.project = project;
        init();
    }

    private JComponent getOldComponent() {

        final String stepDoc = renderFullDoc(
                StateManager.getInstance().getState(project)
                        .getStepMetaLabel(step.getTitle()).orElse("Step " + step.getTitle()),
                step.getDescription(),
                step.getFile() != null ? String.format("%s:%s", step.getFile(), step.getLine()) : "");

        final DocumentationManager documentationManager = DocumentationManager.getInstance(project);
        final DocumentationComponent component = new DocumentationComponent(documentationManager);
        component.setData(null, stepDoc, null, null, null);

        return component;
    }

    private JComponent getComponent() {
        LightVirtualFile virtualFile = new LightVirtualFile(step.getTitle(), step.getDescription());
        String s = MarkdownUtil.INSTANCE.generateMarkdownHtml(virtualFile, step.getDescription(), project);
        DocumentationManager documentationManager = DocumentationManager.getInstance(project);
        DocumentationComponent component = new DocumentationComponent(documentationManager);
        component.setData(null, s, null, null, null);

        return component;
    }

    private JComponent getComponent1() {
        LightVirtualFile virtualFile = new LightVirtualFile(step.getTitle(), step.getDescription());
        MarkdownJCEFHtmlPanel htmlPanel = new MarkdownJCEFHtmlPanel(project, virtualFile);

        return htmlPanel.getComponent();
    }

    protected void init() {
        setLayout(new BorderLayout());
        add(getOldComponent(), BorderLayout.CENTER);
    }
}
