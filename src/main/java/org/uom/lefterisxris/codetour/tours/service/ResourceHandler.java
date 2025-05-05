package org.uom.lefterisxris.codetour.tours.service;

import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.io.IOException;
import java.io.InputStream;

public class ResourceHandler implements CefResourceHandler {
    private InputStream inputStream;
    private String mimeType;

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        String url = request.getURL();
        // 解析URL路径（例如 "myapp:///html/index.html"）
        String resourcePath = url.replace("http://codecour", "");

        // 从类路径加载资源
        inputStream = getClass().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            return false;
        }

        // 根据文件后缀设置MIME类型
        if (resourcePath.endsWith(".html")) {
            mimeType = "text/html";
        } else if (resourcePath.endsWith(".css")) {
            mimeType = "text/css";
        } else if (resourcePath.endsWith(".js")) {
            mimeType = "application/javascript";
        } else {
            mimeType = "application/octet-stream";
        }

        callback.Continue();
        return true;
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        response.setMimeType(mimeType);
        response.setStatus(200);
        responseLength.set(-1); // -1表示流式传输
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
        try {
            int available = inputStream.available();
            if (available == 0) {
                bytesRead.set(0);
                return false;
            }
            bytesRead.set(inputStream.read(dataOut, 0, Math.min(bytesToRead, available)));
            return bytesRead.get() > 0;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void cancel() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}