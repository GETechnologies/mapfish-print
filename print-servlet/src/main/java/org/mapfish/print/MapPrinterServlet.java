package org.mapfish.print;

import com.lowagie.text.DocumentException;
import org.json.JSONException;
import org.json.JSONWriter;
import org.pvalsecc.misc.FileUtilities;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Main print servlet.
 */
public class MapPrinterServlet extends BaseMapServlet {
    private static final String INFO_URL = "/info.json";
    private static final String PRINT_URL = "/print.pdf";
    private static final String CREATE_URL = "/create.json";
    private static final String TEMP_FILE_PREFIX = "mapfish-print";
    private static final String TEMP_FILE_SUFFIX = ".pdf";

    private File tempDir = null;
    private final Map<String, File> tempFiles = Collections.synchronizedMap(new HashMap<String, File>());

    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        final String additionalPath = httpServletRequest.getPathInfo();
        String basePath = httpServletRequest.getRequestURL().toString().replaceFirst(additionalPath + "$", "");

        if (additionalPath.equals(PRINT_URL)) {
            createAndGetPDF(httpServletRequest, httpServletResponse);
        } else if (additionalPath.equals(INFO_URL)) {
            getInfo(httpServletRequest, httpServletResponse, basePath);
        } else if (additionalPath.startsWith("/") && additionalPath.endsWith(TEMP_FILE_SUFFIX)) {
            getPDF(httpServletResponse, additionalPath.substring(1, additionalPath.length() - 4));
        } else {
            error(httpServletResponse, "Unknown method: " + additionalPath, 404);
        }
    }

    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        final String additionalPath = httpServletRequest.getPathInfo();
        String basePath = httpServletRequest.getRequestURL().toString().replaceFirst(additionalPath + "$", "");

        if (additionalPath.equals(CREATE_URL)) {
            createPDF(httpServletRequest, httpServletResponse, basePath);
        } else {
            error(httpServletResponse, "Unknown method: " + additionalPath, 404);
        }
    }

    public void destroy() {
        for (File file : tempFiles.values()) {
            deleteFile(file);
        }
        super.destroy();
    }

    /**
     * All in one method: create and returns the PDF to the client.
     */
    private void createAndGetPDF(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        //get the spec from the query
        try {
            httpServletRequest.setCharacterEncoding("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        final String spec = httpServletRequest.getParameter("spec");
        if (spec == null) {
            error(httpServletResponse, "Missing 'spec' parameter", 500);
            return;
        }

        File tempFile = null;
        try {
            tempFile = doCreatePDFFile(spec);
            sendPdfFile(httpServletResponse, tempFile);
        } catch (Throwable e) {
            error(httpServletResponse, e);
        } finally {
            deleteFile(tempFile);
        }
    }

    /**
     * Create the PDF and returns to the client (in JSON) the URL to get the PDF.
     */
    private void createPDF(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String basePath) throws ServletException {
        File tempFile = null;
        try {
            BufferedReader data = httpServletRequest.getReader();
            StringBuilder spec = new StringBuilder();
            String cur;
            while ((cur = data.readLine()) != null) {
                spec.append(cur).append("\n");
            }

            tempFile = doCreatePDFFile(spec.toString());
            if (tempFile == null) {
                error(httpServletResponse, "Missing 'spec' parameter", 500);
                return;
            }
        } catch (Throwable e) {
            deleteFile(tempFile);
            error(httpServletResponse, e);
        }

        final String id = generateId(tempFile);
        httpServletResponse.setContentType("application/json; charset=utf-8");
        try {
            final PrintWriter writer = httpServletResponse.getWriter();
            JSONWriter json = new JSONWriter(writer);
            json.object();
            {
                json.key("getURL").value(basePath + "/" + id + TEMP_FILE_SUFFIX);
            }
            json.endObject();
        } catch (JSONException e) {
            deleteFile(tempFile);
            throw new ServletException(e);
        } catch (IOException e) {
            deleteFile(tempFile);
            throw new ServletException(e);
        }
        tempFiles.put(id, tempFile);
    }

    /**
     * To get the PDF created previously.
     */
    private void getPDF(HttpServletResponse httpServletResponse, String id) throws IOException {
        File file = tempFiles.remove(id);
        if (file == null) {
            error(httpServletResponse, "File already requested once", 404);
            return;
        }

        try {
            sendPdfFile(httpServletResponse, file);
        } finally {
            deleteFile(file);
        }
    }

    /**
     * To get (in JSON) the information about the available formats and CO.
     */
    protected void getInfo(HttpServletRequest req, HttpServletResponse resp, String basePath) throws ServletException, IOException {
        MapPrinter printer = getMapPrinter();
        resp.setContentType("application/json; charset=utf-8");
        final PrintWriter writer = resp.getWriter();

        final String var = req.getParameter("var");
        if (var != null) {
            writer.print(var + "=");
        }

        JSONWriter json = new JSONWriter(writer);
        try {
            json.object();
            {
                printer.printClientConfig(json);
                json.key("printURL").value(basePath + PRINT_URL);
                json.key("createURL").value(basePath + CREATE_URL);
            }
            json.endObject();
        } catch (JSONException e) {
            throw new ServletException(e);
        }
        if (var != null) {
            writer.print(";");
        }
        writer.close();
    }


    /**
     * Do the actual work of creating the PDF temporary file.
     */
    private File doCreatePDFFile(String spec) throws IOException, DocumentException, ServletException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Generating PDF for spec=" + spec);
        }

        //create a temporary file that will contain the PDF
        File tempFile = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, getTempDir());
        try {
            FileOutputStream out = new FileOutputStream(tempFile);

            getMapPrinter().print(spec, out);
            out.close();
            return tempFile;
        } catch (IOException e) {
            deleteFile(tempFile);
            throw e;
        } catch (DocumentException e) {
            deleteFile(tempFile);
            throw e;
        } catch (ServletException e) {
            deleteFile(tempFile);
            throw e;
        }
    }

    /**
     * copy the PDF into the output stream
     */
    private void sendPdfFile(HttpServletResponse httpServletResponse, File tempFile) throws IOException {
        FileInputStream pdf = new FileInputStream(tempFile);
        final OutputStream response = httpServletResponse.getOutputStream();
        httpServletResponse.setContentType("application/x-pdf");
        FileUtilities.copyStream(pdf, response);
        pdf.close();
        response.close();
    }

    private void error(HttpServletResponse httpServletResponse, Throwable e) {
        try {
            httpServletResponse.setContentType("text/plain");
            httpServletResponse.setStatus(500);
            PrintWriter out = httpServletResponse.getWriter();
            out.println("Error while generating PDF:");
            e.printStackTrace(out);
            out.close();

            LOGGER.error("Error while generating PDF", e);
        } catch (IOException ex) {
            throw new RuntimeException(e);
        }
    }

    private void error(HttpServletResponse httpServletResponse, String message, int code) {
        try {
            httpServletResponse.setContentType("text/plain");
            httpServletResponse.setStatus(code);
            PrintWriter out = httpServletResponse.getWriter();
            out.println("Error while generating PDF:");
            out.println(message);
            out.close();
            LOGGER.error("Error while generating PDF: " + message);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private File getTempDir() {
        if (tempDir == null) {
            tempDir = (File) getServletContext().
                    getAttribute("javax.servlet.context.tempdir");
            LOGGER.debug("Using '" + tempDir.getAbsolutePath() + "' as temporary directory");
        }
        return tempDir;
    }

    private void deleteFile(File file) {
        if (file != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Deleting PDF file: " + file.getName());
            }
            if (!file.delete()) {
                LOGGER.warn("Cannot delete file:" + file.getAbsolutePath());
            }
        }
    }

    private String generateId(File tempFile) {
        final String name = tempFile.getName();
        return name.substring(
                TEMP_FILE_PREFIX.length(),
                name.length() - TEMP_FILE_SUFFIX.length());
    }
}
