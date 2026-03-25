package com.yupi.yuaicodemother.core.parser;

import com.yupi.yuaicodemother.ai.model.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 单文件代码解析器
 *
 * @author yupi
 */
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_DOCUMENT_PATTERN = Pattern.compile("(?is)<!doctype\\s+html[\\s\\S]*?</html>|<html[\\s\\S]*?</html>");
    private static final Pattern MARKDOWN_HINT_PATTERN = Pattern.compile("(?m)^\\s*(#{1,6}\\s+|```)");
    private static final int RAW_PREVIEW_MAX_LENGTH = 4000;

    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        // 提取 HTML 代码
        String htmlCode = extractHtmlCode(codeContent);
        if (htmlCode == null) {
            htmlCode = extractHtmlDocument(codeContent);
        }
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        } else if (looksLikeMarkdownInstruction(codeContent)) {
            result.setHtmlCode(buildDiagnosticHtml(codeContent));
        } else {
            // 如果没有找到代码块，将整个内容作为HTML
            result.setHtmlCode(codeContent.trim());
        }
        return result;
    }

    /**
     * 提取 HTML 代码内容
     *
     * @param content 原始内容
     * @return HTML代码
     */
    private String extractHtmlCode(String content) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 提取完整 HTML 文档（兼容模型未使用 markdown 代码块的情况）
     */
    private String extractHtmlDocument(String content) {
        Matcher matcher = HTML_DOCUMENT_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 判断内容是否更像是项目说明文而不是 HTML
     */
    private boolean looksLikeMarkdownInstruction(String content) {
        return MARKDOWN_HINT_PATTERN.matcher(content).find();
    }

    /**
     * 构建可读的诊断页，避免把 Markdown 说明文直接当成网页内容。
     */
    private String buildDiagnosticHtml(String rawContent) {
        String safeRawContent = escapeHtml(rawContent);
        if (safeRawContent.length() > RAW_PREVIEW_MAX_LENGTH) {
            safeRawContent = safeRawContent.substring(0, RAW_PREVIEW_MAX_LENGTH)
                    + "\n\n...（内容过长，已截断）";
        }
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>代码提取失败</title>
                  <style>
                    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif; background: #f8fafc; color: #111827; }
                    .wrap { max-width: 900px; margin: 48px auto; padding: 0 20px; }
                    .card { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 24px; box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06); }
                    h1 { margin: 0 0 12px; font-size: 22px; }
                    p { margin: 8px 0; line-height: 1.7; color: #374151; }
                    .tip { margin-top: 16px; padding: 12px 14px; border-radius: 8px; background: #eff6ff; border: 1px solid #bfdbfe; color: #1e3a8a; }
                    pre { margin-top: 16px; padding: 14px; border-radius: 8px; background: #0b1020; color: #d1d5db; overflow: auto; white-space: pre-wrap; word-break: break-word; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <div class="card">
                      <h1>未检测到可渲染的 HTML 代码</h1>
                      <p>当前返回内容更像是 Markdown 说明文，系统无法提取出 HTML 页面代码。</p>
                      <div class="tip">建议：如果你要生成 Vue 项目，请将应用生成类型切换为“Vue 工程模式”；若使用 HTML 模式，请让模型只输出 html 代码块。</div>
                      <pre>%s</pre>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(safeRawContent);
    }

    private String escapeHtml(String content) {
        return content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}