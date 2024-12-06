package com.distributed.chat.system.common.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.tomcat.util.http.fileupload.IOUtils;

public class CustomHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public CustomHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        IOUtils.copy(super.getInputStream(), outputStream);
    }

    public byte[] getContents() {
        return outputStream.toByteArray();
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream buffer = new ByteArrayInputStream(outputStream.toByteArray());
        return new ServletInputStream() {
            @Override
            public int read() {
                return buffer.read();
            }

            @Override
            public boolean isFinished() {
                return buffer.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
            }
        };
    }
}
