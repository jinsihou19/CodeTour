package org.uom.lefterisxris.codetour.tours.service;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.markdown.parser.MarkdownParser;
import org.jetbrains.annotations.NotNull;
import org.uom.lefterisxris.codetour.tours.domain.Props;
import org.uom.lefterisxris.codetour.tours.domain.Step;

import java.nio.file.Paths;

/**
 * @author Eleftherios Chrysochoidis
 * Date: 21/5/2022
 */
public class Utils {

    /**
     * Custom TagRenderer for md to html, as for some strange reason there is no default implementation now
     * in the related Jetbrains library
     */
    private static final HtmlGenerator.TagRenderer TAG_RENDERER = new HtmlGenerator.TagRenderer() {
        @NotNull
        @Override
        public CharSequence printHtml(@NotNull CharSequence charSequence) {
            return charSequence;
        }

        @NotNull
        @Override
        public CharSequence openTag(@NotNull ASTNode astNode, @NotNull CharSequence tagName,
                                    @NotNull CharSequence[] attributes, boolean autoClose) {
            StringBuilder builder = new StringBuilder();
            builder.append("<").append(tagName);
            for (CharSequence attribute : attributes) {
                if (attribute == null || attribute.isEmpty()) {
                    continue;
                }
                builder.append(" ").append(attribute);
            }
            if (autoClose) {
                builder.append(" />");
            } else {
                builder.append(">");
            }

            return builder.toString();
        }

        @NotNull
        @Override
        public CharSequence closeTag(@NotNull CharSequence charSequence) {
            return String.format("</%s>", charSequence);
        }
    };

    /**
     * Removes whitespaces and transforms the given title in camelCase
     * e.g. Basket Items Issue Reproduce --> basketItemsIssueReproduce.tour
     *
     * @param title The given title
     * @return The suggested filename for this title, in camelCase without whitespaces
     */
    public static String fileNameFromTitle(String title) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            if (StringUtils.isWhitespace(title.charAt(i) + "")) {
                if (i < title.length() - 1 && !StringUtils.isWhitespace(title.charAt(i + 1) + "")) {
                    sb.append(StringUtils.capitalize(title.charAt(i + 1) + ""));
                    i++; // skip the next
                }
            } else
                sb.append(title.charAt(i));
        }

        return StringUtils.uncapitalize(sb.append(Props.TOUR_EXTENSION_FULL).toString());
    }

    public static boolean equalStr(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        return getOrDef(s1, "").equals(getOrDef(s2, ""));
    }

    public static boolean equalInt(Integer i1, Integer i2) {
        if (i1 == null && i2 == null) return true;
        return getOrDef(i1, Integer.MIN_VALUE).equals(getOrDef(i2, Integer.MIN_VALUE));
    }

    public static String renderFullDoc(String title, String description, String file) {
        StringBuilder sb = new StringBuilder();
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append(title);
        sb.append(DocumentationMarkup.DEFINITION_END);
        sb.append(DocumentationMarkup.CONTENT_START);
        if (description != null)
            // For formatting purposes, add <br/> tag when there are 2 consecutive empty lines
            description = description.replaceAll("\\n\\n\\n", "\n\n<br/>\n\n");
        sb.append("\n\n").append(description).append("\n");
        sb.append(DocumentationMarkup.CONTENT_END);
        if (StringUtils.isNotEmpty(file)) {
            sb.append(DocumentationMarkup.SECTIONS_START);
            addKeyValueSection("File:", file, sb);
            sb.append(DocumentationMarkup.SECTIONS_END);
        }
        return mdToHtml(sb.toString());
    }

    public static String mdToHtml(String markdown) {
        // 预处理Mermaid代码块
        String processedMarkdown = markdown;
        if (markdown.contains("```mermaid")) {
            processedMarkdown = markdown.replaceAll(
                "```mermaid\\s*([\\s\\S]*?)```",
                "<div class='mermaid'>$1</div>"
            );
        }

        final MarkdownFlavourDescriptor flavour = new GFMFlavourDescriptor();
        final ASTNode parsedTree = new MarkdownParser(flavour).buildMarkdownTreeFromString(processedMarkdown);
        String html = new HtmlGenerator(processedMarkdown, parsedTree, flavour, false).generateHtml(TAG_RENDERER);

        // 包裹 markdown-body
        html = "<article class=\"markdown-body\">" + html + "</article>";

        // 引入暗黑CSS
        String darkCss = """
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/github-markdown-css@5.5.1/github-markdown-dark.min.css">
            <style>
              body, .markdown-body {
                background: #23272e !important;
                color: #e6e6e6 !important;
              }
              .mermaid, .mermaid svg {
                background: transparent !important;
                color: #e6e6e6 !important;
              }
              /* 滚动条美化 */
              .markdown-body ::-webkit-scrollbar {
                width: 12px;
                background: #23272e;
              }
              .markdown-body ::-webkit-scrollbar-thumb {
                background: #444950;
                border-radius: 6px;
                border: 2px solid #23272e;
              }
              .markdown-body ::-webkit-scrollbar-thumb:hover {
                background: #5c6370;
              }
              .markdown-body ::-webkit-scrollbar-track {
                background: #23272e;
              }
            </style>
        """;

        if (html.contains("class='mermaid'")) {
            String mermaidScript = """
                <script src="https://cdn.jsdelivr.net/npm/mermaid@10.6.1/dist/mermaid.min.js"></script>
                <script>
                    document.addEventListener('DOMContentLoaded', function() {
                        if (typeof mermaid !== 'undefined') {
                            mermaid.initialize({
                                startOnLoad: true,
                                theme: 'dark',
                                securityLevel: 'loose'
                            });
                            mermaid.init(undefined, document.querySelectorAll('.mermaid'));
                        }
                    });
                </script>
            """;
            html = darkCss + mermaidScript + html;
        } else {
            html = darkCss + html;
        }

        return html;
    }

    public static boolean isFileMatchesStep(VirtualFile file, @NotNull Step step) {
        if (file.isDirectory())
            return false;

        final String stepDirectory = step.getDirectory() != null ? step.getDirectory() : "";
        final String stepFilePath = Paths.get(stepDirectory, step.getFile()).toString();
        final String filePath = Paths.get(file.getPath()).toString();

        return filePath.endsWith(stepFilePath);
    }

    private static void addKeyValueSection(String key, String value, StringBuilder sb) {
        sb.append(DocumentationMarkup.SECTION_HEADER_START);
        sb.append(key);
        sb.append(DocumentationMarkup.SECTION_SEPARATOR);
        sb.append("<p>");
        sb.append(createLink(value));
        sb.append(DocumentationMarkup.SECTION_END);
    }

    private static String createLink(String value) {
        return String.format("<a href='navigate://%s'>%s</a>", value, value);
    }

    /**
     * normalize (remove nulls)
     */
    private static <T> T getOrDef(T s, T def) {
        return s != null ? s : def;
    }


}