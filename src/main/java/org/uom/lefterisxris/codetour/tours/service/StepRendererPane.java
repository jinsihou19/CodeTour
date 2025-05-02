package org.uom.lefterisxris.codetour.tours.service;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationHintEditorPane;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.lang.documentation.DocumentationImageResolver;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil;
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.uom.lefterisxris.codetour.tours.domain.Step;
import org.uom.lefterisxris.codetour.tours.state.StateManager;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Image;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.regex.Pattern;

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

    /**
     * 利用idea中的文档pane作为渲染
     * 已知问题：
     * 1. 代码渲染需要自己提供样式
     * 2. 图片由于内部限制无法渲染
     * 3. 无法使用markdown的功能，比如渲染uml
     * 优势
     * 1. 代码导航
     *
     *
     * @return
     */
    private JComponent documentationHintPaneForRender() {

        final String stepDoc = renderFullDoc(
                StateManager.getInstance().getState(project).getStepMetaLabel(step.getTitle()),
                step.getDescription(),
                step.getFile() != null ? String.format("%s:%s", step.getFile(), step.getLine()) : "");
//        System.out.println(stepDoc);

        // 创建 DocumentationHintEditorPane
        DocumentationHintEditorPane editorPane = new DocumentationHintEditorPane(project, Collections.emptyMap(), new DocumentationImageResolver() {
            @Override
            public @Nullable Image resolveImage(@NotNull String s) {
                try {
                    return ImageIO.read(new URL(s).openStream());
                } catch (IOException e) {
                    return null;
                }
            }
        });
        editorPane.setEditable(false); // 禁止编辑
        editorPane.setText(stepDoc);
        editorPane.setBorder(JBUI.Borders.empty(5)); // 设置边距
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleHyperlinkClick(e.getDescription());
            }
        });

        return new JBScrollPane(editorPane);
    }

    /**
     * 处理超链接点击事件
     */
    private void handleHyperlinkClick(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            BrowserUtil.browse(url);
        } else if (url.startsWith("navigate://") || matchCode(url)) {
            Navigator.navigateCode(url, project);
        }
    }

    private Pattern JAVA_FILE_PATTERN = Pattern.compile("([\\w.]+\\.java):(\\d+)");
    private Pattern METHOD_PATTERN = Pattern.compile("([\\w.]+)#([\\w]+)");

    private boolean matchCode(String url) {
        return JAVA_FILE_PATTERN.matcher(url).matches() || METHOD_PATTERN.matcher(url).matches();
    }


    private JComponent getComponent() {
        LightVirtualFile virtualFile = new LightVirtualFile(step.getTitle(), step.getDescription());
        String s = MarkdownUtil.INSTANCE.generateMarkdownHtml(virtualFile, step.getDescription(), project);
        DocumentationManager documentationManager = DocumentationManager.getInstance(project);
        DocumentationComponent component = new DocumentationComponent(documentationManager);
        component.setData(null, s, null, null, null);

        return component;
    }

    private JComponent  markdownJCEFHtmlPanelForRender() {
        LightVirtualFile virtualFile = new LightVirtualFile(step.getTitle(), step.getDescription());
        MarkdownJCEFHtmlPanel htmlPanel = new MarkdownJCEFHtmlPanel(project, virtualFile);

        return htmlPanel.getComponent();
    }

    protected void init() {
        setLayout(new BorderLayout());
        add(documentationHintPaneForRender(), BorderLayout.CENTER);
    }
}
